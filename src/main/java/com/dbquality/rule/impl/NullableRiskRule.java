package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.SQLRecord;
import com.dbquality.collector.model.Column;
import com.dbquality.collector.model.Table;
import com.dbquality.rule.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Phát hiện các cột nullable được dùng thường xuyên trong WHERE clause.
 * Cột nullable trong WHERE có thể gây ra full scan do index không hiệu quả.
 */
public class NullableRiskRule implements Rule {

  @Override
  public String getName() {
    return "NULLABLE_RISK";
  }

  @Override
  public Severity getSeverity() {
    return Severity.WARNING;
  }

  @Override
  public RuleResult analyze(DDLContext ddl, SQLContext sql) {
    List<Finding> findings = new ArrayList<>();

    for (Table table : ddl.getTables()) {
      for (Column column : table.getColumns()) {
        if (!column.isNullable()) continue;

        // Kiểm tra xem cột này có xuất hiện trong WHERE của SQL không
        boolean usedInWhere = sql.getRecords().stream()
            .anyMatch(r -> isUsedInWhere(r.getSql(),
                table.getName(), column.getName()));

        if (usedInWhere) {
          findings.add(Finding.builder()
              .rule(getName())
              .severity(getSeverity())
              .table(table.getName())
              .column(column.getName())
              .message("Cột nullable " + column.getName()
                  + " trên bảng " + table.getName()
                  + " được dùng trong WHERE — có thể gây full scan")
              .recommendation("Cân nhắc NOT NULL + DEFAULT hoặc xử lý NULL trong query")
              .calledFrom("Schema analysis — no call site")
              .build());
        }
      }
    }

    return new RuleResult(findings);
  }

  private boolean isUsedInWhere(String sql, String tableName, String columnName) {
    if (sql == null) return false;
    String upper = sql.toUpperCase();
    return upper.contains("WHERE") && upper.contains(columnName.toUpperCase());
  }
}