package com.saas.kb.service;

import com.saas.kb.client.AiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class Text2SqlService {

    @Autowired
    private AiClient aiClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> query(String question, Long tenantId) {
        String prompt = """
                根据表结构生成SQL查询语句。
                表结构：kb_document(id,tenant_id,title,content,create_time)
                硬性规则：
                1.只允许生成SELECT语句，禁止INSERT/UPDATE/DELETE/DROP/ALTER。
                2.必须在WHERE语句中包含tenant_id=%d。
                3.不要用分号结尾，不要写注释。
                问题：%s
                只返回SQL语句本身，不要解释
                """.formatted(tenantId, question);

        Map<String, Object> resp = aiClient.analyze(Map.of("content", prompt));
        String sql = String.valueOf(resp.get("reply")).trim();
        System.out.println("🤖 LLM 生成的 SQL: " + sql);

        // 3. 代码层硬校验，必须 SELECT 开头
        if (!sql.toUpperCase().startsWith("SELECT")) {
            throw new IllegalArgumentException("⚠️ 安全拦截：只允许只读查询！");
        }

        // 🌟 作业：代码级兜底，强制校验是否包含 tenant_id
        if (!sql.toLowerCase().contains("tenant_id")) {
            throw new IllegalArgumentException("⚠️ 安全拦截：SQL 中缺少 tenant_id 过滤条件！");
        }

        return jdbcTemplate.queryForList(sql);
    }
}