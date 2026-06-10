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
 * <p>Chia thời gian thành các bucket (mặc định 30 giây mỗi bucket).
 * Mỗi bucket lưu latency values của các SQL queries trong khoảng thời gian đó.
 * Dùng để vẽ trend chart trên dashboard — thấy latency tăng/giảm theo thời gian.</p>
 *
 * <p>Ví dụ với bucket size 30s:</p>
 * <pre>
 * 10:00:00 - 10:00:30 → [1ms, 2ms, 5ms] → P99=5ms
 * 10:00:30 - 10:01:00 → [1ms, 1ms, 800ms] → P99=800ms  ← spike!
 * 10:01:00 - 10:01:30 → [1ms, 2ms, 1ms] → P99=2ms
 * </pre>
 */
public class MetricsCollector {

  private final long bucketSizeSeconds;
  private final LatencyCalculator calculator;

  // Key: bucket start time (epoch seconds), Value: list of latencies in that bucket
  private final Map<Long, List<Long>> buckets = new ConcurrentHashMap<>();

  /**
   * Tạo MetricsCollector với bucket size mặc định 30 giây.
   */
  public MetricsCollector() {
    this(30);
  }

  /**
   * Tạo MetricsCollector với bucket size tùy chỉnh.
   *
   * @param bucketSizeSeconds kích thước mỗi time bucket tính bằng giây
   */
  public MetricsCollector(long bucketSizeSeconds) {
    this.bucketSizeSeconds = bucketSizeSeconds;
    this.calculator = new LatencyCalculator();
  }

  /**
   * Thêm một SQL record vào bucket tương ứng với timestamp của nó.
   *
   * @param record SQL record đã được intercept
   */
  public void record(SQLRecord record) {
    if (record == null || record.getTimestamp() == null) return;
    long bucketKey = toBucketKey(record.getTimestamp());
    buckets.computeIfAbsent(bucketKey, k -> new ArrayList<>())
        .add(record.getExecutionTime());
  }

  /**
   * Lấy tất cả time buckets với P99 latency của từng bucket,
   * sắp xếp theo thời gian tăng dần.
   *
   * @return list các {@link BucketMetrics}, mỗi item là 1 data point trên trend chart
   */
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

  /**
   * Xóa toàn bộ dữ liệu — dùng khi reset session.
   */
  public void clear() {
    buckets.clear();
  }

  /**
   * @return tổng số bucket hiện có
   */
  public int getBucketCount() {
    return buckets.size();
  }

  private long toBucketKey(Instant timestamp) {
    long epochSeconds = timestamp.getEpochSecond();
    return (epochSeconds / bucketSizeSeconds) * bucketSizeSeconds;
  }

  /**
   * Metrics tổng hợp của một time bucket.
   */
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

    /** @return thời điểm bắt đầu của bucket */
    public Instant getBucketStart() { return bucketStart; }
    public long getP50() { return p50; }
    public long getP95() { return p95; }
    public long getP99() { return p99; }
    public int getQueryCount() { return queryCount; }
  }
}