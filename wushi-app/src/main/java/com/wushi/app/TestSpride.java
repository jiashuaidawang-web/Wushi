package com.wushi.app;
import com.microsoft.playwright.*;

public class TestSpride {
  // 目标网站
  private static String pageUrl = "http://83.push2.eastmoney.com/api/qt/clist/get?&pn=2&pz=100&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281&fltt=2&invt=2&fid=f3&fs=m:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23&fields=f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f12,f13,f14,f15,f16,f17,f18,f20,f21,f23,f24,f25,f22,f11,f62,f128,f136,f115,f152,f173&_=1634565291549";
  // 用户名密码认证(隧道代理)
  private static String tunnelHost = "f278.kdltpspro.com";
  private static String tunnelPort = "15818";
  private static String ProxyUser = "t18377527660878";
  private static String Proxypass = "oyu11md5";

  public static void main(String[] args) {

    try (Playwright playwright = Playwright.create()) {

      Browser browser = playwright.chromium().launch();
      BrowserContext context = browser.newContext(new Browser.NewContextOptions()
        .setProxy(String.format("http://%s:%s", tunnelHost, tunnelPort))
        .setHttpCredentials(ProxyUser, Proxypass));
      Page page = context.newPage();
      Response response = page.navigate(pageUrl);
      System.out.println("响应为：" + response.text());
    }
  }
}
