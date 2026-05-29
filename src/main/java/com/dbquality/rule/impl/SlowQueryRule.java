package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.SQLRecord;
import com.dbquality.rule.*;

import java.util.ArrayList;
import java.util.List;

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
    return "SLOW_QUERY";
  }

  @Override
  public Severity getSeverity() {
    return Severity.HIGH;
  }

  @Override
  public RuleResult analyze(DDLContext ddl, SQLContext sql) {
    List<Finding> findings = new ArrayList<>();

    for (SQLRecord record : sql.getRecords()) {
      if (record.getExecutionTime() >= thresholdMs) {
        findings.add(Finding.builder()
            .rule(getName())
            .severity(getSeverity())
            .message("Query chạy " + record.getExecutionTime()
                + "ms — vượt ngưỡng " + thresholdMs + "ms")
            .recommendation("Kiểm tra index và tối ưu câu SQL")
            .calledFrom(record.getCalledFrom())
            .build());
      }
    }

    return new RuleResult(findings);
  }
}