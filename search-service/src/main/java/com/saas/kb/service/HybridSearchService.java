package com.saas.kb.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 混合检索：关键词检索 + 向量检索，加权融合后排序。
 * 检索单元 = 文档分块（chunk）：由 DocumentIndexService 负责建索引（Kafka 事件驱动），
 * 本类只负责【内存索引 + 打分】。
 *
 * 以后要换生产方案，只需替换 DocumentIndexService 的实现
 * （比如关键词换 ES、向量换 Milvus），对外接口不用动——策略模式思想。
 */
@Service
public class HybridSearchService {

    // 内存索引：每个分块 = {documentId, chunkIndex, content, vector}
    private final List<DocVector> index = new CopyOnWriteArrayList<>();

    @Autowired
    private EmbeddingService embeddingService; // 千问向量化

    @Value("${search.keyword-weight:0.4}")
    private double keywordWeight;

    @Value("${search.vector-weight:0.6}")
    private double vectorWeight;

    /**
     * 替换整个内存索引（DocumentIndexService 建索引后调用）
     */
    public synchronized void replaceIndex(List<DocVector> newIndex) {
        index.clear();
        index.addAll(newIndex);
    }

    /**
     * 混合检索：返回按总分降序的全部结果（调用方取 Top-K）
     */
    public List<SearchResult> search(String query) {
        double[] queryVec = embeddingService.embed(query);
        List<SearchResult> scored = new ArrayList<>();
        for (DocVector doc : index) {
            double kw = keywordScore(query, doc.content);
            double vec = cosine(queryVec, doc.vector);
            double total = keywordWeight * kw + vectorWeight * vec;
            scored.add(new SearchResult(doc.documentId, doc.chunkIndex, doc.content, kw, vec, total));
        }
        scored.sort((a, b) -> Double.compare(b.totalScore, a.totalScore));
        return scored;
    }

    // ---------- 关键词打分：查询词按 2-gram 切分，统计分块中的命中比例 ----------
    private double keywordScore(String query, String content) {
        if (query == null || query.isEmpty()) {
            return 0;
        }
        if (content.contains(query)) {
            return 1.0; // 整串命中直接满分
        }
        List<String> grams = bigrams(query);
        if (grams.isEmpty()) {
            return 0;
        }
        int hit = 0;
        for (String g : grams) {
            if (content.contains(g)) {
                hit++;
            }
        }
        return (double) hit / grams.size();
    }

    private List<String> bigrams(String s) {
        List<String> grams = new ArrayList<>();
        for (int i = 0; i < s.length() - 1; i++) {
            grams.add(s.substring(i, i + 2));
        }
        return grams;
    }

    // ---------- 向量打分：余弦相似度 ----------
    private double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    // ---------- 内部数据结构 ----------
    public static class DocVector {
        public Long documentId;
        public Integer chunkIndex;
        public String content;
        public double[] vector;

        public DocVector(Long documentId, Integer chunkIndex, String content, double[] vector) {
            this.documentId = documentId;
            this.chunkIndex = chunkIndex;
            this.content = content;
            this.vector = vector;
        }
    }

    // 检索结果（public 字段，Jackson 可直接序列化成 JSON 返回给调用方）
    public static class SearchResult {
        public Long documentId;
        public Integer chunkIndex;
        public String content;
        public double keywordScore;
        public double vectorScore;
        public double totalScore;

        public SearchResult(Long documentId, Integer chunkIndex, String content,
                            double keywordScore, double vectorScore, double totalScore) {
            this.documentId = documentId;
            this.chunkIndex = chunkIndex;
            this.content = content;
            this.keywordScore = keywordScore;
            this.vectorScore = vectorScore;
            this.totalScore = totalScore;
        }
    }
}
