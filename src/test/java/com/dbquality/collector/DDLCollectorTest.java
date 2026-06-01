package com.dbquality.collector;

import com.dbquality.collector.model.Column;
import com.dbquality.collector.model.ForeignKey;
import com.dbquality.collector.model.Index;
import com.dbquality.collector.model.Table;
import com.dbquality.core.QualityDataSource;
import com.dbquality.config.QualityConfig;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DDLCollectorTest {

  private QualityDataSource qualityDataSource;
  private DDLCollector collector;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource h2 = new JdbcDataSource();
    h2.setURL("jdbc:h2:mem:ddltestdb;DB_CLOSE_DELAY=-1");
    h2.setUser("sa");
    h2.setPassword("");

    qualityDataSource = new QualityDataSource(h2, QualityConfig.getTestDefault());
    collector = new DDLCollector();

    // Tạo schema test
    try (Connection conn = qualityDataSource.getConnection()) {
      // Xóa nếu đã tồn tại
      conn.prepareStatement("DROP TABLE IF EXISTS order_items").execute();
      conn.prepareStatement("DROP TABLE IF EXISTS orders").execute();
      conn.prepareStatement("DROP TABLE IF EXISTS users").execute();

      // Bảng có PK
      conn.prepareStatement("""
                CREATE TABLE users (
                    id      INT PRIMARY KEY,
                    name    VARCHAR(100) NOT NULL,
                    email   VARCHAR(100)
                )
            """).execute();

      // Bảng có PK + FK + Index
      conn.prepareStatement("""
                CREATE TABLE orders (
                    id      INT PRIMARY KEY,
                    user_id INT NOT NULL,
                    total   DECIMAL(10,2),
                    FOREIGN KEY (user_id) REFERENCES users(id)
                )
            """).execute();

      // Bảng KHÔNG có PK — để test MissingPrimaryKeyRule sau này
      conn.prepareStatement("""
                CREATE TABLE order_items (
                    order_id   INT,
                    product_id INT,
                    quantity   INT
                )
            """).execute();

      // Thêm index thủ công
      conn.prepareStatement(
          "CREATE INDEX idx_orders_user_id ON orders(user_id)"
      ).execute();
    }
  }

  @Test
  void shouldCollectAllTables() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      DDLContext context = collector.collect(conn);

      List<String> tableNames = context.getTables().stream()
          .map(Table::getName)
          .toList();

      assertTrue(tableNames.contains("USERS"));
      assertTrue(tableNames.contains("ORDERS"));
      assertTrue(tableNames.contains("ORDER_ITEMS"));
    }
  }

  @Test
  void shouldDetectPrimaryKey() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      DDLContext context = collector.collect(conn);

      Table users = findTable(context, "USERS");
      assertNotNull(users);
      assertTrue(users.hasPrimaryKey());

      Column idColumn = users.getColumns().stream()
          .filter(c -> c.getName().equals("ID"))
          .findFirst()
          .orElse(null);
      assertNotNull(idColumn);
      assertTrue(idColumn.isPrimaryKey());
    }
  }

  @Test
  void shouldDetectTableWithoutPrimaryKey() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      DDLContext context = collector.collect(conn);

      Table orderItems = findTable(context, "ORDER_ITEMS");
      assertNotNull(orderItems);
      assertFalse(orderItems.hasPrimaryKey());
    }
  }

  @Test
  void shouldCollectColumns() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      DDLContext context = collector.collect(conn);

      Table users = findTable(context, "USERS");
      assertNotNull(users);
      assertEquals(3, users.getColumns().size());

      List<String> columnNames = users.getColumns().stream()
          .map(Column::getName)
          .toList();
      assertTrue(columnNames.contains("ID"));
      assertTrue(columnNames.contains("NAME"));
      assertTrue(columnNames.contains("EMAIL"));
    }
  }

  @Test
  void shouldDetectNullableColumn() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      DDLContext context = collector.collect(conn);

      Table users = findTable(context, "USERS");
      assertNotNull(users);

      Column email = users.getColumns().stream()
          .filter(c -> c.getName().equals("EMAIL"))
          .findFirst()
          .orElse(null);
      assertNotNull(email);
      assertTrue(email.isNullable());

      Column name = users.getColumns().stream()
          .filter(c -> c.getName().equals("NAME"))
          .findFirst()
          .orElse(null);
      assertNotNull(name);
      assertFalse(name.isNullable());
    }
  }

  @Test
  void shouldCollectForeignKeys() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      DDLContext context = collector.collect(conn);

      Table orders = findTable(context, "ORDERS");
      assertNotNull(orders);
      assertFalse(orders.getForeignKeys().isEmpty());

      ForeignKey fk = orders.getForeignKeys().get(0);
      assertEquals("USER_ID", fk.getColumn().toUpperCase());
      assertEquals("USERS", fk.getReferencedTable().toUpperCase());
    }
  }

  @Test
  void shouldCollectIndexes() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      DDLContext context = collector.collect(conn);

      Table orders = findTable(context, "ORDERS");
      assertNotNull(orders);

      List<String> indexNames = orders.getIndexes().stream()
          .map(Index::getName)
          .toList();
      assertTrue(indexNames.stream()
          .anyMatch(n -> n.toUpperCase().contains("USER_ID")));
    }
  }

  @Test
  void shouldDetectIndexOnColumn() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      DDLContext context = collector.collect(conn);

      Table orders = findTable(context, "ORDERS");
      assertNotNull(orders);

      Column userIdColumn = orders.getColumns().stream()
          .filter(c -> c.getName().equalsIgnoreCase("user_id"))
          .findFirst()
          .orElse(null);
      assertNotNull(userIdColumn);

      // user_id có index
      assertTrue(context.hasIndexOn(orders, userIdColumn));
    }
  }

  // ── Helper ────────────────────────────────────────────────────────

  private Table findTable(DDLContext context, String tableName) {
    return context.getTables().stream()
        .filter(t -> t.getName().equalsIgnoreCase(tableName))
        .findFirst()
        .orElse(null);
  }
}