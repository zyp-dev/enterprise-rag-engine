package com.saas.kb.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 项目7 联调桥：直连调用 metric-service（Python 离线评测服务）
 * metric-service 不进 Nacos，Java 服务通过 HTTP 直连；
 * 评测是异步的——提交后拿 task_id，再轮询拿结果，热路径不阻塞。
 */
@Service
public class EvalBridgeService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${metric.base-url}")
    private String metricBaseUrl;

    /**
     * 提交一次评测：把 RAG 的四个字段发给 metric-service，立即返回 task_id
     */
    public String submitEval(String question, List<String> contexts, String answer, String groundTruth) {
        String url = metricBaseUrl + "/evaluate";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "question", question,
                "contexts", contexts,
                "answer", answer,
                "ground_truth", groundTruth == null ? "" : groundTruth
        );
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        Map<?, ?> resp = restTemplate.postForObject(url, entity, Map.class);
        return String.valueOf(resp.get("task_id"));
    }

    /**
     * 轮询评测结果：返回 {"status": running/done/error, "scores": {...}}
     */
    public Map<?, ?> getEvalResult(String taskId) {
        String url = metricBaseUrl + "/task/" + taskId;
        return restTemplate.getForObject(url, Map.class);
    }
}
