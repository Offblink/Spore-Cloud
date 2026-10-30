package com.offblink.spore.cloud.common.dto;

import java.util.List;

/** 分页返回（MyBatis-Plus 分页插件拆包用，字段与单体 PageResult 一致）。 */
public class PageResult<T> {

    private long total;
    private long page;
    private long size;
    private List<T> records;

    public PageResult() {
    }

    public PageResult(long total, long page, long size, List<T> records) {
        this.total = total;
        this.page = page;
        this.size = size;
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }
}
