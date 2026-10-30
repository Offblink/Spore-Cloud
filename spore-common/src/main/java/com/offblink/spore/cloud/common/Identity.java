package com.offblink.spore.cloud.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 经网关校验后的调用者身份（user 服务查库得出，随请求头下发）。
 * 下游服务只信「带网关签名的」那一份，见 {@link GatewayHeaders}。
 *
 * <p>{@code perms} 是角色权限码（如 {@code user:manage}）：认证在网关做、授权在服务做
 * （{@code @RequirePermission} 切面读它），避免每个请求再向 user 服务查一次权限表。
 */
public class Identity {

    private Long userId;
    private Long roleId;
    private List<String> perms = new ArrayList<String>();

    public Identity() {
    }

    public Identity(Long userId, Long roleId, List<String> perms) {
        this.userId = userId;
        this.roleId = roleId;
        this.perms = perms == null ? new ArrayList<String>() : perms;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public List<String> getPerms() {
        return perms == null ? new ArrayList<String>() : perms;
    }

    public void setPerms(List<String> perms) {
        this.perms = perms;
    }
}
