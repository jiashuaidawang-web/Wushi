package com.wushi.infrastructure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource mysqlDataSource(Environment env) {
        return buildHikariDataSource(env, "spring.datasource.mysql.", "mysqlDataSource");
    }

    @Bean
    public DataSource clickHouseDataSource(Environment env) {
        return buildHikariDataSource(env, "spring.datasource.clickhouse.", "clickHouseDataSource");
    }

    private HikariDataSource buildHikariDataSource(Environment env, String prefix, String poolName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(env.getProperty(prefix + "jdbc-url"));
        config.setUsername(env.getProperty(prefix + "username"));
        config.setPassword(env.getProperty(prefix + "password"));
        config.setDriverClassName(env.getProperty(prefix + "driver-class-name"));
        config.setPoolName(poolName);
        config.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(config);
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource mysqlDataSource) {
        return new JdbcTemplate(mysqlDataSource);
    }

    @Bean
    public JdbcTemplate clickHouseJdbcTemplate(DataSource clickHouseDataSource) {
        return new JdbcTemplate(clickHouseDataSource);
    }

    /**
     * 启动后打印数据源连接诊断信息
     */
    @Bean
    public ApplicationRunner dataSourceDiagnostics(
            Environment env,
            @Qualifier("mysqlDataSource") DataSource mysqlDs,
            @Qualifier("clickHouseDataSource") DataSource clickHouseDs) {
        return args -> {
            System.out.println("[DataSource] MySQL 配置: url=" + env.getProperty("spring.datasource.mysql.jdbc-url"));
            System.out.println("[DataSource] ClickHouse 配置: url=" + env.getProperty("spring.datasource.clickhouse.jdbc-url"));
            try (Connection conn = mysqlDs.getConnection()) {
                System.out.println("[DataSource] MySQL 连接成功: " + conn.getMetaData().getURL());
            } catch (SQLException e) {
                System.err.println("[DataSource] MySQL 连接失败: " + e.getMessage());
            }
            try (Connection conn = clickHouseDs.getConnection()) {
                System.out.println("[DataSource] ClickHouse 连接成功: " + conn.getMetaData().getURL());
            } catch (SQLException e) {
                System.err.println("[DataSource] ClickHouse 连接失败: " + e.getMessage());
            }
        };
    }
}
