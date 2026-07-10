package com.wushi.module.spider.common;

import org.springframework.http.RequestEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.net.*;
import java.time.Duration;
import java.util.*;

@Component
public class SpiderHttpClient {

    private final RestTemplate restTemplate;

    public SpiderHttpClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    /** 基础 GET, 返回 body */
    public String get(String url) {
        return get(url, null, null);
    }

    public String get(String url, Map<String, String> headers) {
        return get(url, headers, null);
    }

    public String get(String url, Map<String, String> headers, String proxyAddress) {
        var builder = RequestEntity.get(url);
        if (headers != null) {
            headers.forEach(builder::header);
        }
        RestTemplate template = StringUtils.hasText(proxyAddress) ? proxiedRestTemplate(proxyAddress) : restTemplate;
        return template.exchange(builder.build(), String.class).getBody();
    }

    /** GET 请求, 返回响应头 (用于 Cookie 提取) */
    public Map<String, String> getWithHeaders(String url, Map<String, String> headers, String proxyAddress) {
        var builder = RequestEntity.get(url);
        if (headers != null) {
            headers.forEach(builder::header);
        }
        RestTemplate template = StringUtils.hasText(proxyAddress) ? proxiedRestTemplate(proxyAddress) : restTemplate;
        ResponseEntity<String> response = template.exchange(builder.build(), String.class);
        Map<String, String> result = new LinkedHashMap<>();
        response.getHeaders().forEach((key, values) -> {
            if (!values.isEmpty()) {
                result.put(key, values.get(0));
            }
        });
        return result;
    }

    private Proxy proxy(String proxyAddress) {
        String normalized = proxyAddress.replace("http://", "").replace("https://", "").trim();
        String[] parts = normalized.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid proxy address: " + proxyAddress);
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(parts[0], Integer.parseInt(parts[1])));
    }

    private RestTemplate proxiedRestTemplate(String proxyAddress) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setProxy(proxy(proxyAddress));
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        return new RestTemplate(factory);
    }
}
