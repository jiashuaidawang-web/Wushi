package com.wushi.module.spider.ths;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "wushi.spider.ths.browser")
public class ThsBrowserProperties {

    /**
     * Selenium standalone/chrome 远端地址. 例: http://selenium-chrome:4444/wd/hub
     */
    private String remoteUrl;

    /**
     * 本地 Chrome/Chromium 路径. 仅当 remoteUrl 为空时使用.
     */
    private String chromeBinary;

    /**
     * 本地 chromedriver 路径. 仅当 remoteUrl 为空时使用.
     */
    private String chromeDriver;

    private boolean headless = true;

    /**
     * 代理IP池, 格式: ip:port 或 http://ip:port
     * 用户自建代理IP, 通过配置文件注入
     */
    private List<String> proxyPool = new ArrayList<>();

    /**
     * 代理供应商API地址(可选). 响应格式需包含 ip:port.
     */
    private String proxyProviderUrl;

    private List<String> proxyProviderUrls = new ArrayList<>();

    /**
     * 代理池刷新间隔(秒)
     */
    private long proxyPoolRefreshSeconds = 300;

    /**
     * 最大代理池大小
     */
    private int maxProxyPoolSize = 200;

    /**
     * 最低健康代理数
     */
    private int minUsableProxyCount = 10;

    /**
     * 代理候选上限
     */
    private int proxyCandidateLimit = 1000;

    /**
     * 代理供应商分页限制
     */
    private int proxyProviderPageLimit = 5;

    /**
     * 代理健康检查URL
     */
    private String proxyTestUrl = "http://q.10jqka.com.cn/gn/";

    private List<String> proxyTestUrls = new ArrayList<>(List.of(
            "http://q.10jqka.com.cn/gn/",
            "http://q.10jqka.com.cn/dy/",
            "http://q.10jqka.com.cn/thshy/",
            "http://q.10jqka.com.cn/zjhhy/"
    ));

    private int proxyTestTimeoutMs = 2_000;

    private int proxyTestConcurrency = 32;

    private int proxyTestCandidateLimit = 200;

    private long proxyPoolMonitorDelayMs = 60_000;

    private long proxyPoolInitialDelayMs = 15_000;

    private boolean proxyPoolLogEnabled = true;

    private long proxyPoolLogDelayMs = 30_000;

    private int candidateProxyLogLimit = 100;

    private int maxProxyRetries = 20;

    private boolean directFirst = true;

    private long proxyPoolWarmupWaitMs = 30_000;

    private List<String> arguments = new ArrayList<>(List.of(
            "--disable-gpu",
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--window-size=1920,1080",
            "--disable-blink-features=AutomationControlled"
    ));

    private int pageLoadTimeoutSeconds = 60;

    // ========== 快代理配置 ==========

    /**
     * 是否启用快代理IP自动刷新
     */
    private boolean kuaidailiEnabled = false;

    /**
     * 快代理API地址
     */
    private String kuaidailiApiUrl;

    /**
     * 每次请求提取的IP数量
     */
    private int kuaidailiNum = 1;

    /**
     * 响应格式: text 或 json
     */
    private String kuaidailiFormat = "text";

    /**
     * JSON响应中IP列表的路径(仅json格式)
     */
    private String kuaidailiJsonPath = "data.proxy_list";

    /**
     * API调用超时(毫秒)
     */
    private int kuaidailiTimeoutMs = 5000;

    /**
     * 每个IP最大使用次数
     */
    private int kuaidailiMaxUsesPerIp = 5;
}
