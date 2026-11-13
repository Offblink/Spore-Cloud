package com.offblink.spore.cloud.user.service;

import com.offblink.spore.cloud.common.BizException;
import com.offblink.spore.cloud.common.ErrorCode;
import com.offblink.spore.cloud.common.dto.UserDtos.QrExchangeVO;
import com.offblink.spore.cloud.common.dto.UserDtos.QrTicketVO;
import com.offblink.spore.cloud.common.dto.UserDtos.QrUserBrief;
import com.offblink.spore.cloud.user.entity.QrLoginTicket;
import com.offblink.spore.cloud.user.entity.User;
import com.offblink.spore.cloud.user.mapper.QrTicketMapper;
import com.offblink.spore.cloud.user.mapper.UserMapper;
import com.offblink.spore.cloud.user.security.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 扫码登录（Spore-Mobile v1.7+ 的唯一登录方式）：服务器页面申请一次性票据 → 手机扫码 →
 * 票据换 JWT。协议见 Spore-Mobile {@code docs/qr-login-server.md}。
 *
 * <p>安全口径：
 * <ul>
 *   <li>库表只存 {@code SHA-256(ticket)}，明文票据只在签发响应里出现一次；</li>
 *   <li>兑换是<b>原子消费</b>（一条 UPDATE 判受影响行数），并发重复兑换只成功一次；</li>
 *   <li>票据与申请人绑定，TTL 默认 60s；同一用户同时最多 3 张可用票（超出的旧票直接作废）；</li>
 *   <li>签发的 JWT 与 {@code /auth/login} 完全同构（同一密钥/有效期），同步接口直接认。</li>
 * </ul>
 */
@Service
public class QrLoginService {

    private static final Logger log = LoggerFactory.getLogger(QrLoginService.class);

    /** 同一用户最多同时存在的可用票据数（防刷新/防刷） */
    private static final int MAX_ACTIVE = 3;
    private static final int TICKET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final QrTicketMapper ticketMapper;
    private final UserMapper userMapper;
    private final TokenService tokenService;
    private final long ttlSeconds;

    public QrLoginService(QrTicketMapper ticketMapper, UserMapper userMapper,
                          TokenService tokenService,
                          @Value("${spore.qr.ticket-ttl-seconds:60}") long ttlSeconds) {
        this.ticketMapper = ticketMapper;
        this.userMapper = userMapper;
        this.tokenService = tokenService;
        this.ttlSeconds = ttlSeconds;
    }

    /** 申请票据（需已登录）。返回的明文只此一次，库里只有哈希。 */
    public QrTicketVO issue(Long userId) {
        expireSurplus(userId);
        String ticket = "qrl_" + randomToken();
        ticketMapper.insertTicket(sha256(ticket), userId, ttlSeconds);
        return new QrTicketVO(ticket, ttlSeconds);
    }

    /** 票据换 JWT（公开接口，无需 Authorization）。 */
    public QrExchangeVO exchange(String rawTicket) {
        String ticket = rawTicket == null ? "" : rawTicket.trim();
        if (ticket.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "缺少登录票据");
        }
        String hash = sha256(ticket);
        if (ticketMapper.consume(hash) == 0) {
            throw exchangeFailure(hash);
        }
        QrLoginTicket row = ticketMapper.selectByHash(hash);
        if (row == null) {
            // 消费成功后行被清理任务删掉：极端竞态，当作过期处理
            throw new BizException(ErrorCode.UNAUTHORIZED, "二维码已过期，请刷新后重试");
        }
        User user = userMapper.selectById(row.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已停用");
        }
        log.info("qr login: uid={} 票据已兑换", user.getId());
        return new QrExchangeVO(tokenService.issue(user.getId()),
                new QrUserBrief(user.getId(), user.getUsername(), user.getNickname()));
    }

    /** 定时清理过期一天以上的行（票据本身 60s 失效，留一天只为排查）。 */
    @Scheduled(fixedDelay = 3_600_000L)
    public void purgeExpired() {
        int removed = ticketMapper.deleteExpiredBefore(LocalDateTime.now().minusDays(1));
        if (removed > 0) {
            log.info("qr login: 清理过期票据 {} 张", removed);
        }
    }

    /** 已达上限时把最旧的几张作废（不删行，expires_at 提前到 NOW()）。 */
    private void expireSurplus(Long userId) {
        List<Long> active = ticketMapper.selectActiveIds(userId);
        if (active.size() < MAX_ACTIVE) {
            return;
        }
        int expireCount = active.size() - MAX_ACTIVE + 1;
        ticketMapper.expireByIds(active.subList(0, expireCount));
    }

    /** 消费失败时区分「已用过」与「过期/不存在」，给手机不同的话术。 */
    private BizException exchangeFailure(String hash) {
        QrLoginTicket row = ticketMapper.selectByHash(hash);
        if (row == null) {
            return new BizException(ErrorCode.UNAUTHORIZED, "二维码无效，请刷新后重试");
        }
        if (row.getUsedAt() != null) {
            return new BizException(ErrorCode.QR_USED, "该二维码已被使用，请刷新后重试");
        }
        return new BizException(ErrorCode.UNAUTHORIZED, "二维码已过期，请刷新后重试");
    }

    private static String randomToken() {
        byte[] buf = new byte[TICKET_BYTES];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，走不到这里
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
