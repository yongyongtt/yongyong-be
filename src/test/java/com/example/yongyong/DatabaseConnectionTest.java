package com.example.yongyong;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 실제 DB에 접속해 yongin, yongin_app의 테이블 목록을 콘솔에 출력한다.
 * 조회만 하며, DB가 켜져 있어야 통과한다.
 */
@SpringBootTest
class DatabaseConnectionTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void showCurrentAccount() {
        String account = jdbcTemplate.queryForObject("SELECT CURRENT_USER()", String.class);
        System.out.println("[접속 계정] " + account);
    }

    @Test
    void showYonginTables() {
        List<String> tables = showTables("yongin");
        assertFalse(tables.isEmpty(), "yongin에 테이블이 없습니다.");
    }

    @Test
    void showYonginAppTables() {
        // ERD를 정하기 전에는 테이블이 없을 수 있어, 조회 성공만 확인한다.
        showTables("yongin_app");
    }

    private List<String> showTables(String database) {
        List<String> tables = jdbcTemplate.queryForList("SHOW TABLES FROM " + database, String.class);
        System.out.println("[" + database + "] 테이블 " + tables.size() + "개");
        tables.forEach(table -> System.out.println("  - " + table));
        return tables;
    }
}
