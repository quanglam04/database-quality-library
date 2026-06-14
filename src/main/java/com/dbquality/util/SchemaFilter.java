package com.dbquality.util;

/**
 * Utility class lọc các database objects thuộc về framework/tool nội bộ.
 *
 * <p>Dùng để loại trừ các bảng, index, và schema objects không thuộc về
 * business logic của ứng dụng khỏi kết quả phân tích của Rule Engine.</p>
 *
 * <p>Các framework được nhận diện:</p>
 * <ul>
 *   <li><b>Flyway</b> — {@code flyway_schema_history}, {@code flyway_schema_hist}</li>
 *   <li><b>Liquibase</b> — {@code databasechangelog}, {@code databasechangeloglock}</li>
 *   <li><b>Spring Batch</b> — {@code batch_*}, {@code spring_batch_*}</li>
 *   <li><b>Spring Session</b> — {@code spring_session*}</li>
 *   <li><b>Quartz Scheduler</b> — {@code qrtz_*}</li>
 * </ul>
 */
public class SchemaFilter {

  private SchemaFilter() {}

  /**
   * Kiểm tra một bảng có phải là bảng system/internal của framework không.
   *
   * <p>Các bảng system không thuộc về business logic của ứng dụng —
   * ví dụ bảng migration history của Flyway, bảng job của Quartz Scheduler.
   * Rule Engine nên bỏ qua các bảng này để tránh false positive.</p>
   *
   * @param tableName tên bảng cần kiểm tra (case-insensitive)
   * @return {@code true} nếu là bảng system và nên bị loại trừ khỏi phân tích
   */
  public static boolean isSystemTable(String tableName) {
    if (tableName == null) return false;
    String upper = tableName.toUpperCase();

    // Flyway migration history
    if (upper.startsWith("FLYWAY_")) return true;

    // Liquibase changelog
    if (upper.startsWith("DATABASECHANGELOG")) return true;

    // Spring Batch
    if (upper.startsWith("BATCH_")
        || upper.startsWith("SPRING_BATCH_")) return true;

    // Spring Session
    if (upper.startsWith("SPRING_SESSION")) return true;

    // Quartz Scheduler
    if (upper.startsWith("QRTZ_")) return true;

    // Legacy schema version table (Flyway < 5)
    if (upper.equals("SCHEMA_VERSION")) return true;

    return false;
  }
}