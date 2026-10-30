package com.offblink.spore.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offblink.spore.cloud.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 Mapper：BaseMapper 提供 CRUD / 分页，自定义 SQL 写在
 * {@code resources/mapper/UserMapper.xml}（SSM 的 XML 一条不少）。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 按登录名查（含 deleted 过滤；自定义 SQL 不会被 MP 逻辑删除拦截器改写，必须自己带） */
    User selectByUsername(@Param("username") String username);
}
