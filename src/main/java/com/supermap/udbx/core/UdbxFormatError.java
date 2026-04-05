package com.supermap.udbx.core;

/**
 * 格式错误（损坏的 GAIA BLOB、非法字节序等）。
 */
public class UdbxFormatError extends UdbxError {

    public UdbxFormatError(String message) {
        super(message);
    }

    public UdbxFormatError(String message, Throwable cause) {
        super(message, cause);
    }
}
