package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.SQLRecord;
import com.dbquality.collector.model.Column;
import com.dbquality.collector.model.Table;
import com.dbquality.constant.Constant;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.constant.Severity;
import com.dbquality.rule.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gợi ý thêm index cho các cột được dùng thường xuyên trong WHERE
 * nhưng chưa có index.
 */
public class MissingIndexSuggestionRule implements Rule {

  // Pattern tìm cột trong WHERE: WHERE col = hoặc WHERE col >/
  private static final Pattern WHERE_COLUMN_PATTERN =
      Pattern.compile(Constant.WHERE_COLUMN_PATTERN, Pattern.CASE_INSENSITIVE);

  @Override
  public String getName() {
    return RuleName.MissingIndexSuggestion;
  }

  @Override
  public Severity getSeverity() {
    return Severity.MEDIUM;
  }

  @Override
  public RuleResult analyze(DDLContext ddl, SQLContext sql) {
    List<Finding> findings = new ArrayList<>();

    // Đếm tần suất mỗi cột xuất hiện trong WHERE
    Map<String, Integer> columnWhereFrequency = countWhereFrequency(sql);

    for (Table table : ddl.getTables()) {
      for (Column column : table.getColumns()) {
        // Bỏ qua PK — đã có index
        if (column.isPrimaryKey()) continue;

        String key = table.getName().toUpperCase() + "." + column.getName().toUpperCase();
        int frequency = columnWhereFrequency.getOrDefault(
            column.getName().toUpperCase(), 0);

        // Cột xuất hiện >= 3 lần trong WHERE nhưng không có index
        if (frequency >= 3 && !ddl.hasIndexOn(table, column)) {
          findings.add(Finding.builder()
              .rule(getName())
              .severity(getSeverity())
              .table(table.getName())
              .column(column.getName())
              .message("Cột " + column.getName() + " xuất hiện " + frequency
                  + " lần trong WHERE nhưng chưa có index")
              .recommendation("CREATE INDEX idx_"
                  + table.getName().toLowerCase() + "_"
                  + column.getName().toLowerCase()
                  + " ON " + table.getName()
                  + "(" + column.getName() + ")")
              .calledFrom("Schema analysis — no call site")
              .build());
        }
      }
    }

    return new RuleResult(findings);
  }

  private Map<String, Integer> countWhereFrequency(SQLContext sql) {
    Map<String, Integer> frequency = new HashMap<>();

    for (SQLRecord record : sql.getRecords()) {
      if (record.getSql() == null) continue;
      Matcher matcher = WHERE_COLUMN_PATTERN.matcher(record.getSql());
      while (matcher.find()) {
        String col = matcher.group(1).toUpperCase();
        frequency.merge(col, 1, Integer::sum);
      }
    }

    return frequency;
  }
}