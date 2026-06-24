package com.dbquality.explain;

/**
 * Phân tích kết quả EXPLAIN từ một nhà cung cấp cơ sở dữ liệu cụ thể.<br>
 * Mỗi cơ sở dữ liệu có một định dạng EXPLAIN khác nhau, vì vậy mỗi nhà cung cấp cần một parser riêng.<br>
 * Nếu không có parser nào hỗ trợ cơ sở dữ liệu hiện tại, việc phân tích Execution Plan sẽ được bỏ qua một cách âm thầm.<br>
 */
public interface ExplainParser {

  /**
   * Phân tích kết quả EXPLAIN thô thành một kết quả có cấu trúc.
   *
   * @param explainOutput  chuỗi thô được trả về bởi câu lệnh EXPLAIN
   * @return               kết quả đã được phân tích chứa các findings (quét toàn bảng, thiếu index, v.v.)
   */
  ExplainResult parse(String explainOutput);

  /**
   * Kiểm tra xem parser này có hỗ trợ cơ sở dữ liệu được cung cấp hay không.
   * Tên được lấy từ {@code DatabaseMetaData.getDatabaseProductName()}.
   *
   * @param databaseProductName  ví dụ: "MySQL", "PostgreSQL", "MariaDB", "Microsoft SQL Server"
   * @return                     true nếu parser này có thể xử lý cơ sở dữ liệu được cung cấp
   */
  boolean supports(String databaseProductName);
}