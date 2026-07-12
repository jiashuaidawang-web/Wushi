package com.wushi.app;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;

@Component
public class JdbcTemplateChecker implements CommandLineRunner {

  private final JdbcTemplate ch;

  public JdbcTemplateChecker(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate ch) {
    this.ch = ch;
  }

  @Override
  public void run(String... args) throws Exception {
    System.out.println("===== CH JDBC CHECK =====");
    System.out.println("CH DataSource class = " + ch.getDataSource().getClass().getName());
    try (Connection c = ch.getDataSource().getConnection()) {
      System.out.println("CH URL = " + c.getMetaData().getURL());
    }
    System.out.println("==========================");
  }
}
