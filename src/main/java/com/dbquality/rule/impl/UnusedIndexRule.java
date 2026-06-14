package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.SQLRecord;
import com.dbquality.collector.model.Index;
import com.dbquality.collector.model.Table;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.rule.*;

import com.dbquality.util.SchemaFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * Phát hiện các index không được sử dụng trong session hiện tại.
 * Index không được dùng = tốn storage và làm chậm INSERT/UPDATE/DELETE.
 */
public class UnusedIndexRule implements Rule {

  @Override
  public String getName() {
    return RuleName.UnusedIndex;
  }

  @Override
  public Severity getSeverity() {
    return Severity.WARNING;
  }

  @Override
  public RuleResult analyze(DDLContext ddl, SQLContext sql) {
    List<Finding> findings = new ArrayList<>();

    // Nếu không có SQL nào được thu thập thì bỏ qua
    if (sql.getRecords().isEmpty()) return new RuleResult(findings);

    for (Table table : ddl.getTables()) {
      if (SchemaFilter.isSystemTable(table.getName())) continue;
      for (Index index : table.getIndexes()) {
        // Bỏ qua Primary Key index
        if (isPrimaryKeyIndex(index.getName())) continue;

        boolean isUsed = isIndexUsedInQueries(index, table, sql);
        if (!isUsed) {
          findings.add(Finding.builder()
              .rule(getName())
              .severity(getSeverity())
              .table(table.getName())
              .message("Index " + index.getName() + " trên bảng "
                  + table.getName() + " không được dùng trong session này"
                  + " — kết quả chỉ dựa trên session hiện tại, không phải lịch sử toàn bộ")
              .recommendation("Cân nhắc xóa index nếu không cần thiết sau khi verify trên production — "
                  + "index thừa làm chậm INSERT/UPDATE/DELETE")
              .calledFrom("Schema analysis — no call site")
              .build());
        }
      }
    }

    return new RuleResult(findings);
  }

  private boolean isPrimaryKeyIndex(String indexName) {
    if (indexName == null) return false;
    String upper = indexName.toUpperCase();
    return upper.contains("PRIMARY") || upper.startsWith("PK_");
  }

  private boolean isIndexUsedInQueries(Index index, Table table, SQLContext sql) {
    for (SQLRecord record : sql.getRecords()) {
      String upper = record.getSql().toUpperCase();
      // Kiểm tra bảng có xuất hiện trong query không
      if (!upper.contains(table.getName().toUpperCase())) continue;
      // Kiểm tra các cột của index có xuất hiện trong WHERE/JOIN không
      for (String col : index.getColumns()) {
        if (upper.contains(col.toUpperCase())) return true;
      }
    }
    return false;
  }
}