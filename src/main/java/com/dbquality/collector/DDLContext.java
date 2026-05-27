package com.dbquality.collector;

import com.dbquality.collector.model.Column;
import com.dbquality.collector.model.Table;
import java.util.List;

/**
 * Chứa toàn bộ thông tin cấu trúc database (DDL).
 * Được thu thập một lần khi thư viện khởi động qua DatabaseMetaData.
 */
public class DDLContext {

  private final List<Table> tables;

  public DDLContext(List<Table> tables) {
    this.tables = tables;
  }

  public List<Table> getTables() {
    return tables;
  }

  /**
   * Kiểm tra một cột có index không.
   *
   * @param table  bảng cần kiểm tra
   * @param column cột cần kiểm tra
   * @return true nếu cột có index
   */
  public boolean hasIndexOn(Table table, Column column) {
    return table.getIndexes().stream()
        .anyMatch(index -> index.getColumns().contains(column.getName()));
  }
}