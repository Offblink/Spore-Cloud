package com.offblink.spore.cloud.user.controller;

import com.offblink.spore.cloud.common.IdentityContext;
import com.offblink.spore.cloud.common.R;
import com.offblink.spore.cloud.common.annotation.OpLog;
import com.offblink.spore.cloud.common.dto.UserDtos.LoginReq;
import com.offblink.spore.cloud.common.dto.UserDtos.LoginVO;
import com.offblink.spore.cloud.common.dto.UserDtos.RegisterReq;
import com.offblink.spore.cloud.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 认证接口（经网关暴露为 /api/auth/**）。@Valid 参数校验 + @OpLog 操作日志切面。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @OpLog("user.register")
    public R<Long> register(@Valid @RequestBody RegisterReq req) {
        return R.ok(authService.register(req));
    }

    @PostMapping("/login")
    @OpLog("user.login")
    public R<LoginVO> login(@Valid @RequestBody LoginReq req) {
        return R.ok(authService.login(req));
    }

    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                          String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authService.logout(authorization.substring(7));
        }
        return R.ok();
    }

    /** LAN token（扫码配对载荷）；取用即改绑到当前登录用户 */
    @GetMapping("/lan-token")
    @OpLog("user.lan-token")
    public R<String> lanToken() {
        return R.ok(authService.lanToken(IdentityContext.requireUserId()));
    }

    @PostMapping("/device-token")
    public R<String> createDeviceToken() {
        return R.ok(authService.createDeviceToken(IdentityContext.requireUserId()));
    }

    /** 设备令牌换 JWT（公开） */
    @PostMapping("/device-login")
    public R<String> deviceLogin(@RequestBody Map<String, String> body) {
        return R.ok(authService.deviceLogin(body.get("deviceToken")));
    }
}
