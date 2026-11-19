package com.saas.kb.controller;

import com.saas.kb.auth.RequirePermission;
import com.saas.kb.doc.DocumentVersion;
import com.saas.kb.service.DocIndexEventService;
import com.saas.kb.service.DocService;
import com.saas.kb.service.DocumentVersionService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/doc")
public class DocController {

    @Autowired
    private DocService docService;

    @Autowired
    private DocumentVersionService versionService;

    @Autowired
    private DocIndexEventService eventService;

    // 文本 JSON 上传（保留原接口）
    @PostMapping("/upload")
    @RequirePermission("document:write") // 加上 RBAC 权限校验
    public Map<String, Object> upload(@RequestBody Map<String, String> body) {
        String content = body.getOrDefault("content", "");
        String title = body.getOrDefault("title", "");
        return doUpload(title, content, body);
    }

    // 文件上传（txt/md/pdf）：解析成文本 → 入库 → 发 Kafka 事件
    @PostMapping(value = "/upload/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("document:write")
    public Map<String, Object> uploadFile(@RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "title", required = false) String title) {
        String content = parseFile(file);
        Map<String, String> body = new HashMap<>();
        body.put("content", content);
        return doUpload(title, content, body);
    }

    /**
     * 上传主流程：落库（主表+版本1）→ 发 Kafka 事件 → AI 分析（带熔断降级）
     */
    private Map<String, Object> doUpload(String title, String content, Map<String, String> body) {
        if (title == null || title.isEmpty()) {
            title = content.length() > 20 ? content.substring(0, 20) + "..." : content;
        }
        DocumentVersion saved = null;
        try {
            saved = versionService.saveNewDocument(title, content);
            // 事件驱动：通知 search-service 建索引
            eventService.publishDocumentIndexed(saved.getDocumentId(), 1L, title, content);
        } catch (Exception e) {
            System.out.println("文档落库/发事件失败（不影响AI分析返回）：" + e.getMessage());
        }
        Map<String, Object> result = docService.analyzeWithProtection(body);
        if (saved != null) {
            result.put("documentId", saved.getDocumentId());
            result.put("version", saved.getVersion());
        }
        return result;
    }

    /**
     * 文件解析：pdf 用 PDFBox 提取文本；txt/md/json 直接按 UTF-8 读取
     */
    private String parseFile(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        try {
            if (filename.endsWith(".pdf")) {
                try (PDDocument doc = PDDocument.load(file.getInputStream())) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    String text = stripper.getText(doc);
                    return text == null ? "" : text.trim();
                }
            }
            if (filename.endsWith(".txt") || filename.endsWith(".md") || filename.endsWith(".json")) {
                return new String(file.getBytes(), StandardCharsets.UTF_8);
            }
            throw new IllegalArgumentException("暂不支持的文件格式（支持 txt/md/json/pdf）");
        } catch (Exception e) {
            throw new RuntimeException("文件解析失败：" + e.getMessage(), e);
        }
    }

    // 文档列表：每个文档的当前内容（给 search-service 首次建索引用）
    @GetMapping("/list")
    public List<Map<String, Object>> list() {
        return versionService.listLatest();
    }
}
