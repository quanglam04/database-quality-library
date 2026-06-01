package com.dbquality.collector;

import com.dbquality.config.QualityConfig;
import com.dbquality.core.QualityDataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SQLCollectorTest {

  private QualityDataSource qualityDataSource;
  private SQLCollector sqlCollector;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource h2 = new JdbcDataSource();
    h2.setURL("jdbc:h2:mem:sqlcollectordb;DB_CLOSE_DELAY=-1");
    h2.setUser("sa");
    h2.setPassword("");

    qualityDataSource = new QualityDataSource(h2, QualityConfig.getTestDefault());
    sqlCollector = new SQLCollector(qualityDataSource.getSqlContext());

    // Tạo schema
    try (Connection conn = qualityDataSource.getConnection()) {
      conn.prepareStatement("DROP TABLE IF EXISTS products").execute();
      conn.prepareStatement("DROP TABLE IF EXISTS users").execute();
      conn.prepareStatement("""
                CREATE TABLE users (
                    id   INT PRIMARY KEY,
                    name VARCHAR(100)
                )
            """).execute();
      conn.prepareStatement("""
                CREATE TABLE products (
                    id    INT PRIMARY KEY,
                    name  VARCHAR(100),
                    price DECIMAL(10,2)
                )
            """).execute();

      // Insert data mẫu
      try (PreparedStatement stmt = conn.prepareStatement(
          "INSERT INTO users VALUES (?, ?)")) {
        for (int i = 1; i <= 5; i++) {
          stmt.setInt(1, i);
          stmt.setString(2, "User " + i);
          stmt.executeUpdate();
        }
      }
      try (PreparedStatement stmt = conn.prepareStatement(
          "INSERT INTO products VALUES (?, ?, ?)")) {
        for (int i = 1; i <= 3; i++) {
          stmt.setInt(1, i);
          stmt.setString(2, "Product " + i);
          stmt.setBigDecimal(3, new java.math.BigDecimal("9.99"));
          stmt.executeUpdate();
        }
      }
    }

    // Reset context để chỉ test queries thật sự
    qualityDataSource.getSqlContext().clear();
  }

  @Test
  void shouldCollectAllQueries() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      conn.prepareStatement("SELECT * FROM users").executeQuery();
      conn.prepareStatement("SELECT * FROM products").executeQuery();
    }

    assertEquals(2, sqlCollector.getTotalCount());
  }

  @Test
  void shouldDetectSlowQueries() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      conn.prepareStatement("SELECT * FROM users").executeQuery();
      conn.prepareStatement("SELECT * FROM products").executeQuery();
    }

    // threshold = 0ms → tất cả đều là slow query
    List<SQLRecord> slow = sqlCollector.getSlowQueries(0);
    assertEquals(2, slow.size());

    // threshold = 999999ms → không có slow query
    List<SQLRecord> notSlow = sqlCollector.getSlowQueries(999999);
    assertEquals(0, notSlow.size());
  }

  @Test
  void shouldDetectFailedQueries() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      try (PreparedStatement stmt = conn.prepareStatement(
          "INSERT INTO users VALUES (?, ?)")) {
        // Insert trùng PK → lỗi lúc executeUpdate
        stmt.setInt(1, 1);
        stmt.setString(2, "Duplicate");
        stmt.executeUpdate();
      } catch (Exception ignored) {}
    }

    assertEquals(1, sqlCollector.getFailedCount());
    assertEquals(1, sqlCollector.getFailedQueries().size());
    assertFalse(sqlCollector.getFailedQueries().get(0).isSuccess());
  }

  @Test
  void shouldGroupBySqlPattern() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      // Cùng 1 pattern lặp lại 3 lần. giả lập cho trường hợp N+1
      for (int i = 1; i <= 3; i++) {
        PreparedStatement stmt = conn.prepareStatement(
            "SELECT * FROM users WHERE id = ?");
        stmt.setInt(1, i);
        stmt.executeQuery();
      }
      // Pattern khác
      conn.prepareStatement("SELECT * FROM products").executeQuery();
    }

    Map<String, List<SQLRecord>> grouped = sqlCollector.groupBySqlPattern();

    assertEquals(2, grouped.size());
    assertEquals(3, grouped.get("SELECT * FROM users WHERE id = ?").size());
    assertEquals(1, grouped.get("SELECT * FROM products").size());
  }

  @Test
  void shouldDetectRepeatedPatterns() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      for (int i = 1; i <= 5; i++) {
        PreparedStatement stmt = conn.prepareStatement(
            "SELECT * FROM users WHERE id = ?");
        stmt.setInt(1, i);
        stmt.executeQuery();
      }
    }

    // threshold = 3 → pattern lặp > 3 lần mới tính
    List<SQLRecord> repeated = sqlCollector.getRepeatedPatterns(3);
    assertEquals(5, repeated.size());

    // threshold = 10 → không có pattern nào lặp > 10 lần
    List<SQLRecord> notRepeated = sqlCollector.getRepeatedPatterns(10);
    assertEquals(0, notRepeated.size());
  }

  @Test
  void shouldGetTopSlowQueries() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      conn.prepareStatement("SELECT * FROM users").executeQuery();
      conn.prepareStatement("SELECT * FROM products").executeQuery();
      conn.prepareStatement("SELECT * FROM users WHERE id = 1").executeQuery();
    }

    List<SQLRecord> top2 = sqlCollector.getTopSlowQueries(2);
    assertEquals(2, top2.size());
    assertTrue(top2.get(0).getExecutionTime()
        >= top2.get(1).getExecutionTime());
  }

  @Test
  void shouldCountQueriesByTable() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      conn.prepareStatement("SELECT * FROM users").executeQuery();
      conn.prepareStatement("SELECT * FROM users WHERE id = 1").executeQuery();
      conn.prepareStatement("SELECT * FROM products").executeQuery();
    }

    Map<String, Long> countByTable = sqlCollector.getQueryCountByTable();

    assertEquals(2L, countByTable.get("USERS"));
    assertEquals(1L, countByTable.get("PRODUCTS"));
  }
}