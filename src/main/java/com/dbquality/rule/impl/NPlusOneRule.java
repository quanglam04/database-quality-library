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
 * Phát hiện N+1 query pattern dựa trên aggregated metrics.
 *
 * <p>Khác với version cũ (chỉ đếm count > threshold), rule này phân tích nhiều
 * chỉ số để phân loại N+1 chính xác hơn:</p>
 *
 * <ul>
 *   <li><b>Count cao + duration ổn định (variance thấp) + avg duration thấp</b>
 *       → N+1 typical: query nhỏ lặp trong loop, đặc trưng của lazy loading</li>
 *   <li><b>Count cao + avg duration cao</b>
 *       → N+1 nghiêm trọng: cần fix gấp vì tổng thời gian tốn nhiều</li>
 *   <li><b>Count cao + variance cao</b>
 *       → có thể là cache miss/hit pattern, không hẳn N+1 — flag MEDIUM</li>
 * </ul>
 *
 * <p>Severity dynamic theo total impact = count × avgDuration:</p>
 * <ul>
 *   <li>HIGH: total impact > 1000ms (tốn tổng > 1s)</li>
 *   <li>MEDIUM: còn lại</li>
 * </ul>
 *

 */
public class NPlusOneRule implements MetricsBasedRule {

  private final int threshold;

  public NPlusOneRule(int threshold) {
    this.threshold = threshold;
  }

  @Override
  public String getName() {
    return RuleName.NPlusOne;
  }

  @Override
  public Severity getSeverity() {
    return Severity.HIGH;
  }

  @Override
  public RuleResult analyze(DDLContext ddl, QueryMetricsStore metricsStore) {
    List<Finding> findings = new ArrayList<>();

    for (QueryMetric metric : metricsStore.getAllMetrics()) {
      if (metric.getCallCount() <= threshold) continue;

      NPlusOneAnalysis analysis = analyzePattern(metric);
      if (analysis == null) continue;

      findings.add(Finding.builder()
          .rule(getName())
          .severity(analysis.severity)
          .message(buildMessage(metric, analysis))
          .recommendation(analysis.recommendation)
          .calledFrom(metric.getMostFrequentCalledFrom())
          .build());
    }

    return new RuleResult(findings);
  }

  /**
   * Phân tích metrics để phân loại N+1 pattern.
   * Trả về null nếu không phải N+1 (chỉ là query phổ biến).
   */
  private NPlusOneAnalysis analyzePattern(QueryMetric metric) {
    long count = metric.getCallCount();
    double avgMs = metric.getAvgDurationMs();
    long minMs = metric.getMinDurationMs();
    long maxMs = metric.getMaxDurationMs();
    long totalImpactMs = (long) (count * avgMs);

    // Variance ratio: max/min — nếu gần bằng 1 thì duration rất ổn định
    double varianceRatio = minMs == 0 ? 1.0 : (double) maxMs / minMs;

    // Trường hợp 1: query nặng lặp nhiều — N+1 nghiêm trọng
    if (avgMs >= 50) {
      return new NPlusOneAnalysis(
          Severity.HIGH,
          "nghiêm trọng",
          "Query nặng lặp nhiều lần — fix gấp. Dùng JOIN FETCH (JPA) hoặc "
              + "batch fetch để giảm số lần round-trip xuống DB"
      );
    }

    // Trường hợp 2: query nhỏ + ổn định → N+1 điển hình từ lazy loading
    if (varianceRatio < 5.0 && avgMs < 10) {
      Severity sev = totalImpactMs > 1000 ? Severity.HIGH : Severity.MEDIUM;
      return new NPlusOneAnalysis(
          sev,
          "điển hình (lazy loading)",
          "Pattern điển hình của Hibernate lazy loading trong vòng lặp. "
              + "Dùng @EntityGraph, JOIN FETCH, hoặc @BatchSize để fetch eagerly"
      );
    }

    // Trường hợp 3: variance lớn → có thể là cache pattern, không hẳn N+1
    if (varianceRatio >= 5.0) {
      // Chỉ flag nếu tổng impact đáng kể
      if (totalImpactMs > 500) {
        return new NPlusOneAnalysis(
            Severity.MEDIUM,
            "biến động (có thể do cache miss/hit)",
            "Duration biến động lớn — có thể do cache miss/hit pattern. "
                + "Verify xem có phải N+1 thật sự không, hoặc cân nhắc warm cache"
        );
      }
      return null;
    }

    // Trường hợp 4: count > threshold nhưng impact thấp — vẫn flag nhưng MEDIUM
    if (totalImpactMs > 100) {
      return new NPlusOneAnalysis(
          Severity.MEDIUM,
          "có dấu hiệu",
          "Query lặp nhiều lần. Cân nhắc batch fetch nếu các lần gọi "
              + "đều từ cùng một service method"
      );
    }

    return null;
  }

  private String buildMessage(QueryMetric metric, NPlusOneAnalysis analysis) {
    return String.format(
        "N+1 %s — query lặp %d lần (avg %.1fms, min %dms, max %dms, "
            + "tổng %dms). SQL: %s",
        analysis.label,
        metric.getCallCount(),
        metric.getAvgDurationMs(),
        metric.getMinDurationMs(),
        metric.getMaxDurationMs(),
        (long) (metric.getCallCount() * metric.getAvgDurationMs()),
        truncate(metric.getSqlPattern(), 100)
    );
  }

  private String truncate(String text, int maxLen) {
    if (text == null) return "";
    return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
  }

  private record NPlusOneAnalysis(Severity severity, String label, String recommendation) {}
}