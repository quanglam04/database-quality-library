package com.dbquality.collector;

import java.time.Instant;
import java.util.Map;

/**
 * Đại diện cho một lần thực thi SQL.
 * Được tạo ra mỗi khi một câu SQL được intercept qua QualityDataSource.
 *
 * <p>Mỗi instance chứa đủ thông tin để Rule Engine phân tích:
 * câu SQL gốc, tham số binding, thời gian thực thi, timestamp, stack frame
 * của code nghiệp vụ gọi SQL ({@code calledFrom}), và trạng thái thành công/thất bại.</p>
 *
 * <p>Chỉ DML (SELECT/INSERT/UPDATE/DELETE) được lưu —
 * DDL và system queries bị lọc bỏ.</p>
 */
public class SQLRecord {

  private String sql;
  private Map<Integer, Object> parameters;
  private long executionTime;
  private Instant timestamp;
  private String calledFrom;
  private boolean success;
  private String errorMessage;

  private SQLRecord() {}

  /**
   * @return Builder để tạo instance SQLRecord mới
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder cho {@link SQLRecord}.
   */
  public static class Builder {
    private final SQLRecord record = new SQLRecord();

    public Builder sql(String sql) { record.sql = sql; return this; }
    public Builder parameters(Map<Integer, Object> parameters) { record.parameters = parameters; return this; }
    public Builder executionTime(long executionTime) { record.executionTime = executionTime; return this; }
    public Builder timestamp(Instant timestamp) { record.timestamp = timestamp; return this; }
    public Builder calledFrom(String calledFrom) { record.calledFrom = calledFrom; return this; }
    public Builder success(boolean success) { record.success = success; return this; }
    public Builder errorMessage(String errorMessage) { record.errorMessage = errorMessage; return this; }
    public SQLRecord build() { return record; }
  }

  public String getSql() { return sql; }
  public Map<Integer, Object> getParameters() { return parameters; }
  public long getExecutionTime() { return executionTime; }
  public Instant getTimestamp() { return timestamp; }
  public String getCalledFrom() { return calledFrom; }
  public boolean isSuccess() { return success; }
  public String getErrorMessage() { return errorMessage; }
}