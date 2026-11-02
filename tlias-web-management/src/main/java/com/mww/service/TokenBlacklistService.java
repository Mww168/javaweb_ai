package com.mww.service;

import java.time.Duration;

/**
 * JWT 黑名单。
 * <p>
 * JWT 是无状态的，签发后服务端无法收回。要实现「退出登录 / 强制下线」，
 * 必须在服务端保存一份失效名单。这里用 Redis 存 jti（JWT ID）而非整个 token：
 * <ul>
 *   <li>token 很长（几百字节），jti 是 UUID，占用小得多</li>
 *   <li>TTL 与 token 剩余有效期一致，token 自然过期后记录自动清除，内存不会无限增长</li>
 * </ul>
 */
public interface TokenBlacklistService {

    /**
     * 将 jti 加入黑名单。
     *
     * @param jti  JWT 唯一标识
     * @param ttl  存活时间，应等于 token 剩余有效期
     */
    void blacklist(String jti, Duration ttl);

    /**
     * 判断 jti 是否已失效。
     * <b>Redis 异常时必须放行（fail-open）</b>，否则 Redis 一抖动全站用户都被踢下线。
     */
    boolean isBlacklisted(String jti);
}
