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

    // Flyway PostgreSQL — pg_catalog queries
    if (upper.contains("PG_CATALOG")
        || upper.contains("PG_NAMESPACE")
        || upper.contains("PG_CLASS")
        || upper.contains("PG_TYPE")
        || upper.contains("PG_PROC")
        || upper.contains("PG_DEPEND")) return true;

    // Flyway PostgreSQL — schema existence check
    if (upper.contains("SELECT COUNT(*) FROM PG_NAMESPACE")
        || upper.contains("SELECT EXISTS")) return true;

    // Flyway PostgreSQL — advisory lock, config, schema
    if (upper.contains("PG_TRY_ADVISORY")
        || upper.contains("PG_ADVISORY")
        || upper.contains("SET_CONFIG(")
        || upper.contains("CURRENT_SCHEMA")
        || upper.contains("SELECT COUNT(*) FROM PG_NAMESPACE")) return true;

    // Flyway PostgreSQL — remaining queries
    if (upper.contains("CURRENT_USER")
        || upper.contains("CURRENT_SCHEMA")
        || upper.contains("SET_CONFIG(")
        || upper.contains("PG_TRY_ADVISORY")
        || upper.contains("PG_ADVISORY")) return true;

    // SELECT COUNT(*) FROM pg_namespace — dùng contains thay vì exact match
    if (upper.contains("FROM PG_NAMESPACE")) return true;

    // SELECT EXISTS với pg_catalog
    if (upper.contains("SELECT EXISTS") && upper.contains("PG_CATALOG")) return true;
    if (upper.contains("SELECT EXISTS") && upper.contains("PG_NAMESPACE")) return true;

    return false;


  }
}