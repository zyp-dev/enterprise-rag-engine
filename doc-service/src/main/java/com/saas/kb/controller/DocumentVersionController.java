package com.saas.kb.controller;

import com.saas.kb.service.DocumentVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/doc/version")
public class DocumentVersionController {
    @Autowired
    private DocumentVersionService versionService;

    @PostMapping("/save")
    public String save(@RequestParam Long docId, @RequestParam String content) {
        versionService.saveVersion(docId, content);
        return "已保存新版本";
    }

    @GetMapping("/rollback")
    public String rollback(@RequestParam Long docId, @RequestParam int version) {
        return versionService.rollback(docId, version);
    }
}