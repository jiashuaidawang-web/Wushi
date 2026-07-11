package com.wushi.module.spider.ths;

import com.wushi.module.spider.common.SpiderHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 同花顺代理池管理器
 * 支持用户自建代理IP(配置文件注入) + 代理供应商API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThsProxyProvider {

    private static final Pattern PROXY_PATTERN = Pattern.compile(
            "(?<![\\d.])(?:https?://)?((?:\\d{1,3}\\.){3}\\d{1,3}:\\d{2,5})(?!\\d)");

    private final ThsBrowserProperties properties;
    private final SpiderHttpClient httpClient;

    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile List<String> healthyProxies = List.of();
    private volatile Instant lastProviderFetchTime = Instant.EPOCH;

    public List<String> availableProxies() {
        return healthyProxies;
    }

    public boolean hasHealthyProxies() {
        return !healthyProxies.isEmpty();
    }

    public Map<String, Object> warmup() {
        ensureMinimumUsable();
        List<String> snapshot = healthyProxies;
        return Map.of(
                "healthyCount", snapshot.size(),
                "minUsableCount", properties.getMinUsableProxyCount(),
                "proxies", snapshot);
    }

    @Scheduled(initialDelayString = "${spider.ths.browser.proxy-pool-initial-delay-ms:15000}",
            fixedDelayString = "${spider.ths.browser.proxy-pool-monitor-delay-ms:60000}")
    public void refreshNow() {
        if (!refreshing.compareAndSet(false, true)) {
            log.debug("代理池正在刷新中, 跳过");
            return;
        }
        try {
            ensureMinimumUsable();
        } finally {
            refreshing.set(false);
        }
    }

    private void ensureMinimumUsable() {
        if (!healthyProxies.isEmpty() && !cacheExpired()) {
            return;
        }
        log.info("开始刷新代理池...");
        List<String> candidates = new ArrayList<>();

        // 1. 加载用户配置的代理IP
        if (properties.getProxyPool() != null) {
            properties.getProxyPool().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .filter(this::isValidProxy)
                    .forEach(candidates::add);
        }

        // 2. 从代理供应商获取
        if (StringUtils.hasText(properties.getProxyProviderUrl())) {
            try {
                String body = httpClient.simpleGet(properties.getProxyProviderUrl());
                Set<String> parsed = parseProxies(body);
                candidates.addAll(parsed);
                log.info("从供应商获取代理: count={}", parsed.size());
            } catch (Exception e) {
                log.warn("从供应商获取代理失败: {}", e.getMessage());
            }
        }

        if (candidates.isEmpty()) {
            log.warn("没有可用代理IP, 将直连同花顺(可能触发反爬)");
            healthyProxies = List.of();
            return;
        }

        // 去重并限制数量
        List<String> distinct = candidates.stream().distinct()
                .limit(properties.getMaxProxyPoolSize()).toList();
        log.info("代理池候选数量: {}", distinct.size());
        lastProviderFetchTime = Instant.now();

        // 健康检查(测试连通性)
        healthyProxies = testProxies(distinct);
        log.info("代理池刷新完成: healthy={}/{}", healthyProxies.size(), distinct.size());
    }

    private List<String> testProxies(List<String> candidates) {
        // 简单健康检查: 直连测试即可(ThsSpiderServiceImpl 里面有完整检查逻辑)
        // 这里只做格式校验，实际连通性由使用方检测
        return candidates.stream()
                .filter(this::isValidProxy)
                .limit(properties.getMaxProxyPoolSize())
                .toList();
    }

    private Set<String> parseProxies(String body) {
        Set<String> proxies = new LinkedHashSet<>();
        if (body == null) return proxies;
        Matcher matcher = PROXY_PATTERN.matcher(body);
        while (matcher.find()) {
            String proxy = matcher.group(1);
            if (isValidProxy(proxy)) {
                proxies.add(proxy);
            }
        }
        return proxies;
    }

    private boolean isValidProxy(String proxyAddress) {
        if (!StringUtils.hasText(proxyAddress)) return false;
        String addr = proxyAddress.replaceAll("^https?://", "").trim();
        if (!PROXY_PATTERN.matcher(addr).matches()) return false;
        String[] parts = addr.split(":", 2);
        try {
            int port = Integer.parseInt(parts[1]);
            return port > 0 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean cacheExpired() {
        long refreshSeconds = Math.max(30, properties.getProxyPoolRefreshSeconds());
        return Duration.between(lastProviderFetchTime, Instant.now()).getSeconds() >= refreshSeconds;
    }
}
