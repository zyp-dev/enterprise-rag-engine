package com.saas.kb.controller;

import com.saas.kb.service.EvalBridgeService;
import com.saas.kb.service.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目7 联调闭环（按需求文档数据流，编排在 ai-service）：
 *   RAG 生成 → 提交 RAGAS 评测（异步）→ 轮询结果
 *
 * 调用方式：
 *   POST /ai/evaluate {"query": "年假有几天", "ground_truth": "参考答案(可选)"}
 *     → 返回 {"taskId": "...", "query": ..., "contexts": ..., "answer": ...}
 *   GET  /ai/evaluate/{taskId}
 *     → {"status": "done", "scores": {"faithfulness": 1.0, ...}}
 */
@RestController
@RequestMapping("/ai")
public class EvaluateController {

    @Autowired
    private RagService ragService;

    @Autowired
    private EvalBridgeService evalBridge;

    /**
     * 一键评测：先跑真实 RAG 拿 answer/contexts，再异步提交给 metric-service 打分
     */
    @PostMapping("/evaluate")
    public Map<String, Object> evaluate(@RequestBody Map<String, String> body) {
        String query = body.getOrDefault("query", "");
        String groundTruth = body.getOrDefault("ground_truth", "");

        // 1. 跑真实 RAG 链路（search-service 检索 + 治理链生成）
        Map<String, Object> ragResult = ragService.rag(query);
        String answer = String.valueOf(ragResult.getOrDefault("answer", ""));

        // 2. 提取 contexts 的纯文本列表（RAGAS 要的是字符串列表）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contexts = (List<Map<String, Object>>) ragResult.get("contexts");
        List<String> contextTexts = new ArrayList<>();
        for (Map<String, Object> c : contexts) {
            contextTexts.add(String.valueOf(c.get("content")));
        }

        // 3. 异步提交评测（不阻塞，拿到 task_id 就返回）
        String taskId = evalBridge.submitEval(query, contextTexts, answer, groundTruth);

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("query", ragResult.get("query"));
        result.put("contexts", contexts);
        result.put("answer", answer);
        result.put("usage", ragResult.get("usage"));
        result.put("hint", "约 30~60 秒后，GET /ai/evaluate/" + taskId + " 查分数");
        return result;
    }

    /**
     * 轮询评测结果
     */
    @GetMapping("/evaluate/{taskId}")
    public Map<?, ?> getResult(@PathVariable String taskId) {
        return evalBridge.getEvalResult(taskId);
    }
}
