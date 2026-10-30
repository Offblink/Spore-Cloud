package com.offblink.spore.cloud.user.controller;

import com.offblink.spore.cloud.common.BizException;
import com.offblink.spore.cloud.common.ErrorCode;
import com.offblink.spore.cloud.common.Identity;
import com.offblink.spore.cloud.common.IdentityContext;
import com.offblink.spore.cloud.common.R;
import com.offblink.spore.cloud.common.dto.UserDtos.LoginReq;
import com.offblink.spore.cloud.common.dto.UserDtos.LoginVO;
import com.offblink.spore.cloud.common.dto.UserDtos.RegisterReq;
import com.offblink.spore.cloud.common.dto.UserDtos.UserVO;
import com.offblink.spore.cloud.user.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

/** 对外的认证与用户接口（经网关暴露为 /api/**，路由规则见 gateway/application.yml）。 */
public final class ApiControllers {

    private ApiControllers() {
    }

    @RestController
    @RequestMapping("/api/auth")
    public static class AuthController {

        private final AuthService authService;

        public AuthController(AuthService authService) {
            this.authService = authService;
        }

        @PostMapping("/register")
        public R<Long> register(@RequestBody RegisterReq req) {
            return R.ok(authService.register(req));
        }

        @PostMapping("/login")
        public R<LoginVO> login(@RequestBody LoginReq req) {
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

    @RestController
    @RequestMapping("/api/users")
    public static class UserController {

        private final AuthService authService;

        public UserController(AuthService authService) {
            this.authService = authService;
        }

        /** 当前用户 + 权限码；手机配对后用它验证连通性 */
        @GetMapping("/me")
        public R<UserVO> me() {
            return R.ok(authService.me(IdentityContext.requireUserId()));
        }
    }

    /**
     * 服务间内部接口：网关用它校验 token。**不在网关路由表里**（路由只覆盖 /api/**），
     * 外部拿不到这个路径；直连 8081 也只是「验证 token 真伪」的只读操作，不构成提权。
     */
    @RestController
    @RequestMapping("/internal/auth")
    public static class InternalAuthController {

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

        @GetMapping("/ping")
        public R<Map<String, String>> ping() {
            return R.ok(Collections.singletonMap("service", "spore-user"));
        }
    }
}
