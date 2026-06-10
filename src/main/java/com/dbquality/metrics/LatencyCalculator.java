package com.dbquality.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tính toán latency percentiles từ danh sách execution times.
 *
 * <p>Hỗ trợ tính P50, P95, P99 và average cho cả toàn session
 * lẫn từng time bucket cụ thể.</p>
 */
public class LatencyCalculator {

  /**
   * Tính percentile từ danh sách latency values.
   *
   * @param latencies danh sách execution times (ms), không cần sắp xếp trước
   * @param percentile giá trị percentile từ 0.0 đến 100.0 (ví dụ: 50.0, 95.0, 99.0)
   * @return giá trị latency tại percentile đó, hoặc 0 nếu list rỗng
   */
  public long calculate(List<Long> latencies, double percentile) {
    if (latencies == null || latencies.isEmpty()) return 0;

    List<Long> sorted = new ArrayList<>(latencies);
    Collections.sort(sorted);

    if (percentile <= 0)   return sorted.get(0);
    if (percentile >= 100) return sorted.get(sorted.size() - 1);

    // Nearest rank method
    int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
    return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
  }

  /**
   * Tính P50 (median).
   *
   * @param latencies danh sách execution times
   * @return P50 latency tính bằng milliseconds
   */
  public long p50(List<Long> latencies) {
    return calculate(latencies, 50.0);
  }

  /**
   * Tính P95.
   *
   * @param latencies danh sách execution times
   * @return P95 latency tính bằng milliseconds
   */
  public long p95(List<Long> latencies) {
    return calculate(latencies, 95.0);
  }

  /**
   * Tính P99.
   *
   * @param latencies danh sách execution times
   * @return P99 latency tính bằng milliseconds
   */
  public long p99(List<Long> latencies) {
    return calculate(latencies, 99.0);
  }

  /**
   * Tính average latency.
   *
   * @param latencies danh sách execution times
   * @return average latency tính bằng milliseconds, hoặc 0 nếu list rỗng
   */
  public long average(List<Long> latencies) {
    if (latencies == null || latencies.isEmpty()) return 0;
    return (long) latencies.stream()
        .mapToLong(Long::longValue)
        .average()
        .orElse(0);
  }
}