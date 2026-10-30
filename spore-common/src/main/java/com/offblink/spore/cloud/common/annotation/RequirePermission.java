package com.offblink.spore.cloud.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限码校验（AOP 切面）：当前角色的 perms 里必须含 {@link #value()}，否则 40301。
 * 权限码来自网关 verify 的返回值，随签名头下发——切面不查库，零 RPC。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    String value();
}
