package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.SQLRecord;
import com.dbquality.rule.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Phát hiện các câu SQL dùng SELECT * — lấy thừa dữ liệu không cần thiết.
 */
public class SelectStarRule implements Rule {

  @Override
  public String getName() {
    return "SELECT_STAR";
  }

  @Override
  public Severity getSeverity() {
    return Severity.MEDIUM;
  }

  @Override
  public RuleResult analyze(DDLContext ddl, SQLContext sql) {
    List<Finding> findings = new ArrayList<>();

    for (SQLRecord record : sql.getRecords()) {
      if (isSelectStar(record.getSql())) {
        findings.add(Finding.builder()
            .rule(getName())
            .severity(getSeverity())
            .message("Câu SQL dùng SELECT * — nên chỉ lấy các cột cần thiết")
            .recommendation("Thay SELECT * bằng danh sách cột cụ thể")
            .calledFrom(record.getCalledFrom())
            .build());
      }
    }

    return new RuleResult(findings);
  }

  private boolean isSelectStar(String sql) {
    if (sql == null) return false;
    String upper = sql.trim().toUpperCase();
    return upper.startsWith("SELECT") && upper.contains("SELECT *");
  }
}