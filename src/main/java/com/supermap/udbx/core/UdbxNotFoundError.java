package com.supermap.udbx.core;

/**
 * 未找到错误（数据集或要素不存在）。
 */
public class UdbxNotFoundError extends UdbxError {

    private final Integer id;

    public UdbxNotFoundError(String what) {
        super(what);
        this.id = null;
    }

    public UdbxNotFoundError(String what, int id) {
        super(what + " (id=" + id + ")");
        this.id = id;
    }

    public Integer getId() {
        return id;
    }
}
