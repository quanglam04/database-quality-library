package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.model.Table;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.constant.Severity;
import com.dbquality.rule.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Phát hiện các bảng không có Primary Key.
 */
public class MissingPrimaryKeyRule implements Rule {

  @Override
  public String getName() {
    return RuleName.MissingPrimaryKey;
  }

  @Override
  public Severity getSeverity() {
    return Severity.CRITICAL;
  }

  @Override
  public RuleResult analyze(DDLContext ddl, SQLContext sql) {
    List<Finding> findings = new ArrayList<>();

    for (Table table : ddl.getTables()) {
      if (!table.hasPrimaryKey()) {
        findings.add(Finding.builder()
            .rule(getName())
            .severity(getSeverity())
            .table(table.getName())
            .message("Bảng " + table.getName() + " không có Primary Key")
            .recommendation("Thêm cột id BIGINT AUTO_INCREMENT PRIMARY KEY")
            .calledFrom("Schema analysis — no call site")
            .build());
      }
    }

    return new RuleResult(findings);
  }
}