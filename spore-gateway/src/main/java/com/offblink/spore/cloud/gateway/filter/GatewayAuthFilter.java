package com.offblink.spore.cloud.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offblink.spore.cloud.common.ErrorCode;
import com.offblink.spore.cloud.common.GatewayHeaders;
import com.offblink.spore.cloud.common.Identity;
import com.offblink.spore.cloud.common.R;
import com.offblink.spore.cloud.gateway.client.AuthClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 网关鉴权：唯一入口，先认 token 再放行。
 *
 * <ol>
 *   <li>白名单（注册/登录/设备登录/健康检查）直通；</li>
 *   <li>其余 {@code /api/**} 必须带 {@code Authorization: Bearer <token>}，
 *       交给 user 服务校验（JWT 或 LAN token 都认）；</li>
 *   <li>校验通过 → 写带 HMAC 签名的身份头并转发；下游服务只信这份签名；</li>
 *   <li>客户端自带的 {@code X-Spore-*} 一律先删掉——否则伪造头能混进下游。</li>
 * </ol>
 */
@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    /** 无需登录的接口（与 Spore-GUI 的拦截器排除项一致） */
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/device-login"
    );

    private final AuthClient authClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String signSecret;

    public GatewayAuthFilter(AuthClient authClient,
                             @Value("${spore.gateway.sign-secret}") String signSecret) {
        this.authClient = authClient;
        this.signSecret = signSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isPublic(path)) {
            return chain.filter(withScrubbedHeaders(exchange, null));
        }

        String token = bearerToken(exchange.getRequest().getHeaders());
        if (token == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED,
                    R.fail(ErrorCode.UNAUTHORIZED, "未登录或 token 失效"));
        }

        return authClient.verify(token)
                .flatMap(identity -> exchange.getResponse().isCommitted()
                        ? Mono.empty()
                        : chain.filter(withScrubbedHeaders(exchange, identity)))
                .switchIfEmpty(Mono.defer(() -> reject(exchange, HttpStatus.UNAUTHORIZED,
                        R.fail(ErrorCode.UNAUTHORIZED, "未登录或 token 失效"))))
                .onErrorResume(e -> reject(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                        R.fail(ErrorCode.SERVER_ERROR, "认证服务不可用")));
    }

    private static boolean isPublic(String path) {
        return PUBLIC_PATHS.contains(path) || path.startsWith("/actuator");
    }

    private static String bearerToken(HttpHeaders headers) {
        String auth = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        String token = auth.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    /** 删掉客户端可能伪造的身份头；有 identity 时写入签名头 */
    private ServerWebExchange withScrubbedHeaders(ServerWebExchange exchange, Identity identity) {
        String path = exchange.getRequest().getURI().getPath();
        ServerHttpRequest request = exchange.getRequest().mutate().headers(h -> {
            h.remove(GatewayHeaders.USER);
            h.remove(GatewayHeaders.ROLE);
            h.remove(GatewayHeaders.TS);
            h.remove(GatewayHeaders.SIG);
            if (identity != null) {
                long ts = System.currentTimeMillis();
                h.set(GatewayHeaders.USER, String.valueOf(identity.getUserId()));
                h.set(GatewayHeaders.ROLE, String.valueOf(identity.getRoleId()));
                h.set(GatewayHeaders.TS, String.valueOf(ts));
                h.set(GatewayHeaders.SIG, GatewayHeaders.sign(signSecret,
                        identity.getUserId(), identity.getRoleId(), ts, path));
            }
        }).build();
        return exchange.mutate().request(request).build();
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, R<?> body) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            bytes = "{\"code\":50000,\"message\":\"网关错误\"}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
