package com.supermap.udbx.core;

/**
 * IO 错误（文件读写失败）。
 */
public class UdbxIOError extends UdbxError {

    public UdbxIOError(String message) {
        super(message);
    }

    public UdbxIOError(String message, Throwable cause) {
        super(message, cause);
    }

    public UdbxIOError(Throwable cause) {
        super(cause.getMessage(), cause);
    }
}
