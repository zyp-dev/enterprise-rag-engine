package com.saas.kb.service;

import com.saas.kb.doc.Document;
import com.saas.kb.doc.DocumentMapper;
import com.saas.kb.doc.DocumentVersion;
import com.saas.kb.doc.DocumentVersionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class DocumentVersionService {
    @Autowired
    private DocumentVersionMapper versionMapper;

    @Autowired
    private DocumentMapper documentMapper;

    @Transactional
    public void saveVersion(Long docId, String content) {
        int nextVersion = versionMapper.selectMaxVersion(docId) + 1;
        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(docId);
        v.setVersion(nextVersion);
        v.setContent(content);
        versionMapper.insert(v);
    }

    @Transactional
    public String rollback(Long docId, int version) {
        DocumentVersion v = versionMapper.selectByVersion(docId, version);
        if (v == null) {
            throw new IllegalArgumentException("版本不存在");
        }
        return v.getContent();
    }

    /**
     * 上传即落库：先插文档主表 kb_document，再插版本表 kb_document_version（版本 1）
     * tenant_id 课程演示固定为 1，等多租户章节完善后改成从登录上下文取
     */
    @Transactional
    public DocumentVersion saveNewDocument(String title, String content) {
        Document doc = new Document();
        doc.setTenantId(1L);
        doc.setTitle(title);
        doc.setContent(content);
        documentMapper.insert(doc); // MyBatis-Plus 会把自增 id 回填到 doc.getId()

        DocumentVersion v = new DocumentVersion();
        v.setDocumentId(doc.getId());
        v.setVersion(1);
        v.setContent(content);
        versionMapper.insert(v);
        return v;
    }

    /**
     * 每个文档的当前内容（search-service 建索引用）
     * 返回：[{documentId, version, content}, ...]
     */
    public List<Map<String, Object>> listLatest() {
        return versionMapper.selectLatestWithContent();
    }
}
