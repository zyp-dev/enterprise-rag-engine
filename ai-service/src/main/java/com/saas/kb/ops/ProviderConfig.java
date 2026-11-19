package com.saas.kb.ops;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
public class ProviderConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); // 连接超时 3秒
        factory.setReadTimeout(15000);   // 读取超时 15秒 (大模型生成较慢)
        return new RestTemplate(factory);
    }

    // 定义优先级：DeepSeek 排前面（主），Ollama 排后面（备）
    @Bean
    public List<LlmProvider> providers(DeepSeekProvider deepseek, OllamaProvider ollama) {
        return List.of(deepseek, ollama);
    }
}