package com.dbquality.util;

/**
 * Normalize SQL text để các query khác giá trị bind nhưng cùng pattern
 * được gom thành 1 nhóm khi đếm metrics.
 *
 * <p>Ví dụ:</p>
 * <pre>
 *   SELECT * FROM users WHERE id = 1     → SELECT * FROM users WHERE id = ?
 *   SELECT * FROM users WHERE id = 2     → SELECT * FROM users WHERE id = ?
 *   SELECT * FROM users WHERE id = ?     → SELECT * FROM users WHERE id = ?
 * </pre>
 *
 * <p>Với PreparedStatement (JPA/Hibernate dùng mặc định), SQL đã được
 * tham số hóa thành {@code ?} sẵn — normalizer chỉ cần handle trim
 * whitespace. Normalize literal giá trị chỉ cần thiết khi app dùng
 * Statement thường với string concatenation.</p>
 */
public class SQLNormalizer {

  private SQLNormalizer() {}

  /**
   * Normalize SQL text.
   * @param sql raw SQL có thể có literal values hoặc bind parameters
   * @return SQL đã chuẩn hóa, lowercase, single-space, literals → ?
   */
  public static String normalize(String sql) {
    if (sql == null) return "";
    return sql
        .replaceAll("'[^']*'", "?")        // string literals: 'abc' → ?
        .replaceAll("\\b\\d+\\b", "?")     // numeric literals: 123 → ?
        .replaceAll("\\s+", " ")           // multiple spaces → single
        .trim()
        .toLowerCase();
  }
}