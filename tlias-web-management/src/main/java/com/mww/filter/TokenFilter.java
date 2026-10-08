package com.mww.filter;

import com.mww.utils.JwtUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;

import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
/*@WebFilter("/*")*/
public class TokenFilter implements Filter {
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        //获取请求的url
        String url = request.getRequestURL().toString();
        //判断是否是登录请求
        if (url.contains("/login")) {
            //放行
            log.info("放行登录请求");
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        //获取请求头token
        String token = request.getHeader("token");

        //判断是否有token，没有返回401
        if (token == null || token.isEmpty()) {
            log.info("未携带token，返回401");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        //解析token，不合法返回401
            try {
                JwtUtils.parseJwt(token);
            } catch (Exception e) {
                log.info("token解析失败，返回401");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
        }
        //放行
        log.info("放行请求");
        filterChain.doFilter(servletRequest, servletResponse);
    }
}
