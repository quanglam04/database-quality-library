package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.model.Column;
import com.dbquality.collector.model.Table;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.constant.Severity;
import com.dbquality.rule.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Phát hiện các cột có kiểu dữ liệu không phù hợp hoặc có thể gây vấn đề.
 *
 * <p>Các pattern detect được:</p>
 * <ul>
 *   <li>FLOAT/DOUBLE cho tiền tệ → mất precision</li>
 *   <li>VARCHAR cho ID/số → mất performance và type safety</li>
 *   <li>VARCHAR cho date/time → không sort/filter chính xác</li>
 *   <li>VARCHAR cho boolean → tốn storage, không type safe</li>
 *   <li>VARCHAR cho email/URL không giới hạn độ dài</li>
 *   <li>TEXT/BLOB cho dữ liệu ngắn → không index được hiệu quả</li>
 *   <li>CHAR cho dữ liệu có độ dài biến đổi → tốn storage</li>
 *   <li>TIMESTAMP cho ngày sinh → giới hạn năm 1970-2038</li>
 * </ul>
 */
public class SuspiciousDataTypeRule implements Rule {

  @Override
  public String getName() {
    return RuleName.SuspiciousDataType;
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
        TypeIssue issue = detectIssue(column);
        if (issue != null) {
          findings.add(Finding.builder()
              .rule(getName())
              .severity(getSeverity())
              .table(table.getName())
              .column(column.getName())
              .message("Cột " + column.getName() + " có kiểu dữ liệu đáng ngờ: " + issue.reason())
              .recommendation(issue.recommendation())
              .calledFrom("Schema analysis — no call site")
              .build());
        }
      }
    }

    return new RuleResult(findings);
  }

  /**
   * Record chứa lý do detect và recommendation tương ứng.
   */
  private record TypeIssue(String reason, String recommendation) {}

  /**
   * Phân tích column và trả về TypeIssue nếu phát hiện vấn đề.
   * Các check được sắp xếp theo độ ưu tiên — case cụ thể trước, case chung sau.
   */
  private TypeIssue detectIssue(Column column) {
    String type = column.getType().toUpperCase();
    String name = column.getName().toUpperCase();

    //  FLOAT/DOUBLE
    if (type.equals("FLOAT") || type.equals("DOUBLE") || type.equals("REAL")) {
      if (isMoneyColumn(name)) {
        return new TypeIssue(
            "FLOAT/DOUBLE không chính xác cho dữ liệu tài chính",
            "Dùng DECIMAL(19,4) hoặc NUMERIC(19,4) để tránh lỗi làm tròn");
      }
      if (isCountColumn(name)) {
        return new TypeIssue(
            "FLOAT/DOUBLE cho cột đếm — không cần phần thập phân",
            "Dùng INT hoặc BIGINT cho dữ liệu đếm");
      }
    }

    //  VARCHAR cho dữ liệu nên là kiểu khác
    if (type.startsWith("VARCHAR") || type.startsWith("NVARCHAR")) {

      // VARCHAR cho ID/số
      if (isNumericColumn(name)) {
        return new TypeIssue(
            "VARCHAR cho cột ID/số — mất performance khi JOIN, không type safe",
            "Dùng INT hoặc BIGINT cho ID, INT/DECIMAL cho số đếm");
      }

      // VARCHAR cho ngày giờ
      if (isDateTimeColumn(name)) {
        return new TypeIssue(
            "VARCHAR cho cột ngày/giờ — không sort/filter chính xác, không dùng được date function",
            "Dùng DATE cho ngày, DATETIME/TIMESTAMP cho ngày giờ");
      }

      // VARCHAR cho boolean
      if (isBooleanColumn(name)) {
        return new TypeIssue(
            "VARCHAR cho cột boolean — tốn storage, không type safe",
            "Dùng BOOLEAN, BIT, hoặc TINYINT(1)");
      }

      // VARCHAR cho enum/status với độ dài quá lớn
      if (isEnumColumn(name) && getVarcharLength(type) > 50) {
        return new TypeIssue(
            "VARCHAR(" + getVarcharLength(type) + ") cho cột enum/status — quá dài cho tập giá trị cố định",
            "Dùng ENUM, hoặc VARCHAR ngắn hơn (20-30 ký tự là đủ)");
      }

      // VARCHAR cho email không giới hạn hoặc quá lớn
      if (name.contains("EMAIL") && getVarcharLength(type) > 320) {
        return new TypeIssue(
            "VARCHAR cho email quá dài — RFC 5321 giới hạn 320 ký tự",
            "Dùng VARCHAR(320) là đủ cho email theo chuẩn RFC");
      }

      // VARCHAR cho UUID/hash với độ dài sai
      if (isUuidColumn(name) && getVarcharLength(type) != 36 && getVarcharLength(type) != 32) {
        return new TypeIssue(
            "VARCHAR(" + getVarcharLength(type) + ") cho UUID — UUID chuẩn là 36 ký tự (32 không có dấu gạch)",
            "Dùng CHAR(36) hoặc UUID type (PostgreSQL) cho hiệu suất tốt hơn");
      }
    }

    //  TEXT/BLOB
    if (type.equals("TEXT") || type.equals("BLOB")
        || type.equals("CLOB") || type.equals("LONGTEXT")) {
      if (isShortDataColumn(name)) {
        return new TypeIssue(
            "TEXT/BLOB cho dữ liệu ngắn — không index được hiệu quả",
            "Dùng VARCHAR(255) hoặc VARCHAR(100) nếu dữ liệu có độ dài giới hạn");
      }
    }

    //  CHAR fixed-length không phù hợp
    if (type.startsWith("CHAR(") && !isUuidColumn(name) && !isCodeColumn(name)) {
      int length = getVarcharLength(type);
      if (length > 10) {
        return new TypeIssue(
            "CHAR(" + length + ") cho dữ liệu có độ dài biến đổi — tốn storage do padding",
            "Dùng VARCHAR(" + length + ") cho dữ liệu có độ dài thay đổi");
      }
    }

    //  TIMESTAMP cho ngày sinh
    if (type.equals("TIMESTAMP") && (name.contains("BIRTH") || name.equals("DOB"))) {
      return new TypeIssue(
          "TIMESTAMP cho ngày sinh — giới hạn khoảng 1970-2038 trên MySQL",
          "Dùng DATE cho ngày sinh để tránh giới hạn UNIX timestamp");
    }

    //  INT cho cột tiền không có decimal
    if ((type.equals("INT") || type.equals("INTEGER") || type.equals("BIGINT"))
        && isMoneyColumn(name)) {
      return new TypeIssue(
          "INT/BIGINT cho tiền tệ — không hỗ trợ phần thập phân (lẻ)",
          "Dùng DECIMAL(19,4) nếu cần phần thập phân, hoặc lưu dưới dạng cents (xu) nếu cố tình dùng INT");
    }

    return null;
  }

  //  Helper methods phân loại cột theo tên

  private boolean isMoneyColumn(String name) {
    return name.contains("PRICE") || name.contains("AMOUNT")
        || name.contains("TOTAL") || name.contains("SALARY")
        || name.contains("COST") || name.contains("FEE")
        || name.contains("BUDGET") || name.contains("BALANCE")
        || name.contains("REVENUE") || name.contains("PROFIT")
        || name.contains("PAYMENT") || name.contains("WAGE")
        || name.contains("TAX") || name.contains("DISCOUNT");
  }

  private boolean isCountColumn(String name) {
    return name.contains("COUNT") || name.contains("QUANTITY")
        || name.endsWith("_NUM") || name.equals("QTY")
        || name.contains("STOCK");
  }

  private boolean isNumericColumn(String name) {
    return name.equals("ID") || name.endsWith("_ID")
        || name.contains("COUNT") || name.contains("NUMBER")
        || name.contains("QUANTITY") || name.equals("AGE")
        || name.equals("YEAR") || name.equals("MONTH")
        || name.equals("DAY") || name.endsWith("_NO");
  }

  private boolean isDateTimeColumn(String name) {
    return name.contains("DATE") || name.contains("TIME")
        || name.equals("DOB") || name.contains("BIRTH")
        || name.contains("CREATED") || name.contains("UPDATED")
        || name.contains("MODIFIED") || name.contains("DELETED")
        || name.contains("EXPIRED") || name.contains("EXPIRES")
        || name.endsWith("_AT") || name.endsWith("_ON")
        || name.contains("TIMESTAMP");
  }

  private boolean isBooleanColumn(String name) {
    return name.startsWith("IS_") || name.startsWith("HAS_")
        || name.startsWith("CAN_") || name.startsWith("SHOULD_")
        || name.equals("ACTIVE") || name.equals("ENABLED")
        || name.equals("DELETED") || name.equals("VERIFIED")
        || name.equals("APPROVED") || name.equals("PUBLISHED")
        || name.equals("VISIBLE") || name.equals("LOCKED")
        || name.startsWith("FLAG_") || name.endsWith("_FLAG");
  }

  private boolean isEnumColumn(String name) {
    return name.equals("STATUS") || name.equals("TYPE")
        || name.equals("STATE") || name.equals("ROLE")
        || name.equals("LEVEL") || name.equals("CATEGORY")
        || name.endsWith("_STATUS") || name.endsWith("_TYPE")
        || name.endsWith("_STATE") || name.endsWith("_ROLE");
  }

  private boolean isUuidColumn(String name) {
    return name.contains("UUID") || name.contains("GUID")
        || name.equals("UID");
  }

  private boolean isCodeColumn(String name) {
    return name.contains("CODE") || name.equals("SKU")
        || name.contains("ISBN") || name.equals("ISO")
        || name.contains("HASH");
  }

  private boolean isShortDataColumn(String name) {
    return name.contains("NAME") || name.contains("CODE")
        || name.contains("STATUS") || name.contains("TYPE")
        || name.contains("TITLE") || name.contains("LABEL")
        || name.contains("TAG") || name.contains("CATEGORY")
        || name.contains("EMAIL") || name.contains("PHONE")
        || name.contains("URL") || name.contains("SLUG");
  }

  /**
   * Extract độ dài từ VARCHAR(N) hoặc CHAR(N).
   * Trả về 0 nếu không parse được.
   */
  private int getVarcharLength(String type) {
    int start = type.indexOf('(');
    int end = type.indexOf(')');
    if (start == -1 || end == -1 || end <= start) return 0;
    try {
      return Integer.parseInt(type.substring(start + 1, end).trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}