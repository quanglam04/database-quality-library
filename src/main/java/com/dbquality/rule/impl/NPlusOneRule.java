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
 * <p>Phân loại theo nhiều chỉ số (count + duration + variance):</p>
 *
 * <ul>
 *   <li><b>Count cao + duration ổn định + avg duration thấp</b>
 *       → N+1 typical: query nhỏ lặp trong loop, đặc trưng lazy loading</li>
 *   <li><b>Count cao + avg duration cao</b>
 *       → N+1 nghiêm trọng: cần fix gấp</li>
 *   <li><b>Count cao + variance cao</b>
 *       → có thể là cache miss/hit pattern</li>
 * </ul>
 *
 * <p>Severity dựa trên cả số lần lặp VÀ total impact:</p>
 * <ul>
 *   <li>HIGH: callCount >= 50 lần HOẶC total impact > 1000ms</li>
 *   <li>MEDIUM: callCount >= 20 lần HOẶC total impact > 200ms</li>
 *   <li>WARNING: còn lại</li>
 * </ul>
 *
 * <p>Lý do tách callCount khỏi total impact: DB modern thường nhanh
 * (mỗi query &lt;1ms), nhưng pattern lặp 50-100 lần vẫn là N+1 nghiêm trọng
 * cần fix — vì khi scale lên production với data lớn hơn, mỗi query sẽ chậm
 * hơn và pattern lặp sẽ phóng đại vấn đề.</p>
 */
public class NPlusOneRule implements MetricsBasedRule {

  // Ngưỡng số lần lặp để phân loại N+1 — bất kể duration
  private static final int CALL_COUNT_HIGH = 50;
  private static final int CALL_COUNT_MEDIUM = 20;

  // Ngưỡng total impact (ms) để phân loại theo thời gian
  private static final long TOTAL_IMPACT_HIGH = 1000;
  private static final long TOTAL_IMPACT_MEDIUM = 200;

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

  private NPlusOneAnalysis analyzePattern(QueryMetric metric) {
    long count = metric.getCallCount();
    double avgMs = metric.getAvgDurationMs();
    long minMs = metric.getMinDurationMs();
    long maxMs = metric.getMaxDurationMs();
    long totalImpactMs = (long) (count * avgMs);

    // Variance ratio: max/min — gần 1 nghĩa là duration rất ổn định
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
      Severity sev = determineSeverity(count, totalImpactMs);
      return new NPlusOneAnalysis(
          sev,
          "điển hình (lazy loading)",
          "Pattern điển hình của Hibernate lazy loading trong vòng lặp. "
              + "Dùng @EntityGraph, JOIN FETCH, hoặc @BatchSize để fetch eagerly"
      );
    }

    // Trường hợp 3: variance lớn → có thể là cache pattern
    if (varianceRatio >= 5.0) {
      if (count >= CALL_COUNT_MEDIUM || totalImpactMs > 500) {
        Severity sev = determineSeverity(count, totalImpactMs);
        return new NPlusOneAnalysis(
            sev,
            "biến động (có thể do cache miss/hit)",
            "Duration biến động lớn — có thể do cache miss/hit pattern. "
                + "Verify xem có phải N+1 thật sự không, hoặc cân nhắc warm cache"
        );
      }
      return null;
    }

    // Trường hợp 4: count > threshold nhưng impact thấp
    if (count >= CALL_COUNT_MEDIUM || totalImpactMs > 100) {
      Severity sev = determineSeverity(count, totalImpactMs);
      return new NPlusOneAnalysis(
          sev,
          "có dấu hiệu",
          "Query lặp nhiều lần. Cân nhắc batch fetch nếu các lần gọi "
              + "đều từ cùng một service method"
      );
    }

    return null;
  }

  /**
   * Xác định severity dựa trên cả count và total impact.
   * Lấy mức cao hơn của 2 tiêu chí.
   */
  private Severity determineSeverity(long count, long totalImpactMs) {
    if (count >= CALL_COUNT_HIGH || totalImpactMs > TOTAL_IMPACT_HIGH) {
      return Severity.HIGH;
    }
    if (count >= CALL_COUNT_MEDIUM || totalImpactMs > TOTAL_IMPACT_MEDIUM) {
      return Severity.MEDIUM;
    }
    return Severity.WARNING;
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