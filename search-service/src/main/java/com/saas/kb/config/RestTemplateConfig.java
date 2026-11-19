package com.saas.kb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 调用千问 embedding 接口的 RestTemplate（超时设置防止线程拖死）
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);  // 连接超时 3s
        factory.setReadTimeout(15000);    // 读取超时 15s
        return new RestTemplate(factory);
    }
}
