package com.saas.kb.controller;

import com.saas.kb.service.DocumentIndexService;
import com.saas.kb.service.HybridSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * search-service：只负责检索（按需求文档数据流，RAG 编排在 ai-service）
 */
@RestController
@RequestMapping("/search")
public class SearchController {

    @Autowired
    private HybridSearchService hybridSearchService;

    @Autowired
    private DocumentIndexService documentIndexService;

    @Value("${search.top-k:3}")
    private int topK;

    /**
     * 纯检索：只返回 Top-K 上下文分块（含每条的三种得分）
     * 调用方式：POST /search/search {"query": "年假有几天"}
     */
    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "");
        List<HybridSearchService.SearchResult> results = hybridSearchService.search(query);

        List<Map<String, Object>> contexts = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, results.size()); i++) {
            HybridSearchService.SearchResult r = results.get(i);
            Map<String, Object> c = new HashMap<>();
            c.put("documentId", r.documentId);
            c.put("chunkIndex", r.chunkIndex);
            c.put("content", r.content);
            c.put("keywordScore", r.keywordScore);
            c.put("vectorScore", r.vectorScore);
            c.put("totalScore", r.totalScore);
            contexts.add(c);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("query", query);
        result.put("contexts", contexts);
        return result;
    }

    /**
     * 手动重建索引（兜底用；正常情况下 Kafka 事件会自动建索引）
     * 调用方式：POST /search/index/rebuild
     */
    @PostMapping("/index/rebuild")
    public Map<String, Object> rebuild() {
        int count = documentIndexService.rebuildFromDb();
        Map<String, Object> result = new HashMap<>();
        result.put("indexed", count);
        return result;
    }
}
