package com.offblink.spore.cloud.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.offblink.spore.cloud.common.BizException;
import com.offblink.spore.cloud.common.ErrorCode;
import com.offblink.spore.cloud.common.Identity;
import com.offblink.spore.cloud.common.dto.PageResult;
import com.offblink.spore.cloud.common.dto.UserDtos.LoginReq;
import com.offblink.spore.cloud.common.dto.UserDtos.LoginVO;
import com.offblink.spore.cloud.common.dto.UserDtos.RegisterReq;
import com.offblink.spore.cloud.common.dto.UserDtos.UserVO;
import com.offblink.spore.cloud.user.entity.Role;
import com.offblink.spore.cloud.user.entity.User;
import com.offblink.spore.cloud.user.mapper.PermissionMapper;
import com.offblink.spore.cloud.user.mapper.RoleMapper;
import com.offblink.spore.cloud.user.mapper.UserMapper;
import com.offblink.spore.cloud.user.security.TokenService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证与用户管理（SSM 的 Service 层）：BCrypt 口令哈希（绝不明文）+ JWT/LAN token 双通道。
 * 单表查询走 {@code resources/mapper/*.xml}（MyBatis 映射文件），分页走 MyBatis-Plus 插件。
 */
@Service
public class AuthService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final TokenService tokenService;

    public AuthService(UserMapper userMapper, RoleMapper roleMapper,
                       PermissionMapper permissionMapper, TokenService tokenService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.tokenService = tokenService;
    }

    public Long register(RegisterReq req) {
        String username = req.getUsername().trim();
        if (userMapper.selectByUsername(username) != null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "用户名已存在");
        }
        long total = userMapper.selectCount(null);
        // 首个注册者 = 管理员（六表种子只预置角色，用户不预置——口令红线）
        Role role = roleMapper.selectByCode(total == 0 ? "ADMIN" : "USER");
        if (role == null) {
            throw new BizException(ErrorCode.SERVER_ERROR, "角色数据缺失，请先执行六表 SQL");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(ENCODER.encode(req.getPassword()));
        user.setNickname(isBlank(req.getNickname()) ? username : req.getNickname().trim());
        user.setRoleId(role.getId());
        user.setStatus(1);
        userMapper.insert(user);
        if (total == 0) {
            tokenService.bindLanUser(user.getId());
        }
        return user.getId();
    }

    public LoginVO login(LoginReq req) {
        User user = userMapper.selectByUsername(req.getUsername().trim());
        if (user == null || !ENCODER.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已停用");
        }
        Role role = roleMapper.selectById(user.getRoleId());
        LoginVO vo = new LoginVO();
        vo.setToken(tokenService.issue(user.getId()));
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRoleCode(role == null ? null : role.getCode());
        return vo;
    }

    public void logout(String token) {
        tokenService.revoke(token);
    }

    public UserVO me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        Role role = roleMapper.selectById(user.getRoleId());
        UserVO vo = toVo(user, role);
        List<String> codes = permissionMapper.codesOfRole(user.getRoleId());
        vo.setPermissions(codes == null ? new ArrayList<String>() : codes);
        return vo;
    }

    /** 用户分页（MyBatis-Plus 分页插件：自动 count + limit） */
    public PageResult<UserVO> page(int page, int size) {
        Page<User> query = new Page<User>(Math.max(page, 1), Math.min(Math.max(size, 1), 200));
        Page<User> result = userMapper.selectPage(query,
                new LambdaQueryWrapper<User>().orderByAsc(User::getId));
        Map<Long, Role> roles = new HashMap<Long, Role>();
        List<UserVO> rows = new ArrayList<UserVO>(result.getRecords().size());
        for (User u : result.getRecords()) {
            Role role = roles.computeIfAbsent(u.getRoleId(), roleMapper::selectById);
            rows.add(toVo(u, role));
        }
        return new PageResult<UserVO>(result.getTotal(), result.getCurrent(), result.getSize(), rows);
    }

    /** 谁取配对二维码，手机就同步谁：发 token 前先改绑 */
    public String lanToken(Long userId) {
        tokenService.rebindLanUser(userId);
        return tokenService.lanToken();
    }

    public String createDeviceToken(Long userId) {
        return tokenService.createDeviceToken(userId);
    }

    public String deviceLogin(String deviceToken) {
        String jwt = tokenService.deviceLogin(deviceToken);
        if (jwt == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "设备令牌无效，请重新登录");
        }
        return jwt;
    }

    /**
     * 网关内部用：Bearer → 身份（顺带校验账号存在且启用，并把角色权限码一起带出去）。
     * 权限码随签名头下发，下游 {@code @RequirePermission} 切面直接读。
     */
    public Identity verify(String token) {
        Long userId = tokenService.verify(token);
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            return null;
        }
        return new Identity(user.getId(), user.getRoleId(),
                permissionMapper.codesOfRole(user.getRoleId()));
    }

    private UserVO toVo(User user, Role role) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRoleId(user.getRoleId());
        vo.setRoleName(role == null ? null : role.getName());
        vo.setStatus(user.getStatus());
        vo.setCreatedTime(user.getCreatedTime());
        return vo;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
