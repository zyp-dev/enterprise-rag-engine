package com.saas.kb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.saas.kb.doc") // 扫描 Mapper 接口
public class DocServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocServiceApplication.class, args);
    }
}