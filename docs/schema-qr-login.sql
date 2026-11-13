-- ============================================================================
-- 扫码登录票据表（手机端 Spore-Mobile v1.7+ 的唯一登录方式）
--
-- 客户端协议见 Spore-Mobile/docs/qr-login-server.md。
-- 幂等：只在缺表时建（CREATE TABLE IF NOT EXISTS），升级/重复执行都不清数据。
--
--   mysql -uroot -p spore_cms < docs/schema-qr-login.sql
--
-- 只存 SHA-256(ticket) 的十六进制，不存明文票据：库被翻到也换不到 token。
-- 一次性消费靠 UPDATE ... WHERE used_at IS NULL AND expires_at > NOW() 的
-- 受影响行数判定（并发同一张票只有一个人拿到 1）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `qr_login_ticket` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ticket_hash` char(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'SHA-256(ticket) 十六进制，不存明文',
  `user_id` bigint NOT NULL COMMENT '外键→user；票据与申请者绑定',
  `expires_at` datetime NOT NULL COMMENT '过期时刻（签发时用 DB 的 NOW() 算，不混客户端时钟）',
  `used_at` datetime DEFAULT NULL COMMENT '被兑换的时刻；NULL=未使用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qr_ticket_hash` (`ticket_hash`),
  KEY `idx_qr_expires` (`expires_at`),
  KEY `idx_qr_user` (`user_id`),
  CONSTRAINT `fk_qr_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='扫码登录一次性票据（只存哈希）';
