package com.saas.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.kb.client.DocClient;
import com.saas.kb.doc.DocChunk;
import com.saas.kb.doc.DocChunkMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 项目7 索引服务（事件驱动）：
 *   1. 启动时：doc_chunk 表为空 → 从 doc-service 拉全量文档分块入库；
 *     否则直接读 doc_chunk → 向量化 → 构建内存索引
 *   2. 运行中：消费 Kafka「文档已索引」事件 → 该文档重新分块入库 → 重建索引
 *      （doc-service 上传新文档后，无需再手动调 /search/index/rebuild）
 */
@Service
public class DocumentIndexService {

    @Autowired
    private DocChunkMapper chunkMapper;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private HybridSearchService hybridSearchService;

    @Autowired
    private DocClient docClient;

    @Value("${search.chunk-size:400}")
    private int chunkSize;

    @Value("${search.chunk-overlap:50}")
    private int chunkOverlap;

    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            int n = rebuildFromDb();
            System.out.println("启动建索引成功，共 " + n + " 个分块");
        } catch (Exception e) {
            System.out.println("启动时建索引失败（MySQL/doc-service 未就绪？）：" + e.getMessage());
        }
    }

    /**
     * 全量重建索引：分块表为空时先从 doc-service 拉全量；否则读表 + 向量化
     */
    public synchronized int rebuildFromDb() {
        if (chunkMapper.selectCount(null) == 0) {
            pullAllFromDocService();
        }
        List<DocChunk> chunks = chunkMapper.selectList(null);
        List<HybridSearchService.DocVector> index = new ArrayList<>();
        for (DocChunk c : chunks) {
            double[] vec = embeddingService.embed(c.getContent());
            index.add(new HybridSearchService.DocVector(
                    c.getDocumentId(), c.getChunkIndex(), c.getContent(), vec));
        }
        hybridSearchService.replaceIndex(index);
        return index.size();
    }

    /**
     * 消费「文档已索引」事件：该文档重新分块入库，然后重建内存索引
     */
    @KafkaListener(topics = "${search.kafka-topic}", groupId = "search-service")
    public void onDocumentIndexed(String message) {
        try {
            JsonNode root = mapper.readTree(message);
            Long documentId = root.path("documentId").asLong();
            Long tenantId = root.path("tenantId").asLong(1L);
            String content = root.path("content").asText();

            // 该文档的旧分块删掉，插入新分块（等价于索引更新）
            chunkMapper.delete(new QueryWrapper<DocChunk>().eq("document_id", documentId));
            insertChunks(documentId, tenantId, content);

            // 课程简化：全量重建内存索引（文档量小）；生产环境可做增量重建
            rebuildFromDb();
            System.out.println("已消费「文档已索引」事件并重建索引：documentId=" + documentId);
        } catch (Exception e) {
            System.out.println("消费文档索引事件失败：" + e.getMessage());
        }
    }

    /**
     * 首次启动（分块表为空）时，从 doc-service 拉全量文档并分块入库
     */
    private void pullAllFromDocService() {
        List<Map<String, Object>> docs = docClient.listDocuments();
        for (Map<String, Object> d : docs) {
            Long documentId = ((Number) d.get("documentId")).longValue();
            String content = String.valueOf(d.get("content"));
            insertChunks(documentId, 1L, content);
        }
        System.out.println("首次建索引：从 doc-service 拉取 " + docs.size() + " 篇文档分块入库");
    }

    /**
     * 分块入库：按 chunkSize 切块，块间重叠 chunkOverlap 字符
     */
    private void insertChunks(Long documentId, Long tenantId, String content) {
        List<String> pieces = chunk(content);
        for (int i = 0; i < pieces.size(); i++) {
            DocChunk c = new DocChunk();
            c.setDocumentId(documentId);
            c.setTenantId(tenantId);
            c.setChunkIndex(i);
            c.setContent(pieces.get(i));
            chunkMapper.insert(c);
        }
    }

    private List<String> chunk(String content) {
        List<String> chunks = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return chunks;
        }
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            chunks.add(content.substring(start, end));
            if (end >= content.length()) {
                break;
            }
            start = end - chunkOverlap;
        }
        return chunks;
    }
}
