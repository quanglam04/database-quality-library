package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.QueryMetric;
import com.dbquality.collector.QueryMetricsStore;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.constant.Severity;
import com.dbquality.rule.Finding;
import com.dbquality.rule.MetricsBasedRule;
import com.dbquality.rule.RuleResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Phát hiện query có execution time vượt ngưỡng cấu hình.
 *
 * <p>Dùng max duration làm tiêu chí — nếu có ít nhất 1 lần query chạy quá chậm
 * thì flag. Đồng thời report avg duration để user biết query có chậm
 * thường xuyên không.</p>
 *
 * <p>Severity dynamic:</p>
 * <ul>
 *   <li>HIGH: avg duration vượt ngưỡng (chậm thường xuyên)</li>
 *   <li>MEDIUM: chỉ max duration vượt ngưỡng (chậm thỉnh thoảng)</li>
 * </ul>
 */
public class SlowQueryRule implements MetricsBasedRule {

  private final long thresholdMs;

  public SlowQueryRule(long thresholdMs) {
    this.thresholdMs = thresholdMs;
  }

  @Override
  public String getName() {
    return RuleName.SlowQuery;
  }

  @Override
  public Severity getSeverity() {
    return Severity.HIGH;
  }

  @Override
  public RuleResult analyze(DDLContext ddl, QueryMetricsStore metricsStore) {
    List<Finding> findings = new ArrayList<>();

    for (QueryMetric metric : metricsStore.getAllMetrics()) {
      // Chỉ flag nếu max duration vượt threshold
      if (metric.getMaxDurationMs() < thresholdMs) continue;

      boolean isFrequentlySlow = metric.getAvgDurationMs() >= thresholdMs;
      Severity severity = isFrequentlySlow ? Severity.HIGH : Severity.MEDIUM;

      String label = isFrequentlySlow
          ? "chậm thường xuyên"
          : "thỉnh thoảng chậm";

      findings.add(Finding.builder()
          .rule(getName())
          .severity(severity)
          .message(String.format(
              "Query %s — chạy %d lần (avg %.1fms, max %dms, threshold %dms): %s",
              label,
              metric.getCallCount(),
              metric.getAvgDurationMs(),
              metric.getMaxDurationMs(),
              thresholdMs,
              truncate(metric.getSqlPattern(), 100)
          ))
          .recommendation(buildRecommendation(metric, isFrequentlySlow))
          .calledFrom(metric.getMostFrequentCalledFrom())
          .build());
    }

    return new RuleResult(findings);
  }

  private String buildRecommendation(QueryMetric metric, boolean isFrequentlySlow) {
    if (isFrequentlySlow) {
      return "Query chậm thường xuyên — chạy EXPLAIN để tìm nguyên nhân "
          + "(thiếu index, full table scan, JOIN không tối ưu)";
    }
    return "Query thỉnh thoảng chậm — có thể do data spike, lock contention, "
        + "hoặc cache miss. Theo dõi pattern duration để xác định nguyên nhân";
  }

  private String truncate(String text, int maxLen) {
    if (text == null) return "";
    return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
  }
}