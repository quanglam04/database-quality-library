package com.dbquality.report;

import com.dbquality.collector.SQLRecord;
import com.dbquality.explain.ExplainResult;

/**
 * Kết hợp một slow query record với kết quả EXPLAIN tương ứng.
 * Được dùng để hiển thị trên dashboard tab Overview và endpoint /slow-queries.
 */
public class SlowQueryReport {

  private final SQLRecord record;
  private final ExplainResult explainResult;

  /**
   * @param record        SQL record của câu query chậm
   * @param explainResult kết quả EXPLAIN, hoặc {@code null} nếu không hỗ trợ hoặc EXPLAIN thất bại
   */
  public SlowQueryReport(SQLRecord record, ExplainResult explainResult) {
    this.record = record;
    this.explainResult = explainResult;
  }

  /** @return SQL record gốc của câu query chậm */
  public SQLRecord getRecord() { return record; }

  /** @return kết quả EXPLAIN, hoặc {@code null} nếu không có */
  public ExplainResult getExplainResult() { return explainResult; }

  /** @return {@code true} nếu có kết quả EXPLAIN */
  public boolean hasExplain() { return explainResult != null; }
}