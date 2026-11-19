package com.saas.kb.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "ai-service")
public interface AiClient {

    // 统一方法名为 analyze，返回类型用 Map<String, Object> 兼容 Token 等对象
    @PostMapping("/ai/analyze")
    Map<String, Object> analyze(@RequestBody Map<String, String> body);

}