package com.saas.kb.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 调 search-service：混合检索（拿 Top-K 上下文）
 */
@FeignClient(name = "search-service")
public interface SearchClient {

    @PostMapping("/search/search")
    Map<String, Object> search(@RequestBody Map<String, String> body);
}
