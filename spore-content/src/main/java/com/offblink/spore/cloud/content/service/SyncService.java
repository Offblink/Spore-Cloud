package com.offblink.spore.cloud.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offblink.spore.cloud.common.BizException;
import com.offblink.spore.cloud.common.ErrorCode;
import com.offblink.spore.cloud.common.dto.SyncPushReq;
import com.offblink.spore.cloud.content.entity.Article;
import com.offblink.spore.cloud.content.entity.Category;
import com.offblink.spore.cloud.content.mapper.SyncMappers.ArticleMapper;
import com.offblink.spore.cloud.content.mapper.SyncMappers.CategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步实现（与 Spore-GUI 单体逐条对齐，protocol 见 design/02 §4）：
 * <ul>
 *   <li><b>LWW</b>：客户端 updated(epoch ms) 严格大于服务端 update_time 才覆盖；</li>
 *   <li><b>墓碑</b>：deleted=1 双向传播，行不真删；pull 查询不过滤 deleted；</li>
 *   <li><b>引用完整性</b>：push 先落 categories 再落 articles；悬空 categoryId → NULL（accepted:true）；</li>
 *   <li><b>游标</b>：nextCursor = 本批最大 update_time（毫秒）；任一类被 limit 截断时，
 *       游标不得越过该类本批最大值，否则另一类会把游标抬走、被截断类的行永久跳过。</li>
 * </ul>
 */
@Service
public class SyncService {

    private static final int LIMIT_MAX = 500;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String EMPTY_CONTENT = "{\"messages\":[]}";

    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final ObjectMapper objectMapper;

