package com.dbquality.metrics;

import com.dbquality.collector.SQLRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thu thập và tổng hợp metrics theo time bucket.
 *
 * <p>Sau Phase 3 refactor, hỗ trợ 2 cách ghi:</p>
 * <ul>
 *   <li>{@link #record(SQLRecord)} — legacy, per-call</li>
 *   <li>{@link #recordSnapshot(String, double, long, long)} — từ aggregated metrics</li>
 * </ul>
 */
public class MetricsCollector {

  private final long bucketSizeSeconds;
  private final LatencyCalculator calculator;
  private final Map<Long, List<Long>> buckets = new ConcurrentHashMap<>();

  public MetricsCollector() {
    this(30);
  }

  public MetricsCollector(long bucketSizeSeconds) {
    this.bucketSizeSeconds = bucketSizeSeconds;
    this.calculator = new LatencyCalculator();
  }

  /**
   * Ghi nhận từng SQLRecord (legacy).
   */
  public void record(SQLRecord record) {
    if (record == null || record.getTimestamp() == null) return;
    long bucketKey = toBucketKey(record.getTimestamp());
    buckets.computeIfAbsent(bucketKey, k -> new ArrayList<>())
        .add(record.getExecutionTime());
  }

  /**
   * Ghi nhận từ aggregated QueryMetric.
   * Mỗi metric tạo {@code callCount} entry trong bucket tương ứng với
   * {@code lastSeenAt} — approximation để trend chart vẫn hoạt động khi
   * không có per-call timestamp.
   *
   * @param sqlPattern    SQL pattern (chỉ để debug, không lưu)
   * @param avgDurationMs duration trung bình
   * @param lastSeenAt    epoch millis của lần cuối SQL được chạy
   * @param callCount     số lần chạy
   */
  public void recordSnapshot(String sqlPattern, double avgDurationMs,
      long lastSeenAt, long callCount) {
    if (lastSeenAt <= 0) return;
    Instant timestamp = Instant.ofEpochMilli(lastSeenAt);
    long bucketKey = toBucketKey(timestamp);
    long duration = (long) avgDurationMs;

    List<Long> bucket = buckets.computeIfAbsent(bucketKey, k -> new ArrayList<>());
    // Add callCount entries với duration = avg
    for (long i = 0; i < callCount; i++) {
      bucket.add(duration);
    }
  }

  public List<BucketMetrics> getBucketMetrics() {
    return buckets.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> new BucketMetrics(
            Instant.ofEpochSecond(entry.getKey()),
            calculator.p50(entry.getValue()),
            calculator.p95(entry.getValue()),
            calculator.p99(entry.getValue()),
            entry.getValue().size()
        ))
        .collect(Collectors.toList());
  }

  public void clear() {
    buckets.clear();
  }

  public int getBucketCount() {
    return buckets.size();
  }

  private long toBucketKey(Instant timestamp) {
    long epochSeconds = timestamp.getEpochSecond();
    return (epochSeconds / bucketSizeSeconds) * bucketSizeSeconds;
  }

  public static class BucketMetrics {
    private final Instant bucketStart;
    private final long p50;
    private final long p95;
    private final long p99;
    private final int queryCount;

    public BucketMetrics(Instant bucketStart, long p50, long p95,
        long p99, int queryCount) {
      this.bucketStart = bucketStart;
      this.p50 = p50;
      this.p95 = p95;
      this.p99 = p99;
      this.queryCount = queryCount;
    }

    public Instant getBucketStart() { return bucketStart; }
    public long getP50() { return p50; }
    public long getP95() { return p95; }
    public long getP99() { return p99; }
    public int getQueryCount() { return queryCount; }
  }
}