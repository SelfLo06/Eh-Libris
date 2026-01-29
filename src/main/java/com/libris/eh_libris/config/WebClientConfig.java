package com.libris.eh_libris.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

//  针对 E-Hentai API 的特殊性（比如返回的 JSON 数据可能较大），
// 我们会在配置中额外增加一个 “内存缓冲区” 的设置，防止抓取大量数据时报错。

@Configuration // 告诉 Spring 这是一个配置类
public class WebClientConfig {

    @Bean
    public WebClient ehWebClient(WebClient.Builder builder) {
        // 1. 配置底层的 HttpClient，设置超时时间
        // 防止因为 E 站服务器响应慢导致你的程序一直卡死
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(10)); // 设置响应超时为 10 秒


        // 2. 创建一个自定义的 ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        // 忽略 API 返回中我们没定义在 DTO 里的多余字段（比如 current_gid 等）
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return builder
                .baseUrl("https://api.e-hentai.org")
                .codecs(configurer -> {
                    // 3. 注册一个能处理 text/html 的 JSON 解码器
                    configurer.defaultCodecs().jackson2JsonDecoder(
                            new Jackson2JsonDecoder(objectMapper, MediaType.TEXT_HTML, MediaType.APPLICATION_JSON)
                    );
                    // 4. 将缓冲区扩大到 2MB，防止大 JSON 溢出
                    configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024);
                })
                .build();
    }
}