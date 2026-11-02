package com.mww.service;

import com.fasterxml.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 缓存操作抽象（Cache-Aside 模式）。
 * <p>
 * 设计目标：
 * <ol>
 *   <li><b>防缓存穿透</b>：回源结果为空时缓存空标记（短 TTL），避免同一个不存在的 key 反复打到数据库</li>
 *   <li><b>防缓存击穿</b>：回源前用 SETNX 抢互斥锁，同一 key 只放一个线程回源，其余线程短暂等待后重读</li>
 *   <li><b>防缓存雪崩</b>：TTL 加随机抖动，避免大批 key 同时失效</li>
 *   <li><b>故障降级</b>：Redis 不可用时直接回源，缓存永远不能成为系统的单点</li>
 * </ol>
 */
public interface CacheService {

    /**
     * 读缓存，未命中则调用 loader 回源并写回缓存。
     *
     * @param key    缓存 key
     * @param type   反序列化目标类型（泛型用 {@code new TypeReference<...>() {}}）
     * @param ttl    过期时间，实际写入时会叠加随机抖动
     * @param loader 回源逻辑
     * @return 缓存值或回源值；loader 返回 null 时返回 null
     */
    <T> T get(String key, TypeReference<T> type, Duration ttl, Supplier<T> loader);

    /**
     * 删除缓存（写操作后调用，先更库、再删缓存）。
     */
    void evict(String... keys);
}
