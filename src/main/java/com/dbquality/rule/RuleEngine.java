package com.dbquality.rule;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.QueryMetricsStore;
import com.dbquality.constant.Severity;
import com.dbquality.explain.ExplainCache;
import com.dbquality.rule.impl.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Chạy tất cả các rules đã đăng ký và tổng hợp kết quả.
 *
 * <p>Sau Phase 3 refactor, có 2 loại rule:</p>
 * <ul>
 *   <li>{@link Rule} — schema-only rules (không cần SQL metrics)</li>
 *   <li>{@link MetricsBasedRule} — rules dùng aggregated metrics</li>
 * </ul>
 *
 * <p>Cả 2 đều nhận {@link DDLContext}; metrics rules nhận thêm
 * {@link QueryMetricsStore}.</p>
 */
public class RuleEngine {

  private final List<Rule> schemaRules = new ArrayList<>();
  private final List<MetricsBasedRule> metricsRules = new ArrayList<>();

  public RuleEngine register(Rule rule) {
    schemaRules.add(rule);
    return this;
  }

  public RuleEngine register(MetricsBasedRule rule) {
    metricsRules.add(rule);
    return this;
  }

  /**
   * Chạy tất cả rules — cả schema và metrics-based.
   */
  public List<Finding> analyze(DDLContext ddl, QueryMetricsStore metrics) {
    List<Finding> allFindings = new ArrayList<>();

    // Schema rules — dùng SQLContext rỗng vì các rule này chỉ dựa vào DDL
    for (Rule rule : schemaRules) {
      try {
        RuleResult result = rule.analyze(ddl, new com.dbquality.collector.SQLContext());
        allFindings.addAll(result.getFindings());
      } catch (Exception e) {
        allFindings.add(buildErrorFinding(rule.getName(), e));
      }
    }

    // Metrics rules
    for (MetricsBasedRule rule : metricsRules) {
      try {
        RuleResult result = rule.analyze(ddl, metrics);
        allFindings.addAll(result.getFindings());
      } catch (Exception e) {
        allFindings.add(buildErrorFinding(rule.getName(), e));
      }
    }

    return allFindings;
  }

  private Finding buildErrorFinding(String ruleName, Exception e) {
    return Finding.builder()
        .rule(ruleName)
        .severity(Severity.WARNING)
        .message("Rule execution failed: " + e.getMessage())
        .recommendation("Check rule implementation")
        .build();
  }

  public int getRuleCount() {
    return schemaRules.size() + metricsRules.size();
  }

  public static RuleEngine withDefaultRules(long slowQueryThresholdMs,
      int nPlusOneThreshold, ExplainCache explainCache) {
    RuleEngine engine = new RuleEngine()
        .register(new MissingPrimaryKeyRule())
        .register(new UnindexedForeignKeyRule())
        .register(new NullableRiskRule())
        .register(new SuspiciousDataTypeRule());

    engine.register(new SelectStarRule());
    engine.register(new NPlusOneRule(nPlusOneThreshold));
    engine.register(new SlowQueryRule(slowQueryThresholdMs));

    if (explainCache != null) {
      engine.register(new FullTableScanRule(explainCache));
      engine.register(new MissingIndexRule(explainCache));
      engine.register(new UnusedIndexRule(explainCache));
    }

    return engine;
  }

  public static RuleEngine withDefaultRules(long slowQueryThresholdMs,
      int nPlusOneThreshold) {
    return withDefaultRules(slowQueryThresholdMs, nPlusOneThreshold, null);
  }
}