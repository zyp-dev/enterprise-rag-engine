package com.saas.kb.service;

import com.saas.kb.client.AiClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class DocService {

    @Autowired
    private AiClient aiClient;

    // 核心：熔断降级
    @CircuitBreaker(name = "aiBreaker", fallbackMethod = "aiFallback")
    public Map<String, Object> analyzeWithProtection(Map<String, String> body) {
        // 这里也要改成 analyze
        return aiClient.analyze(body);
    }

    // 降级兜底
    public Map<String, Object> aiFallback(Map<String, String> body, Throwable t) {
        Map<String, Object> fallbackResult = new HashMap<>();
        fallbackResult.put("sentiment", "未知");
        fallbackResult.put("level", "无法判断（AI 服务不可用）");
        return fallbackResult;
    }
}