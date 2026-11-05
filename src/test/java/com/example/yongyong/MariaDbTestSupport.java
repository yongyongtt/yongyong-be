package com.example.yongyong;

import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mariadb.MariaDBContainer;

public abstract class MariaDbTestSupport {

  private static final String SEED = "db/seed-dong-2025.sql";
  private static final MariaDBContainer CONTAINER =
      new MariaDBContainer("mariadb:12.2").withDatabaseName("yongin");

  static {
    // seed는 git에 없어 CI에는 파일이 없다 (decisions D11)
    if (new ClassPathResource(SEED).exists()) {
      CONTAINER.withInitScript(SEED);
    }
    CONTAINER.start();
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
    registry.add("spring.datasource.username", CONTAINER::getUsername);
    registry.add("spring.datasource.password", CONTAINER::getPassword);
  }
}
