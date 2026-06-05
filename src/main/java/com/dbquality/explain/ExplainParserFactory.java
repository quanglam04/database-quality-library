package com.dbquality.explain;

import com.dbquality.explain.impl.MariaDBExplainParser;
import com.dbquality.explain.impl.MySQLExplainParser;
import com.dbquality.explain.impl.PostgreSQLExplainParser;
import com.dbquality.explain.impl.SQLServerExplainParser;

import java.util.List;

/**
 * Factory tạo {@link ExplainParser} phù hợp dựa trên tên database vendor.
 * Dùng {@code DatabaseMetaData.getDatabaseProductName()} để xác định vendor.
 */
public class ExplainParserFactory {

  private static final List<ExplainParser> PARSERS = List.of(
      new MySQLExplainParser(),
      new MariaDBExplainParser(),
      new PostgreSQLExplainParser(),
      new SQLServerExplainParser()
  );

  /**
   * Tạo ExplainParser phù hợp với database vendor.
   *
   * @param databaseProductName tên vendor từ {@code DatabaseMetaData.getDatabaseProductName()}
   * @return ExplainParser phù hợp, hoặc {@code null} nếu không có parser hỗ trợ vendor này
   */
  public static ExplainParser create(String databaseProductName) {
    if (databaseProductName == null) return null;
    return PARSERS.stream()
        .filter(p -> p.supports(databaseProductName))
        .findFirst()
        .orElse(null);
  }
}