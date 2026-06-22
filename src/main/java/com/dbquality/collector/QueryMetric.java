package com.dbquality.collector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregated metrics cho một unique SQL pattern.
 * Thread-safe để hot path (interceptor) có thể update concurrent.
 *
 * <p>Thay vì lưu từng SQLRecord rời rạc, mọi lần SQL chạy sẽ update
 * vào QueryMetric tương ứng với pattern đó. Giúp giảm memory footprint
 * và cho phép rule engine phân tích nhanh dựa trên metrics aggregated.</p>
 */
public class QueryMetric {

  private final String sqlPattern;
  private final AtomicLong callCount = new AtomicLong(0);
  private final AtomicLong totalDurationMs = new AtomicLong(0);
  private volatile long minDurationMs = Long.MAX_VALUE;
  private volatile long maxDurationMs = 0;
  private volatile long lastSeenAt = System.currentTimeMillis();

  /** Track số lần gọi từ mỗi calledFrom — dùng để lấy phổ biến nhất */
  private final Map<String, AtomicLong> calledFromCounts = new ConcurrentHashMap<>();

  public QueryMetric(String sqlPattern) {
    this.sqlPattern = sqlPattern;
  }

  /**
   * Ghi nhận một lần thực thi SQL với duration và calledFrom.
   * Hot path — phải nhanh, thread-safe.
   */
  public synchronized void record(long durationMs, String calledFrom) {
    callCount.incrementAndGet();
    totalDurationMs.addAndGet(durationMs);
    if (durationMs < minDurationMs) minDurationMs = durationMs;
    if (durationMs > maxDurationMs) maxDurationMs = durationMs;
    lastSeenAt = System.currentTimeMillis();

    if (calledFrom != null) {
      calledFromCounts.computeIfAbsent(calledFrom, k -> new AtomicLong(0))
          .incrementAndGet();
    }
  }


  public String getSqlPattern() { return sqlPattern; }

  public long getCallCount() { return callCount.get(); }

  public long getTotalDurationMs() { return totalDurationMs.get(); }

  public long getMinDurationMs() {
    return minDurationMs == Long.MAX_VALUE ? 0 : minDurationMs;
  }

  public long getMaxDurationMs() { return maxDurationMs; }

  public double getAvgDurationMs() {
    long count = callCount.get();
    return count == 0 ? 0 : (double) totalDurationMs.get() / count;
  }

  public long getLastSeenAt() { return lastSeenAt; }

  public Map<String, AtomicLong> getCalledFromCounts() { return calledFromCounts; }

  /**
   * @return calledFrom xuất hiện nhiều nhất — vị trí code chính gây ra pattern này
   */
  public String getMostFrequentCalledFrom() {
    return calledFromCounts.entrySet().stream()
        .max((e1, e2) -> Long.compare(e1.getValue().get(), e2.getValue().get()))
        .map(Map.Entry::getKey)
        .orElse("unknown");
  }
}