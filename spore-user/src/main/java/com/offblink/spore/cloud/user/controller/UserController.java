package com.offblink.spore.cloud.user.controller;

import com.offblink.spore.cloud.common.IdentityContext;
import com.offblink.spore.cloud.common.R;
import com.offblink.spore.cloud.common.annotation.RequirePermission;
import com.offblink.spore.cloud.common.dto.PageResult;
import com.offblink.spore.cloud.common.dto.UserDtos.UserVO;
import com.offblink.spore.cloud.user.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户接口：/me 给手机做配对连通校验；分页列表考 @RequirePermission 授权切面。 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    /** 当前用户 + 权限码（手机配对后用它验证连通性） */
    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(authService.me(IdentityContext.requireUserId()));
    }

    /** 分页列表（MyBatis-Plus 分页插件 + AOP 权限校验） */
    @GetMapping
    @RequirePermission("user:manage")
    public R<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        IdentityContext.requireUserId();
        return R.ok(authService.page(page, size));
    }
}
