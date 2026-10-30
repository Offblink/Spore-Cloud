package com.offblink.spore.cloud.user.controller;

import com.offblink.spore.cloud.common.ErrorCode;
import com.offblink.spore.cloud.common.Identity;
import com.offblink.spore.cloud.common.R;
import com.offblink.spore.cloud.user.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 服务间内部接口：网关用它校验 token（认证的唯一事实来源）。
 * **不在网关路由表里**（路由只覆盖 /api/**），外部拿不到这个路径；直连 19081 也只是
 * 「验证 token 真伪」的只读操作，不构成提权。
 */
@RestController
@RequestMapping("/internal/auth")
public class InternalAuthController {

    private final AuthService authService;

    public InternalAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/verify")
    public R<Identity> verify(@RequestBody Map<String, String> body) {
        Identity identity = authService.verify(body.get("token"));
        return identity == null
                ? R.fail(ErrorCode.UNAUTHORIZED, "未登录或 token 失效")
                : R.ok(identity);
    }
}
