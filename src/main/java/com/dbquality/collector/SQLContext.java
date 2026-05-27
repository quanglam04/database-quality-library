package com.dbquality.collector;

import java.util.ArrayList;
import java.util.List;

/**
 * Tổng hợp toàn bộ SQL records đã được thu thập trong session hiện tại.
 * Rule Engine đọc từ đây để phân tích chất lượng.
 * Dữ liệu được lưu in-memory và reset khi ứng dụng restart.
 */
public class SQLContext {

  private final List<SQLRecord> records = new ArrayList<>();

  /**
   * Thêm một SQL record vào context.
   *
   * @param record SQL record vừa được intercept
   */
  public void add(SQLRecord record) {
    records.add(record);
  }

  /**
   * @return toàn bộ SQL records trong session hiện tại
   */
  public List<SQLRecord> getRecords() {
    return records;
  }

  /**
   * @return số lượng SQL đã được intercept
   */
  public int size() {
    return records.size();
  }

  /**
   * Xóa toàn bộ records — thường dùng khi reset session.
   */
  public void clear() {
    records.clear();
  }
}