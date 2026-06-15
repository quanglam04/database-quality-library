package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.SQLRecord;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.constant.Severity;
import com.dbquality.rule.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Phát hiện các câu SQL có khả năng thực hiện full table scan.
 * Dấu hiệu: WHERE clause dùng LIKE với wildcard đầu, OR nhiều điều kiện,
 * hoặc function trên cột trong WHERE.
 */
public class FullTableScanCandidateRule implements Rule {

  @Override
  public String getName() {
    return RuleName.FullTableScanCandidate;
  }

  @Override
  public Severity getSeverity() {
    return Severity.HIGH;
  }

  @Override
  public RuleResult analyze(DDLContext ddl, SQLContext sql) {
    List<Finding> findings = new ArrayList<>();

    sql.getRecords().stream()
        .filter(r -> r.getSql() != null)
        .collect(Collectors.toMap(
            SQLRecord::getSql,
            r -> r,
            (existing, replacement) -> existing
        ))
        .values()
        .forEach(record -> {
          String reason = detectFullScanReason(record.getSql());
          if (reason != null) {
            findings.add(Finding.builder()
                .rule(getName())
                .severity(getSeverity())
                .message("Câu SQL có khả năng full table scan: " + reason
                    + getQueryType(record.getSql()))
                .recommendation("Tránh LIKE '%...', OR nhiều điều kiện, hoặc function trên cột trong WHERE")
                .calledFrom(record.getCalledFrom())
                .build());
          }
        });

    return new RuleResult(findings);
  }

  private String getQueryType(String sql) {
    if (sql == null) return "";
    String upper = sql.trim().toUpperCase();
    if (upper.startsWith("SELECT COUNT")) return " [COUNT]";
    if (upper.contains(" LIMIT "))       return " [PAGINATED]";
    return "";
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