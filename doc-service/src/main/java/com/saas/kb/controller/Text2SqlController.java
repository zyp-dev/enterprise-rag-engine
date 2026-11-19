package com.saas.kb.controller;

import com.saas.kb.service.Text2SqlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/doc")
public class Text2SqlController {

    @Autowired
    private Text2SqlService text2SqlService;

    @GetMapping("/query")
    public List<Map<String, Object>> query(@RequestParam String question, @RequestParam Long tenantId) {
        return text2SqlService.query(question, tenantId);
    }
}