package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.model.Column;
import com.dbquality.collector.model.ForeignKey;
import com.dbquality.collector.model.Table;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.rule.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Phát hiện các Foreign Key không có index — gây full scan khi JOIN.
 */
public class UnindexedForeignKeyRule implements Rule {

  @Override
  public String getName() {
    return RuleName.UnindexedForeignKey;
  }

  @Override
  public Severity getSeverity() {
    return Severity.HIGH;
  }

  @Override
  public RuleResult analyze(DDLContext ddl, SQLContext sql) {
    List<Finding> findings = new ArrayList<>();

    for (Table table : ddl.getTables()) {
      for (ForeignKey fk : table.getForeignKeys()) {
        Column fkColumn = table.getColumns().stream()
            .filter(c -> c.getName().equalsIgnoreCase(fk.getColumn()))
            .findFirst()
            .orElse(null);

        if (fkColumn != null && !ddl.hasIndexOn(table, fkColumn)) {
          findings.add(Finding.builder()
              .rule(getName())
              .severity(getSeverity())
              .table(table.getName())
              .column(fk.getColumn())
              .message("FK " + fk.getColumn() + " trên bảng "
                  + table.getName() + " không có index — JOIN sẽ full scan")
              .recommendation("CREATE INDEX idx_" + table.getName().toLowerCase()
                  + "_" + fk.getColumn().toLowerCase()
                  + " ON " + table.getName()
                  + "(" + fk.getColumn() + ")")
              .calledFrom("Schema analysis — no call site")
              .build());
        }
      }
    }

    return new RuleResult(findings);
  }
}