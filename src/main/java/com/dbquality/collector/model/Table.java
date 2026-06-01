package com.dbquality.collector.model;

import java.util.List;

/**
 * Đại diện cho một bảng trong database.
 * Thu thập từ {@code DatabaseMetaData.getTables()} cùng columns, indexes, và foreign keys.
 */
public class Table {

  private String name;
  private List<Column> columns;
  private List<Index> indexes;
  private List<ForeignKey> foreignKeys;

  /**
   * @param name        tên bảng, ví dụ {@code "orders"}
   * @param columns     danh sách tất cả cột trong bảng
   * @param indexes     danh sách tất cả index trong bảng (bao gồm Primary Key index)
   * @param foreignKeys danh sách tất cả Foreign Key constraints trong bảng
   */
  public Table(String name, List<Column> columns,
      List<Index> indexes, List<ForeignKey> foreignKeys) {
    this.name = name;
    this.columns = columns;
    this.indexes = indexes;
    this.foreignKeys = foreignKeys;
  }

  /**
   * Kiểm tra bảng có Primary Key không.
   *
   * @return true nếu có ít nhất một cột là primary key
   */
  public boolean hasPrimaryKey() {
    return columns.stream().anyMatch(Column::isPrimaryKey);
  }

  public String getName() { return name; }
  public List<Column> getColumns() { return columns; }
  public List<Index> getIndexes() { return indexes; }
  public List<ForeignKey> getForeignKeys() { return foreignKeys; }
}