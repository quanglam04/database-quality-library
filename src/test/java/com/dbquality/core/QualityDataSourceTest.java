package com.dbquality.core;

import com.dbquality.collector.SQLRecord;
import com.dbquality.config.QualityConfig;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QualityDataSourceTest {

  private QualityDataSource qualityDataSource;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource h2 = new JdbcDataSource();
    h2.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
    h2.setUser("sa");
    h2.setPassword("");

    qualityDataSource = new QualityDataSource(h2, QualityConfig.getTestDefault());

    // Tạo bảng nếu chưa có
    try (Connection conn = qualityDataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
            "CREATE TABLE IF NOT EXISTS users " +
                "(id INT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100))")) {
      stmt.execute();
    }

    // Xóa data cũ trước khi insert
    try (Connection conn = qualityDataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement("DELETE FROM users")) {
      stmt.executeUpdate();
    }

    // Insert data mẫu
    try (Connection conn = qualityDataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO users VALUES (?, ?, ?)")) {
      stmt.setInt(1, 1);
      stmt.setString(2, "Alice");
      stmt.setString(3, "alice@example.com");
      stmt.executeUpdate();

      stmt.setInt(1, 2);
      stmt.setString(2, "Bob");
      stmt.setString(3, "bob@example.com");
      stmt.executeUpdate();
    }

    // Reset SQLContext sau khi setup
    qualityDataSource.getSqlContext().clear();
  }

  @Test
  void shouldCaptureSelectQuery() throws Exception {
    // Chạy 1 query SELECT
    try (Connection conn = qualityDataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
            "SELECT * FROM users WHERE id = ?")) {
      stmt.setInt(1, 1);
      stmt.executeQuery();
    }

    List<SQLRecord> records = qualityDataSource.getSqlContext().getRecords();

    assertEquals(1, records.size());
    assertEquals("SELECT * FROM users WHERE id = ?", records.get(0).getSql());
  }

  @Test
  void shouldCaptureExecutionTime() throws Exception {
    try (Connection conn = qualityDataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
            "SELECT * FROM users WHERE id = ?")) {
      stmt.setInt(1, 1);
      stmt.executeQuery();
    }

    SQLRecord record = qualityDataSource.getSqlContext().getRecords().get(0);

    // executionTime phải >= 0
    assertTrue(record.getExecutionTime() >= 0);
  }

  @Test
  void shouldCaptureParameters() throws Exception {
    try (Connection conn = qualityDataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
            "SELECT * FROM users WHERE id = ?")) {
      stmt.setInt(1, 42);
      stmt.executeQuery();
    }

    SQLRecord record = qualityDataSource.getSqlContext().getRecords().get(0);

    assertEquals(42, record.getParameters().get(1));
  }

  @Test
  void shouldCaptureCalledFrom() throws Exception {
    try (Connection conn = qualityDataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
            "SELECT * FROM users WHERE id = ?")) {
      stmt.setInt(1, 1);
      stmt.executeQuery();
    }

    SQLRecord record = qualityDataSource.getSqlContext().getRecords().get(0);


    assertNotNull(record.getCalledFrom());
    assertNotEquals("unknown", record.getCalledFrom());
  }

  @Test
  void shouldCaptureSuccessTrue() throws Exception {
    try (Connection conn = qualityDataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
            "SELECT * FROM users WHERE id = ?")) {
      stmt.setInt(1, 1);
      stmt.executeQuery();
    }

    SQLRecord record = qualityDataSource.getSqlContext().getRecords().get(0);

    assertTrue(record.isSuccess());
    assertNull(record.getErrorMessage());
  }

  @Test
  void shouldCaptureSuccessFalseOnError() throws Exception {
    try (Connection conn = qualityDataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO users VALUES (?, ?, ?)")) {
      // Insert trùng PK → lỗi lúc executeUpdate
      stmt.setInt(1, 1);
      stmt.setString(2, "Duplicate");
      stmt.setString(3, "dup@example.com");
      stmt.executeUpdate();
    } catch (Exception ignored) {
      // Expected
    }

    SQLRecord record = qualityDataSource.getSqlContext().getRecords().get(0);

    assertFalse(record.isSuccess());
    assertNotNull(record.getErrorMessage());
  }

  @Test
  void shouldCaptureMultipleQueries() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      try (PreparedStatement stmt = conn.prepareStatement(
          "SELECT * FROM users WHERE id = ?")) {
        stmt.setInt(1, 1);
        stmt.executeQuery();
      }
      try (PreparedStatement stmt = conn.prepareStatement(
          "SELECT * FROM users WHERE id = ?")) {
        stmt.setInt(1, 2);
        stmt.executeQuery();
      }
    }

    List<SQLRecord> records = qualityDataSource.getSqlContext().getRecords();

    assertEquals(2, records.size());
  }

  @Test
  void shouldCaptureTimestamp() throws Exception {
    try (Connection conn = qualityDataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(
            "SELECT * FROM users WHERE id = ?")) {
      stmt.setInt(1, 1);
      stmt.executeQuery();
    }

    SQLRecord record = qualityDataSource.getSqlContext().getRecords().get(0);

    assertNotNull(record.getTimestamp());
  }
}