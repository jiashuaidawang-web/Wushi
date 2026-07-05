package com.wushi.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.wushi")
public class WushiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WushiApplication.class, args);
    }
}
