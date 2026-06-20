package com.dbquality.constant;

/**
 * Mức độ nghiêm trọng của một vấn đề được phát hiện bởi Rule Engine.
 * Dùng để phân loại findings và tính điểm chất lượng tổng thể ({@link com.dbquality.report.ReportBuilder}).
 *
 * <p>Thứ tự ưu tiên: {@code CRITICAL > HIGH > MEDIUM > WARNING}.</p>
 */


public enum Severity {

  /** Ảnh hưởng tính toàn vẹn dữ liệu hoặc gây lỗi hệ thống. Ví dụ: thiếu Primary Key. */
  CRITICAL(20, 60),

  /** Ảnh hưởng đáng kể đến hiệu năng. Ví dụ: FK không index, N+1 query, slow query. */
  HIGH(10, 30),

  /** Nên cải thiện để tối ưu. Ví dụ: SELECT *, thiếu index suggestion. */
  MEDIUM(3, 15),

  /** Cần theo dõi, không khẩn cấp. Ví dụ: nullable trong WHERE, kiểu dữ liệu đáng ngờ. */
  WARNING(1, 5);

  private final int weight;
  private final int maxDeduction;

  Severity(int weight, int maxDeduction) {
    this.weight = weight;
    this.maxDeduction = maxDeduction;
  }

  /** @return số điểm trừ cho mỗi finding ở severity này */
  public int getWeight() { return weight; }

  /** @return tổng điểm trừ tối đa cho severity này */
  public int getMaxDeduction() { return maxDeduction; }
}