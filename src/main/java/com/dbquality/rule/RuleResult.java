package com.dbquality.rule;

import java.util.List;

/**
 * Chứa danh sách các kết quả được tạo ra từ một lần thực thi rule
 */
public class RuleResult {

  private final List<Finding> findings;

  public RuleResult(List<Finding> findings) {
    this.findings = findings;
  }

  public List<Finding> getFindings() {
    return findings;
  }

  public boolean hasIssues() {
    return !findings.isEmpty();
  }
}