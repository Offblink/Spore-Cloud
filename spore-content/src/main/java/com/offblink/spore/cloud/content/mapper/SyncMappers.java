package com.offblink.spore.cloud.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offblink.spore.cloud.content.entity.Article;
import com.offblink.spore.cloud.content.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 同步用 SQL。
 *
 * <p>三条刻意的选择：
 * <ul>
 *   <li><b>增量查询不加 deleted 过滤</b>：墓碑必须下发（deleted=1 的行随 since 一起走）；</li>
 *   <li><b>UPDATE 手写全列</b>：MyBatis-Plus 的 updateById 会自动追加 {@code deleted=0}，
 *       墓碑行（deleted=1）永远更新不到——删了再恢复就静默失败；手写 SQL 才守得住 LWW；</li>
 *   <li><b>category_id 全列覆盖</b>：手机上「移出科目」传空串，语义就是把它写成 NULL；
 *       半截实体更新会把这列漏掉。</li>
 * </ul>
 * {@code update_time > since} 是严格大于：等于游标的行靠下一批 limit 推进。
 */
public final class SyncMappers {

    private SyncMappers() {
    }

    @Mapper
    public interface ArticleMapper extends BaseMapper<Article> {

        @Select("SELECT * FROM article WHERE user_id = #{userId} AND update_time > #{since} "
                + "ORDER BY update_time ASC LIMIT #{limit}")
        List<Article> selectSince(@Param("userId") Long userId,
                                  @Param("since") LocalDateTime since,
                                  @Param("limit") int limit);

        /** 含墓碑：push 合并必须先看见 deleted=1 的行，否则误判不存在 → INSERT 撞主键 */
        @Select("SELECT * FROM article WHERE id = #{id}")
        Article selectByIdAny(@Param("id") String id);

        @Update("UPDATE article SET user_id = #{userId}, category_id = #{categoryId}, "
                + "title = #{title}, content = #{content}, fav = #{fav}, status = #{status}, "
                + "audit_status = #{auditStatus}, attachment_path = #{attachmentPath}, "
                + "update_time = #{updateTime}, deleted = #{deleted} WHERE id = #{id}")
        int updateFull(Article article);

        /** 题图路径回写：抬 update_time，其它端靠增量 pull 看到新路径 */
        @Update("UPDATE article SET attachment_path = #{path}, update_time = #{now} "
                + "WHERE id = #{id} AND user_id = #{userId}")
        int bindAttachment(@Param("userId") Long userId,
                           @Param("id") String id,
                           @Param("path") String path,
                           @Param("now") LocalDateTime now);
    }

    @Mapper
    public interface CategoryMapper extends BaseMapper<Category> {

        @Select("SELECT * FROM category WHERE user_id = #{userId} AND update_time > #{since} "
                + "ORDER BY update_time ASC LIMIT #{limit}")
        List<Category> selectSince(@Param("userId") Long userId,
                                   @Param("since") LocalDateTime since,
                                   @Param("limit") int limit);

        @Select("SELECT * FROM category WHERE id = #{id}")
        Category selectByIdAny(@Param("id") String id);

        @Update("UPDATE category SET user_id = #{userId}, parent_id = #{parentId}, name = #{name}, "
                + "sort_order = #{sortOrder}, status = #{status}, update_time = #{updateTime}, "
                + "deleted = #{deleted} WHERE id = #{id}")
        int updateFull(Category category);
    }
}
