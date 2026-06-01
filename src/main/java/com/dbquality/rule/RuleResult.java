package com.dbquality.rule;

import java.util.List;

/**
 * Chứa kết quả phân tích của một {@link Rule} sau khi chạy xong.
 *
 * <p>Mỗi lần {@link Rule#analyze(com.dbquality.collector.DDLContext, com.dbquality.collector.SQLContext)}
 * được gọi, rule trả về một {@code RuleResult} chứa danh sách các
 * {@link Finding} — mỗi finding tương ứng với một vấn đề được phát hiện.</p>
 *
 * <p>Nếu rule không tìm thấy vấn đề nào, danh sách findings sẽ rỗng và
 * {@link #hasIssues()} trả về {@code false}.</p>
 */
public class RuleResult {

  private final List<Finding> findings;

  /**
   * Tạo một RuleResult với danh sách findings cho trước.
   *
   * @param findings danh sách vấn đề được phát hiện; truyền list rỗng nếu không có vấn đề gì
   */
  public RuleResult(List<Finding> findings) {
    this.findings = findings;
  }

  /**
   * @return danh sách tất cả vấn đề phát hiện được — rỗng nếu rule không tìm thấy gì
   */
  public List<Finding> getFindings() {
    return findings;
  }

  /**
   * @return {@code true} nếu có ít nhất một vấn đề được phát hiện
   */
  public boolean hasIssues() {
    return !findings.isEmpty();
  }
}