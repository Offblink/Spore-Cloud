package com.offblink.spore.cloud.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 网关 → 下游服务的身份传递协议。
 *
 * <p>网关校验 Bearer（JWT 或 LAN token）后，把「userId/roleId/权限码/时间戳/签名」写进请求头；
 * 下游服务用同一个签名密钥验签后才认这份身份——这样绕过网关直连 19081/19082 伪造
 * {@code X-Spore-User} 是无效的（无签名或签名不对一律 401）。
 *
 * <p>签名原文 {@code userId|roleId|perms|ts|path}：路径绑进去防重放打别的接口，
 * 权限码一起签，防止伪造 {@code X-Spore-Perms} 越权。
 */
public final class GatewayHeaders {

    public static final String USER = "X-Spore-User";
    public static final String ROLE = "X-Spore-Role";
    public static final String PERMS = "X-Spore-Perms";
    public static final String TS = "X-Spore-Ts";
    public static final String SIG = "X-Spore-Sig";

    /** 允许的时钟偏差（毫秒）：超过即视为重放 */
    public static final long MAX_SKEW_MS = 5 * 60 * 1000L;

    private GatewayHeaders() {
    }

    public static String sign(String secret, Long userId, Long roleId, String perms, long ts,
                              String path) {
        String raw = userId + "|" + roleId + "|" + (perms == null ? "" : perms) + "|" + ts + "|"
                + (path == null ? "" : path);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    /** 常量时间比较，避免签名被逐字节探测 */
    public static boolean verify(String secret, Long userId, Long roleId, String perms, long ts,
                                 String path, String sig) {
        if (userId == null || roleId == null || sig == null) {
            return false;
        }
        if (Math.abs(System.currentTimeMillis() - ts) > MAX_SKEW_MS) {
            return false;
        }
        String expect = sign(secret, userId, roleId, perms, ts, path);
        return MessageDigest.isEqual(expect.getBytes(StandardCharsets.US_ASCII),
                sig.getBytes(StandardCharsets.US_ASCII));
    }
}
