package com.saas.kb.ops;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多供应商 Failover 组件 + 健康检查（第二章 2.3 课后练习）
 *
 * 健康检查 = 手写版熔断器状态机：
 *   CLOSED（健康）──连续失败 MAX_FAIL 次──▶ OPEN（摘除，冷却期内直接跳过）
 *   OPEN ──冷却 COOLDOWN_MS 到期──▶ HALF_OPEN（放行试探一次）
 *   HALF_OPEN ──成功──▶ CLOSED（恢复健康）／失败──▶ OPEN（再次摘除）
 */
@Service
public class FailoverChatService {

    @Autowired
    private List<LlmProvider> providers; // 顺序就是优先级【主在前、备在后】

    // 🌟 注入计费服务
    @Autowired
    private TokenMeterService tokenMeterService;

    private static final int MAX_FAIL = 3;        // 连续失败 3 次 → 摘除
    private static final long COOLDOWN_MS = 30_000; // 冷却 30 秒后放行试探

    // 每个供应商的健康状态（key = 供应商名，ConcurrentHashMap 保证线程安全）
    private final Map<String, ProviderHealth> health = new ConcurrentHashMap<>();

    public LlmChatResult chat(String message) {
        Exception lastError = null;
        for (LlmProvider provider : providers) {
            String name = provider.name();
            ProviderHealth h = health.computeIfAbsent(name, k -> new ProviderHealth());

            // 1. 冷却期内：直接跳过，试下一个供应商（不浪费真实调用）
            if (h.downUntil > System.currentTimeMillis()) {
                long remain = (h.downUntil - System.currentTimeMillis()) / 1000;
                System.out.println("供应商 " + name + " 冷却中，跳过（剩余 " + remain + " 秒）");
                continue;
            }

            try {
                // 2. 成功：清零计数、清除摘除，恢复健康
                LlmChatResult result = provider.chat(message);
                h.failCount = 0;
                h.downUntil = 0;
                System.out.println("使用供应商: " + name);

                // 🌟 核心：成功调用后，自动记录 Token！
                // 这里为了演示，假设租户 ID 是 "t1"，真实项目从 Request Header 或 Context 获取
                String tenantId = "t1";
                tokenMeterService.record(
                        provider.name().split("\\(")[0].toLowerCase(), // 提取 provider 名字
                        tenantId,
                        result.getPromptTokens(),
                        result.getCompletionTokens()
                );

                return result;
            } catch (Exception e) {
                // 3. 失败：累计连续失败次数；达到阈值就摘除
                lastError = e;
                h.failCount++;
                System.out.println("供应商 " + name + " 失败（连续第 " + h.failCount + " 次）: " + e.getMessage());
                if (h.failCount >= MAX_FAIL) {
                    h.downUntil = System.currentTimeMillis() + COOLDOWN_MS;
                    h.failCount = 0; // 摘除后重置计数，冷却结束后再重新累计
                    System.out.println("供应商 " + name + " 连续失败 " + MAX_FAIL + " 次，摘除 " + (COOLDOWN_MS / 1000) + " 秒");
                }
            }
        }
        // 4. 全部失败/都在冷却：抛统一 503
        System.out.println("所有供应商均失败，最后错误: " + (lastError != null ? lastError.getMessage() : "无"));
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "所有 LLM 供应商均不可用，请稍后重试");
    }

    // 单个供应商的健康状态（内部类）
    static class ProviderHealth {
        int failCount = 0; // 连续失败次数
        long downUntil = 0; // 摘除到期时间戳（毫秒），0 = 未摘除
    }
}
