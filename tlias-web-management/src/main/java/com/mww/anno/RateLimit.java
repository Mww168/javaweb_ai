package com.mww.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解。
 * <p>
 * 采用 Redis 固定窗口计数（INCR + EXPIRE，用 Lua 保证原子性）。
 * 适合「防刷」场景：登录、上传等低频高危接口。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 场景名，用于拼接 key，需全局唯一，如 login / upload */
    String key();

    /** 窗口内允许的最大请求次数 */
    int permits() default 10;

    /** 窗口长度（秒） */
    int windowSeconds() default 60;

    /** 限流维度：true 按客户端 IP，false 按当前登录用户（需 TokenInterceptor 已注入） */
    boolean byIp() default true;

    /** 触发限流时的提示文案 */
    String message() default "操作过于频繁，请稍后再试";
}
