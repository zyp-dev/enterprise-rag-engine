package com.saas.kb.ops;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GovernedChatService {

    @Autowired
    private FailoverChatService failoverChatService;

    // 作业：Micrometer 指标注册表，用它自定义「LLM 调用耗时」Timer
    @Autowired
    private MeterRegistry meterRegistry;

    // 核心：返回值改成 LlmChatResult
    // 用 Timer.Sample 记录每次真实 LLM 调用的耗时：
    //   - 重试时每次尝试都会记一个样本（@Retry 会重新进入本方法）
    //   - status 标签区分成功/失败，Grafana 里可以分开画线
    @RateLimiter(name = "llmRateLimiter", fallbackMethod = "rateLimitFallback")
    @CircuitBreaker(name = "llmBreaker", fallbackMethod = "circuitBreakerFallback")
    @Retry(name = "llmRetry", fallbackMethod = "retryFallback")
    public LlmChatResult chat(String message) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String status = "success";
        try {
            return failoverChatService.chat(message);
        } catch (RuntimeException e) {
            status = "error";
            throw e;
        } finally {
            sample.stop(Timer.builder("llm_call_duration")
                    .description("LLM 调用耗时（治理链内单次尝试）")
                    .tag("status", status)
                    .register(meterRegistry));
        }
    }

    // 1. 限流降级
    public LlmChatResult rateLimitFallback(String message, RequestNotPermitted e) {
        System.out.println("触发限流降级：" + e.getMessage());
        return new LlmChatResult("请求过于频繁，请稍后重试（限流保护）", 0, 0, 0);
    }

    // 2. 熔断降级
    public LlmChatResult circuitBreakerFallback(String message, CallNotPermittedException e) {
        System.out.println("触发熔断降级：" + e.getMessage());
        return new LlmChatResult("服务繁忙，请稍后重试（熔断保护）", 0, 0, 0);
    }

    // 3. 重试降级
    public LlmChatResult retryFallback(String message, Throwable e) {
        System.out.println("触发重试降级：" + e.getMessage());
        return new LlmChatResult("服务繁忙，请稍后再试（重试耗尽）", 0, 0, 0);
    }
}
