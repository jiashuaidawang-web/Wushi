package com.wushi.module.spider.eastmoney;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "wushi.spider.eastmoney")
public class EastMoneyProperties {

  /**
   * 代理IP池, 格式: ip:port 或 http://ip:port
   */
  private List<String> proxyPool = new ArrayList<>();

  /**
   * 最大代理重试次数
   */
  private int maxProxyRetries = 3;

  /**
   * 优先直连(不使用时代理)
   */
  private boolean directFirst = true;

  private boolean kuaidailiEnabled = false;
  private String kuaidailiApiUrl;
  private int kuaidailiNum = 1;
  private String kuaidailiFormat = "text";
  private String kuaidailiJsonPath = "data.proxy_list";
  private int kuaidailiTimeoutMs = 5000;
  private int kuaidailiMaxUsesPerIp = 5;
}
