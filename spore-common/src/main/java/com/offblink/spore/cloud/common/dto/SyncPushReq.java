package com.offblink.spore.cloud.common.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 同步上推载荷（design/02 §4，与手机端 SyncEngine.push 逐字段对齐）：
 * created/updated = epoch 毫秒；deleted=1 = 墓碑；categoryId 空串 = 移出科目。
 *
 * <p>{@code messages} 用 {@link JsonNode} 原样接：手机端的消息结构随版本演进，
 * 服务端不解析字段、只存原文，回吐时也不会丢未知键（早期版本用强类型 DTO 会静默裁字段）。
 */
public class SyncPushReq {

    private List<CategoryItem> categories;
    private List<ArticleItem> articles;

    public List<CategoryItem> getCategories() {
        return categories;
    }

    public void setCategories(List<CategoryItem> categories) {
        this.categories = categories;
    }

    public List<ArticleItem> getArticles() {
        return articles;
    }

    public void setArticles(List<ArticleItem> articles) {
        this.articles = articles;
    }

    public static class CategoryItem {
        private String id;
        private String parentId;
        private String name;
        private Integer sortOrder;
        private Integer status;
        private Long created;
        private Long updated;
        private Integer deleted;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getParentId() {
            return parentId;
        }

        public void setParentId(String parentId) {
            this.parentId = parentId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public Long getCreated() {
            return created;
        }

        public void setCreated(Long created) {
            this.created = created;
        }

        public Long getUpdated() {
            return updated;
        }

        public void setUpdated(Long updated) {
            this.updated = updated;
        }

        public Integer getDeleted() {
            return deleted;
        }

        public void setDeleted(Integer deleted) {
            this.deleted = deleted;
        }
    }

    public static class ArticleItem {
        private String id;
        private String categoryId;
        private String title;
        private JsonNode messages;
        /** 0/1（手机端传数字，不是布尔） */
        private Integer fav;
        private String status;
        private Integer auditStatus;
        private String attachmentPath;
        private Long created;
        private Long updated;
        private Integer deleted;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
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

        public JsonNode getMessages() {
            return messages;
        }

        public void setMessages(JsonNode messages) {
            this.messages = messages;
        }

        public Integer getFav() {
            return fav;
        }

        public void setFav(Integer fav) {
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

        public String getAttachmentPath() {
            return attachmentPath;
        }

        public void setAttachmentPath(String attachmentPath) {
            this.attachmentPath = attachmentPath;
        }

        public Long getCreated() {
            return created;
        }

        public void setCreated(Long created) {
            this.created = created;
        }

        public Long getUpdated() {
            return updated;
        }

        public void setUpdated(Long updated) {
            this.updated = updated;
        }

        public Integer getDeleted() {
            return deleted;
        }

        public void setDeleted(Integer deleted) {
            this.deleted = deleted;
        }
    }
}
