package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.SQLRecord;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.constant.Severity;
import com.dbquality.rule.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Phát hiện N+1 query — cùng một pattern SQL lặp lại quá nhiều lần.
 */
public class NPlusOneRule implements Rule {

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
  public RuleResult analyze(DDLContext ddl, SQLContext sql) {
    List<Finding> findings = new ArrayList<>();

    Map<String, List<SQLRecord>> grouped = sql.getRecords().stream()
        .collect(Collectors.groupingBy(SQLRecord::getSql));

    for (Map.Entry<String, List<SQLRecord>> entry : grouped.entrySet()) {
      if (entry.getValue().size() > threshold) {
        String calledFrom = entry.getValue().stream()
            .collect(Collectors.groupingBy(SQLRecord::getCalledFrom, Collectors.counting()))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("unknown");

        findings.add(Finding.builder()
            .rule(getName())
            .severity(getSeverity())
            .message("Query pattern lặp lại " + entry.getValue().size()
                + " lần — dấu hiệu N+1: " + entry.getKey())
            .recommendation("Dùng JOIN hoặc batch fetch thay vì query trong vòng lặp")
            .calledFrom(calledFrom)
            .build());
      }
    }

    return new RuleResult(findings);
  }
}