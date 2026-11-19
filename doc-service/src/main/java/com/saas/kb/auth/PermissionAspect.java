package com.saas.kb.auth;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

@Aspect
@Component
public class PermissionAspect {

    // 模拟角色-权限映射关系
    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
            "admin", Set.of("document:read", "document:write", "document:delete"),
            "editor", Set.of("document:read", "document:write"),
            "guest", Set.of("document:read")
    );

    @Around("@annotation(requirePermission)")
    public Object check(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        String required = requirePermission.value();
        String role = getCurrentRole();

        // 校验权限
        if (!ROLE_PERMISSIONS.getOrDefault(role, Set.of()).contains(required)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问");
        }

        return joinPoint.proceed();
    }

    private String getCurrentRole() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String role = attrs.getRequest().getHeader("X-User-Role");
        return role == null ? "guest" : role;
    }
}