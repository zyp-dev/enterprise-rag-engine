package com.saas.kb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 向量化服务：调用千问 text-embedding-v4（OpenAI 兼容接口）
 */
@Service
public class EmbeddingService {

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${search.embedding.base-url}")
    private String baseUrl;

    @Value("${search.embedding.api-key}")
    private String apiKey;

    @Value("${search.embedding.model}")
    private String model;

    /**
     * 把一段文本转成 1024 维向量
     */
    public double[] embed(String text) {
        String url = baseUrl + "/embeddings";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey); // Authorization: Bearer <key>

        Map<String, Object> body = Map.of("model", model, "input", List.of(text));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        String resp = restTemplate.postForObject(url, entity, String.class);
        try {
            JsonNode root = mapper.readTree(resp);
            JsonNode emb = root.path("data").get(0).path("embedding");
            double[] vec = new double[emb.size()];
            for (int i = 0; i < emb.size(); i++) {
                vec[i] = emb.get(i).asDouble();
            }
            return vec;
        } catch (Exception e) {
            throw new RuntimeException("解析 embedding 响应失败：" + e.getMessage(), e);
        }
    }
}
