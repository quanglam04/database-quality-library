package com.dbquality.rule;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.constant.Severity;

/**
 * Ngày tạo: 25/05/2026 <br><br>
 * Định nghĩa một rule phân tích chất lượng duy nhất cho tương tác với cơ sở dữ liệu.<br>
 * Mỗi rule sẽ kiểm tra một vấn đề cụ thể (ví dụ: thiếu khóa chính, truy vấn N+1).<br>
 */
public interface Rule {

  /**
   * Phân tích cấu trúc cơ sở dữ liệu và các câu lệnh SQL khi chạy để phát hiện vấn đề.
   *
   * @param ddl  ngữ cảnh cấu trúc cơ sở dữ liệu (bảng, cột, chỉ mục, khóa ngoại)
   * @param sql  ngữ cảnh SQL thời gian chạy (các truy vấn được ghi nhận trong phiên hiện tại)
   * @return     kết quả phân tích bao gồm các phát hiện và khuyến nghị
   */
  RuleResult analyze(DDLContext ddl, SQLContext sql);

  /**
   * @return định danh của rule theo định dạng UPPER_SNAKE_CASE
   *         (ví dụ: "MISSING_PRIMARY_KEY")
   */
  String getName();

  /**
   * @return mức độ nghiêm trọng mặc định:
   *         CRITICAL, HIGH, MEDIUM hoặc WARNING
   */
  Severity getSeverity();
}