    public SyncService(ArticleMapper articleMapper, CategoryMapper categoryMapper,
                       ObjectMapper objectMapper) {
        this.articleMapper = articleMapper;
        this.categoryMapper = categoryMapper;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> pull(Long userId, long cursor, int limit) {
        if (cursor < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "cursor 非法");
        }
        int capped = Math.min(Math.max(limit, 1), LIMIT_MAX);
        LocalDateTime since = toDateTime(cursor);

        List<Category> cats = categoryMapper.selectSince(userId, since, capped);
        List<Article> arts = articleMapper.selectSince(userId, since, capped);

        long maxCat = 0L;
        List<Map<String, Object>> catOut = new ArrayList<Map<String, Object>>(cats.size());
        for (Category c : cats) {
            catOut.add(categoryPayload(c));
            maxCat = Math.max(maxCat, toMillis(c.getUpdateTime()));
        }
        long maxArt = 0L;
        List<Map<String, Object>> artOut = new ArrayList<Map<String, Object>>(arts.size());
        for (Article a : arts) {
            artOut.add(articlePayload(a));
            maxArt = Math.max(maxArt, toMillis(a.getUpdateTime()));
        }

        boolean catsHit = cats.size() >= capped;
        boolean artsHit = arts.size() >= capped;
        long nextCursor;
        if (catsHit || artsHit) {
            long cap = Long.MAX_VALUE;
            if (catsHit) {
                cap = Math.min(cap, maxCat);
            }
            if (artsHit) {
                cap = Math.min(cap, maxArt);
            }
            nextCursor = Math.max(cursor, cap);
        } else {
            nextCursor = Math.max(cursor, Math.max(maxCat, maxArt));
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("categories", catOut);
        data.put("articles", artOut);
        data.put("nextCursor", nextCursor);
        return data;
    }

    @Transactional
    public Map<String, Object> push(Long userId, SyncPushReq req) {
        if (req == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "载荷为空");
        }
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();

        // 第一段：categories（文章引用的科目必须先在）
        if (req.getCategories() != null) {
            for (SyncPushReq.CategoryItem item : req.getCategories()) {
                results.add(resultOf(item.getId(), mergeCategory(userId, item)));
            }
        }
        // 第二段：articles
        if (req.getArticles() != null) {
            for (SyncPushReq.ArticleItem item : req.getArticles()) {
                results.add(resultOf(item.getId(), mergeArticle(userId, item)));
            }
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("results", results);
        return data;
    }

    /** 题图路径回写（带 user 作用域，防跨用户写）；update_time 一起抬 */
    public void bindAttachment(Long userId, String articleId, String path) {
        articleMapper.bindAttachment(userId, articleId, path, LocalDateTime.now(ZONE));
    }

    private boolean mergeCategory(Long userId, SyncPushReq.CategoryItem item) {
        if (item == null || item.getId() == null || item.getId().trim().isEmpty()) {
            return false;
        }
        Category local = categoryMapper.selectByIdAny(item.getId());
        if (local == null) {
            Category c = new Category();
            c.setId(item.getId());
            c.setUserId(userId);
            c.setParentId(item.getParentId());
            c.setName(isBlank(item.getName()) ? "未命名科目" : item.getName());
            c.setSortOrder(item.getSortOrder() == null ? 0 : item.getSortOrder());
            c.setStatus(item.getStatus() == null ? 1 : item.getStatus());
            // 时间戳以手机为准：游标语义不能被服务器时钟覆盖
            c.setCreatedTime(toDateTime(item.getCreated()));
            c.setUpdateTime(updateTimeOf(item.getUpdated()));
            c.setDeleted(item.getDeleted() != null && item.getDeleted() == 1 ? 1 : 0);
            categoryMapper.insert(c);
            return true;
        }
        boolean ownerChanged = !userId.equals(local.getUserId());
        if (ownerChanged) {
            local.setUserId(userId); // 同文章：归属跟推送者走（改绑后重推即迁籍）
        }
        if (!lwwWins(item.getUpdated(), local.getUpdateTime())) {
            if (ownerChanged) {
                categoryMapper.updateFull(local);
            }
            return false;
        }
        if (item.getParentId() != null && item.getParentId().equals(item.getId())) {
            return false; // 自引用保护
        }
        local.setParentId(item.getParentId());
        if (!isBlank(item.getName())) {
            local.setName(item.getName().trim());
        }
        if (item.getSortOrder() != null) {
            local.setSortOrder(item.getSortOrder());
        }
        if (item.getStatus() != null) {
            local.setStatus(item.getStatus());
        }
        local.setUpdateTime(updateTimeOf(item.getUpdated()));
        local.setDeleted(item.getDeleted() != null && item.getDeleted() == 1 ? 1 : 0);
        categoryMapper.updateFull(local);
        return true;
    }

    private boolean mergeArticle(Long userId, SyncPushReq.ArticleItem item) {
        if (item == null || item.getId() == null || item.getId().trim().isEmpty()) {
            return false;
        }
        // 必须看得见墓碑行（updateById/selectById 会滤掉 deleted=1）
        Article local = articleMapper.selectByIdAny(item.getId());
        if (local == null) {
            Article a = new Article();
            a.setId(item.getId());
            a.setUserId(userId);
            a.setCategoryId(categoryExists(userId, item.getCategoryId()));
            a.setTitle(isBlank(item.getTitle()) ? "新会话" : item.getTitle().trim());
            a.setContent(contentOf(item.getMessages()));
            a.setFav(item.getFav() != null && item.getFav() == 1);
            a.setStatus(item.getStatus() == null ? "done" : item.getStatus());
            a.setAuditStatus(item.getAuditStatus() == null ? 2 : item.getAuditStatus());
            a.setOrigin("mobile");
            a.setAttachmentPath(item.getAttachmentPath());
            a.setCreatedTime(toDateTime(item.getCreated()));
            a.setUpdateTime(updateTimeOf(item.getUpdated()));
            a.setDeleted(item.getDeleted() != null && item.getDeleted() == 1 ? 1 : 0);
            articleMapper.insert(a);
            return true;
        }
        // 归属跟推送者走：LAN token 是本机信任边界；改绑后手机重推旧行必须能迁籍
        boolean ownerChanged = !userId.equals(local.getUserId());
        if (ownerChanged) {
            local.setUserId(userId);
        }
        if (!lwwWins(item.getUpdated(), local.getUpdateTime())) {
            if (ownerChanged) {
                articleMapper.updateFull(local); // 内容被 LWW 拒也把归属落库
            }
            return false;
        }
        if (item.getCategoryId() != null) {
            local.setCategoryId(categoryExists(userId, item.getCategoryId()));
        }
        if (!isBlank(item.getTitle())) {
            local.setTitle(item.getTitle().trim());
        }
        if (item.getMessages() != null) {
            local.setContent(contentOf(item.getMessages()));
        }
        if (item.getFav() != null) {
            local.setFav(item.getFav() == 1);
        }
        if (item.getStatus() != null) {
            local.setStatus(item.getStatus());
        }
        if (item.getAuditStatus() != null) {
            local.setAuditStatus(item.getAuditStatus());
        }
        if (item.getAttachmentPath() != null) {
            local.setAttachmentPath(item.getAttachmentPath());
        }
        local.setUpdateTime(updateTimeOf(item.getUpdated()));
        local.setDeleted(item.getDeleted() != null && item.getDeleted() == 1 ? 1 : 0);
        articleMapper.updateFull(local);
        return true;
    }

    /** 悬空科目 → NULL 回未分组（accepted:true，不整批拒收） */
    private String categoryExists(Long userId, String categoryId) {
        if (categoryId == null || categoryId.trim().isEmpty()) {
            return null;
        }
        Category c = categoryMapper.selectById(categoryId.trim());
        return (c != null && userId.equals(c.getUserId())) ? c.getId() : null;
    }

    /** LWW：客户端 updated 严格大于服务端 update_time 才胜（同毫秒保留服务端） */
    private boolean lwwWins(Long clientUpdated, LocalDateTime serverTime) {
        if (clientUpdated == null || serverTime == null) {
            return false;
        }
        return clientUpdated > toMillis(serverTime);
    }

    private Map<String, Object> categoryPayload(Category c) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", c.getId());
        m.put("parentId", c.getParentId());
        m.put("name", c.getName());
        m.put("sortOrder", c.getSortOrder());
        m.put("status", c.getStatus());
        m.put("created", toMillis(c.getCreatedTime()));
        m.put("updated", toMillis(c.getUpdateTime()));
        m.put("deleted", c.getDeleted() == null ? 0 : c.getDeleted());
        return withoutNulls(m);
    }

