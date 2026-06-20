package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.SQLRecord;
import com.dbquality.collector.model.Index;
import com.dbquality.collector.model.Table;
import com.dbquality.constant.Constant;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.constant.Severity;
import com.dbquality.rule.*;
import com.dbquality.util.SchemaFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phát hiện các index không được sử dụng trong session hiện tại.
 * Index không được dùng = tốn storage và làm chậm INSERT/UPDATE/DELETE.
 *
 */
public class UnusedIndexRule implements Rule {

  // Pattern lấy phần sau WHERE/ON/ORDER BY/GROUP BY (nơi index thực sự được dùng)
  private static final Pattern FILTER_CLAUSE_PATTERN =
      Pattern.compile(Constant.FILTER_CLAUSE_PATTERN, Pattern.CASE_INSENSITIVE | Pattern.DOTALL
  );

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
        // Bỏ qua index không có cột nào (edge case)
        if (index.getColumns() == null || index.getColumns().isEmpty()) continue;

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

  /**
   * Kiểm tra index có phải Primary Key index không.
   * Hỗ trợ convention của MySQL (PRIMARY), Oracle/SQL Server (PK_*),
   * và PostgreSQL (*_pkey).
   */
  private boolean isPrimaryKeyIndex(String indexName) {
    if (indexName == null) return false;
    String upper = indexName.toUpperCase();
    return upper.contains("PRIMARY")
        || upper.startsWith("PK_")
        || upper.endsWith("_PKEY");
  }

  /**
   * Kiểm tra index có được dùng trong bất kỳ query nào của session không.
   * Áp dụng leftmost prefix rule cho composite index — chỉ check cột đầu tiên.
   */
  private boolean isIndexUsedInQueries(Index index, Table table, SQLContext sql) {
    // Composite index: chỉ check leading column theo leftmost prefix rule
    String leadingColumn = index.getColumns().get(0);

    for (SQLRecord record : sql.getRecords()) {
      String upper = record.getSql().toUpperCase();

      // Kiểm tra bảng có xuất hiện trong query không
      if (!upper.contains(table.getName().toUpperCase())) continue;

      // Extract phần WHERE/ON/ORDER BY/GROUP BY — nơi index thực sự được dùng
      String filterClauses = extractFilterClauses(upper);
      if (filterClauses.isEmpty()) continue;

      // Dùng word boundary để tránh match partial (id vs customer_id)
      if (containsColumnAsWord(filterClauses, leadingColumn.toUpperCase())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Lấy phần sau WHERE/ON/ORDER BY/GROUP BY trong SQL.
   * Đây là nơi index thực sự được dùng.
   */
  private String extractFilterClauses(String upperSql) {
    StringBuilder result = new StringBuilder();
    Matcher matcher = FILTER_CLAUSE_PATTERN.matcher(upperSql);
    while (matcher.find()) {
      result.append(matcher.group(1)).append(" ");
    }
    return result.toString();
  }

  /**
   * Kiểm tra cột có xuất hiện như một từ độc lập (word boundary).
   * Tránh false positive: tìm "ID" không match "CUSTOMER_ID".
   */
  private boolean containsColumnAsWord(String text, String column) {
    Pattern pattern = Pattern.compile("\\b" + Pattern.quote(column) + "\\b");
    return pattern.matcher(text).find();
  }
}