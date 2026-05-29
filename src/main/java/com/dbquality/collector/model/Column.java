package com.dbquality.collector.model;

/**
 * Đại diện cho một cột trong bảng.
 */
public class Column {

  private String name;
  private String type;
  private boolean nullable;
  private boolean primaryKey;

  public Column(String name, String type, boolean nullable, boolean primaryKey) {
    this.name = name;
    this.type = type;
    this.nullable = nullable;
    this.primaryKey = primaryKey;
  }

  // Getters
  public String getName() { return name; }
  public String getType() { return type; }
  public boolean isNullable() { return nullable; }
  public boolean isPrimaryKey() { return primaryKey; }
}