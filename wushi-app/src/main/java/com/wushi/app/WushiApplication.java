package com.wushi.app;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan(basePackages = "com.wushi", markerInterface = BaseMapper.class)
@SpringBootApplication(scanBasePackages = "com.wushi")
public class WushiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WushiApplication.class, args);
    }
}
