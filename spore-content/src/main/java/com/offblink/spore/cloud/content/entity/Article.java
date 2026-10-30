package com.offblink.spore.cloud.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 会话（article 表）。content 列是 MySQL JSON，这里按原始 JSON 字符串存取：
 * 同步链路上它只是「手机端的 messages 数组原样搬运」，服务端不解析字段，
 * 版本演进也不会丢未知键（强类型 DTO 会把新字段悄悄裁掉）。
 */
@TableName("article")
public class Article {

    /** 会话 id：yyyyMMdd-HHmmssSSS，与手机端文件名同源 */
    @TableId(type = IdType.INPUT)
    private String id;

    private Long userId;

    /** NULL = 未分组；手机上「移出科目」编码成空串 categoryId */
    private String categoryId;

    private String title;

    /** messages 数组整体 JSON */
    private String content;

    private Boolean fav;

    /** done / error / aborted / … */
    private String status;

    /** 0 草稿 1 待审 2 通过 3 驳回 */
    private Integer auditStatus;

    /** mobile / gui */
    private String origin;

    private String attachmentPath;

    private LocalDateTime createdTime;

    private LocalDateTime updateTime;

    /** 墓碑 0/1（同步游标靠 update_time，不真删） */
    private Integer deleted;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getFav() {
        return fav;
    }

    public void setFav(Boolean fav) {
        this.fav = fav;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAuditStatus() {
        return auditStatus;
    }

    public void setAuditStatus(Integer auditStatus) {
        this.auditStatus = auditStatus;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getAttachmentPath() {
        return attachmentPath;
    }

    public void setAttachmentPath(String attachmentPath) {
        this.attachmentPath = attachmentPath;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
