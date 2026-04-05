package com.supermap.udbx.core;

/**
 * 所有 UDBX 相关错误的基类。
 *
 * <p>遵循 udbx4spec 错误分类规范。
 */
public class UdbxError extends RuntimeException {

    public UdbxError(String message) {
        super(message);
    }

    public UdbxError(String message, Throwable cause) {
        super(message, cause);
    }
}
