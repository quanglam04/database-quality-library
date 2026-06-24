package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.QueryMetric;
import com.dbquality.collector.QueryMetricsStore;
import com.dbquality.collector.model.Column;
import com.dbquality.collector.model.Table;
import com.dbquality.constant.Constant;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.constant.Severity;
import com.dbquality.explain.ExplainCache;
import com.dbquality.explain.ExplainResult;
import com.dbquality.rule.Finding;
import com.dbquality.rule.MetricsBasedRule;
import com.dbquality.rule.RuleResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phát hiện cột cần thêm index dựa trên kết quả EXPLAIN thực tế.
 *
 * <p>Logic phát hiện:</p>
 * <ol>
 *   <li>Duyệt qua các query trong metrics store</li>
 *   <li>Với mỗi query, lấy EXPLAIN result từ cache</li>
 *   <li>Nếu EXPLAIN có finding "FULL_TABLE_SCAN" hoặc "INDEX_NOT_USED" →
 *       phân tích câu SQL để tìm cột trong WHERE/JOIN</li>
 *   <li>Cross-check với DDL: cột nào chưa có index → đề xuất tạo</li>
 * </ol>
 *
 */
public class MissingIndexRule implements MetricsBasedRule {

  // Match cột trong WHERE/AND/OR/ON — cả simple và qualified (alias.col)
  private static final Pattern COLUMN_IN_FILTER = Pattern.compile(
      Constant.COLUMN_IN_FILTER,
      Pattern.CASE_INSENSITIVE
  );

  private final ExplainCache explainCache;

  public MissingIndexRule(ExplainCache explainCache) {
    this.explainCache = explainCache;
  }

  @Override
  public String getName() {
    return RuleName.MissingIndexSuggestion;
  }

  @Override
  public Severity getSeverity() {
    return Severity.MEDIUM;
  }

  @Override
  public RuleResult analyze(DDLContext ddl, QueryMetricsStore metricsStore) {
    List<Finding> findings = new ArrayList<>();
    Set<String> reportedKeys = new HashSet<>();  // Tránh duplicate finding

    for (QueryMetric metric : metricsStore.getAllMetrics()) {
      Optional<ExplainResult> explainOpt = explainCache.getOrCompute(metric.getSqlPattern());
      if (explainOpt.isEmpty()) continue;

      ExplainResult explain = explainOpt.get();
      boolean hasFullScan = explain.getFindings().stream()
          .anyMatch(f -> f.getRule() != null
              && Constant.FULL_SCAN_RULE_NAMES.stream().anyMatch(f.getRule()::contains));
      if (!hasFullScan) continue;

      // Query này bị full scan — phân tích các cột trong WHERE
      List<ColumnRef> columnsInFilter = extractColumnsFromFilter(metric.getSqlPattern());

      for (ColumnRef ref : columnsInFilter) {
        Table table = findTable(ddl, ref.tableHint);
        if (table == null) continue;

        Column column = findColumn(table, ref.columnName);
        if (column == null) continue;

        // Skip nếu đã có index trên cột
        if (ddl.hasIndexOn(table, column)) continue;

        // Skip FK columns (đã có UnindexedForeignKeyRule xử lý)
        if (isFKColumn(table, column.getName())) continue;

        String key = table.getName().toUpperCase() + "." + column.getName().toUpperCase();
        if (reportedKeys.contains(key)) continue;
        reportedKeys.add(key);

        findings.add(Finding.builder()
            .rule(getName())
            .severity(determineSeverity(metric))
            .table(table.getName())
            .column(column.getName())
            .message("Cột " + column.getName() + " trên bảng " + table.getName()
                + " được dùng trong WHERE/JOIN của query bị full scan "
                + "(query chạy " + metric.getCallCount() + " lần, "
                + "avg " + String.format("%.1f", metric.getAvgDurationMs()) + "ms)")
            .recommendation("CREATE INDEX idx_"
                + table.getName().toLowerCase() + "_"
                + column.getName().toLowerCase()
                + " ON " + table.getName()
                + "(" + column.getName() + ")")
            .calledFrom(metric.getMostFrequentCalledFrom())
            .build());
      }
    }

    return new RuleResult(findings);
  }

  /**
   * Severity dựa trên impact thực tế:
   * - HIGH: query chạy nhiều và tốn tổng > 5s
   * - MEDIUM: còn lại
   */
  private Severity determineSeverity(QueryMetric metric) {
    long totalImpact = metric.getCallCount() * (long) metric.getAvgDurationMs();
    return totalImpact > 5000 ? Severity.HIGH : Severity.MEDIUM;
  }

  /**
   * Extract các cột xuất hiện trong WHERE/AND/OR/ON của SQL.
   * Hỗ trợ cả qualified (alias.col) và simple (col).
   */
  private List<ColumnRef> extractColumnsFromFilter(String sql) {
    List<ColumnRef> refs = new ArrayList<>();
    Matcher matcher = COLUMN_IN_FILTER.matcher(sql);
    while (matcher.find()) {
      String tableHint = matcher.group(1);  // có thể null nếu không qualified
      String columnName = matcher.group(2);
      if (columnName != null && !isSQLKeyword(columnName)) {
        refs.add(new ColumnRef(tableHint, columnName));
      }
    }
    return refs;
  }

  /**
   * Tìm table trong DDL theo hint (tên alias hoặc tên bảng).
   * Đơn giản: tìm bảng có tên match hint, nếu hint null thì trả null
   * (vì không biết cột thuộc bảng nào nếu không qualified — tránh false positive).
   */
  private Table findTable(DDLContext ddl, String hint) {
    if (hint == null) return null;
    String upper = hint.toUpperCase();
    return ddl.getTables().stream()
        .filter(t -> t.getName().toUpperCase().equals(upper))
        .findFirst()
        .orElse(null);
  }

  private Column findColumn(Table table, String columnName) {
    return table.getColumns().stream()
        .filter(c -> c.getName().equalsIgnoreCase(columnName))
        .findFirst()
        .orElse(null);
  }

  private boolean isFKColumn(Table table, String columnName) {
    if (table.getForeignKeys() == null) return false;
    return table.getForeignKeys().stream()
        .anyMatch(fk -> fk.getColumn() != null
            && fk.getColumn().equalsIgnoreCase(columnName));
  }

  private boolean isSQLKeyword(String word) {
    return Set.of("NULL", "TRUE", "FALSE", "NOT").contains(word.toUpperCase());
  }

  /** DTO nội bộ — đại diện cột trong WHERE với optional table hint. */
  private record ColumnRef(String tableHint, String columnName) {}
}