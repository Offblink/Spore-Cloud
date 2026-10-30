package com.offblink.spore.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offblink.spore.cloud.user.entity.Entities.Role;
import com.offblink.spore.cloud.user.entity.Entities.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** user 服务的三个 Mapper。 */
public final class Mappers {

    private Mappers() {
    }

    @Mapper
    public interface UserMapper extends BaseMapper<User> {
    }

    @Mapper
    public interface RoleMapper extends BaseMapper<Role> {
    }

    @Mapper
    public interface PermissionMapper {
        /** 角色对应的权限码（显式 deleted=0，不依赖 MP 的逻辑删除拦截器） */
        @Select("SELECT code FROM permission WHERE role_id = #{roleId} AND deleted = 0")
        List<String> codesOfRole(@Param("roleId") Long roleId);
    }
}
