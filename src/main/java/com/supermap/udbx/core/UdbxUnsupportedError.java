package com.supermap.udbx.core;

/**
 * 不支持的数据集 kind 或几何类型。
 */
public class UdbxUnsupportedError extends UdbxError {

    public UdbxUnsupportedError(String what) {
        super(what);
    }
}
