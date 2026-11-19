package com.saas.kb.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

/**
 * 调 doc-service：拉每个文档的最新版本（建索引用）
 */
@FeignClient(name = "doc-service") // 服务名：Feign 去 Nacos 找实例 + 负载均衡
public interface DocClient {

    @GetMapping("/doc/list")
    List<Map<String, Object>> listDocuments();
}
