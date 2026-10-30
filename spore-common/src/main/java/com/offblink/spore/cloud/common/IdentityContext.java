package com.offblink.spore.cloud.common;

import java.util.Collections;
import java.util.List;

/** 当前请求的调用者身份（由 {@link IdentityFilter} 写入，请求结束清理）。 */
public final class IdentityContext {

    private static final ThreadLocal<Identity> HOLDER = new ThreadLocal<Identity>();

    private IdentityContext() {
    }

    public static void set(Identity identity) {
        HOLDER.set(identity);
    }

    /** 未登录（匿名）返回 null */
    public static Identity get() {
        return HOLDER.get();
    }

    /** 取当前用户 id；匿名/伪造请求返回 null */
    public static Long userId() {
        Identity id = HOLDER.get();
        return id == null ? null : id.getUserId();
    }

    /** 取当前用户 id，匿名直接 401——受保护接口第一行调它 */
    public static long requireUserId() {
        Long id = userId();
        if (id == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或 token 失效");
        }
        return id;
    }

    /** 当前角色的权限码；匿名返回空集合 */
    public static List<String> permissions() {
        Identity id = HOLDER.get();
        return id == null ? Collections.<String>emptyList() : id.getPerms();
    }

    public static boolean has(String code) {
        return permissions().contains(code);
    }

    public static void clear() {
        HOLDER.remove();
    }
}
