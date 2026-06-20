package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.SQLRecord;
import com.dbquality.constant.Constant;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.constant.Severity;
import com.dbquality.rule.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Phát hiện các câu SQL có khả năng thực hiện full table scan.
 *
 * <p>Các pattern detect được:</p>
 * <ul>
 *   <li>LIKE với leading wildcard ({@code LIKE '%abc%'} hoặc {@code LIKE LOWER(?)})</li>
 *   <li>Function trên cột trong WHERE ({@code LOWER, UPPER, YEAR, CAST, ...})</li>
 *   <li>NOT IN với subquery — thường bypass index</li>
 *   <li>Không equal operator ({@code <>} hoặc {@code !=}) — không dùng được index</li>
 *   <li>OR điều kiện trên các cột khác nhau (relaxed — chỉ flag khi có > 1 OR)</li>
 *   <li>IS NULL / IS NOT NULL trong WHERE — thường bypass index trừ partial index</li>
 *   <li>Implicit cast pattern phổ biến</li>
 * </ul>
 */
public class FullTableScanCandidateRule implements Rule {

  // Function trên cột trong WHERE — match các function phổ biến gây bypass index
  private static final Pattern FUNCTION_ON_COLUMN = Pattern.compile(Constant.FUNCTION_ON_COLUMN_PATTERN,
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL
  );

  // LIKE với leading wildcard (literal hoặc qua CONCAT/LOWER)
  private static final Pattern LIKE_LEADING_WILDCARD = Pattern.compile(Constant.LIKE_LEADING_WILDCARD,
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL
  );

  // NOT IN với subquery
  private static final Pattern NOT_IN_SUBQUERY = Pattern.compile(
      Constant.NOT_IN_SUBQUERY,
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL
  );

  // Không equal operator
  private static final Pattern NOT_EQUAL_OPERATOR = Pattern.compile(
      Constant.NOT_EQUAL_OPERATOR,
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL
  );

  // IS NULL / IS NOT NULL trong WHERE
  private static final Pattern IS_NULL_FILTER = Pattern.compile(
      Constant.IS_NULL_FILTER,
      Pattern.CASE_INSENSITIVE | Pattern.DOTALL
  );

  // Đếm số OR trong WHERE clause
  private static final Pattern OR_PATTERN = Pattern.compile(
      Constant.OR_PATTERN,
      Pattern.CASE_INSENSITIVE
  );

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

    // Group theo SQL text và đếm số lần xuất hiện
    Map<String, List<SQLRecord>> grouped = sql.getRecords().stream()
        .filter(r -> r.getSql() != null)
        .collect(Collectors.groupingBy(SQLRecord::getSql));

    for (Map.Entry<String, List<SQLRecord>> entry : grouped.entrySet()) {
      String sqlText = entry.getKey();
      List<SQLRecord> records = entry.getValue();
      int count = records.size();

      ScanIssue issue = detectIssue(sqlText);
      if (issue == null) continue;

      // Lấy calledFrom phổ biến nhất
      String calledFrom = records.stream()
          .collect(Collectors.groupingBy(SQLRecord::getCalledFrom, Collectors.counting()))
          .entrySet().stream()
          .max(Map.Entry.comparingByValue())
          .map(Map.Entry::getKey)
          .orElse("unknown");

      String executionInfo = count > 1
          ? " (chạy " + count + " lần)"
          : "";

      findings.add(Finding.builder()
          .rule(getName())
          .severity(issue.severity())
          .message("Câu SQL có khả năng full table scan: " + issue.reason()
              + executionInfo + getQueryType(sqlText))
          .recommendation(issue.recommendation())
          .calledFrom(calledFrom)
          .build());
    }

    return new RuleResult(findings);
  }

  /**
   * Record chứa lý do detect, severity và recommendation tương ứng.
   */
  private record ScanIssue(String reason, Severity severity, String recommendation) {}

  /**
   * Phân tích SQL và trả về ScanIssue nếu phát hiện vấn đề.
   * Sắp xếp theo độ tin cậy giảm dần — HIGH severity trước, MEDIUM sau.
   */
  private ScanIssue detectIssue(String sql) {
    if (sql == null) return null;
    String upper = sql.toUpperCase();

    // Chỉ check các SELECT statement có WHERE
    if (!upper.contains("WHERE")) return null;

    //  HIGH severity — patterns chắc chắn bypass index

    if (LIKE_LEADING_WILDCARD.matcher(upper).matches()) {
      return new ScanIssue(
          "LIKE với wildcard ở đầu — index không được sử dụng",
          Severity.HIGH,
          "Cân nhắc dùng full-text index (MySQL FULLTEXT, PostgreSQL tsvector) "
              + "hoặc reverse index nếu chỉ cần suffix matching");
    }

    if (NOT_IN_SUBQUERY.matcher(upper).matches()) {
      return new ScanIssue(
          "NOT IN với subquery — thường bypass index và xử lý NULL không nhất quán",
          Severity.HIGH,
          "Dùng NOT EXISTS hoặc LEFT JOIN ... WHERE ... IS NULL thay vì NOT IN");
    }

    if (FUNCTION_ON_COLUMN.matcher(upper).matches()) {
      return new ScanIssue(
          "Function trên cột trong WHERE — index không được sử dụng",
          Severity.HIGH,
          "Tránh dùng function trên cột — refactor sang dùng functional index, "
              + "computed column, hoặc lưu sẵn giá trị đã transform");
    }

    //  MEDIUM severity — patterns có thể bypass index

    if (NOT_EQUAL_OPERATOR.matcher(upper).matches()) {
      return new ScanIssue(
          "Không equal operator (<> hoặc !=) — không dùng được index",
          Severity.MEDIUM,
          "Refactor sang positive condition (IN, =) nếu có thể, "
              + "hoặc đảm bảo điều kiện khác trong WHERE có index");
    }

    // OR conditions — chỉ flag khi có nhiều OR
    int orCount = countOccurrences(upper, OR_PATTERN);
    if (orCount >= 2) {
      return new ScanIssue(
          "OR với " + orCount + " điều kiện trong WHERE — có thể bypass index",
          Severity.MEDIUM,
          "Cân nhắc tách thành nhiều query với UNION ALL, "
              + "hoặc đảm bảo mọi cột trong OR đều có index");
    }

    if (IS_NULL_FILTER.matcher(upper).matches()) {
      return new ScanIssue(
          "IS NULL / IS NOT NULL trong WHERE — thường bypass index trừ khi có partial index",
          Severity.MEDIUM,
          "Cân nhắc partial index (PostgreSQL) hoặc filtered index (SQL Server) "
              + "cho cột có nhiều NULL");
    }

    return null;
  }

  /**
   * Đếm số lần pattern xuất hiện trong text.
   */
  private int countOccurrences(String text, Pattern pattern) {
    int count = 0;
    var matcher = pattern.matcher(text);
    while (matcher.find()) count++;
    return count;
  }

  /**
   * Phân loại query type để hiển thị label trong message.
   * Giúp phân biệt COUNT query và pagination query từ JPA/Hibernate.
   */
  private String getQueryType(String sql) {
    if (sql == null) return "";
    String upper = sql.trim().toUpperCase();
    if (upper.startsWith("SELECT COUNT")) return " [COUNT]";
    if (upper.contains(" LIMIT ") || upper.contains(" OFFSET ")
        || upper.contains(" FETCH FIRST ")) return " [PAGINATED]";
    return "";
  }
}