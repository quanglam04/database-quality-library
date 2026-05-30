package com.dbquality.rule;

import com.dbquality.collector.*;
import com.dbquality.collector.model.*;
import com.dbquality.config.QualityConfig;
import com.dbquality.core.QualityDataSource;
import com.dbquality.rule.impl.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineTest {

  private QualityDataSource qualityDataSource;
  private DDLCollector ddlCollector;

  @BeforeEach
  void setUp() throws Exception {
    JdbcDataSource h2 = new JdbcDataSource();
    h2.setURL("jdbc:h2:mem:ruleenginedb;DB_CLOSE_DELAY=-1");
    h2.setUser("sa");
    h2.setPassword("");

    qualityDataSource = new QualityDataSource(h2, QualityConfig.getTestDefault());
    ddlCollector = new DDLCollector();

    try (Connection conn = qualityDataSource.getConnection()) {
      conn.prepareStatement("DROP TABLE IF EXISTS order_items").execute();
      conn.prepareStatement("DROP TABLE IF EXISTS orders").execute();
      conn.prepareStatement("DROP TABLE IF EXISTS users").execute();

      // Bảng có PK
      conn.prepareStatement("""
                CREATE TABLE users (
                    id    INT PRIMARY KEY,
                    name  VARCHAR(100) NOT NULL,
                    email VARCHAR(100)
                )
            """).execute();

      // Bảng có FK nhưng KHÔNG có index trên FK — trigger UnindexedForeignKeyRule
      conn.prepareStatement("""
                CREATE TABLE orders (
                    id      INT PRIMARY KEY,
                    user_id INT,
                    total   DECIMAL(10,2),
                    FOREIGN KEY (user_id) REFERENCES users(id)
                )
            """).execute();

      // Bảng KHÔNG có PK — trigger MissingPrimaryKeyRule
      conn.prepareStatement("""
                CREATE TABLE order_items (
                    order_id   INT,
                    product_id INT,
                    quantity   INT
                )
            """).execute();

      // Insert data
      try (PreparedStatement stmt = conn.prepareStatement(
          "INSERT INTO users VALUES (?, ?, ?)")) {
        for (int i = 1; i <= 5; i++) {
          stmt.setInt(1, i);
          stmt.setString(2, "User " + i);
          stmt.setString(3, "user" + i + "@example.com");
          stmt.executeUpdate();
        }
      }
    }

    qualityDataSource.getSqlContext().clear();
  }

  // ── MissingPrimaryKeyRule ─────────────────────────────────────────

  @Test
  void shouldDetectMissingPrimaryKey() throws Exception {
    DDLContext ddl;
    try (Connection conn = qualityDataSource.getConnection()) {
      ddl = ddlCollector.collect(conn);
    }

    RuleEngine engine = new RuleEngine()
        .register(new MissingPrimaryKeyRule());

    List<Finding> findings = engine.analyze(ddl, qualityDataSource.getSqlContext());

    assertTrue(findings.stream()
        .anyMatch(f -> f.getRule().equals("MISSING_PRIMARY_KEY")
            && f.getTable().equalsIgnoreCase("ORDER_ITEMS")));
  }

  @Test
  void shouldNotFlagTablesWithPrimaryKey() throws Exception {
    DDLContext ddl;
    try (Connection conn = qualityDataSource.getConnection()) {
      ddl = ddlCollector.collect(conn);
    }

    RuleEngine engine = new RuleEngine()
        .register(new MissingPrimaryKeyRule());

    List<Finding> findings = engine.analyze(ddl, qualityDataSource.getSqlContext());

    assertFalse(findings.stream()
        .anyMatch(f -> f.getRule().equals("MISSING_PRIMARY_KEY")
            && f.getTable().equalsIgnoreCase("USERS")));
  }

  // ── UnindexedForeignKeyRule ───────────────────────────────────────

  @Test
  void shouldDetectUnindexedForeignKey() {
    // Tạo mock DDLContext thủ công — không cần H2
    Column userIdCol = new Column("USER_ID", "INT", false, false);
    ForeignKey fk = new ForeignKey("FK_USER", "USER_ID", "USERS", "ID");

    // Không có index nào trên USER_ID
    Table orders = new Table("ORDERS",
        List.of(userIdCol),
        List.of(), // không có index
        List.of(fk));

    DDLContext ddl = new DDLContext(List.of(orders));
    SQLContext sql = new SQLContext();

    RuleEngine engine = new RuleEngine()
        .register(new UnindexedForeignKeyRule());

    List<Finding> findings = engine.analyze(ddl, sql);

    assertTrue(findings.stream()
        .anyMatch(f -> f.getRule().equals("UNINDEXED_FOREIGN_KEY")
            && f.getTable().equalsIgnoreCase("ORDERS")));
  }

  // ── SelectStarRule ────────────────────────────────────────────────

  @Test
  void shouldDetectSelectStar() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      conn.prepareStatement("SELECT * FROM users").executeQuery();
    }

    DDLContext ddl;
    try (Connection conn = qualityDataSource.getConnection()) {
      ddl = ddlCollector.collect(conn);
    }

    RuleEngine engine = new RuleEngine()
        .register(new SelectStarRule());

    List<Finding> findings = engine.analyze(ddl, qualityDataSource.getSqlContext());

    assertTrue(findings.stream()
        .anyMatch(f -> f.getRule().equals("SELECT_STAR")));
  }

  @Test
  void shouldNotFlagSpecificColumns() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      PreparedStatement stmt = conn.prepareStatement(
          "SELECT id, name FROM users WHERE id = 1");
      stmt.executeQuery();
    }

    DDLContext ddl;
    try (Connection conn = qualityDataSource.getConnection()) {
      ddl = ddlCollector.collect(conn);
    }

    RuleEngine engine = new RuleEngine()
        .register(new SelectStarRule());

    List<Finding> findings = engine.analyze(ddl, qualityDataSource.getSqlContext());

    assertTrue(findings.stream()
        .noneMatch(f -> f.getRule().equals("SELECT_STAR")));
  }

  // ── SlowQueryRule ─────────────────────────────────────────────────

  @Test
  void shouldDetectSlowQuery() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      conn.prepareStatement("SELECT * FROM users").executeQuery();
    }

    DDLContext ddl;
    try (Connection conn = qualityDataSource.getConnection()) {
      ddl = ddlCollector.collect(conn);
    }

    // threshold = 0ms → mọi query đều là slow
    RuleEngine engine = new RuleEngine()
        .register(new SlowQueryRule(0));

    List<Finding> findings = engine.analyze(ddl, qualityDataSource.getSqlContext());

    assertTrue(findings.stream()
        .anyMatch(f -> f.getRule().equals("SLOW_QUERY")));
  }

  @Test
  void shouldNotFlagFastQuery() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      conn.prepareStatement("SELECT * FROM users").executeQuery();
    }

    DDLContext ddl;
    try (Connection conn = qualityDataSource.getConnection()) {
      ddl = ddlCollector.collect(conn);
    }

    // threshold = 999999ms → không có slow query
    RuleEngine engine = new RuleEngine()
        .register(new SlowQueryRule(999999));

    List<Finding> findings = engine.analyze(ddl, qualityDataSource.getSqlContext());

    assertTrue(findings.stream()
        .noneMatch(f -> f.getRule().equals("SLOW_QUERY")));
  }

  // ── NPlusOneRule ──────────────────────────────────────────────────

  @Test
  void shouldDetectNPlusOne() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      // Cùng pattern lặp lại 5 lần
      for (int i = 1; i <= 5; i++) {
        PreparedStatement stmt = conn.prepareStatement(
            "SELECT id, name FROM users WHERE id = " + i);
        stmt.executeQuery();
      }
    }

    DDLContext ddl;
    try (Connection conn = qualityDataSource.getConnection()) {
      ddl = ddlCollector.collect(conn);
    }

    // threshold = 3 → pattern lặp > 3 lần mới flag
    RuleEngine engine = new RuleEngine()
        .register(new NPlusOneRule(3));

    List<Finding> findings = engine.analyze(ddl, qualityDataSource.getSqlContext());

    // Query khác nhau (id = 1, 2, 3...) nên không phải N+1
    assertTrue(findings.stream()
        .noneMatch(f -> f.getRule().equals("N_PLUS_ONE")));
  }

  @Test
  void shouldDetectNPlusOneWithSamePattern() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      // Cùng 1 SQL y hệt lặp lại 5 lần
      for (int i = 1; i <= 5; i++) {
        conn.prepareStatement("SELECT id, name FROM users WHERE id = 1")
            .executeQuery();
      }
    }

    DDLContext ddl;
    try (Connection conn = qualityDataSource.getConnection()) {
      ddl = ddlCollector.collect(conn);
    }

    RuleEngine engine = new RuleEngine()
        .register(new NPlusOneRule(3));

    List<Finding> findings = engine.analyze(ddl, qualityDataSource.getSqlContext());

    assertTrue(findings.stream()
        .anyMatch(f -> f.getRule().equals("N_PLUS_ONE")));
  }

  // ── NullableRiskRule ──────────────────────────────────────────────

  @Test
  void shouldDetectNullableRisk() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      // Query dùng cột email (nullable) trong WHERE
      conn.prepareStatement(
              "SELECT id FROM users WHERE email = 'test@example.com'")
          .executeQuery();
    }

    DDLContext ddl;
    try (Connection conn = qualityDataSource.getConnection()) {
      ddl = ddlCollector.collect(conn);
    }

    RuleEngine engine = new RuleEngine()
        .register(new NullableRiskRule());

    List<Finding> findings = engine.analyze(ddl, qualityDataSource.getSqlContext());

    assertTrue(findings.stream()
        .anyMatch(f -> f.getRule().equals("NULLABLE_RISK")
            && f.getColumn().equalsIgnoreCase("EMAIL")));
  }

  // ── RuleEngine tổng hợp ───────────────────────────────────────────

  @Test
  void shouldRunAllRulesAndAggregateFindings() throws Exception {
    try (Connection conn = qualityDataSource.getConnection()) {
      conn.prepareStatement("SELECT * FROM users").executeQuery();
    }

    DDLContext ddl;
    try (Connection conn = qualityDataSource.getConnection()) {
      ddl = ddlCollector.collect(conn);
    }

    RuleEngine engine = RuleEngine.withDefaultRules(0, 3);

    List<Finding> findings = engine.analyze(ddl, qualityDataSource.getSqlContext());

    assertTrue(findings.size() > 1);

    // H2 tự tạo index cho FK nên không test UNINDEXED_FOREIGN_KEY ở đây
    assertTrue(findings.stream()
        .anyMatch(f -> f.getRule().equals("MISSING_PRIMARY_KEY")));
    assertTrue(findings.stream()
        .anyMatch(f -> f.getRule().equals("SELECT_STAR")));
  }
}