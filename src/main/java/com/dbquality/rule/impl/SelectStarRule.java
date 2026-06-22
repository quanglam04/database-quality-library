package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.QueryMetric;
import com.dbquality.collector.QueryMetricsStore;
import com.dbquality.constant.Constant;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.constant.Severity;
import com.dbquality.rule.Finding;
import com.dbquality.rule.MetricsBasedRule;
import com.dbquality.rule.RuleResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Phát hiện câu SQL dùng SELECT *.
 *
 * <p>Dùng QueryMetricsStore (đã group sẵn theo unique SQL pattern) nên
 * không cần group lại trong rule. Mỗi pattern chỉ tạo 1 finding,
 * kèm số lần xuất hiện và average duration.</p>
 */
public class SelectStarRule implements MetricsBasedRule {

  private static final Pattern SELECT_STAR_PATTERN =
      Pattern.compile(Constant.SELECT_STAR_PATTERN);

  @Override
  public String getName() {
    return RuleName.SelectStar;
  }

  @Override
  public Severity getSeverity() {
    return Severity.MEDIUM;
  }

  @Override
  public RuleResult analyze(DDLContext ddl, QueryMetricsStore metricsStore) {
    List<Finding> findings = new ArrayList<>();

    for (QueryMetric metric : metricsStore.getAllMetrics()) {
      if (!isSelectStar(metric.getSqlPattern())) continue;

      findings.add(Finding.builder()
          .rule(getName())
          .severity(getSeverity())
          .message("Câu SQL dùng SELECT * — nên chỉ lấy các cột cần thiết "
              + "(xuất hiện " + metric.getCallCount() + " lần, "
              + "avg " + String.format("%.1f", metric.getAvgDurationMs()) + "ms)")
          .recommendation("Thay SELECT * bằng danh sách cột cụ thể")
          .calledFrom(metric.getMostFrequentCalledFrom())
          .build());
    }

    return new RuleResult(findings);
  }

  private boolean isSelectStar(String sql) {
    if (sql == null) return false;
    return SELECT_STAR_PATTERN.matcher(sql).find();
  }
}