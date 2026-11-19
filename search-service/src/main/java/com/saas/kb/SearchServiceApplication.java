package com.saas.kb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient   // 注册进 Nacos
@EnableFeignClients      // 开启 Feign：调 doc-service / ai-service
@MapperScan("com.saas.kb.doc") // 扫描 MyBatis-Plus Mapper（doc_chunk 表）
public class SearchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
