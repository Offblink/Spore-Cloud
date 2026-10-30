package com.offblink.spore.cloud.user.config;

import com.offblink.spore.cloud.common.IdentityFilter;
import com.offblink.spore.cloud.common.aspect.OpLogAspect;
import com.offblink.spore.cloud.common.aspect.PermissionAspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Web 层装配：
 * <ol>
 *   <li>身份过滤器（验网关 HMAC 签名 → IdentityContext）；</li>
 *   <li>两个 AOP 切面（操作日志 / 权限校验）——定义在 spore-common（不在本服务扫描包里），
 *       所以在这里显式注册成 Bean。</li>
 * </ol>
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
