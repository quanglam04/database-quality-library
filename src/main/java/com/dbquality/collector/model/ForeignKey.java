package com.dbquality.collector.model;

/**
 * Đại diện cho một foreign key trong bảng.
 */
public class ForeignKey {

  private String name;
  private String column;
  private String referencedTable;
  private String referencedColumn;

  /**
   * @param name             tên FK constraint, ví dụ {@code "fk_order_user"}
   * @param column           tên cột giữ FK trong bảng hiện tại, ví dụ {@code "user_id"}
   * @param referencedTable  tên bảng được tham chiếu, ví dụ {@code "users"}
   * @param referencedColumn tên cột được tham chiếu, ví dụ {@code "id"}
   */
  public ForeignKey(String name, String column,
      String referencedTable, String referencedColumn) {
    this.name = name;
    this.column = column;
    this.referencedTable = referencedTable;
    this.referencedColumn = referencedColumn;
  }

  public String getName() { return name; }
  public String getColumn() { return column; }
  public String getReferencedTable() { return referencedTable; }
  public String getReferencedColumn() { return referencedColumn; }
}