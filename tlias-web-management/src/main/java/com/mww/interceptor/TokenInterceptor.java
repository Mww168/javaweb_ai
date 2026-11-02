package com.mww.interceptor;

import com.mww.service.TokenBlacklistService;
import com.mww.utils.CurrentHolder;
import com.mww.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenInterceptor implements HandlerInterceptor {

    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取请求头 token
        String token = request.getHeader("token");

        // 没有 token 直接 401
        if (token == null || token.isEmpty()) {
            log.info("未携带 token，返回 401");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 解析 token：签名非法、过期、格式错误都在这里被拦下
        Claims claims;
        Integer empId;
        try {
            claims = JwtUtils.parseJwt(token);
            empId = Integer.valueOf(claims.get("id").toString());
        } catch (Exception e) {
            log.info("token 解析失败，返回 401: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 查黑名单：用户已退出登录的 token 立即失效（这是 JWT 无状态下的「主动吊销」）
        // 内部 fail-open：Redis 故障时放行，避免缓存抖动把全站用户踢下线
        if (tokenBlacklistService.isBlacklisted(claims.getId())) {
            log.info("token 已注销，返回 401: jti={}", claims.getId());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 校验全部通过后再写 ThreadLocal，保证不会残留脏数据
        CurrentHolder.setCurrentId(empId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 关键：请求结束清理，防止 Tomcat 线程复用造成用户串号
        CurrentHolder.remove();
    }
}
