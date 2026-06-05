package com.dbquality.report;

import com.dbquality.collector.SQLRecord;
import com.dbquality.rule.Finding;
import java.time.Instant;
import java.util.List;

/**
 * Report đầy đủ sau khi phân tích xong.
 * Tổng hợp toàn bộ findings từ DDL, SQL runtime, metrics, và AI insights.
 */
public class QualityReport {

  private Instant reportGeneratedAt;
  private String appName;
  private int overallScore;
  private List<Finding> ddlFindings;
  private List<Finding> sqlFindings;
  private List<SlowQueryReport> slowQueries;
  private MetricsReport metrics;
  private String aiReadyContext;
  private String aiInsights;

  private QualityReport() {}

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final QualityReport report = new QualityReport();

    public Builder reportGeneratedAt(Instant v) { report.reportGeneratedAt = v; return this; }
    public Builder appName(String v) { report.appName = v; return this; }
    public Builder overallScore(int v) { report.overallScore = v; return this; }
    public Builder ddlFindings(List<Finding> v) { report.ddlFindings = v; return this; }
    public Builder sqlFindings(List<Finding> v) { report.sqlFindings = v; return this; }
    public Builder slowQueries(List<SlowQueryReport> v) { report.slowQueries = v; return this; }
    public Builder metrics(MetricsReport v) { report.metrics = v; return this; }
    public Builder aiReadyContext(String v) { report.aiReadyContext = v; return this; }
    public Builder aiInsights(String v) { report.aiInsights = v; return this; }
    public QualityReport build() { return report; }
  }

  public Instant getReportGeneratedAt() { return reportGeneratedAt; }
  public String getAppName() { return appName; }
  public int getOverallScore() { return overallScore; }
  public List<Finding> getDdlFindings() { return ddlFindings; }
  public List<Finding> getSqlFindings() { return sqlFindings; }
  public List<SlowQueryReport> getSlowQueries() { return slowQueries; }
  public MetricsReport getMetrics() { return metrics; }
  public String getAiReadyContext() { return aiReadyContext; }
  public String getAiInsights() { return aiInsights; }
}