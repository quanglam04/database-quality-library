package com.dbquality.rule;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.QueryMetricsStore;
import com.dbquality.collector.SQLContext;
import com.dbquality.constant.Severity;

/**
 * Marker interface cho các rule đã migrate sang dùng {@link QueryMetricsStore}
 * thay vì {@link SQLContext}.
 *
 * <p>Thư viện hỗ trợ cả 2 loại rule:</p>
 * <ul>
 *   <li>Rule cũ implement {@link Rule} — nhận {@code DDLContext + SQLContext}</li>
 *   <li>Rule mới implement {@link MetricsBasedRule} — nhận {@code DDLContext + QueryMetricsStore}</li>
 * </ul>
 *
 * <p>{@link RuleEngine} sẽ detect và gọi đúng method tương ứng.</p>
 */
public interface MetricsBasedRule {

  /**
   * Analyze schema + metrics để phát hiện vấn đề.
   *
   * @param ddl cấu trúc database
   * @param metrics aggregated metrics (count, duration) per SQL pattern
   * @return danh sách findings
   */
  RuleResult analyze(DDLContext ddl, QueryMetricsStore metrics);

  String getName();

  Severity getSeverity();
}