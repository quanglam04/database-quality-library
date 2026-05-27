package com.dbquality.explain;

import com.dbquality.rule.Finding;
import java.util.List;

/**
 * Kết quả sau khi parse output của lệnh EXPLAIN từ database.
 * Chứa danh sách các vấn đề phát hiện được từ execution plan.
 */
public class ExplainResult {

  private final List<Finding> findings;
  private final String rawOutput;
  private final String databaseType;

  public ExplainResult(List<Finding> findings, String rawOutput, String databaseType) {
    this.findings = findings;
    this.rawOutput = rawOutput;
    this.databaseType = databaseType;
  }

  /**
   * @return danh sách vấn đề phát hiện được từ execution plan
   */
  public List<Finding> getFindings() { return findings; }

  /**
   * @return raw output gốc trả về từ lệnh EXPLAIN
   */
  public String getRawOutput() { return rawOutput; }

  /**
   * @return tên database vendor (MySQL, PostgreSQL,...)
   */
  public String getDatabaseType() { return databaseType; }

  /**
   * @return true nếu execution plan có vấn đề cần chú ý
   */
  public boolean hasIssues() { return !findings.isEmpty(); }
}