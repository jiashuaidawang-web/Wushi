package com.wushi.module.spider.eastmoney;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.RequestEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class KuaidailiProxyRefresher {

  private static final Pattern PROXY_PATTERN = Pattern.compile(
    "(?<![\\d.])(?:https?://)?((?:\\d{1,3}\\.){3}\\d{1,3}:\\d{2,5})(?!\\d)");

  private final EastMoneyProperties properties;
  private final ObjectMapper objectMapper;
  private final RestTemplateBuilder restTemplateBuilder;

  private volatile String currentProxy;
  private final ConcurrentHashMap<String, AtomicInteger> useCount = new ConcurrentHashMap<>();

  public synchronized String nextProxy() {
    log.info("[Kuaidaili] nextProxy 调用: enabled={}, apiUrl={}", properties.isKuaidailiEnabled(), properties.getKuaidailiApiUrl());
    if (!properties.isKuaidailiEnabled()) return null;
    if (currentProxy != null) {
      AtomicInteger count = useCount.get(currentProxy);
      if (count != null && count.get() < properties.getKuaidailiMaxUsesPerIp()) {
        count.incrementAndGet();
        return currentProxy;
      }
      log.info("快代理IP使用次数耗尽自动换新: ip={}, count={}", currentProxy, count != null ? count.get() : 0);
    }
    return refreshProxyInternal();
  }

  public String refreshOnBlock() {
    log.info("[Kuaidaili] refreshOnBlock 调用: enabled={}, apiUrl={}", properties.isKuaidailiEnabled(), properties.getKuaidailiApiUrl());
    if (!properties.isKuaidailiEnabled()) return null;
    log.warn("检测到403/block, 强制刷新快代理IP");
    return refreshProxyInternal();
  }

  private synchronized String refreshProxyInternal() {
    try {
      if (!StringUtils.hasText(properties.getKuaidailiApiUrl())) {
        log.warn("快代理API地址未配置");
        return null;
      }
      RestTemplate template = restTemplateBuilder
        .setConnectTimeout(Duration.ofMillis(properties.getKuaidailiTimeoutMs()))
        .setReadTimeout(Duration.ofMillis(properties.getKuaidailiTimeoutMs()))
        .build();
      String url = properties.getKuaidailiApiUrl().replace("{num}", String.valueOf(properties.getKuaidailiNum()));
      String body = template.exchange(
        RequestEntity.get(url)
          .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
          .build(), String.class).getBody();
      List<String> proxies = parseResponse(body);
      if (proxies.isEmpty()) { log.warn("快代理API返回空IP列表"); return null; }
      currentProxy = proxies.get(0);
      useCount.clear();
      useCount.put(currentProxy, new AtomicInteger(1));
      log.info("快代理IP刷新成功: ip={}", currentProxy);
      return currentProxy;
    } catch (Exception e) {
      log.error("快代理API调用失败: {}", e.getMessage());
      return null;
    }
  }

  private List<String> parseResponse(String body) {
    if (body == null || body.isBlank()) return List.of();
    if ("json".equalsIgnoreCase(properties.getKuaidailiFormat())) return parseJson(body);
    return parseText(body);
  }

  private List<String> parseText(String body) {
    List<String> proxies = new ArrayList<>();
    for (String line : body.split("\\n")) {
      String t = line.trim();
      if (PROXY_PATTERN.matcher(t).matches()) proxies.add(t);
    }
    return proxies;
  }

  private List<String> parseJson(String body) {
    try {
      JsonNode root = objectMapper.readTree(body);
      String[] parts = properties.getKuaidailiJsonPath().split("\\.");
      JsonNode node = root;
      for (String p : parts) { node = node.path(p); if (node.isMissingNode()) return List.of(); }
      List<String> proxies = new ArrayList<>();
      if (node.isArray()) { for (JsonNode item : node) { String v = item.asText().trim(); if (PROXY_PATTERN.matcher(v).matches()) proxies.add(v); } }
      return proxies;
    } catch (Exception e) { return parseText(body); }
  }

  public void reportFailed(String proxy) {
    if (proxy == null || !properties.isKuaidailiEnabled()) return;
    if (proxy.equals(currentProxy)) currentProxy = null;
  }

  public String getCurrentProxy() { return currentProxy; }
  public boolean isEnabled() { return properties.isKuaidailiEnabled(); }
}
