package com.offblink.spore.cloud.gateway.client;

import com.offblink.spore.cloud.common.Identity;
import com.offblink.spore.cloud.common.R;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;

/**
 * 调用户服务做 token 校验：网关自己不解析 JWT、也不碰 LAN token 的库表——
 * 认证的唯一事实来源是 user 服务（token 签发/吊销/改绑都在它那）。
 */
@Component
public class AuthClient {

    private static final ParameterizedTypeReference<R<Identity>> TYPE =
            new ParameterizedTypeReference<R<Identity>>() {
            };

    private final WebClient web;

    public AuthClient(WebClient.Builder loadBalancedWebClientBuilder) {
        this.web = loadBalancedWebClientBuilder.baseUrl("http://spore-user").build();
    }

    /** 校验 Bearer token；无效返回空 Mono（调用方转 401） */
    public Mono<Identity> verify(String token) {
        return web.post()
                .uri("/internal/auth/verify")
                .body(BodyInserters.fromValue(Collections.singletonMap("token", token)))
                .retrieve()
                .bodyToMono(TYPE)
                .flatMap(r -> r != null && r.getCode() == 0 && r.getData() != null
                        ? Mono.just(r.getData())
                        : Mono.<Identity>empty());
    }
}
