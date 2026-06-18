package com.dbquality.rule;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;

import com.dbquality.constant.Severity;
import com.dbquality.rule.impl.FullTableScanCandidateRule;
import com.dbquality.rule.impl.MissingIndexSuggestionRule;
import com.dbquality.rule.impl.MissingPrimaryKeyRule;
import com.dbquality.rule.impl.NPlusOneRule;
import com.dbquality.rule.impl.NullableRiskRule;
import com.dbquality.rule.impl.SelectStarRule;
import com.dbquality.rule.impl.SlowQueryRule;
import com.dbquality.rule.impl.SuspiciousDataTypeRule;
import com.dbquality.rule.impl.UnindexedForeignKeyRule;
import com.dbquality.rule.impl.UnusedIndexRule;
import java.util.ArrayList;
import java.util.List;

/**
 * Chạy tất cả các rules đã đăng ký và tổng hợp kết quả. <br>
 * Mỗi rule phân tích độc lập — kết quả được gộp lại thành danh sách findings.
 */
public class RuleEngine {

  private final List<Rule> rules = new ArrayList<>();

  /**
   * Đăng ký một rule vào engine.
   *
   * @param rule rule cần thêm vào
   * @return this — hỗ trợ chaining
   */
  public RuleEngine register(Rule rule) {
    rules.add(rule);
    return this;
  }

  /**
   * Chạy tất cả rules đã đăng ký và trả về toàn bộ findings.
   *
   * @param ddl  cấu trúc database đã thu thập
   * @param sql  SQL records đã thu thập trong session
   * @return     danh sách tất cả findings từ mọi rule
   */
  public List<Finding> analyze(DDLContext ddl, SQLContext sql) {
    List<Finding> allFindings = new ArrayList<>();

    for (Rule rule : rules) {
      try {
        RuleResult result = rule.analyze(ddl, sql);
        allFindings.addAll(result.getFindings());
      } catch (Exception e) {
        // 1 rule lỗi không ảnh hưởng các rule khác
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
   * @return số lượng rules đã đăng ký
   */
  public int getRuleCount() {
    return rules.size();
  }

  /**
   * Tạo RuleEngine với toàn bộ built-in rules mặc định.
   *
   * @param slowQueryThresholdMs ngưỡng slow query tính bằng milliseconds
   * @param nPlusOneThreshold    ngưỡng số lần lặp để phát hiện N+1
   * @return RuleEngine đã được cấu hình sẵn
   */
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