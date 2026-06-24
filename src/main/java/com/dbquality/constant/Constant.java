package com.dbquality.constant;

import java.util.List;
import java.util.Set;

public class Constant {

  // Provider identifiers
  public static final String PROVIDER_OPENAI = "openai";
  public static final String PROVIDER_CLAUDE = "claude";
  public static final String PROVIDER_GEMINI = "gemini";

  // API endpoint
  public static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
  public static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
  public static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

  // Claude specific
  public static final String ANTHROPIC_VERSION = "2023-06-01";

  // HTTP timeouts
  public static final int CONNECT_TIMEOUT_SECONDS = 30;
  public static final int REQUEST_TIMEOUT_SECONDS = 60;

  // LLM request defaults
  public static final int DEFAULT_MAX_TOKENS = 2000;
  public static final double DEFAULT_TEMPERATURE = 0.3;

  // Rule name
  public class RuleName {
    public static String MissingPrimaryKey = "MISSING_PRIMARY_KEY";
    public static String UnindexedForeignKey = "UNINDEXED_FOREIGN_KEY";
    public static String NullableRisk = "NULLABLE_RISK";
    public static String SuspiciousDataType = "SUSPICIOUS_DATA_TYPE";
    public static String UnusedIndex = "UNUSED_INDEX";
    public static String MissingIndexSuggestion = "MISSING_INDEX_SUGGESTION";
    public static String FullTableScanCandidate = "FULL_TABLE_SCAN_CANDIDATE";
    public static String NPlusOne = "N_PLUS_ONE";
    public static String SelectStar = "SELECT_STAR";
    public static String SlowQuery = "SLOW_QUERY";
  }

  // Database name
  public class DatabaseName {
    public static String MySQL = "MYSQL";
    public static String MariaDB = "MARIADB";
    public static String PostgreSQL = "POSTGRESQL";
    public static String SQlServer = "MICROSOFT SQL SERVER";
    public static String Oracle = "ORACLE";
  }

  /**
   * Danh sách package prefix bị loại trừ khi capture calledFrom.
   * Khi thư viện intercept SQL, nó duyệt stack trace và bỏ qua các frame
   * thuộc các prefix này để tìm đúng frame code nghiệp vụ của người dùng.
   *
   * <p>Bao gồm: JDK internals, test frameworks, Spring, Hibernate,
   * Jackson, Servlet containers (Tomcat, Catalina), JDBC drivers,
   * và các class nội bộ của thư viện.</p>
   */
  public static final List<String> INTERNAL_PREFIXES = List.of(
      "java.", "javax.", "sun.", "jdk.", "com.sun.",
      "org.junit.", "org.opentest4j.",
      "org.apache.maven.", "org.apache.surefire.",
      "org.apache.catalina.", "org.apache.tomcat.",
      "com.intellij.",
      "org.springframework.",
      "org.hibernate.",
      "org.flywaydb.",
      "com.fasterxml.jackson.",
      "jakarta.servlet.",
      "javax.servlet.",
      "com.zaxxer.", "org.apache.commons.dbcp.", "c3p0.",
      "com.mysql.", "org.postgresql.", "org.h2.",
      "com.microsoft.sqlserver.", "org.mariadb.", "org.sqlite.",
      "com.dbquality.core.QualityDataSource.",
      "com.dbquality.core.QualityConnection.",
      "com.dbquality.core.QualityPreparedStatement",
      "com.dbquality.collector.",
      "com.dbquality.config."
  );

  public static final List<String> INTERNAL_CONTAINS_PATTERNS = List.of(
      "$HibernateProxy$",     // Hibernate lazy loading proxy
      "$$EnhancerBy",         // CGLIB / Spring AOP proxy
      "$$_javassist_",        // Javassist proxy
      "$Proxy",               // JDK dynamic proxy
      "$$Lambda$"             // Lambda generated classes
  );

  // Regex patterns
  /**
   * Mask password trong JDBC URL — không phân biệt hoa thường.
   * Match: {@code password=xxx} hoặc {@code pwd=xxx} đến khi gặp {@code &} hoặc {@code ;}
   * <br>Ví dụ: {@code jdbc:mysql://host/db?user=admin&password=secret}
   * → {@code jdbc:mysql://host/db?user=admin&password=***}
   */
  public static final String JDBC_PASSWORD_MASK_PATTERN = "(?i)(password|pwd)=[^&;]*";

  /**
   * Extract tên bảng sau từ khoá FROM/JOIN/INTO/UPDATE.
   * Group 1: tên bảng (bắt đầu bằng chữ cái hoặc underscore).
   * <br>Ví dụ: {@code SELECT * FROM users WHERE...} → group 1 = {@code users}
   */
  public static final String SQL_TABLE_NAME_PATTERN = "(?:FROM|JOIN|INTO|UPDATE)\\s+([a-zA-Z_][a-zA-Z0-9_]*)";

