package com.saas.kb.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class DeepSeekProvider implements LlmProvider {

    @Autowired
    private RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${llm.provider.deepseek.base-url}")
    private String baseUrl;
    @Value("${llm.provider.deepseek.api-key}")
    private String apiKey;
    @Value("${llm.provider.deepseek.model}")
    private String model;

    @Override
    public String name() { return "DeepSeek(" + model + ")"; }

    @Override
    public LlmChatResult chat(String message) throws Exception {
        String url = baseUrl + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "你是一个专业、友好的AI助手。"),
                        Map.of("role", "user", "content", message)
                )
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String resp = restTemplate.postForObject(url, entity, String.class);
        JsonNode root = mapper.readTree(resp);

        String content = root.path("choices").get(0).path("message").path("content").asText();

        // 3.4 核心：解析 Token 消耗
        JsonNode usage = root.path("usage");
        int promptT = usage.path("prompt_tokens").asInt(0);
        int compT = usage.path("completion_tokens").asInt(0);
        int totalT = usage.path("total_tokens").asInt(0);

        return new LlmChatResult(content, promptT, compT, totalT);
    }
}