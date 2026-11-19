package com.saas.kb.service;

import com.saas.kb.client.SearchClient;
import com.saas.kb.ops.GovernedChatService;
import com.saas.kb.ops.LlmChatResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目7 RAG 编排者（按需求文档的数据流）：
 *   用户提问 → ai-service（本类）→ 调 search-service 混合检索拿上下文
 *           → 拼提示词 → 走治理链（Failover + 熔断限流 + Token 计量）调 LLM 生成
 * 返回的 query / contexts / answer 正是 RAGAS 评测需要的输入字段
 */
@Service
public class RagService {

    @Autowired
    private SearchClient searchClient;      // Feign：search-service 混合检索

    @Autowired
    private GovernedChatService governedChatService; // LLM 生成（带治理链）

    /**
     * RAG 完整链路
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> rag(String query) {
        // 1. 调 search-service 混合检索
        Map<String, Object> searchResult = searchClient.search(Map.of("query", query));
        List<Map<String, Object>> contexts = (List<Map<String, Object>>) searchResult.getOrDefault("contexts", List.of());

        // 2. 把上下文拼进提示词
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contexts.size(); i++) {
            sb.append(i + 1).append(". ").append(contexts.get(i).get("content")).append("\n");
        }
        String prompt = "请严格根据以下上下文回答问题，如果上下文没有答案，请回答\"文档中没有相关信息\"。\n\n"
                + "上下文：\n" + sb + "\n问题：" + query;

        // 3. 走治理链生成（自动经过 Failover / 限流熔断 / Token 计量，并记录 LLM 耗时 Timer）
        LlmChatResult chatResult = governedChatService.chat(prompt);

        Map<String, Object> result = new HashMap<>();
        result.put("query", query);
        result.put("contexts", contexts);
        result.put("answer", chatResult.getContent());
        result.put("usage", Map.of(
                "promptTokens", chatResult.getPromptTokens(),
                "completionTokens", chatResult.getCompletionTokens(),
                "totalTokens", chatResult.getTotalTokens()
        ));
        return result;
    }
}
