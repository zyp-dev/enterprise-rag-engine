package com.saas.kb;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * 项目7 网关 RBAC 鉴权（需求文档：gateway 职责 = 统一入口 + 路由 + RBAC 鉴权）
 *
 * 规则：
 *   请求头 X-User-Role 标识角色（admin/editor/guest，缺省 guest）
 *   POST /doc/** 需要 document:write
 *   GET  /doc/** 需要 document:read
 *   其余路由放行（/ai/**、/search/**）
 * 鉴权失败直接返回 403，请求不再转发到下游。
 * （doc-service 内部的 @RequirePermission + PermissionAspect 保留，作为纵深防御）
 */
@Component
public class RbacAuthFilter implements GlobalFilter, Ordered {

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            "admin", Set.of("document:read", "document:write", "document:delete"),
            "editor", Set.of("document:read", "document:write"),
            "guest", Set.of("document:read")
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        String required = requiredPermission(method, path);
        if (required == null) {
            return chain.filter(exchange); // 该路径无需鉴权
        }

        String role = exchange.getRequest().getHeaders().getFirst("X-User-Role");
        if (role == null || role.isEmpty()) {
            role = "guest";
        }

        if (!ROLE_PERMISSIONS.getOrDefault(role, Set.of()).contains(required)) {
            return reject(exchange, "无权限访问（需要 " + required + "，当前角色 " + role + "）");
        }
        return chain.filter(exchange);
    }

    /**
     * 路径 → 所需权限（null = 不校验）
     */
    private String requiredPermission(HttpMethod method, String path) {
        if (path.startsWith("/doc/")) {
            if (HttpMethod.POST.equals(method)) {
                return "document:write";
            }
            return "document:read";
        }
        return null;
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // 在路由转发之前执行
    }
}
