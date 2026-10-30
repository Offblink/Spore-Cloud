package com.offblink.spore.cloud.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 下游服务的身份过滤器：验网关签名 → 写 {@link IdentityContext}。
 *
 * <p>没有身份头 = 匿名（放行，受保护接口自己用 {@link IdentityContext#requireUserId()} 拦）；
 * 有身份头但验签失败 = 401（防绕过网关伪造 X-Spore-User）。
 * servlet 服务启动时把它注册成 {@code /*} 过滤器（见各服务 WebConfig）。
 */
public class IdentityFilter implements Filter {

    private final String secret;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IdentityFilter(String secret) {
        this.secret = secret;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String userHeader = req.getHeader(GatewayHeaders.USER);
        String sig = req.getHeader(GatewayHeaders.SIG);
        if (userHeader != null || sig != null) {
            Long userId = parseLong(userHeader);
            Long roleId = parseLong(req.getHeader(GatewayHeaders.ROLE));
            long ts = parseLongOrZero(req.getHeader(GatewayHeaders.TS));
            String path = req.getRequestURI();
            if (!GatewayHeaders.verify(secret, userId, roleId, ts, path, sig)) {
                unauthorized(resp, "身份签名无效");
                return;
            }
            IdentityContext.set(new Identity(userId, roleId));
        }
        try {
            chain.doFilter(request, response);
        } finally {
            IdentityContext.clear();
        }
    }

    private void unauthorized(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(401);
        resp.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(resp.getWriter(),
                R.fail(ErrorCode.UNAUTHORIZED, message));
    }

    private static Long parseLong(String s) {
        try {
            return s == null ? null : Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long parseLongOrZero(String s) {
        try {
            return s == null ? 0L : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
