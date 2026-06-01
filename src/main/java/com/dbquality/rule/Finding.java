package com.dbquality.rule;

/**
 * Đại diện cho một vấn đề được phát hiện bởi một rule.
 *
 * <p>Mỗi {@code Finding} chứa đầy đủ thông tin để người dùng hiểu vấn đề là gì,
 * nằm ở đâu trong code/schema, và cách khắc phục. Dùng Builder pattern để tạo instance.</p>
 *
 */
public class Finding {

  private String rule;
  private Severity severity;
  private String table;
  private String column;
  private String message;
  private String recommendation;
  private String calledFrom;

  private Finding() {}

  /**
   * @return Builder để tạo instance Finding mới
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder cho {@link Finding}.
   */
  public static class Builder {
    private final Finding finding = new Finding();

    /**
     * @param rule tên rule tạo ra finding này, dạng UPPER_SNAKE_CASE (ví dụ: {@code "MISSING_PRIMARY_KEY"})
     */
    public Builder rule(String rule) { finding.rule = rule; return this; }

    /**
     * @param severity mức độ nghiêm trọng của vấn đề
     */
    public Builder severity(Severity severity) { finding.severity = severity; return this; }

    /**
     * @param table tên bảng liên quan (nếu có); {@code null} nếu finding không gắn với bảng cụ thể
     */
    public Builder table(String table) { finding.table = table; return this; }

    /**
     * @param column tên cột liên quan (nếu có); {@code null} nếu finding không gắn với cột cụ thể
     */
    public Builder column(String column) { finding.column = column; return this; }

    /**
     * @param message mô tả ngắn gọn vấn đề — hiển thị trên dashboard và report
     */
    public Builder message(String message) { finding.message = message; return this; }

    /**
     * @param recommendation hướng dẫn khắc phục — có thể bao gồm SQL mẫu cụ thể
     */
    public Builder recommendation(String recommendation) { finding.recommendation = recommendation; return this; }

    /**
     * @param calledFrom stack frame của code nghiệp vụ gọi SQL gây ra finding này;
     *                   {@code "Schema analysis — no call site"} với các DDL findings
     */
    public Builder calledFrom(String calledFrom) {
      finding.calledFrom = calledFrom;
      return this;
    }

    public Finding build() { return finding; }
  }
  public String getRule() { return rule; }
  public Severity getSeverity() { return severity; }
  public String getTable() { return table; }
  public String getColumn() { return column; }
  public String getMessage() { return message; }
  public String getRecommendation() { return recommendation; }
  public String getCalledFrom() { return calledFrom; }
}