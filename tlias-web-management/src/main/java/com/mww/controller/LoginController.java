package com.mww.controller;

import com.mww.anno.RateLimit;
import com.mww.pojo.Emp;
import com.mww.pojo.LoginInfo;
import com.mww.pojo.Result;
import com.mww.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class LoginController {

    private final AuthService authService;

    /**
     * 登录。
     * <p>
     * 两层防护：
     * <ul>
     *   <li>{@code @RateLimit}：按 IP 限流，防止同一台机器暴力撞库</li>
     *   <li>{@code AuthService} 内部按用户名计数，失败 N 次锁定 M 分钟</li>
     * </ul>
     */
    @RateLimit(key = "login", permits = 10, windowSeconds = 60, message = "登录请求过于频繁，请稍后再试")
    @PostMapping("/login")
    public Result login(@RequestBody Emp emp) {
        // 只打印用户名，避免把密码写进日志
        log.info("登录请求: username={}", emp.getUsername());
        LoginInfo loginInfo = authService.login(emp);
        return Result.success(loginInfo);
    }

    /**
     * 登出：把当前 token 加入 Redis 黑名单，直到其自然过期。
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader("token") String token) {
        authService.logout(token);
        return Result.success();
    }
}
