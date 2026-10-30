package com.offblink.spore.cloud.content;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import com.offblink.spore.cloud.common.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** 内容服务：手机端同步的唯一数据面（科目 / 会话 / 题图）。 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)   // spore-common 的统一异常出口不在本包扫描范围
@MapperScan("com.offblink.spore.cloud.content.mapper")
public class ContentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentApplication.class, args);
    }
}
