// src/test/java/com/dbquality/demo/DemoApp.java
package com.dbquality.demo;

import com.dbquality.config.QualityConfig;
import com.dbquality.core.QualityDataSource;
import org.h2.jdbcx.JdbcDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class DemoApp {

  public static void main(String[] args) throws Exception {
    // Setup H2
    JdbcDataSource h2 = new JdbcDataSource();
    h2.setURL("jdbc:h2:mem:demodb;DB_CLOSE_DELAY=-1");
    h2.setUser("sa");
    h2.setPassword("");

    // Wrap với QualityDataSource — dashboard tự start
    QualityDataSource ds = new QualityDataSource(h2, QualityConfig.getDefault());

    // Tạo schema
    try (Connection conn = ds.getConnection()) {
      conn.prepareStatement("""
                CREATE TABLE users (
                    id    INT PRIMARY KEY,
                    name  VARCHAR(100) NOT NULL,
                    email VARCHAR(100)
                )
            """).execute();

      conn.prepareStatement("""
                CREATE TABLE orders (
                    id      INT PRIMARY KEY,
                    user_id INT,
                    total   FLOAT,
                    FOREIGN KEY (user_id) REFERENCES users(id)
                )
            """).execute();

      conn.prepareStatement("""
                CREATE TABLE logs (
                    message TEXT,
                    created VARCHAR(50)
                )
            """).execute();
    }

    // Insert data
    try (Connection conn = ds.getConnection()) {
      for (int i = 1; i <= 10; i++) {
        PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO users VALUES (?, ?, ?)");
        stmt.setInt(1, i);
        stmt.setString(2, "User " + i);
        stmt.setString(3, "user" + i + "@example.com");
        stmt.executeUpdate();
      }
    }

    // Giả lập các anti-pattern
    try (Connection conn = ds.getConnection()) {
      // SELECT * — trigger SelectStarRule
      conn.prepareStatement("SELECT id, name, email FROM users").executeQuery();

      // N+1 — cùng query lặp lại nhiều lần
      for (int i = 1; i <= 12; i++) {
        conn.prepareStatement(
            "SELECT * FROM users WHERE id = 1").executeQuery();
      }

      // Query dùng email (nullable) trong WHERE
      conn.prepareStatement(
              "SELECT id FROM users WHERE email = 'test@example.com'")
          .executeQuery();
    }

    System.out.println("=== App đang chạy ===");
    System.out.println("Mở browser: http://localhost:9876");
    System.out.println("Nhấn Enter để dừng...");
    System.in.read();
  }
}