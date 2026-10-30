package com.offblink.spore.cloud.content.config;

import com.offblink.spore.cloud.common.IdentityFilter;
import com.offblink.spore.cloud.common.aspect.OpLogAspect;
import com.offblink.spore.cloud.common.aspect.PermissionAspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 身份过滤器 + 两个 AOP 切面。
 * 切面定义在 spore-common（不在本服务扫描包里），所以显式注册成 Bean。
 */
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<IdentityFilter> identityFilter(
            @Value("${spore.gateway.sign-secret}") String signSecret) {
        FilterRegistrationBean<IdentityFilter> bean =
                new FilterRegistrationBean<IdentityFilter>(new IdentityFilter(signSecret));
        bean.addUrlPatterns("/*");
        bean.setOrder(1);
        return bean;
    }

    @Bean
    public OpLogAspect opLogAspect(JdbcTemplate jdbcTemplate) {
        return new OpLogAspect(jdbcTemplate);
    }

    @Bean
    public PermissionAspect permissionAspect() {
        return new PermissionAspect();
    }
}
