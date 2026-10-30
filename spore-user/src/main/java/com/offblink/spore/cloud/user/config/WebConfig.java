package com.offblink.spore.cloud.user.config;

import com.offblink.spore.cloud.common.IdentityFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 注册身份过滤器：验网关 HMAC 签名 → IdentityContext（防绕过网关伪造身份头）。 */
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
}
