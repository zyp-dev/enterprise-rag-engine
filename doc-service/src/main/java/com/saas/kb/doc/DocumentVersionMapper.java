package com.saas.kb.doc;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DocumentVersionMapper extends BaseMapper<DocumentVersion> {
    @Select("SELECT COALESCE(MAX(version), 0) FROM kb_document_version WHERE document_id = #{docId}")
    int selectMaxVersion(@Param("docId") Long docId);

    @Select("SELECT * FROM kb_document_version WHERE document_id = #{docId} AND version = #{version}")
    DocumentVersion selectByVersion(@Param("docId") Long docId, @Param("version") int version);

    // 每个文档的当前内容：优先取最新版本，没有版本记录时回退到主表内容
    // （存量文档可能只有主表记录，没有版本记录）
    @Select("SELECT d.id AS documentId, COALESCE(v.version, 0) AS version, " +
            "COALESCE(v.content, d.content) AS content " +
            "FROM kb_document d " +
            "LEFT JOIN kb_document_version v ON v.id = (" +
            "  SELECT v2.id FROM kb_document_version v2 " +
            "  WHERE v2.document_id = d.id ORDER BY v2.version DESC LIMIT 1" +
            ")")
    java.util.List<java.util.Map<String, Object>> selectLatestWithContent();
}