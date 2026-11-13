package com.offblink.spore.cloud.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import com.offblink.spore.cloud.common.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 用户服务：认证的唯一事实来源（签发 JWT / 管理 LAN token / 校验 token）。 */
@SpringBootApplication
@EnableScheduling                       // 定时清理过期扫码票据（QrLoginService.purgeExpired）
@Import(GlobalExceptionHandler.class)   // spore-common 的统一异常出口不在本包扫描范围
@MapperScan("com.offblink.spore.cloud.user.mapper")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
