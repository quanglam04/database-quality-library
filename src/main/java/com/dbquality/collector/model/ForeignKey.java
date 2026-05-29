package com.dbquality.collector.model;

/**
 * Đại diện cho một foreign key trong bảng.
 */
public class ForeignKey {

  private String name;
  private String column;
  private String referencedTable;
  private String referencedColumn;

  public ForeignKey(String name, String column,
      String referencedTable, String referencedColumn) {
    this.name = name;
    this.column = column;
    this.referencedTable = referencedTable;
    this.referencedColumn = referencedColumn;
  }

  // Getters
  public String getName() { return name; }
  public String getColumn() { return column; }
  public String getReferencedTable() { return referencedTable; }
  public String getReferencedColumn() { return referencedColumn; }
}