    private Map<String, Object> articlePayload(Article a) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", a.getId());
        m.put("categoryId", a.getCategoryId());
        m.put("title", a.getTitle());
        m.put("messages", messagesOf(a.getContent()));
        m.put("fav", Boolean.TRUE.equals(a.getFav()) ? 1 : 0);
        m.put("status", a.getStatus());
        m.put("auditStatus", a.getAuditStatus());
        m.put("attachmentPath", a.getAttachmentPath());
        m.put("created", toMillis(a.getCreatedTime()));
        m.put("updated", toMillis(a.getUpdateTime()));
        m.put("deleted", a.getDeleted() == null ? 0 : a.getDeleted());
        return withoutNulls(m);
    }

    /** null 值直接不发（手机端 opt* 读缺省=空串，与 stripNulls 后一致） */
    private static Map<String, Object> withoutNulls(Map<String, Object> m) {
        m.values().removeIf(java.util.Objects::isNull);
        return m;
    }

    private JsonNode messagesOf(String content) {
        if (content == null || content.trim().isEmpty()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode msgs = root == null ? null : root.get("messages");
            return msgs != null && msgs.isArray() ? msgs : objectMapper.createArrayNode();
        } catch (Exception e) {
            return objectMapper.createArrayNode();
        }
    }

    private String contentOf(JsonNode messages) {
        if (messages == null) {
            return EMPTY_CONTENT;
        }
        Map<String, Object> content = new LinkedHashMap<String, Object>();
        content.put("messages", messages);
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            return EMPTY_CONTENT;
        }
    }

    private static Map<String, Object> resultOf(String id, boolean accepted) {
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("id", id);
        r.put("accepted", accepted);
        return r;
    }

    private LocalDateTime toDateTime(Long epochMs) {
        return epochMs == null ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZONE);
    }

    /** 客户端没带 updated 时用服务器当前时间：update_time 为空会让这一行永远游标不到 */
    private LocalDateTime updateTimeOf(Long epochMs) {
        return epochMs == null ? LocalDateTime.now(ZONE).withNano(0) : toDateTime(epochMs);
    }

    private long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZONE).toInstant().toEpochMilli();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
