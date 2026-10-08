package com.mww.interceptor;

import com.mww.utils.CurrentHolder;
import com.mww.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
@Slf4j
@Component
public class TokenInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
       /* System.out.println(">>> 拦截到: " + request.getMethod() + " " + request.getRequestURI());
        String url = request.getRequestURL().toString();
        //判断是否是登录请求
        if (url.contains("login")) {
            //放行
            log.info("放行登录请求");
            return true;
        }*/
        //获取请求头token
        String token = request.getHeader("token");

        //判断是否有token，没有返回401
        if (token == null || token.isEmpty()) {
            log.info("未携带token，返回401");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        //解析token，不合法返回401
        try {
            Claims claims = JwtUtils.parseJwt(token);
            Integer empId = Integer.valueOf(claims.get("id").toString());
            // ★★★ 关键：塞进 ThreadLocal
            CurrentHolder.setCurrentId(empId);
        } catch (Exception e) {
            log.info("token解析失败，返回401");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        //放行
        log.info("放行请求");
        return true;

    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // ★★★ 关键：请求结束清理，防止线程复用造成脏数据
        CurrentHolder.remove();
    }
}
