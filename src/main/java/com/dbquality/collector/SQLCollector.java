package com.dbquality.collector;

import com.dbquality.constant.Constant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Quản lý buffer SQL records được thu thập trong session hiện tại.
 * Cung cấp các method tiện ích để Rule Engine truy vấn dữ liệu.
 */
public class SQLCollector {

  private final SQLContext sqlContext;

  public SQLCollector(SQLContext sqlContext) {
    this.sqlContext = sqlContext;
  }

  /**
   * @return toàn bộ SQL records trong session
   */
  public List<SQLRecord> getAll() {
    return Collections.unmodifiableList(sqlContext.getRecords());
  }

  /**
   * @return các SQL records có execution time vượt threshold
   *
   * @param thresholdMs ngưỡng thời gian tính bằng milliseconds
   */
  public List<SQLRecord> getSlowQueries(long thresholdMs) {
    return sqlContext.getRecords().stream()
        .filter(r -> r.getExecutionTime() >= thresholdMs)
        .collect(Collectors.toList());
  }

  /**
   * @return các SQL records bị lỗi
   */
  public List<SQLRecord> getFailedQueries() {
    return sqlContext.getRecords().stream()
        .filter(r -> !r.isSuccess())
        .collect(Collectors.toList());
  }

  /**
   * Nhóm SQL records theo pattern (sql text).
   * Dùng để phát hiện N+1 — cùng 1 pattern lặp lại nhiều lần.
   *
   * @return Map từ sql pattern → danh sách records có cùng pattern
   */
  public Map<String, List<SQLRecord>> groupBySqlPattern() {
    return sqlContext.getRecords().stream()
        .collect(Collectors.groupingBy(SQLRecord::getSql));
  }

  public List<SQLRecord> getRepeatedPatterns(int threshold) {
    return groupBySqlPattern().entrySet().stream()
        .filter(e -> e.getValue().size() > threshold)
        .flatMap(e -> e.getValue().stream())
        .collect(Collectors.toList());
  }

  public List<SQLRecord> getTopSlowQueries(int n) {
    return sqlContext.getRecords().stream()
        .sorted((a, b) -> Long.compare(b.getExecutionTime(), a.getExecutionTime()))
        .limit(n)
        .collect(Collectors.toList());
  }

  public int getTotalCount() {
    return sqlContext.getRecords().size();
  }

  public int getFailedCount() {
    return (int) sqlContext.getRecords().stream()
        .filter(r -> !r.isSuccess())
        .count();
  }

  public Map<String, Long> getQueryCountByTable() {
    return sqlContext.getRecords().stream()
        .flatMap(r -> extractTableNames(r.getSql()).stream())
        .collect(Collectors.groupingBy(
            t -> t.toUpperCase(),
            Collectors.counting()
        ));
  }

  //  Helper
  private List<String> extractTableNames(String sql) {
    List<String> tables = new ArrayList<>();
    if (sql == null) return tables;

    String upper = sql.toUpperCase();
    String[] keywords = {"FROM", "JOIN", "INTO", "UPDATE"};

    for (String keyword : keywords) {
      int idx = upper.indexOf(keyword);
      while (idx >= 0) {
        String rest = sql.substring(idx + keyword.length()).trim();
        String[] parts = rest.split(Constant.SQL_TOKEN_DELIMITER_PATTERN);
        if (parts.length > 0 && !parts[0].isEmpty()) {
          tables.add(parts[0]);
        }
        idx = upper.indexOf(keyword, idx + 1);
      }
    }
    return tables;
  }
}