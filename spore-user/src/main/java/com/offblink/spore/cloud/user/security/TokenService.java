package com.offblink.spore.cloud.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * token 签发/校验（认证的唯一事实来源）。
 *
 * <p>与 Spore-GUI 单体的差异：JWT 签名密钥<b>首次启动生成后持久化</b>进 sys_config，
 * 而不是每次重启随机——服务端重启不再把手机踢回「重新配对」（单机课设也受不了每改一行
 * 代码就要重扫一次）。密钥本身仍零硬编码：运行时生成、只落库。
 *
 * <p>三类凭据同一 Bearer 头：
 * <ol>
 *   <li>登录 JWT（手机「账号直连」/桌面登录）；</li>
 *   <li>LAN token（扫码配对，绑定一个用户，可改绑）；</li>
 *   <li>设备 token（桌面记住本机，换 JWT 用）。</li>
 * </ol>
 */
@Service
public class TokenService {

    private static final String KEY_JWT_SECRET = "cloud_jwt_secret";
    private static final String KEY_LAN_TOKEN = "cloud_lan_token";
    private static final String KEY_LAN_UID = "cloud_lan_token_uid";
    private static final String KEY_DEVICE_TOKEN = "cloud_device_token";
    private static final String KEY_DEVICE_UID = "cloud_device_token_uid";

    /** 作废的 JWT jti（logout）；进程内即可——重启后新密钥让旧 token 全失效前它已无用 */
    private static final Set<String> REVOKED = ConcurrentHashMap.newKeySet();

    @Value("${spore.jwt.expire-hours:720}")
    private long expireHours;

    private final SysConfigStore store;
    private SecretKey jwtKey;
    private volatile String lanToken;
    private volatile Long lanTokenUserId;

    public TokenService(SysConfigStore store) {
        this.store = store;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        this.jwtKey = Keys.hmacShaKeyFor(loadOrCreateSecret());
        String lan = store.get(KEY_LAN_TOKEN);
        if (lan == null || lan.isEmpty()) {
            lan = randomHex(24);
            store.put(KEY_LAN_TOKEN, lan);
        }
        this.lanToken = lan;
        String uid = store.get(KEY_LAN_UID);
        this.lanTokenUserId = (uid == null || uid.isEmpty()) ? null : Long.valueOf(uid);
    }

    private byte[] loadOrCreateSecret() {
        String b64 = store.get(KEY_JWT_SECRET);
        if (b64 == null || b64.isEmpty()) {
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            b64 = Base64.getEncoder().encodeToString(raw);
            store.put(KEY_JWT_SECRET, b64);
        }
        return Base64.getDecoder().decode(b64);
    }

    public String issue(Long userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireHours * 3600_000L))
                .signWith(jwtKey)
                .compact();
    }

    /** 校验 Bearer token → 用户 id；无效/过期/已作废返回 null */
    public Long verify(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        if (token.equals(lanToken)) {
            return lanTokenUserId;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(jwtKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (REVOKED.contains(claims.getId())) {
                return null;
            }
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public void revoke(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(jwtKey).build()
                    .parseSignedClaims(token).getPayload();
            REVOKED.add(claims.getId());
        } catch (JwtException | IllegalArgumentException ignored) {
            // 非法 token 本来就过不了校验
        }
    }

    public synchronized void bindLanUser(Long userId) {
        if (lanTokenUserId != null) {
            return;
        }
        lanTokenUserId = userId;
        store.put(KEY_LAN_UID, String.valueOf(userId));
    }

    /** 改绑 LAN token 到当前用户（谁展示配对信息，手机就同步谁；token 本体不变） */
    public synchronized void rebindLanUser(Long userId) {
        lanTokenUserId = userId;
        store.put(KEY_LAN_UID, String.valueOf(userId));
    }

    public String lanToken() {
        return lanToken;
    }

    public synchronized String createDeviceToken(Long userId) {
        String token = randomHex(24);
        store.put(KEY_DEVICE_TOKEN, token);
        store.put(KEY_DEVICE_UID, String.valueOf(userId));
        return token;
    }

    public String deviceLogin(String deviceToken) {
        if (deviceToken == null || deviceToken.isEmpty()) {
            return null;
        }
        String stored = store.get(KEY_DEVICE_TOKEN);
        if (stored == null || !stored.equals(deviceToken)) {
            return null;
        }
        String uid = store.get(KEY_DEVICE_UID);
        return (uid == null || uid.isEmpty()) ? null : issue(Long.valueOf(uid));
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        new SecureRandom().nextBytes(buf);
        StringBuilder sb = new StringBuilder(buf.length * 2);
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
