package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.model.Column;
import com.dbquality.collector.model.Table;
import com.dbquality.rule.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Phát hiện các cột có kiểu dữ liệu không phù hợp hoặc có thể gây vấn đề.
 * Ví dụ: dùng VARCHAR để lưu số, dùng FLOAT/DOUBLE cho tiền tệ...
 */
public class SuspiciousDataTypeRule implements Rule {

  @Override
  public String getName() {
    return "SUSPICIOUS_DATA_TYPE";
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
        String reason = detectSuspiciousType(column);
        if (reason != null) {
          findings.add(Finding.builder()
              .rule(getName())
              .severity(getSeverity())
              .table(table.getName())
              .column(column.getName())
              .message("Cột " + column.getName() + " có kiểu dữ liệu đáng ngờ: " + reason)
              .recommendation(getRecommendation(column))
              .calledFrom("Schema analysis — no call site")
              .build());
        }
      }
    }

    return new RuleResult(findings);
  }

  private String detectSuspiciousType(Column column) {
    String type = column.getType().toUpperCase();
    String name = column.getName().toUpperCase();

    // FLOAT/DOUBLE cho cột có tên liên quan đến tiền tệ
    if ((type.equals("FLOAT") || type.equals("DOUBLE"))
        && (name.contains("PRICE") || name.contains("AMOUNT")
        || name.contains("TOTAL") || name.contains("SALARY")
        || name.contains("COST") || name.contains("FEE"))) {
      return "FLOAT/DOUBLE không chính xác cho dữ liệu tài chính";
    }

    // TEXT/BLOB cho cột có tên ngắn — có thể dùng VARCHAR là đủ
    if ((type.equals("TEXT") || type.equals("BLOB"))
        && (name.contains("NAME") || name.contains("CODE")
        || name.contains("STATUS") || name.contains("TYPE"))) {
      return "TEXT/BLOB cho dữ liệu ngắn — nên dùng VARCHAR";
    }

    // VARCHAR cho cột có tên liên quan đến số
    if (type.startsWith("VARCHAR")
        && (name.equals("ID") || name.endsWith("_ID")
        || name.contains("COUNT") || name.contains("NUMBER"))) {
      return "VARCHAR cho cột ID/số — nên dùng INT/BIGINT";
    }

    return null;
  }

  private String getRecommendation(Column column) {
    String type = column.getType().toUpperCase();
    if (type.equals("FLOAT") || type.equals("DOUBLE")) {
      return "Dùng DECIMAL(19,4) cho dữ liệu tài chính để tránh lỗi làm tròn";
    }
    if (type.equals("TEXT") || type.equals("BLOB")) {
      return "Dùng VARCHAR(255) hoặc VARCHAR(100) nếu dữ liệu có độ dài giới hạn";
    }
    return "Cân nhắc lại kiểu dữ liệu phù hợp với nội dung cột";
  }
}