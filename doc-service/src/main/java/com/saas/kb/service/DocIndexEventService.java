package com.saas.kb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 项目7 事件驱动：文档落库后向 Kafka 发布「文档已索引」事件，
 * search-service 消费该事件自动分块 + 建立向量索引（不再需要手动 rebuild）
 */
@Service
public class DocIndexEventService {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${doc.kafka-topic}")
    private String topic;

    private final ObjectMapper mapper = new ObjectMapper();

    public void publishDocumentIndexed(Long documentId, Long tenantId, String title, String content) {
        Map<String, Object> event = new HashMap<>();
        event.put("documentId", documentId);
        event.put("tenantId", tenantId);
        event.put("title", title);
        event.put("content", content);
        event.put("timestamp", System.currentTimeMillis());
        try {
            kafkaTemplate.send(topic, String.valueOf(documentId), mapper.writeValueAsString(event));
            System.out.println("已发布「文档已索引」事件：documentId=" + documentId + "，topic=" + topic);
        } catch (Exception e) {
            // 事件发布失败不阻塞上传主流程（课程简化；生产上要有重试/补偿）
            System.out.println("发布 Kafka 事件失败：" + e.getMessage());
        }
    }
}
