package com.saas.kb.doc;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档主表 kb_document（之前章节建的，含多租户 tenant_id）
 */
@Data
@TableName("kb_document")
public class Document {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String title;
    private String content;
    private LocalDateTime createTime;
}
