package com.offblink.spore.cloud.gateway.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 带负载均衡的 WebClient：{@code http://spore-user} 这样的服务名由
 * Spring Cloud LoadBalancer + Eureka 解析成真实实例。
 * 网关是 WebFlux 栈，所以不引入 OpenFeign（阻塞客户端塞进 Netty 事件循环会拖死整个网关）。
 */
@Configuration
public class LoadBalancedWebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