  /**
   * Extract mapping alias → table từ FROM/JOIN clause (có dùng AS hoặc không).
   * Group 1: tên bảng, group 2: alias.
   * <br>Ví dụ: {@code FROM employees e1_0} → group 1 = {@code employees}, group 2 = {@code e1_0}
   */
  public static final String TABLE_ALIAS_PATTERN = "(?:FROM|JOIN)\\s+(\\w+)(?:\\s+AS)?\\s+(\\w+)(?:\\s|,|$|\\()";

  /**
   * Phát hiện {@code SELECT * FROM} — không phân biệt hoa thường.
   * <br>Ví dụ: {@code SELECT * FROM users}
   */
  public static final String SELECT_STAR_PATTERN = "(?i)SELECT\\s+\\*\\s+FROM";

  /**
   * Tên index trong JSON output của EXPLAIN — match {@code "key"} (MySQL),
   * {@code "Index Name"} (PostgreSQL), {@code "index_name"} (SQL Server).
   * <br>Group 1 = tên index.
   * <br>Ví dụ: {@code "key": "idx_emp_email"} → {@code idx_emp_email}
   */
  public static final String INDEX_NAME_PATTERN = "\"(?:key|Index Name|index_name)\"\\s*:\\s*\"([^\"]+)\"";

  /**
   * Ký tự phân tách token SQL — dùng split tách tên bảng/cột.
   * <br>Match: whitespace, {@code ,}, {@code ;}, {@code (}.
   */
  public static final String SQL_TOKEN_DELIMITER_PATTERN = "[\\s,;(]";

  /**
   * Số rows được đọc/quét trong message của ExplainParser.
   * <br>Group 1 = số rows.
   * <br>Ví dụ: {@code đọc 508 rows} → {@code 508}
   */
  public static final String ROWS_PATTERN = "(?:đọc|read|examined)\\s+(\\d+)\\s+rows?";

  /**
   * Cột xuất hiện trong điều kiện filter (WHERE/AND/OR/ON).
   * <br>Group 1 = alias (optional), Group 2 = tên cột.
   * <br>Ví dụ: {@code WHERE e1_0.email = ?} → {@code e1_0}, {@code email}
   */
  public static final String COLUMN_IN_FILTER = "(?:WHERE|AND|OR|ON)\\s+(?:(\\w+)\\.)?(\\w+)\\s*(?:=|<>|!=|<|>|<=|>=|LIKE|IN|BETWEEN|IS)";

  /**
   * Check rule name có phải indicator của full scan / index not used không.
   */
  public static final Set<String> FULL_SCAN_RULE_NAMES = Set.of(
      "FULL_TABLE_SCAN",
      "FULL_INDEX_SCAN",
      "INDEX_NOT_USED",
      "TABLE_SCAN",
      "SEQ_SCAN"
  );

  /**
   * Regex thay placeholder {@code ?} bằng giá trị dummy khi build câu EXPLAIN.
   * Các pattern dùng số/integer được xử lý trước (LIMIT/OFFSET/FETCH/TOP),
   * còn {@code ?} ở WHERE/IN/JOIN được thay bằng {@code NULL} ở bước cuối.
   */
  public static final class ExplainPlaceholder {

    /** {@code LIMIT ?, ?} (MySQL/MariaDB: offset, count) → {@code LIMIT 0, 10} */
    public static final String LIMIT_OFFSET_COUNT = "(?i)limit\\s+\\?\\s*,\\s*\\?";
    public static final String LIMIT_OFFSET_COUNT_REPLACEMENT = "LIMIT 0, 10";

    /** {@code LIMIT ? OFFSET ?} (PostgreSQL) → {@code LIMIT 10 OFFSET 0} */
    public static final String LIMIT_WITH_OFFSET = "(?i)limit\\s+\\?\\s+offset\\s+\\?";
    public static final String LIMIT_WITH_OFFSET_REPLACEMENT = "LIMIT 10 OFFSET 0";

    /** {@code LIMIT ?} → {@code LIMIT 10} */
    public static final String LIMIT_SINGLE = "(?i)limit\\s+\\?";
    public static final String LIMIT_SINGLE_REPLACEMENT = "LIMIT 10";

    /** {@code OFFSET ?} → {@code OFFSET 0} */
    public static final String OFFSET_SINGLE = "(?i)offset\\s+\\?";
    public static final String OFFSET_SINGLE_REPLACEMENT = "OFFSET 0";

    /** {@code FETCH NEXT|FIRST ? ROWS} (SQL Server, ANSI) → giữ NEXT/FIRST, thay {@code ?} = 10 */
    public static final String FETCH_ROWS = "(?i)fetch\\s+(next|first)\\s+\\?\\s+rows";
    public static final String FETCH_ROWS_REPLACEMENT = "FETCH $1 10 ROWS";

    /** {@code TOP (?)} (SQL Server) → {@code TOP (10)} */
    public static final String TOP_PAREN = "(?i)top\\s*\\(\\s*\\?\\s*\\)";
    public static final String TOP_PAREN_REPLACEMENT = "TOP (10)";

    /** {@code TOP ?} (SQL Server) → {@code TOP 10} */
    public static final String TOP_SIMPLE = "(?i)top\\s+\\?";
    public static final String TOP_SIMPLE_REPLACEMENT = "TOP 10";

    private ExplainPlaceholder() {}
  }

}
