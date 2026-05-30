package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.SQLRecord;
import com.dbquality.rule.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Phát hiện các câu SQL có khả năng thực hiện full table scan.
 * Dấu hiệu: WHERE clause dùng LIKE với wildcard đầu, OR nhiều điều kiện,
 * hoặc function trên cột trong WHERE.
 */
public class FullTableScanCandidateRule implements Rule {

  @Override
  public String getName() {
    return "FULL_TABLE_SCAN_CANDIDATE";
  }

  @Override
  public Severity getSeverity() {
    return Severity.HIGH;
  }

  @Override
  public RuleResult analyze(DDLContext ddl, SQLContext sql) {
    List<Finding> findings = new ArrayList<>();

    for (SQLRecord record : sql.getRecords()) {
      String reason = detectFullScanReason(record.getSql());
      if (reason != null) {
        findings.add(Finding.builder()
            .rule(getName())
            .severity(getSeverity())
            .message("Câu SQL có khả năng full table scan: " + reason)
            .recommendation("Tránh LIKE '%...', OR nhiều điều kiện, hoặc function trên cột trong WHERE")
            .calledFrom(record.getCalledFrom())
            .build());
      }
    }

    return new RuleResult(findings);
  }

  private String detectFullScanReason(String sql) {
    if (sql == null) return null;
    String upper = sql.toUpperCase();

    if (upper.contains("LIKE '%")) {
      return "LIKE với wildcard ở đầu — index không được sử dụng";
    }
    if (upper.contains("WHERE") && upper.contains(" OR ")) {
      return "OR trong WHERE clause — có thể bypass index";
    }
    if (upper.matches(".*WHERE\\s+\\w+\\s*\\(.*")) {
      return "Function trên cột trong WHERE — index không được sử dụng";
    }
    return null;
  }
}