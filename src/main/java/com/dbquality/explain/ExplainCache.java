package com.dbquality.explain;

import com.dbquality.util.SQLNormalizer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache kết quả EXPLAIN cho từng unique SQL pattern.
 *
 * <p>Theo phản hồi của mentor: app thực tế chỉ có vài chục đến 1-200 unique SQL
 * patterns, nên chỉ cần chạy EXPLAIN 1 lần cho mỗi pattern là đủ — không cần
 * chạy lại cho mỗi lần SQL được thực thi. Cache này đảm bảo overhead minimal.</p>
 *
 * <p>Khi rule cần kết quả EXPLAIN cho 1 SQL:</p>
 * <ol>
 *   <li>Cache hit → return ngay</li>
 *   <li>Cache miss → chạy EXPLAIN qua {@link ExplainParser} → cache lại</li>
 * </ol>
 *
 * <p>Cache thread-safe qua {@link ConcurrentHashMap}.</p>
 */
public class ExplainCache {

  private final DataSource dataSource;
  private final Map<String, ExplainResult> cache = new ConcurrentHashMap<>();
  private volatile ExplainParser parser;

  public ExplainCache(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /**
   * Lấy EXPLAIN result cho 1 SQL pattern. Chạy EXPLAIN nếu chưa có cache.
   *
   * @param sql câu SQL gốc (có thể có literal hoặc {@code ?})
   * @return ExplainResult nếu chạy thành công, empty nếu lỗi hoặc DB không support
   */
  public Optional<ExplainResult> getOrCompute(String sql) {
    if (sql == null || sql.isBlank()) return Optional.empty();
    String key = SQLNormalizer.normalize(sql);

    // Cache hit
    ExplainResult cached = cache.get(key);
    if (cached != null) return Optional.of(cached);

    // Cache miss → run EXPLAIN
    try {
      ExplainParser p = getOrInitParser();
      if (p == null) return Optional.empty();

      String rawExplain = runExplain(sql, p);
      if (rawExplain == null) return Optional.empty();

      ExplainResult result = p.parse(rawExplain);
      cache.put(key, result);
      return Optional.of(result);
    } catch (Exception e) {
      // Một số query không EXPLAIN được (DDL, system query) — silent skip
      return Optional.empty();
    }
  }

  /**
   * Chạy EXPLAIN cho toàn bộ SQL patterns trong store.
   * Gọi từ ScheduledAnalysisJob để warm cache trước khi rule engine chạy.
   */
  public void warmUp(Iterable<String> sqlPatterns) {
    for (String sql : sqlPatterns) {
      getOrCompute(sql);
    }
  }

  /**
   * @return số lượng SQL patterns đã có EXPLAIN result
   */
  public int size() {
    return cache.size();
  }

  /**
   * @return toàn bộ cache — dùng cho rule UnusedIndex check index nào không
   *         xuất hiện trong bất kỳ EXPLAIN nào
   */
  public Map<String, ExplainResult> getAll() {
    return cache;
  }

  /**
   * Clear cache — dùng khi DDL thay đổi (rare, chỉ khi schema migrate).
   */
  public void clear() {
    cache.clear();
  }

  // ── Internal ─────────────────────────────────────────────

  private ExplainParser getOrInitParser() {
    if (parser != null) return parser;
    synchronized (this) {
      if (parser != null) return parser;
      try (Connection conn = dataSource.getConnection()) {
        DatabaseMetaData meta = conn.getMetaData();
        String productName = meta.getDatabaseProductName();
        parser = ExplainParserFactory.create(productName);
        return parser;
      } catch (Exception e) {
        return null;
      }
    }
  }

  /**
   * Chạy EXPLAIN cho SQL — format tùy theo vendor (MySQL: EXPLAIN FORMAT=JSON,
   * PostgreSQL: EXPLAIN (FORMAT JSON), SQL Server: SET SHOWPLAN_XML ON).
   */
  private String runExplain(String sql, ExplainParser parser) {
    try (Connection conn = dataSource.getConnection();
        var stmt = conn.createStatement()) {

      String explainSQL = buildExplainStatement(sql, parser);
      try (var rs = stmt.executeQuery(explainSQL)) {
        StringBuilder sb = new StringBuilder();
        while (rs.next()) {
          sb.append(rs.getString(1));
        }
        return sb.toString();
      }
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Build câu EXPLAIN tương ứng với vendor.
   * Vendor được xác định qua tên class của parser — đơn giản và đủ dùng.
   */
  private String buildExplainStatement(String sql, ExplainParser parser) {
    String parserName = parser.getClass().getSimpleName();
    if (parserName.contains("MySQL") || parserName.contains("MariaDB")) {
      return "EXPLAIN FORMAT=JSON " + sql;
    }
    if (parserName.contains("PostgreSQL")) {
      return "EXPLAIN (FORMAT JSON) " + sql;
    }
    if (parserName.contains("SQLServer")) {
      // SQL Server cần SET SHOWPLAN_XML — phức tạp hơn, để implementation riêng
      return "SET SHOWPLAN_XML ON; " + sql;
    }
    // Fallback: plain EXPLAIN
    return "EXPLAIN " + sql;
  }
}