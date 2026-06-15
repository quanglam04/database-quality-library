package com.dbquality.constant;

/**
 * Mức độ nghiêm trọng của một vấn đề được phát hiện bởi Rule Engine.
 * Dùng để phân loại findings và tính điểm chất lượng tổng thể ({@link com.dbquality.report.ReportBuilder}).
 *
 * <p>Thứ tự ưu tiên: {@code CRITICAL > HIGH > MEDIUM > WARNING}.</p>
 */
public enum Severity {

  /**
   * Vấn đề nghiêm trọng — ảnh hưởng tính toàn vẹn dữ liệu hoặc gây lỗi hệ thống.
   * Ví dụ: bảng không có Primary Key. Cần xử lý ngay trước khi lên production.
   * Trừ tối đa 60 điểm trong scoring.
   */
  CRITICAL,

  /**
   * Vấn đề ảnh hưởng đáng kể đến hiệu năng.
   * Ví dụ: Foreign Key không có index, N+1 query, slow query vượt ngưỡng.
   * Trừ tối đa 30 điểm trong scoring.
   */
  HIGH,

  /**
   * Vấn đề nên cải thiện để tối ưu hoá.
   * Ví dụ: dùng {@code SELECT *}, thiếu index suggestion.
   * Trừ tối đa 15 điểm trong scoring.
   */
  MEDIUM,

  /**
   * Vấn đề cần theo dõi, không khẩn cấp.
   * Ví dụ: cột nullable trong WHERE, index không được dùng trong session, kiểu dữ liệu đáng ngờ.
   * Trừ tối đa 5 điểm trong scoring.
   */
  WARNING
}