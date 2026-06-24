package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.QueryMetric;
import com.dbquality.collector.QueryMetricsStore;
import com.dbquality.collector.model.Table;
import com.dbquality.constant.Constant;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.constant.Severity;
import com.dbquality.explain.ExplainCache;
import com.dbquality.explain.ExplainResult;
import com.dbquality.rule.Finding;
import com.dbquality.rule.MetricsBasedRule;
import com.dbquality.rule.RuleResult;

import com.dbquality.util.SQLFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phát hiện full table scan dựa trên kết quả EXPLAIN thực tế từ DB engine.
 *
 * <p>Logic phát hiện theo vendor:</p>
 * <ul>
 *   <li>MySQL: {@code access_type: ALL}</li>
 *   <li>PostgreSQL: {@code Seq Scan} node</li>
 *   <li>SQL Server: {@code Table Scan} operation</li>
 * </ul>
 *
 * <p><b>Alias resolution:</b> EXPLAIN từ MySQL trả về field {@code "table"}
 * thường là alias Hibernate sinh (vd {@code d1_0}), không phải tên bảng thật.
 * Rule này parse SQL để map alias → real table name trước khi báo cáo.</p>
 *
 * <p>Severity dynamic theo impact thực tế (count × duration) hoặc rows scanned.</p>
 */
public class FullTableScanRule implements MetricsBasedRule {

  // Pattern match "FROM tableName aliasName" hoặc "FROM tableName AS aliasName"
  // Cũng match JOIN tableName aliasName
  private static final Pattern TABLE_ALIAS_PATTERN = Pattern.compile(
      Constant.TABLE_ALIAS_PATTERN,
      Pattern.CASE_INSENSITIVE
  );

  // Extract rows count từ message của EXPLAIN finding (vd "đọc 508 rows")
  private static final Pattern ROWS_PATTERN = Pattern.compile(
      Constant.ROWS_PATTERN,
      Pattern.CASE_INSENSITIVE
  );

  private final ExplainCache explainCache;

  public FullTableScanRule(ExplainCache explainCache) {
    this.explainCache = explainCache;
  }

  @Override
  public String getName() {
    return RuleName.FullTableScanCandidate;
  }

  @Override
  public Severity getSeverity() {
    return Severity.HIGH;
  }

  @Override
  public RuleResult analyze(DDLContext ddl, QueryMetricsStore metricsStore) {
    List<Finding> findings = new ArrayList<>();

    for (QueryMetric metric : metricsStore.getAllMetrics()) {
      Optional<ExplainResult> explainOpt = explainCache.getOrCompute(metric.getSqlPattern());
      if (explainOpt.isEmpty()) continue;

      ExplainResult explain = explainOpt.get();
      List<Finding> scanFindings = explain.getFindings().stream()
          .filter(f -> isFullScanRule(f.getRule()))
          .toList();

      // Build alias map từ SQL pattern để resolve "d1_0" → "departments"
      Map<String, String> aliasMap = buildAliasMap(metric.getSqlPattern(), ddl);

      for (Finding f : scanFindings) {
        String realTable = resolveTableName(f.getTable(), aliasMap);
        long rowsScanned = extractRowsScanned(f.getMessage());

        findings.add(Finding.builder()
            .rule(getName())
            .severity(determineSeverity(metric, rowsScanned, f.getSeverity()))
            .table(realTable)
            .message(buildMessage(f.getMessage(), realTable, f.getTable(), metric))
            .recommendation(f.getRecommendation())
            .calledFrom(metric.getMostFrequentCalledFrom())
            .build());
      }
    }

    return new RuleResult(findings);
  }

  private boolean isFullScanRule(String ruleName) {
    if (ruleName == null) return false;
    return ruleName.contains("FULL_TABLE_SCAN")
        || ruleName.contains("TABLE_SCAN");
  }

  /**
   * Build map alias → real table name từ SQL pattern.
   * Ví dụ với SQL "SELECT ... FROM departments d1_0 WHERE d1_0.id=?":
   * → {"d1_0": "departments"}
   */
  private Map<String, String> buildAliasMap(String sql, DDLContext ddl) {
    Map<String, String> aliasMap = new HashMap<>();
    if (sql == null) return aliasMap;

    // Tên các bảng thật trong DDL — để verify mapping
    java.util.Set<String> realTables = new java.util.HashSet<>();
    for (Table t : ddl.getTables()) {
      realTables.add(t.getName().toLowerCase());
    }

    Matcher matcher = TABLE_ALIAS_PATTERN.matcher(sql);
    while (matcher.find()) {
      String tableName = matcher.group(1).toLowerCase();
      String alias = matcher.group(2).toLowerCase();

      // Chỉ map khi tableName là bảng thật trong DDL
      // (tránh nhầm với SQL keywords như WHERE, ORDER...)
      if (realTables.contains(tableName) && !SQLFilter.isSQLKeyword(alias)) {
        aliasMap.put(alias, tableName);
      }
    }
    return aliasMap;
  }

  /**
   * Resolve alias về table name thật. Nếu không tìm thấy alias trong map,
   * giữ nguyên (có thể là table name thật rồi).
   */
  private String resolveTableName(String rawTable, Map<String, String> aliasMap) {
    if (rawTable == null) return null;
    String resolved = aliasMap.get(rawTable.toLowerCase());
    return resolved != null ? resolved : rawTable;
  }

  /**
   * Extract số rows từ message của EXPLAIN finding.
   * Ví dụ "Full Table Scan trên bảng 'employees' — đọc 508 rows" → 508.
   * Trả -1 nếu không parse được.
   */
  private long extractRowsScanned(String message) {
    if (message == null) return -1;
    Matcher matcher = ROWS_PATTERN.matcher(message);
    if (matcher.find()) {
      try {
        return Long.parseLong(matcher.group(1));
      } catch (NumberFormatException ignored) {}
    }
    return -1;
  }

  /**
   * Severity dựa trên cả total impact VÀ số rows quét.
   * Rows lớn → nguy hiểm khi data tăng lên production, dù demo chạy nhanh.
   */
  private Severity determineSeverity(QueryMetric metric, long rowsScanned,
      Severity defaultSeverity) {
    long totalImpact = metric.getCallCount() * (long) metric.getAvgDurationMs();

    // Ưu tiên check rows
    if (rowsScanned >= 500) return Severity.HIGH;
    if (rowsScanned >= 50) return Severity.MEDIUM;

    // Fallback theo total impact thời gian
    if (totalImpact > 5000) return Severity.HIGH;
    if (totalImpact > 1000) return Severity.MEDIUM;

    return defaultSeverity;
  }

  /**
   * Build message — thay alias bằng table thật nếu khác nhau,
   * note cả alias để dev biết match từ SQL nào.
   */
  private String buildMessage(String originalMessage, String realTable,
      String rawTable, QueryMetric metric) {
    String msg = originalMessage;

    // Nếu resolved khác alias → thay tên bảng trong message
    if (realTable != null && !realTable.equalsIgnoreCase(rawTable)) {
      msg = msg.replace("`" + rawTable + "`", "`" + realTable + "`")
          .replace(rawTable, realTable);
    }

    return msg + " (chạy " + metric.getCallCount() + " lần"
        + ", avg " + String.format("%.1f", metric.getAvgDurationMs()) + "ms)";
  }

}