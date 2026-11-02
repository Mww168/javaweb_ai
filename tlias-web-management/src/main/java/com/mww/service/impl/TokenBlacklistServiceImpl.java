package com.mww.service.impl;

import com.mww.common.RedisKeys;
import com.mww.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void blacklist(String jti, Duration ttl) {
        if (jti == null || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(RedisKeys.tokenBlacklist(jti), "1", ttl);
            log.info("token 已加入黑名单: jti={}, ttl={}s", jti, ttl.getSeconds());
        } catch (Exception e) {
            // 退出失败不阻断流程：前端仍会清掉本地 token，只是服务端不再主动拦截
            log.error("token 加入黑名单失败，本次退出仅前端生效: jti={}", jti, e);
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.tokenBlacklist(jti)));
        } catch (Exception e) {
            // fail-open：Redis 故障时宁可放过，也不能把所有正常用户挡在门外
            log.warn("校验 token 黑名单失败，本次放行: jti={}, err={}", jti, e.getMessage());
            return false;
        }
    }
}
