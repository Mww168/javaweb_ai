package com.mww.exception;

/**
 * 业务异常。
 * <p>
 * 与系统异常区分开：这类异常的 message 是给用户看的（可直接回显），
 * 不需要打印堆栈，也不应该被全局兜底处理器吞成「请联系管理员」。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
