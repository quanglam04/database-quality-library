package com.dbquality.collector;

import com.dbquality.util.SQLNormalizer;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store tập trung lưu metrics cho từng unique SQL pattern.
 */
public class QueryMetricsStore {

  private final Map<String, QueryMetric> metrics = new ConcurrentHashMap<>();

  /**
   * Ghi nhận một lần thực thi SQL.
   * SQL được normalize trước khi dùng làm key để gộp các query
   * khác giá trị bind nhưng cùng pattern.
   */
  public void record(String sql, long durationMs, String calledFrom) {
    if (sql == null) return;
    String pattern = SQLNormalizer.normalize(sql);
    metrics.computeIfAbsent(pattern, QueryMetric::new)
        .record(durationMs, calledFrom);
  }

  public Collection<QueryMetric> getAllMetrics() {
    return metrics.values();
  }

  public int getUniquePatternCount() {
    return metrics.size();
  }

  public long getTotalExecutions() {
    return metrics.values().stream()
        .mapToLong(QueryMetric::getCallCount)
        .sum();
  }

  /**
   * Lấy metric cho 1 SQL pattern cụ thể.
   */
  public QueryMetric get(String sqlPattern) {
    return metrics.get(SQLNormalizer.normalize(sqlPattern));
  }

  /**
   * Reset toàn bộ store — dùng cho test hoặc khi user muốn clear data.
   */
  public void clear() {
    metrics.clear();
  }
}