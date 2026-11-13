package com.offblink.spore.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.offblink.spore.cloud.user.entity.QrLoginTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 扫码登录票据 Mapper：CRUD 走 BaseMapper，四条语义敏感的 SQL 写在
 * {@code resources/mapper/QrTicketMapper.xml}（原子消费必须手写，别用 select-then-update）。
 */
@Mapper
public interface QrTicketMapper extends BaseMapper<QrLoginTicket> {

    /**
     * 签发：过期时刻由 DB 的 NOW() 计算，避免应用与数据库时钟/时区漂移。
     *
     * @param ttlSeconds 有效期（秒）
     */
    int insertTicket(@Param("ticketHash") String ticketHash,
                     @Param("userId") Long userId,
                     @Param("ttlSeconds") long ttlSeconds);

    /**
     * 原子消费：只有「未用 + 未过期」的行能被改成已用。
     *
     * @return 受影响行数，0 = 无效/过期/已被换过（并发同一张票只有一个人拿到 1）
     */
    int consume(@Param("ticketHash") String ticketHash);

    QrLoginTicket selectByHash(@Param("ticketHash") String ticketHash);

    /** 该用户当前可用（未用且未过期）的票据 id，按签发顺序升序 */
    List<Long> selectActiveIds(@Param("userId") Long userId);

    /** 把指定票据直接作废（把 expires_at 提前到 NOW()，行保留便于审计） */
    int expireByIds(@Param("ids") List<Long> ids);

    /** 清理早就过期一天以上的行（定时任务） */
    int deleteExpiredBefore(@Param("before") LocalDateTime before);
}
