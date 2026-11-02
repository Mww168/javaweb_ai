package com.mww.aop;

import com.mww.anno.RateLimit;
import com.mww.common.RedisKeys;
import com.mww.exception.BusinessException;
import com.mww.utils.CurrentHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 限流切面：固定窗口计数器。
 * <p>
 * 为什么用 Lua：{@code INCR} 和 {@code EXPIRE} 是两条命令，
 * 如果分开执行，进程在两步之间挂掉会导致计数 key 永不过期（内存泄漏），
 * 并发下还可能读到「已自增但还没设过期」的中间态。Lua 在 Redis 内单线程原子执行，两者不可分割。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    /**
     * KEYS[1] = 计数器 key
     * ARGV[1] = 窗口秒数
     * ARGV[2] = 允许次数
     * 返回 1 表示放行，0 表示超限
     */
    private static final DefaultRedisScript<Long> LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            if current > tonumber(ARGV[2]) then
                return 0
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Before("@annotation(rateLimit)")
    public void checkRateLimit(JoinPoint joinPoint, RateLimit rateLimit) {
        String identity = rateLimit.byIp() ? resolveClientIp() : resolveCurrentUser();
        if (!StringUtils.hasText(identity)) {
            return;
        }

        String key = RedisKeys.rateLimit(rateLimit.key(), identity);
        try {
            Long allowed = redisTemplate.execute(
                    LIMIT_SCRIPT,
                    List.of(key),
                    String.valueOf(rateLimit.windowSeconds()),
                    String.valueOf(rateLimit.permits()));

            if (allowed != null && allowed == 0L) {
                log.warn("触发限流: key={}, method={}", key, joinPoint.getSignature().toShortString());
                throw new BusinessException(rateLimit.message());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // fail-open：Redis 故障时放行，限流只是保护措施，不能反向导致服务不可用
            log.warn("限流校验失败，本次放行: key={}, err={}", key, e.getMessage());
        }
    }

    /**
     * 取真实客户端 IP。
     * 经过 Nginx 等反向代理时 {@code getRemoteAddr()} 拿到的是代理地址，
     * 需要优先读 {@code X-Forwarded-For}（取第一个，即最初的客户端）。
     */
    private String resolveClientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();

        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded) && !"unknown".equalsIgnoreCase(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    private String resolveCurrentUser() {
        Integer empId = CurrentHolder.getCurrentId();
        return empId == null ? null : String.valueOf(empId);
    }
}
