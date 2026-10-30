package com.offblink.spore.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offblink.spore.cloud.user.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 角色 Mapper（自定义 SQL 见 resources/mapper/RoleMapper.xml）。 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    Role selectByCode(@Param("code") String code);
}
