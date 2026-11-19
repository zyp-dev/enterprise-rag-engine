package com.saas.kb.controller;

import com.saas.kb.ops.GovernedChatService;
import com.saas.kb.ops.LlmChatResult;
import com.saas.kb.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Autowired
    private GovernedChatService governedChatService;

    @Autowired
    private RagService ragService;

    @Value("${server.port}")
    private String port;

    // 项目7：RAG 问答入口（数据流：提问 → ai-service → search-service 检索 → LLM 生成）
    @PostMapping("/rag")
    public Map<String, Object> rag(@RequestBody Map<String, String> body) {
        return ragService.rag(body.getOrDefault("query", ""));
    }

    // 🌟 注意这里：返回值改成了 Map<String, Object>，因为要装 Token 的 Map
    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestBody Map<String, String> body) {
        String content = body.getOrDefault("content", "");

        // 走带治理的链路，接收 LlmChatResult 对象
        LlmChatResult result = governedChatService.chat(content);

        Map<String, Object> response = new HashMap<>();
        // 取出大模型的回复文本
        response.put("reply", result.getContent());
        response.put("instance", port);

        // 把 Token 计量数据组装进去
        response.put("usage", Map.of(
                "promptTokens", result.getPromptTokens(),
                "completionTokens", result.getCompletionTokens(),
                "totalTokens", result.getTotalTokens()
        ));

        return response;
    }
}