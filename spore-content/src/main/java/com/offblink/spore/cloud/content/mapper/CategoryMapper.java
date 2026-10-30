package com.offblink.spore.cloud.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offblink.spore.cloud.content.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 科目 Mapper（SQL 见 resources/mapper/CategoryMapper.xml）。 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    List<Category> selectSince(@Param("userId") Long userId,
                               @Param("since") LocalDateTime since,
                               @Param("limit") int limit);

    Category selectByIdAny(@Param("id") String id);

    int updateFull(Category category);
}
