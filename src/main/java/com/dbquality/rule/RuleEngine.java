package com.dbquality.rule;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.QueryMetricsStore;
import com.dbquality.collector.SQLContext;
import com.dbquality.constant.Severity;
import com.dbquality.explain.ExplainCache;
import com.dbquality.rule.impl.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Chạy tất cả các rules đã đăng ký và tổng hợp kết quả.
 *
 * <p>Hỗ trợ 2 loại rule:</p>
 * <ul>
 *   <li>{@link Rule} — legacy, dùng {@link SQLContext}</li>
 *   <li>{@link MetricsBasedRule} — mới, dùng {@link QueryMetricsStore} (Phase 2+)</li>
 * </ul>
 */
public class RuleEngine {

  private final List<Rule> legacyRules = new ArrayList<>();
  private final List<MetricsBasedRule> metricsRules = new ArrayList<>();

  public RuleEngine register(Rule rule) {
    legacyRules.add(rule);
    return this;
  }

  public RuleEngine register(MetricsBasedRule rule) {
    metricsRules.add(rule);
    return this;
  }

  /**
   * Chạy rules với SQLContext (legacy mode — chỉ chạy legacy rules).
   */
  public List<Finding> analyze(DDLContext ddl, SQLContext sql) {
    List<Finding> allFindings = new ArrayList<>();
    runLegacyRules(ddl, sql, allFindings);
    return allFindings;
  }

  /**
   * Chạy rules với QueryMetricsStore — chạy cả legacy (qua adapter) và metrics rules.
   */
  public List<Finding> analyze(DDLContext ddl, QueryMetricsStore metrics) {
    List<Finding> allFindings = new ArrayList<>();

    // Legacy rules qua adapter
    SQLContext adapter = adaptMetricsToSQLContext(metrics);
    runLegacyRules(ddl, adapter, allFindings);

    // Metrics-based rules dùng trực tiếp
    for (MetricsBasedRule rule : metricsRules) {
      try {
        RuleResult result = rule.analyze(ddl, metrics);
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

  private void runLegacyRules(DDLContext ddl, SQLContext sql, List<Finding> out) {
    for (Rule rule : legacyRules) {
      try {
        RuleResult result = rule.analyze(ddl, sql);
        out.addAll(result.getFindings());
      } catch (Exception e) {
        out.add(Finding.builder()
            .rule(rule.getName())
            .severity(Severity.WARNING)
            .message("Rule execution failed: " + e.getMessage())
            .recommendation("Check rule implementation")
            .build());
      }
    }
  }

  private SQLContext adaptMetricsToSQLContext(QueryMetricsStore store) {
    SQLContext context = new SQLContext();
    for (var metric : store.getAllMetrics()) {
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
    return legacyRules.size() + metricsRules.size();
  }

  /**
   * Tạo RuleEngine với toàn bộ built-in rules.
   * Rule 2,3,4 (FullTableScan, MissingIndex, UnusedIndex) dùng EXPLAIN
   * thông qua ExplainCache.
   */
  public static RuleEngine withDefaultRules(long slowQueryThresholdMs,
      int nPlusOneThreshold, ExplainCache explainCache) {
    RuleEngine engine = new RuleEngine()
        .register(new MissingPrimaryKeyRule())
        .register(new UnindexedForeignKeyRule())
        .register(new SelectStarRule())
        .register(new SlowQueryRule(slowQueryThresholdMs))
        .register(new NPlusOneRule(nPlusOneThreshold))
        .register(new NullableRiskRule())
        .register(new SuspiciousDataTypeRule());

    if (explainCache != null) {
      engine.register(new FullTableScanRule(explainCache));
      engine.register(new MissingIndexRule(explainCache));
      engine.register(new UnusedIndexRule(explainCache));
    }

    return engine;
  }

  // Giữ overload cũ cho backward compat (chưa có ExplainCache)
  public static RuleEngine withDefaultRules(long slowQueryThresholdMs,
      int nPlusOneThreshold) {
    return withDefaultRules(slowQueryThresholdMs, nPlusOneThreshold, null);
  }
}