package com.wushi.module.spider.eastmoney;

import com.wushi.module.spider.common.SpiderHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class EastMoneyProxyProvider {

    private static final Pattern PROXY_PATTERN = Pattern.compile(
            "(?<![\\d.])(?:https?://)?((?:\\d{1,3}\\.){3}\\d{1,3}:\\d{2,5})(?!\\d)");

    private final EastMoneyProperties properties;

    public List<String> availableProxies() {
        return properties.getProxyPool();
    }

    public boolean hasHealthyProxies() {
        return properties.getProxyPool() != null && !properties.getProxyPool().isEmpty();
    }
}
