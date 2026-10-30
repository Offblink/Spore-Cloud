package com.offblink.spore.cloud.user.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * sys_config 键值表访问。
 * {@code key} 是 MySQL 保留字，MyBatis-Plus 生成的 SQL 不会自动加反引号，所以这里
 * 直接走 JdbcTemplate 手写 SQL——比给实体配一堆 {@code @TableField("`key`")} 更省心的做法。
 *
 * <p>本服务用的键统一加 {@code cloud_} 前缀：同一台数据库上还挂着 Spore-GUI 单体，
 * 它的 lan_token/jwt 语义不能被这边覆盖。
 */
@Component
public class SysConfigStore {

    private final JdbcTemplate jdbc;

    public SysConfigStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String get(String key) {
        List<String> rows = jdbc.queryForList(
                "SELECT `value` FROM sys_config WHERE `key` = ?", String.class, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void put(String key, String value) {
        jdbc.update("INSERT INTO sys_config(`key`, `value`) VALUES(?, ?) "
                + "ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)", key, value);
    }
}
