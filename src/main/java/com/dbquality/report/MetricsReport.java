package com.dbquality.report;

import java.util.Map;

/**
 * Chứa các metrics được tính toán từ SQL records trong session.
 * Bao gồm latency percentiles, error rate, và thống kê theo bảng.
 */
public class MetricsReport {

  private int totalSQLIntercepted;
  private int slowQueryCount;
  private int nPlusOneDetected;
  private long p50Latency;
  private long p95Latency;
  private long p99Latency;
  private double errorRate;
  private Map<String, Integer> topTablesByQueryFrequency;

  private MetricsReport() {}

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final MetricsReport report = new MetricsReport();

    public Builder totalSQLIntercepted(int v) { report.totalSQLIntercepted = v; return this; }
    public Builder slowQueryCount(int v) { report.slowQueryCount = v; return this; }
    public Builder nPlusOneDetected(int v) { report.nPlusOneDetected = v; return this; }
    public Builder p50Latency(long v) { report.p50Latency = v; return this; }
    public Builder p95Latency(long v) { report.p95Latency = v; return this; }
    public Builder p99Latency(long v) { report.p99Latency = v; return this; }
    public Builder errorRate(double v) { report.errorRate = v; return this; }
    public Builder topTablesByQueryFrequency(Map<String, Integer> v) { report.topTablesByQueryFrequency = v; return this; }
    public MetricsReport build() { return report; }
  }

  // Getters
  public int getTotalSQLIntercepted() { return totalSQLIntercepted; }
  public int getSlowQueryCount() { return slowQueryCount; }
  public int getNPlusOneDetected() { return nPlusOneDetected; }
  public long getP50Latency() { return p50Latency; }
  public long getP95Latency() { return p95Latency; }
  public long getP99Latency() { return p99Latency; }
  public double getErrorRate() { return errorRate; }
  public Map<String, Integer> getTopTablesByQueryFrequency() { return topTablesByQueryFrequency; }
}