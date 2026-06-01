package com.dbquality.collector.model;

/**
 * Đại diện cho một cột trong bảng.
 */
public class Column {

  private String name;
  private String type;
  private boolean nullable;
  private boolean primaryKey;

  /**
   * @param name       tên cột, ví dụ {@code "user_id"}
   * @param type       kiểu dữ liệu SQL, ví dụ {@code "VARCHAR"}, {@code "BIGINT"}, {@code "DECIMAL"}
   * @param nullable   {@code true} nếu cột cho phép giá trị NULL
   * @param primaryKey {@code true} nếu cột thuộc Primary Key của bảng
   */
  public Column(String name, String type, boolean nullable, boolean primaryKey) {
    this.name = name;
    this.type = type;
    this.nullable = nullable;
    this.primaryKey = primaryKey;
  }

  public String getName() { return name; }
  public String getType() { return type; }
  public boolean isNullable() { return nullable; }
  public boolean isPrimaryKey() { return primaryKey; }
}