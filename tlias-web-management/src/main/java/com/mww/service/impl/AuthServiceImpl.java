package com.mww.service.impl;

import com.mww.common.RedisKeys;
import com.mww.exception.BusinessException;
import com.mww.pojo.Emp;
import com.mww.pojo.LoginInfo;
import com.mww.service.AuthService;
import com.mww.service.EmpService;
import com.mww.service.TokenBlacklistService;
import com.mww.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 登录 / 登出实现。
 * <p>
 * 所有 Redis 计数操作都做了异常兜底：<b>限流组件故障不能演化成「任何人都登不上」</b>。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final EmpService empService;
    private final TokenBlacklistService tokenBlacklistService;
    private final StringRedisTemplate redisTemplate;

    /** 允许的连续失败次数 */
    @Value("${app.auth.max-login-fail:5}")
    private int maxLoginFail;

    /** 锁定时长（分钟），同时也是失败计数的过期时间 */
    @Value("${app.auth.lock-minutes:15}")
    private int lockMinutes;

    @Override
    public LoginInfo login(Emp emp) {
        String username = emp == null ? null : emp.getUsername();
        String failKey = StringUtils.hasText(username) ? RedisKeys.loginFail(username) : null;

        // 1. 先看是否已被锁定
        if (failKey != null) {
            long fails = readFailCount(failKey);
            if (fails >= maxLoginFail) {
                long remainMinutes = Math.max(readTtlMinutes(failKey), 1);
                throw new BusinessException("登录失败次数过多，账号已锁定，请 " + remainMinutes + " 分钟后再试");
            }
        }

        // 2. 校验账号密码
        LoginInfo loginInfo = empService.login(emp);

        // 3. 失败：累加计数并给出剩余次数
        if (loginInfo == null) {
            if (failKey == null) {
                throw new BusinessException("用户名或密码错误");
            }
            long current = increaseFail(failKey);
            if (current < 0) {
                // Redis 不可用，退化为原始提示，不阻断登录流程
                throw new BusinessException("用户名或密码错误");
            }
            long left = maxLoginFail - current;
            if (left <= 0) {
                log.warn("账号 {} 连续登录失败 {} 次，已锁定 {} 分钟", username, current, lockMinutes);
                throw new BusinessException("登录失败次数过多，账号已锁定 " + lockMinutes + " 分钟");
            }
            throw new BusinessException("用户名或密码错误，还可尝试 " + left + " 次");
        }

        // 4. 成功：清空失败计数
        if (failKey != null) {
            clearFail(failKey);
        }
        return loginInfo;
    }

    @Override
    public void logout(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        Claims claims;
        try {
            claims = JwtUtils.parseJwt(token);
        } catch (Exception e) {
            // token 本身非法或已过期，无需拉黑
            log.debug("登出时 token 解析失败，忽略: {}", e.getMessage());
            return;
        }

        String jti = claims.getId();
        Date expiration = claims.getExpiration();
        if (!StringUtils.hasText(jti) || expiration == null) {
            return;
        }

        // TTL 与 token 剩余有效期对齐：token 过期后黑名单记录自动消失
        Duration ttl = Duration.ofMillis(expiration.getTime() - System.currentTimeMillis());
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        tokenBlacklistService.blacklist(jti, ttl);
    }

    // ==================== Redis 计数（全部容错） ====================

    private long readFailCount(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            log.warn("读取登录失败次数失败，跳过锁定校验: key={}, err={}", key, e.getMessage());
            return 0L;
        }
    }

    /**
     * @return 累加后的次数；Redis 异常返回 -1
     */
    private long increaseFail(String key) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                return -1L;
            }
            // 第一次失败时设置过期时间，避免计数器永久驻留
            if (count == 1L) {
                redisTemplate.expire(key, lockMinutes, TimeUnit.MINUTES);
            }
            return count;
        } catch (Exception e) {
            log.warn("累加登录失败次数失败，本次不计数: key={}, err={}", key, e.getMessage());
            return -1L;
        }
    }

    private void clearFail(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("清除登录失败次数失败: key={}, err={}", key, e.getMessage());
        }
    }

    private long readTtlMinutes(String key) {
        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (ttl == null || ttl <= 0) {
                return lockMinutes;
            }
            return Math.max(ttl / 60, 1);
        } catch (Exception e) {
            return lockMinutes;
        }
    }
}
