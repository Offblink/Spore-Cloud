package com.offblink.spore.cloud.common.aspect;

import com.offblink.spore.cloud.common.BizException;
import com.offblink.spore.cloud.common.ErrorCode;
import com.offblink.spore.cloud.common.IdentityContext;
import com.offblink.spore.cloud.common.annotation.RequirePermission;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

/**
 * 权限切面：{@link RequirePermission} 标注的方法执行前校验权限码。
 *
 * <p>认证在网关（token → 身份），授权在服务（AOP → 权限码）。权限码随签名头下发，
 * 伪造 {@code X-Spore-Perms} 过不了 HMAC。
 */
@Aspect
public class PermissionAspect {

    @Before("@annotation(requirePermission)")
    public void check(RequirePermission requirePermission) {
        if (!IdentityContext.has(requirePermission.value())) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "无权限：" + requirePermission.value());
        }
    }
}
