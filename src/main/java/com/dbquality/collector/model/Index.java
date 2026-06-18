package com.dbquality.collector.model;

import java.util.List;

/**
 * Đại diện cho một index trong bảng database.
 * Thu thập từ {@code DatabaseMetaData.getIndexInfo()}.
 *
 * <p>Index có thể là composite (nhiều cột). Primary Key index thường có tên chứa
 * {@code "PRIMARY"} hoặc bắt đầu bằng {@code "PK_"}.</p>
 */
public class Index {

  private String name;
  private List<String> columns;
  private boolean unique;

  /**
   * @param name    tên index, ví dụ {@code "idx_order_user_id"}
   * @param columns danh sách tên cột thuộc index theo thứ tự (composite index có nhiều cột)
   * @param unique  {@code true} nếu là unique index
   */
  public Index(String name, List<String> columns, boolean unique) {
    this.name = name;
    this.columns = columns;
    this.unique = unique;
  }

  public String getName() { return name; }
  public List<String> getColumns() { return columns; }
  public boolean isUnique() { return unique; }
}