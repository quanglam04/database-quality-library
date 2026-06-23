package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.QueryMetric;
import com.dbquality.collector.QueryMetricsStore;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.constant.Severity;
import com.dbquality.explain.ExplainCache;
import com.dbquality.explain.ExplainResult;
import com.dbquality.rule.Finding;
import com.dbquality.rule.MetricsBasedRule;
import com.dbquality.rule.RuleResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Phát hiện full table scan dựa trên kết quả EXPLAIN thực tế từ DB engine.
 *
 * <p>Khác với heuristic regex cũ (đoán dựa trên LIKE '%...', function trên cột,
 * IS NULL...), rule này dùng <b>kết quả EXPLAIN thật</b> để xác định chính xác
 * query nào bị full scan — không có false positive.</p>
 *
 * <p>Logic phát hiện theo vendor (ExplainParser đã làm việc trừu tượng hóa):</p>
 * <ul>
 *   <li>MySQL: {@code access_type: ALL}</li>
 *   <li>PostgreSQL: {@code Seq Scan} node</li>
 *   <li>SQL Server: {@code Table Scan} operation</li>
 * </ul>
 *
 * <p>ExplainResult đã chứa sẵn findings cho từng issue phát hiện được —
 * rule này chỉ enrich thêm context (metrics: call count, avg duration).</p>
 */
public class FullTableScanRule implements MetricsBasedRule {

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
      // Filter findings từ EXPLAIN — chỉ giữ những finding liên quan full scan
      List<Finding> scanFindings = explain.getFindings().stream()
          .filter(f -> isFullScanRule(f.getRule()))
          .toList();

      for (Finding f : scanFindings) {
        findings.add(Finding.builder()
            .rule(getName())
            .severity(determineSeverity(metric, f.getSeverity()))
            .table(f.getTable())
            .message(f.getMessage()
                + " (chạy " + metric.getCallCount() + " lần"
                + ", avg " + String.format("%.1f", metric.getAvgDurationMs()) + "ms)")
            .recommendation(f.getRecommendation())
            .calledFrom(metric.getMostFrequentCalledFrom())
            .build());
      }
    }

    return new RuleResult(findings);
  }

  /**
   * Check finding rule name có phải liên quan full scan không.
   * ExplainParser tạo finding với rule name như "FULL_TABLE_SCAN",
   * "FULL_INDEX_SCAN", "INDEX_NOT_USED" — chỉ keep các loại scan.
   */
  private boolean isFullScanRule(String ruleName) {
    if (ruleName == null) return false;
    return ruleName.contains("FULL_TABLE_SCAN")
        || ruleName.contains("TABLE_SCAN");
  }

  /**
   * Severity tùy theo impact thực tế: query càng chạy nhiều và càng chậm
   * thì severity càng cao.
   */
  private Severity determineSeverity(QueryMetric metric, Severity defaultSeverity) {
    long totalImpact = metric.getCallCount() * (long) metric.getAvgDurationMs();
    if (totalImpact > 5000) return Severity.HIGH;     // tổng tốn > 5s
    if (totalImpact > 1000) return Severity.MEDIUM;   // tổng tốn > 1s
    return defaultSeverity;
  }
}