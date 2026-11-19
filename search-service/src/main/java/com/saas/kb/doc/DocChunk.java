package com.saas.kb.doc;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分块表 doc_chunk（search_db）
 * 向量不落库（课程简化）：检索时由内存索引提供，重启后重新向量化
 */
@Data
@TableName("doc_chunk")
public class DocChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Long tenantId;
    private Integer chunkIndex;
    private String content;
    private LocalDateTime createTime;
}
