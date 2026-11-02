package com.mww.aop;

import com.mww.anno.OperateLog;
import com.mww.common.JsonUtils;
import com.mww.common.RedisKeys;
import com.mww.mapper.OperateLogMapper;
import com.mww.pojo.Log;
import com.mww.utils.CurrentHolder;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.mww.utils.JwtUtils;


/**
 * 操作日志切面：记录 com.mww.controller 包下所有增、删、改接口
 */
@Slf4j
@Aspect
@Component
public class OperateLogAspect {

    /** 数据库字段长度限制 */
    private static final int MAX_LENGTH = 2000;

    @Autowired
    private OperateLogMapper operateLogMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /** 队列积压上限：超过后不再入队，改为同步写库，避免 Redis 内存被日志撑爆 */
    private static final long MAX_QUEUE_SIZE = 10_000L;

    /**
     * 切点：com.mww.controller 包及其子包下的所有方法
     * 注意：.. 表示包及其子包，*.*(..) 表示任意类的任意方法任意参数
     */
    @Around("@annotation(com.mww.anno.OperateLog)")
    public Object recordOperateLog(ProceedingJoinPoint joinPoint) throws Throwable {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 1. 只记录增删改接口，查询接口直接放行
        if (!isCudMethod(method)) {
            return joinPoint.proceed();
        }

        // 2. 记录开始时间
        long begin = System.currentTimeMillis();
        Object result = null;
        Throwable exception = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            exception = t;
            throw t;
        } finally {
            long costTime = System.currentTimeMillis() - begin;
            try {
                Log operateLog = buildLog(joinPoint, method, result, costTime, exception);
                // 优先投递到 Redis 队列，由 OperateLogConsumer 异步批量落库：
                // 请求线程只做一次 LPUSH，不再承担 DB 写入的 IO 开销
                if (!pushToQueue(operateLog)) {
                    // Redis 不可用 / 队列已满：降级为同步写库，保证日志不丢
                    operateLogMapper.insert(operateLog);
                }
            } catch (Exception e) {
                // 记录日志失败不能影响主业务
                log.error("保存操作日志失败", e);
            }
        }
    }

    /**
     * 把日志投递到 Redis 队列。
     *
     * @return true 入队成功；false 需要调用方降级同步写库
     */
    private boolean pushToQueue(Log operateLog) {
        String json = JsonUtils.toJson(operateLog);
        if (json == null) {
            return false;
        }
        try {
            Long queueSize = redisTemplate.opsForList().size(RedisKeys.OPERATE_LOG_QUEUE);
            if (queueSize != null && queueSize >= MAX_QUEUE_SIZE) {
                log.warn("操作日志队列积压 {} 条，降级为同步写库", queueSize);
                return false;
            }
            redisTemplate.opsForList().leftPush(RedisKeys.OPERATE_LOG_QUEUE, json);
            return true;
        } catch (Exception e) {
            log.warn("操作日志入队失败，降级为同步写库: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 判断是否为增删改方法：
     * 1) 方法上有 @PostMapping / @PutMapping / @DeleteMapping / @PatchMapping
     * 2) 方法上有 @RequestMapping 且 method 为 POST/PUT/DELETE/PATCH
     * 3) 方法上有自定义 @OperateLog 注解
     */
    private boolean isCudMethod(Method method) {
        if (method.isAnnotationPresent(OperateLog.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)) {
            return true;
        }
        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
        if (requestMapping != null) {
            for (RequestMethod rm : requestMapping.method()) {
                if (rm == RequestMethod.POST || rm == RequestMethod.PUT
                        || rm == RequestMethod.DELETE || rm == RequestMethod.PATCH) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 组装日志实体
     */
    private Log buildLog(ProceedingJoinPoint joinPoint, Method method,
                         Object result, long costTime, Throwable exception) {
        Log operateLog = new Log();
        // 操作人
        operateLog.setOperateEmpId(getCurrentEmpId());
        // 操作时间
        operateLog.setOperateTime(LocalDateTime.now());
        // 目标类全类名（取真实目标类，避免拿到 CGLIB 代理类名）
        operateLog.setClassName(AopUtils.getTargetClass(joinPoint.getTarget()).getName());
        // 目标方法名
        operateLog.setMethodName(method.getName());
        // 方法参数
        operateLog.setMethodParams(truncate(toJson(extractArgs(joinPoint))));
        // 返回值（异常时记录异常信息）
        if (exception != null) {
            operateLog.setReturnValue(truncate("Exception: "
                    + exception.getClass().getSimpleName() + " : " + exception.getMessage()));
        } else {
            operateLog.setReturnValue(truncate(toJson(result)));
        }
        // 执行耗时
        operateLog.setCostTime(costTime);
        return operateLog;
    }

    /**
     * 提取可序列化的方法参数，过滤掉 Request/Response/文件等无法序列化的参数
     */
    private List<Object> extractArgs(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        List<Object> list = new ArrayList<>();
        if (args == null || args.length == 0) {
            return list;
        }
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof ServletRequest
                    || arg instanceof ServletResponse
                    || arg instanceof MultipartFile
                    || arg instanceof MultipartFile[]
                    || arg instanceof BindingResult) {
                continue;
            }
            list.add(arg);
        }
        return list;
    }

    /**
     * 获取当前登录用户 ID
     * 项目里如果有 ThreadLocal（如 BaseContext）保存当前用户，可直接 return BaseContext.getCurrentId();
     */
/*    private Integer getCurrentEmpId() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            Object empId = request.getSession().getAttribute("empId");
            return empId == null ? null : Integer.valueOf(empId.toString());
        } catch (Exception e) {
            log.warn("获取当前登录用户 ID 失败", e);
            return null;
        }
    }*/
    // 从请求头中获取当前登录用户 ID
/*    private Integer getCurrentEmpId() {
        // 1. 获取当前请求
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }

        // 2. 获取请求头中的 token
        String jwt = attributes.getRequest().getHeader("token");
        if (!StringUtils.hasLength(jwt)) {
            return null;
        }

        // 3. 解析 token 获取 empId
        try {
            Claims claims = JwtUtils.parseJwt(jwt);
            // 注意：这里 claims.get("id") 的 "id" 要和登录时存入的 key 一致
            return Integer.valueOf(claims.get("id").toString());
        } catch (Exception e) {
            log.warn("解析 token 获取当前登录用户 ID 失败", e);
            return null;
        }
    }*/
    private Integer getCurrentEmpId() {
        Integer empId = CurrentHolder.getCurrentId();
        if (empId == null) {
            log.warn("当前请求未获取到登录用户 ID，operate_emp_id 将为 null");
        }
        return empId;
    }

    /**
     * 对象转 JSON 字符串
     */
    private String toJson(Object obj) {
        // 统一走 JsonUtils，保证切面、缓存、队列三处序列化口径一致
        return JsonUtils.toJson(obj);
    }

    /**
     * 超长截断，防止超过数据库字段长度
     */
    private String truncate(String str) {
        if (str == null) {
            return null;
        }
        return str.length() > MAX_LENGTH ? str.substring(0, MAX_LENGTH) : str;
    }
}