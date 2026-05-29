package com.dbquality.collector.model;

import java.util.List;

/**
 * Đại diện cho một index trong bảng.
 */
public class Index {

  private String name;
  private List<String> columns;
  private boolean unique;

  public Index(String name, List<String> columns, boolean unique) {
    this.name = name;
    this.columns = columns;
    this.unique = unique;
  }

  // Getters
  public String getName() { return name; }
  public List<String> getColumns() { return columns; }
  public boolean isUnique() { return unique; }
}