package com.offblink.spore.cloud.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 权限码查询（AOP 授权用；SQL 见 resources/mapper/PermissionMapper.xml）。 */
@Mapper
public interface PermissionMapper {

    /** 角色对应的权限码，如 user:manage / article:delete */
    List<String> codesOfRole(@Param("roleId") Long roleId);
}
