package com.offblink.spore.cloud.common;

/**
 * 经网关校验后的调用者身份（user 服务查库得出，随请求头下发）。
 * 下游服务只信「带网关签名的」那一份，见 {@link GatewayHeaders}。
 */
public class Identity {

    private Long userId;
    private Long roleId;

    public Identity() {
    }

    public Identity(Long userId, Long roleId) {
        this.userId = userId;
        this.roleId = roleId;
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
}
