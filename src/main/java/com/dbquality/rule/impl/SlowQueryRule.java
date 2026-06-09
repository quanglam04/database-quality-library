package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.SQLRecord;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.rule.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Phát hiện các câu SQL có thời gian thực thi vượt ngưỡng cho phép.
 */
public class SlowQueryRule implements Rule {

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
  public RuleResult analyze(DDLContext ddl, SQLContext sql) {
    List<Finding> findings = new ArrayList<>();

    sql.getRecords().stream()
        .filter(r -> r.getExecutionTime() >= thresholdMs)
        .collect(Collectors.toMap(
            SQLRecord::getSql,
            r -> r,
            (a, b) -> a.getExecutionTime() >= b.getExecutionTime() ? a : b
        ))
        .values()
        .forEach(record -> findings.add(Finding.builder()
            .rule(getName())
            .severity(getSeverity())
            .message("Query chạy " + record.getExecutionTime()
                + "ms — vượt ngưỡng " + thresholdMs + "ms" + getQueryType(record.getSql()))
            .recommendation("Kiểm tra index và tối ưu câu SQL")
            .calledFrom(record.getCalledFrom())
            .build()));

    return new RuleResult(findings);
  }

  private String getQueryType(String sql) {
    if (sql == null) return "";
    String upper = sql.trim().toUpperCase();
    if (upper.startsWith("SELECT COUNT")) return " [COUNT]";
    if (upper.contains(" LIMIT "))       return " [PAGINATED]";
    return "";
  }
}