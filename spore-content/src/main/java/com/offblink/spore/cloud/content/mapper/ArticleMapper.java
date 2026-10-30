package com.offblink.spore.cloud.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offblink.spore.cloud.content.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话 Mapper。BaseMapper 只管 insert；其余 SQL 全在 {@code resources/mapper/ArticleMapper.xml}。
 *
 * <p>三条刻意的选择（XML 里也写着）：
 * <ul>
 *   <li><b>增量查询不加 deleted 过滤</b>：墓碑必须下发；</li>
 *   <li><b>UPDATE 手写全列</b>：MyBatis-Plus 的 updateById 会自动追加 {@code deleted=0}，
 *       墓碑行永远更新不到——删了再恢复会静默失败；</li>
 *   <li><b>category_id 全列覆盖</b>：手机上「移出科目」传空串，语义就是把它写成 NULL。</li>
 * </ul>
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /** 游标增量（含墓碑）：{@code update_time > since}，严格大于 */
    List<Article> selectSince(@Param("userId") Long userId,
                              @Param("since") LocalDateTime since,
                              @Param("limit") int limit);

    /** 含墓碑的单行查询：push 合并必须先看见 deleted=1 的行，否则误判不存在 → INSERT 撞主键 */
    Article selectByIdAny(@Param("id") String id);

    int updateFull(Article article);

    /** 题图路径回写：抬 update_time，其它端靠增量 pull 看到新路径 */
    int bindAttachment(@Param("userId") Long userId,
                       @Param("id") String id,
                       @Param("path") String path,
                       @Param("now") LocalDateTime now);
}
