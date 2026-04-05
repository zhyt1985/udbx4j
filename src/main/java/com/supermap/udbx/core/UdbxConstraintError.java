package com.supermap.udbx.core;

/**
 * 约束错误（重复 ID、必填字段缺失等）。
 */
public class UdbxConstraintError extends UdbxError {

    public UdbxConstraintError(String what) {
        super(what);
    }
}
