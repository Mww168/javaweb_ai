package com.mww.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mww.common.JsonUtils;
import com.mww.common.RedisKeys;
import com.mww.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Redis 缓存实现。
 * <p>
 * 所有 Redis 操作都包在 safeXxx 方法里：<b>缓存异常一律降级为回源</b>，
 * 绝不能因为 Redis 抖动导致整个业务接口不可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisCacheServiceImpl implements CacheService {

    /** 空值占位符：用于防穿透，缓存「查不到」这个事实 */
    private static final String NULL_TAG = "__NULL__";

    /** 空值缓存时间：短一些，避免数据刚创建却一直读到空 */
    private static final Duration NULL_TTL = Duration.ofSeconds(60);

    /** 重建锁的过期时间：必须大于一次回源的耗时，防止锁泄漏后永久阻塞 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    /** 未抢到锁的线程最多等这么久再回源，避免长时间阻塞 Tomcat 线程 */
    private static final long RETRY_WAIT_MS = 100L;

    /** TTL 抖动上限（秒）：30 分钟 TTL 随机 ±30 秒，打散失效时间 */
    private static final int TTL_JITTER_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;

    /** 总开关：压测、排障时可临时关掉缓存直连数据库 */
    @Value("${app.cache.enabled:true}")
    private boolean enabled;

    @Override
    public <T> T get(String key, TypeReference<T> type, Duration ttl, Supplier<T> loader) {
        if (!enabled) {
            return loader.get();
        }

        // 1. 查缓存（命中空标记直接返回 null）
        String cached = safeGet(key);
        if (NULL_TAG.equals(cached)) {
            return null;
        }
        if (cached != null) {
            T hit = safeParse(cached, type, key);
            if (hit != null) {
                return hit;
            }
            // 脏数据 / 类型不兼容：删掉重来
            safeDelete(key);
        }

        // 2. 未命中：抢重建锁，避免大量并发同时回源把数据库打穿
        String lockKey = RedisKeys.cacheLock(key);
        Boolean locked = safeSetIfAbsent(lockKey, LOCK_TTL);

        if (Boolean.FALSE.equals(locked)) {
            // 其他线程正在回源，短暂等待后重读一次缓存
            sleepQuietly(RETRY_WAIT_MS);
            String again = safeGet(key);
            if (NULL_TAG.equals(again)) {
                return null;
            }
            if (again != null) {
                T hit = safeParse(again, type, key);
                if (hit != null) {
                    return hit;
                }
            }
            // 仍取不到就不再等待，直接回源（宁可多查一次库，也不能让线程堆积）
        }

        // 3. 回源并写回缓存
        T value;
        try {
            value = loader.get();
        } finally {
            if (Boolean.TRUE.equals(locked)) {
                safeDelete(lockKey);
            }
        }

        if (value == null) {
            safeSet(key, NULL_TAG, NULL_TTL);
        } else {
            String json = JsonUtils.toJson(value);
            if (json != null) {
                safeSet(key, json, jitter(ttl));
            }
        }
        return value;
    }

    @Override
    public void evict(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        try {
            redisTemplate.delete(Arrays.asList(keys));
            log.debug("清除缓存: {}", (Object) keys);
        } catch (Exception e) {
            // 删缓存失败退化为「等 TTL 自然过期」，不影响业务返回
            log.warn("清除缓存失败，等待 TTL 自然过期: keys={}, err={}", (Object) keys, e.getMessage());
        }
    }

    // ==================== 内部工具方法 ====================

    /** 加随机抖动，防止同一批写入的 key 在同一秒集体失效 */
    private Duration jitter(Duration ttl) {
        return ttl.plusSeconds(ThreadLocalRandom.current().nextLong(TTL_JITTER_SECONDS));
    }

    private String safeGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("读取缓存失败，降级回源: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * @return true 抢到锁；false 被其他线程持有；null Redis 异常（调用方需跳过等待直接回源）
     */
    private Boolean safeSetIfAbsent(String key, Duration ttl) {
        try {
            return redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        } catch (Exception e) {
            log.warn("获取缓存重建锁失败，降级回源: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    private void safeSet(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("写入缓存失败: key={}, err={}", key, e.getMessage());
        }
    }

    private void safeDelete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("删除缓存失败: key={}, err={}", key, e.getMessage());
        }
    }

    private <T> T safeParse(String json, TypeReference<T> type, String key) {
        try {
            return JsonUtils.fromJson(json, type);
        } catch (Exception e) {
            log.warn("缓存内容反序列化失败，将删除该 key: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
