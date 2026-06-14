package com.dbquality.util;

/**
 * Utility class lọc các SQL system/internal không cần intercept.
 * Dùng chung cho QualityPreparedStatement và QualityStatement.
 */
public class SQLFilter {

  private SQLFilter() {}

  /**
   * Kiểm tra SQL có phải application SQL cần intercept không.
   * Chỉ capture DML (SELECT/INSERT/UPDATE/DELETE) và không phải system SQL.
   */
  public static boolean isApplicationSQL(String sql) {
    if (sql == null) return false;
    String upper = sql.trim().toUpperCase();
    return (upper.startsWith("SELECT")
        || upper.startsWith("INSERT")
        || upper.startsWith("UPDATE")
        || upper.startsWith("DELETE"))
        && !isSystemSQL(upper);
  }

  /**
   * Kiểm tra SQL có phải system/internal query cần bỏ qua không.
   * Bao gồm: HikariCP health check, Hibernate validation,
   * Flyway migration queries, INFORMATION_SCHEMA queries.
   */
  public static boolean isSystemSQL(String upper) {
    // HikariCP / connection pool health check
    if (upper.equals("SELECT 1")
        || upper.equals("SELECT 1 FROM DUAL")
        || upper.contains("/* PING */")
        || upper.contains("/* ISVALID */")) return true;

    // Hibernate schema validation
    if (upper.contains("WHERE 1=0")) return true;

    // System schema queries
    if (upper.contains("INFORMATION_SCHEMA")) return true;
    if (upper.contains("PERFORMANCE_SCHEMA")) return true;

    // Flyway — bảng history
    if (upper.contains("FLYWAY_SCHEMA_HISTORY")
        || upper.contains("FLYWAY_SCHEMA_HIST")
        || upper.contains("SCHEMA_VERSION")) return true;

    // Liquibase
    if (upper.contains("DATABASECHANGELOG")
        || upper.contains("DATABASECHANGELOGLOCK")) return true;

    // Flyway — system queries
    if (upper.contains("SELECT VERSION()")
        || upper.contains("SELECT DATABASE()")
        || upper.contains("SELECT @@")
        || upper.contains("SHOW ")) return true;

    // Flyway — distributed lock
    if (upper.contains("GET_LOCK(")
        || upper.contains("RELEASE_LOCK(")
        || upper.contains("SUBSTRING_INDEX(USER()")) return true;

    return false;
  }
}