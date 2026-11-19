package com.saas.kb.ops;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TokenMeterService {

    // 供应商维度：内存累加 (统计全站总消耗)
    private final Map<String, AtomicLong> providerTokens = new ConcurrentHashMap<>();
    
    // 租户维度：Redis 自增 (用于给客户计费)
    @Autowired
    private StringRedisTemplate redis;

    // 单价配置（每 1000 Token 的价格，单位：元）
    private static final Map<String, Double> PRICE_PER_K = Map.of(
            "deepseek", 0.001,  // 假设 DeepSeek 每千 Token 0.001 元
            "ollama", 0.0       // 本地免费
    );

    /**
     * 记录一次调用的 Token 消耗。
     */
    public void record(String provider, String tenantId, long input, long output) {
        long total = input + output;
        
        // 1. 供应商维度：内存累加
        providerTokens.computeIfAbsent(provider, k -> new AtomicLong()).addAndGet(total);
        
        // 2. 租户维度：Redis 自增
        redis.opsForValue().increment("token:" + tenantId, total);
    }

    // 查询某供应商累计 Token
    public long providerTotal(String provider) {
        return providerTokens.getOrDefault(provider, new AtomicLong()).get();
    }

    // 查询某租户累计 Token
    public long tenantTotal(String tenantId) {
        String v = redis.opsForValue().get("token:" + tenantId);
        return v == null ? 0 : Long.parseLong(v);
    }

    // 🌟 课后作业：计算各供应商的总费用（元）
    public Map<String, Double> getCostReport() {
        Map<String, Double> report = new ConcurrentHashMap<>();
        providerTokens.forEach((provider, tokenCount) -> {
            double pricePerK = PRICE_PER_K.getOrDefault(provider, 0.0);
            double cost = (tokenCount.get() / 1000.0) * pricePerK;
            report.put(provider, cost);
        });
        return report;
    }
}