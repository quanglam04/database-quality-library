package com.dbquality.rule;

/**
 * Đại diện cho một vấn đề được phát hiện bởi một rule.
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

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final Finding finding = new Finding();

    public Builder rule(String rule) { finding.rule = rule; return this; }
    public Builder severity(Severity severity) { finding.severity = severity; return this; }
    public Builder table(String table) { finding.table = table; return this; }
    public Builder column(String column) { finding.column = column; return this; }
    public Builder message(String message) { finding.message = message; return this; }
    public Builder recommendation(String recommendation) { finding.recommendation = recommendation; return this; }
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