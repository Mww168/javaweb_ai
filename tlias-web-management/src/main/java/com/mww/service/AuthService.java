package com.mww.service;

import com.mww.pojo.Emp;
import com.mww.pojo.LoginInfo;

/**
 * 登录 / 登出。
 * <p>
 * 相比直接在 Controller 里调 {@code EmpService.login}，这里多了一层
 * <b>基于 Redis 的安全策略</b>：
 * <ol>
 *   <li>登录失败次数计数（防暴力破解）：同一账号连续失败 N 次后锁定 M 分钟</li>
 *   <li>登录成功后清空计数</li>
 *   <li>登出时把 token 的 jti 写入黑名单，实现 JWT 的「主动失效」</li>
 * </ol>
 */
public interface AuthService {

    /**
     * 登录。失败次数超限或账号密码错误时抛 {@link com.mww.exception.BusinessException}。
     *
     * @param emp 用户名 + 密码
     * @return 登录信息（含 token）
     */
    LoginInfo login(Emp emp);

    /**
     * 登出：把 token 加入黑名单，直到其自然过期。
     *
     * @param token 请求头中的 token；非法 token 静默忽略
     */
    void logout(String token);
}
