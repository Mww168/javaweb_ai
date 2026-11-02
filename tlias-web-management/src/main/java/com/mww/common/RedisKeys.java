package com.mww.common;

import java.time.Duration;

/**
 * Redis Key 统一管理。
 * <p>
 * 命名规范：{@code tlias:{业务域}:{数据类型}:{标识}}
 * <ul>
 *   <li>{@code cache:} 缓存数据，可随时删除重建</li>
 *   <li>{@code auth:} 鉴权相关，删除会导致登录态/计数失效</li>
 *   <li>{@code queue:} 异步队列</li>
 *   <li>{@code rate:} 限流计数器</li>
 *   <li>{@code lock:} 互斥锁</li>
 * </ul>
 * 统一前缀便于用 {@code SCAN tlias:*} 观测，也避免与同一实例上的其他应用冲突。
 */
public final class RedisKeys {

    /** 全局命名空间前缀 */
    private static final String NS = "tlias:";

    // ==================== TTL 常量 ====================

    /** 部门列表：极少变动，30 分钟 */
    public static final Duration TTL_DEPT_LIST = Duration.ofMinutes(30);

    /** 班级下拉列表：较少变动，30 分钟 */
    public static final Duration TTL_CLAZZ_LIST = Duration.ofMinutes(30);

    /** 统计报表：允许 5 分钟延迟 */
    public static final Duration TTL_REPORT = Duration.ofMinutes(5);

    // ==================== 缓存 Key ====================

    public static String deptList() {
        return NS + "cache:dept:list";
    }

    public static String clazzList() {
        return NS + "cache:clazz:list";
    }

    public static String reportEmpJob() {
        return NS + "cache:report:emp-job";
    }

    public static String reportEmpGender() {
        return NS + "cache:report:emp-gender";
    }

    public static String reportStudentDegree() {
        return NS + "cache:report:student-degree";
    }

    public static String reportStudentCount() {
        return NS + "cache:report:student-count";
    }

    /** 缓存重建互斥锁：同一个 cacheKey 只允许一个线程回源 */
    public static String cacheLock(String cacheKey) {
        return NS + "lock:cache:" + cacheKey;
    }

    // ==================== 鉴权 Key ====================

    /**
     * token 黑名单，按 JWT 的 jti 存储。
     * 注意：TTL 必须与 token 剩余有效期对齐，token 自然过期后这条记录自动消失，不会无限增长。
     */
    public static String tokenBlacklist(String jti) {
        return NS + "auth:token:black:" + jti;
    }

    /** 登录失败计数器，按用户名维度 */
    public static String loginFail(String username) {
        return NS + "auth:login:fail:" + username;
    }

    // ==================== 限流 Key ====================

    public static String rateLimit(String scene, String identity) {
        return NS + "rate:" + scene + ":" + identity;
    }

    // ==================== 队列 Key ====================

    /** 操作日志异步队列（List 结构） */
    public static final String OPERATE_LOG_QUEUE = NS + "queue:operate-log";

    private RedisKeys() {
    }
}
