package com.saas.kb.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class OllamaProvider implements LlmProvider {

    @Autowired
    private RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${llm.provider.ollama.base-url}")
    private String baseUrl;
    @Value("${llm.provider.ollama.model}")
    private String model;

    @Override
    public String name() { return "Ollama(" + model + ")"; }

    @Override
    public LlmChatResult chat(String message) throws Exception {
        String url = baseUrl + "/api/chat";

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "你是一个专业、友好的AI助手。"),
                        Map.of("role", "user", "content", message)
                ),
                "stream", false
        );

        String resp = restTemplate.postForObject(url, body, String.class);
        JsonNode root = mapper.readTree(resp);

        String content = root.path("message").path("content").asText();

        // Ollama 的 Token 统计字段
        int promptT = root.path("prompt_eval_count").asInt(0);
        int compT = root.path("eval_count").asInt(0);

        return new LlmChatResult(content, promptT, compT, promptT + compT);
    }
}