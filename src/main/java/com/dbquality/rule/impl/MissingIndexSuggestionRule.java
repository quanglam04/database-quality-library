package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.SQLRecord;
import com.dbquality.collector.model.Column;
import com.dbquality.collector.model.ForeignKey;
import com.dbquality.collector.model.Table;
import com.dbquality.constant.Constant;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.constant.Severity;
import com.dbquality.rule.*;

import com.dbquality.util.SQLFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gợi ý thêm index cho các cột được dùng thường xuyên trong WHERE/JOIN
 * nhưng chưa có index.
 *
 * <p>Logic phát hiện:</p>
 * <ul>
 *   <li>Đếm tần suất mỗi cột xuất hiện trong WHERE/AND/OR/ON</li>
 *   <li>Hỗ trợ cả {@code col = ?} và {@code alias.col = ?} (JPA/Hibernate alias)</li>
 *   <li>Map alias về table thật dựa trên FROM/JOIN clause</li>
 *   <li>Bỏ qua PK (đã có index) và FK (đã được xử lý bởi UnindexedForeignKeyRule)</li>
 *   <li>Threshold = 3 lần — đủ thấp để phát hiện sớm, đủ cao để tránh noise</li>
 * </ul>
 */
public class MissingIndexSuggestionRule implements Rule {


  private static final int FREQUENCY_THRESHOLD = 3;

  // Match: WHERE col = ?, AND col > ?, ON col = ?
  private static final Pattern SIMPLE_COLUMN_PATTERN = Pattern.compile(
      Constant.SIMPLE_COLUMN_PATTERN,
      Pattern.CASE_INSENSITIVE
  );

  // Match: WHERE alias.col = ?, ON t1.col = t2.col (JPA/Hibernate generated)
  private static final Pattern QUALIFIED_COLUMN_PATTERN = Pattern.compile(
      Constant.QUALIFIED_COLUMN_PATTERN,
      Pattern.CASE_INSENSITIVE
  );

  // Match: FROM table alias, JOIN table alias (lấy mapping alias -> table)
  private static final Pattern TABLE_ALIAS_PATTERN = Pattern.compile(
      Constant.TABLE_ALIAS_PATTERN,
      Pattern.CASE_INSENSITIVE
  );

  // Match: FROM table (không có alias)
  private static final Pattern TABLE_NO_ALIAS_PATTERN = Pattern.compile(
      Constant.TABLE_NO_ALIAS_PATTERN,
      Pattern.CASE_INSENSITIVE
  );

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

    // Đếm tần suất cột xuất hiện theo từng bảng
    Map<String, Integer> columnFrequencyByTable = countFrequencyByTable(sql);

    for (Table table : ddl.getTables()) {
      // Lấy set FK columns để skip — đã được UnindexedForeignKeyRule xử lý
      Set<String> fkColumns = getFKColumns(table);

      for (Column column : table.getColumns()) {
        // Skip PK — đã có index
        if (column.isPrimaryKey()) continue;
        // Skip FK — đã được rule khác xử lý
        if (fkColumns.contains(column.getName().toUpperCase())) continue;

        String key = table.getName().toUpperCase() + "." + column.getName().toUpperCase();
        int frequency = columnFrequencyByTable.getOrDefault(key, 0);

        if (frequency >= FREQUENCY_THRESHOLD && !ddl.hasIndexOn(table, column)) {
          findings.add(Finding.builder()
              .rule(getName())
              .severity(getSeverity())
              .table(table.getName())
              .column(column.getName())
              .message("Cột " + column.getName() + " trên bảng " + table.getName()
                  + " xuất hiện " + frequency + " lần trong WHERE/JOIN nhưng chưa có index")
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

  /**
   * Đếm tần suất mỗi cột xuất hiện trong WHERE/JOIN, group theo bảng.
   * Key format: {@code TABLE.COLUMN}
   */
  private Map<String, Integer> countFrequencyByTable(SQLContext sql) {
    Map<String, Integer> frequency = new HashMap<>();

    for (SQLRecord record : sql.getRecords()) {
      if (record.getSql() == null) continue;
      String sqlText = record.getSql();

      // Build alias -> table mapping cho query này
      Map<String, String> aliasToTable = extractAliasMapping(sqlText);

      // Set tables không có alias (dùng thẳng table name làm "alias")
      Set<String> tablesInQuery = extractTablesInQuery(sqlText);

      // Đếm qualified columns: alias.col
      Matcher qualifiedMatcher = QUALIFIED_COLUMN_PATTERN.matcher(sqlText);
      while (qualifiedMatcher.find()) {
        String aliasOrTable = qualifiedMatcher.group(1).toUpperCase();
        String col = qualifiedMatcher.group(2).toUpperCase();

        // Resolve alias về table thật
        String table = aliasToTable.getOrDefault(aliasOrTable, aliasOrTable);
        frequency.merge(table + "." + col, 1, Integer::sum);
      }

      // Đếm simple columns: col (không có prefix)
      // Với simple column, không biết thuộc table nào → gắn vào tất cả tables trong query
      Matcher simpleMatcher = SIMPLE_COLUMN_PATTERN.matcher(sqlText);
      while (simpleMatcher.find()) {
        String col = simpleMatcher.group(1).toUpperCase();

        // Skip nếu col trùng với keyword (NULL, NOT, ...)
        if (SQLFilter.isSQLKeyword(col)) continue;

        for (String table : tablesInQuery) {
          frequency.merge(table + "." + col, 1, Integer::sum);
        }
      }
    }

    return frequency;
  }

  /**
   * Extract mapping alias -> table từ FROM/JOIN clause.
   * Ví dụ: {@code FROM employees e1_0} → {"E1_0": "EMPLOYEES"}
   */
  private Map<String, String> extractAliasMapping(String sql) {
    Map<String, String> mapping = new HashMap<>();
    Matcher matcher = TABLE_ALIAS_PATTERN.matcher(sql);
    while (matcher.find()) {
      String table = matcher.group(1).toUpperCase();
      String alias = matcher.group(2).toUpperCase();
      // Skip nếu "alias" thực ra là SQL keyword
      if (!SQLFilter.isSQLKeyword(alias)) {
        mapping.put(alias, table);
      }
    }
    return mapping;
  }

  /**
   * Extract set tables xuất hiện trong query.
   */
  private Set<String> extractTablesInQuery(String sql) {
    Set<String> tables = new HashSet<>();

    // Lấy tables có alias
    Matcher withAlias = TABLE_ALIAS_PATTERN.matcher(sql);
    while (withAlias.find()) {
      tables.add(withAlias.group(1).toUpperCase());
    }

    // Lấy tables không alias
    Matcher noAlias = TABLE_NO_ALIAS_PATTERN.matcher(sql);
    while (noAlias.find()) {
      tables.add(noAlias.group(1).toUpperCase());
    }

    return tables;
  }

  /**
   * Lấy set FK columns của 1 table (UPPER CASE).
   */
  private Set<String> getFKColumns(Table table) {
    Set<String> fkColumns = new HashSet<>();
    if (table.getForeignKeys() != null) {
      for (ForeignKey fk : table.getForeignKeys()) {
        if (fk.getColumn() != null) {
          fkColumns.add(fk.getColumn().toUpperCase());
        }
      }
    }
    return fkColumns;
  }
}