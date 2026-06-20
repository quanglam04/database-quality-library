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
 * Phát hiện các câu SQL dùng SELECT * — lấy thừa dữ liệu không cần thiết.
 */
public class SelectStarRule implements Rule {

  private static final Pattern SELECT_STAR_PATTERN =
      Pattern.compile(Constant.SELECT_STAR_PATTERN);

  @Override
  public String getName() {
    return RuleName.SelectStar;
  }

  @Override
  public Severity getSeverity() {
    return Severity.MEDIUM;
  }

  @Override
  public RuleResult analyze(DDLContext ddl, SQLContext sql) {
    List<Finding> findings = new ArrayList<>();

    sql.getRecords().stream()
        .filter(r -> isSelectStar(r.getSql()))
        .collect(Collectors.groupingBy(SQLRecord::getSql))
        .forEach((pattern, records) -> {
          // Lấy calledFrom phổ biến nhất trong group
          String calledFrom = records.stream()
              .collect(Collectors.groupingBy(SQLRecord::getCalledFrom, Collectors.counting()))
              .entrySet().stream()
              .max(Map.Entry.comparingByValue())
              .map(Map.Entry::getKey)
              .orElse("unknown");

          findings.add(Finding.builder()
              .rule(getName())
              .severity(getSeverity())
              .message("Câu SQL dùng SELECT * — nên chỉ lấy các cột cần thiết"
                  + (records.size() > 1 ? " (xuất hiện " + records.size() + " lần)" : ""))
              .recommendation("Thay SELECT * bằng danh sách cột cụ thể")
              .calledFrom(calledFrom)
              .build());
        });

    return new RuleResult(findings);
  }

  private boolean isSelectStar(String sql) {
    if (sql == null) return false;
    return SELECT_STAR_PATTERN.matcher(sql).find();
  }
}