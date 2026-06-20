package com.dbquality.constant;

import java.util.List;

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
   * Phát hiện function được áp dụng lên cột trong WHERE — gây bypass index.
   * Match các function phổ biến: LOWER, UPPER, TRIM, YEAR, MONTH, DATE, CAST, COALESCE...
   * <br>Ví dụ match: {@code WHERE LOWER(name) = 'abc'}, {@code WHERE YEAR(created_at) = 2026}
   */
  public static final String FUNCTION_ON_COLUMN_PATTERN = ".*WHERE\\s+.*\\b(LOWER|UPPER|TRIM|LTRIM|RTRIM|SUBSTRING|SUBSTR|YEAR|MONTH|DAY|DATE|DATEPART|EXTRACT|CAST|CONVERT|COALESCE|IFNULL|NVL|ABS|ROUND|FLOOR|CEILING|CONCAT)\\s*\\(.*";

  /**
   * Extract phần filter sau WHERE/ON/ORDER BY/GROUP BY — nơi index thực sự được dùng.
   * Group 1: nội dung của clause (dừng trước clause tiếp theo).
   * <br>Ví dụ: {@code WHERE status = 'A' ORDER BY id} → group 1 sau WHERE = {@code status = 'A'}
   */
  public static final String FILTER_CLAUSE_PATTERN = "(?:WHERE|ON|ORDER\\s+BY|GROUP\\s+BY)\\s+(.+?)(?=\\s+(?:WHERE|ORDER\\s+BY|GROUP\\s+BY|LIMIT|UNION|$))";

  /**
   * Phát hiện LIKE với leading wildcard — gây full table scan.
   * Match 3 dạng:
   * <ul>
   *   <li>{@code LIKE '%abc'} hoặc {@code LIKE ?} (PreparedStatement)</li>
   *   <li>{@code LIKE LOWER(...)} hoặc {@code LIKE UPPER(...)}</li>
   *   <li>{@code LIKE CONCAT('%', ...)}</li>
   * </ul>
   */
  public static final String LIKE_LEADING_WILDCARD = ".*LIKE\\s+('%|\\?)|.*LIKE\\s+(LOWER|UPPER)\\s*\\(.*|.*LIKE\\s+CONCAT\\s*\\(\\s*['\"]?%.*";

  /**
   * Phát hiện NOT IN với subquery — thường bypass index và xử lý NULL không nhất quán.
   * <br>Ví dụ: {@code WHERE id NOT IN (SELECT user_id FROM ...)}
   */
  public static final String NOT_IN_SUBQUERY = ".*NOT\\s+IN\\s*\\(\\s*SELECT.*";

  /**
   * Phát hiện operator không bằng ({@code <>} hoặc {@code !=}) trong WHERE — không dùng được index.
   * <br>Ví dụ: {@code WHERE status <> 'DELETED'}
   */
  public static final String NOT_EQUAL_OPERATOR = ".*WHERE\\s+.*(<>|!=).*";

  /**
   * Phát hiện filter {@code IS NULL} hoặc {@code IS NOT NULL} trong WHERE.
   * Thường bypass index trừ khi có partial index (PostgreSQL) hoặc filtered index (SQL Server).
   */
  public static final String IS_NULL_FILTER = ".*WHERE\\s+.*IS\\s+(NOT\\s+)?NULL.*";

  /**
   * Match từ {@code OR} đứng độc lập (word boundary).
   * Dùng để đếm số lượng OR trong WHERE clause.
   */
  public static final String OR_PATTERN = "\\bOR\\b";

  /**
   * Match cột đơn (không có table prefix) trong WHERE/AND/OR/ON.
   * Group 1: tên cột.
   * <br>Ví dụ: {@code WHERE status = 'A'} → group 1 = {@code status}
   */
  public static final String SIMPLE_COLUMN_PATTERN = "(?:WHERE|AND|OR|ON)\\s+(\\w+)\\s*(?:=|<>|!=|<|>|<=|>=|LIKE|IN|BETWEEN|IS)";

  /**
   * Match cột có table prefix (JPA/Hibernate thường generate dạng này).
   * Group 1: alias/table, group 2: tên cột.
   * <br>Ví dụ: {@code WHERE e1_0.email = ?} → group 1 = {@code e1_0}, group 2 = {@code email}
   */
  public static final String QUALIFIED_COLUMN_PATTERN = "(?:WHERE|AND|OR|ON)\\s+(\\w+)\\.(\\w+)\\s*(?:=|<>|!=|<|>|<=|>=|LIKE|IN|BETWEEN|IS)";

  /**
   * Extract mapping alias → table từ FROM/JOIN clause (có dùng AS hoặc không).
   * Group 1: tên bảng, group 2: alias.
   * <br>Ví dụ: {@code FROM employees e1_0} → group 1 = {@code employees}, group 2 = {@code e1_0}
   */
  public static final String TABLE_ALIAS_PATTERN = "(?:FROM|JOIN)\\s+(\\w+)\\s+(?:AS\\s+)?(\\w+)";

  /**
   * Extract tên bảng khi không có alias (theo sau là clause khác hoặc cuối câu).
   * Group 1: tên bảng.
   * <br>Ví dụ: {@code FROM employees WHERE...} → group 1 = {@code employees}
   */
  public static final String TABLE_NO_ALIAS_PATTERN = "(?:FROM|JOIN)\\s+(\\w+)(?:\\s+(?:WHERE|ON|INNER|LEFT|RIGHT|GROUP|ORDER|$))";

  /**
   * Phát hiện {@code SELECT * FROM} — không phân biệt hoa thường.
   * <br>Ví dụ: {@code SELECT * FROM users}
   */
  public static final String SELECT_STAR_PATTERN = "(?i)SELECT\\s+\\*\\s+FROM";
}
