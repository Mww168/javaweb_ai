package com.mww.task;

import com.mww.common.JsonUtils;
import com.mww.common.RedisKeys;
import com.mww.mapper.OperateLogMapper;
import com.mww.pojo.Log;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志消费者：把 AOP 切面投递到 Redis List 的日志批量落库。
 * <p>
 * 为什么用 List 而不是直接同步写库：
 * <ul>
 *   <li>写库是 IO，放在请求线程里会抬高接口 RT；异步后请求线程只做一次 O(1) 的 LPUSH</li>
 *   <li>{@code RPOP} 是原子的，多实例部署时天然不会重复消费（每个元素只会被一个消费者取走）</li>
 *   <li>DB 短暂不可用时消息留在队列里，不会丢</li>
 * </ul>
 * 进阶方案见 docs/Redis优化方案.md：消息可靠性要求更高时改用 Redis Stream + 消费组 + XACK。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperateLogConsumer {

    private final StringRedisTemplate redisTemplate;
    private final OperateLogMapper operateLogMapper;

    /** 单批最大条数，避免一次 IN 太多行导致大事务 */
    @Value("${app.log.batch-size:50}")
    private int batchSize;

    /**
     * 固定延迟轮询：上一轮结束后间隔 consumeDelay 毫秒再执行，
     * 不会因为某一轮耗时过长而堆积任务（fixedRate 会）。
     */
    @Scheduled(fixedDelayString = "${app.log.consume-delay:2000}")
    public void consume() {
        List<String> batch;
        try {
            // rightPop + count：一次网络往返批量取出，减少 RTT
            batch = redisTemplate.opsForList().rightPop(RedisKeys.OPERATE_LOG_QUEUE, batchSize);
        } catch (Exception e) {
            // Redis 不可用：本轮跳过，消息仍在队列里，等下一轮
            log.debug("读取操作日志队列失败，跳过本轮: {}", e.getMessage());
            return;
        }
        if (batch == null || batch.isEmpty()) {
            return;
        }

        List<Log> logs = new ArrayList<>(batch.size());
        for (String json : batch) {
            try {
                logs.add(JsonUtils.fromJson(json, Log.class));
            } catch (Exception e) {
                // 单条脏数据不阻塞整批，丢弃并告警
                log.warn("操作日志格式非法，已丢弃: {}", json);
            }
        }
        if (logs.isEmpty()) {
            return;
        }

        try {
            operateLogMapper.insertBatch(logs);
            log.debug("操作日志批量落库成功: {} 条", logs.size());
        } catch (Exception e) {
            log.error("操作日志批量落库失败，回滚到队列重试: {} 条", logs.size(), e);
            requeue(batch);
        }
    }

    /**
     * 落库失败时把原始消息塞回队列头部，等待下一轮重试。
     * 极端情况下（库长时间不可用）队列会持续增长，需要监控 LLEN，见文档「运维要点」。
     */
    private void requeue(List<String> batch) {
        try {
            redisTemplate.opsForList().leftPushAll(RedisKeys.OPERATE_LOG_QUEUE, batch);
        } catch (Exception e) {
            log.error("操作日志重新入队失败，本批日志丢失: {} 条", batch.size(), e);
        }
    }
}
