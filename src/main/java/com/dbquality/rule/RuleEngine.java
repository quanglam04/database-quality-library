package com.dbquality.rule;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.QueryMetricsStore;
import com.dbquality.collector.SQLContext;
import com.dbquality.constant.Severity;
import com.dbquality.rule.impl.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Chạy tất cả các rules đã đăng ký và tổng hợp kết quả.
 *
 * <p>Hỗ trợ 2 mode analysis:</p>
 * <ul>
 *   <li>{@link #analyze(DDLContext, SQLContext)} — legacy, dùng SQLContext với raw records</li>
 *   <li>{@link #analyze(DDLContext, QueryMetricsStore)} — new, dùng aggregated metrics</li>
 * </ul>
 *
 */
public class RuleEngine {

  private final List<Rule> rules = new ArrayList<>();

  public RuleEngine register(Rule rule) {
    rules.add(rule);
    return this;
  }

  /**
   * Chạy rules với SQLContext (legacy mode).
   * Giữ lại để backward compat trong giai đoạn migration.
   */
  public List<Finding> analyze(DDLContext ddl, SQLContext sql) {
    List<Finding> allFindings = new ArrayList<>();

    for (Rule rule : rules) {
      try {
        RuleResult result = rule.analyze(ddl, sql);
        allFindings.addAll(result.getFindings());
      } catch (Exception e) {
        allFindings.add(Finding.builder()
            .rule(rule.getName())
            .severity(Severity.WARNING)
            .message("Rule execution failed: " + e.getMessage())
            .recommendation("Check rule implementation")
            .build());
      }
    }

    return allFindings;
  }

  /**
   * Chạy rules với QueryMetricsStore (new mode).
   *
   * <p>Convert metrics thành SQLContext tạm thời để các rule chưa migrate
   * vẫn chạy được. Sau Phase 3, mọi rule sẽ dùng metrics trực tiếp và
   * adapter này sẽ được remove.</p>
   */
  public List<Finding> analyze(DDLContext ddl, QueryMetricsStore metricsStore) {
    SQLContext adapter = adaptMetricsToSQLContext(metricsStore);
    return analyze(ddl, adapter);
  }

  /**
   * Adapter tạm thời: build SQLContext từ QueryMetricsStore.
   * Mỗi metric tạo ra một SQLRecord đại diện với calledFrom phổ biến nhất
   * và executionTime = avg duration.
   *
   * <p>Note: Adapter này mất thông tin chi tiết (variance, distribution),
   * chỉ phù hợp cho rule cũ chưa cần metrics phân tích sâu. Rule mới
   * nên dùng QueryMetricsStore trực tiếp.</p>
   */
  private SQLContext adaptMetricsToSQLContext(QueryMetricsStore store) {
    SQLContext context = new SQLContext();
    for (var metric : store.getAllMetrics()) {
      // Tạo SQLRecord representative cho mỗi metric
      // Số lượng record = callCount để N+1 rule cũ vẫn detect được
      for (long i = 0; i < metric.getCallCount(); i++) {
        context.add(com.dbquality.collector.SQLRecord.builder()
            .sql(metric.getSqlPattern())
            .executionTime((long) metric.getAvgDurationMs())
            .calledFrom(metric.getMostFrequentCalledFrom())
            .success(true)
            .build());
      }
    }
    return context;
  }

  public int getRuleCount() {
    return rules.size();
  }

  public static RuleEngine withDefaultRules(long slowQueryThresholdMs,
      int nPlusOneThreshold) {
    return new RuleEngine()
        .register(new MissingPrimaryKeyRule())
        .register(new UnindexedForeignKeyRule())
        .register(new SelectStarRule())
        .register(new SlowQueryRule(slowQueryThresholdMs))
        .register(new NPlusOneRule(nPlusOneThreshold))
        .register(new NullableRiskRule())
        .register(new FullTableScanCandidateRule())
        .register(new UnusedIndexRule())
        .register(new SuspiciousDataTypeRule())
        .register(new MissingIndexSuggestionRule());
  }
}