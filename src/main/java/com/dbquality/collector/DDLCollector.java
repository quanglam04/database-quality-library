package com.dbquality.collector;

import com.dbquality.collector.model.Column;
import com.dbquality.collector.model.ForeignKey;
import com.dbquality.collector.model.Index;
import com.dbquality.collector.model.Table;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Thu thập cấu trúc database thông qua JDBC DatabaseMetaData.
 * Được gọi một lần khi thư viện khởi động.
 * Kết quả lưu vào DDLContext để Rule Engine sử dụng.
 */
public class DDLCollector {

  /**
   * Thu thập toàn bộ cấu trúc database từ connection hiện tại.
   *
   * @param connection JDBC connection để lấy metadata
   * @return DDLContext chứa toàn bộ thông tin schema
   * @throws SQLException nếu không thể đọc metadata
   */
  public DDLContext collect(Connection connection) throws SQLException {
    DatabaseMetaData meta = connection.getMetaData();
    String catalog = connection.getCatalog();
    String schema = getSchema(connection);

    List<Table> tables = collectTables(meta, catalog, schema);
    return new DDLContext(tables);
  }

  //  Thu thập danh sách tables

  private List<Table> collectTables(DatabaseMetaData meta,
      String catalog,
      String schema) throws SQLException {
    List<Table> tables = new ArrayList<>();

    try (ResultSet rs = meta.getTables(catalog, schema, "%",
        new String[]{"TABLE"})) {
      while (rs.next()) {
        String tableName = rs.getString("TABLE_NAME");
        List<Column> columns   = collectColumns(meta, catalog, schema, tableName);
        List<Index> indexes    = collectIndexes(meta, catalog, schema, tableName);
        List<ForeignKey> fks   = collectForeignKeys(meta, catalog, schema, tableName);
        tables.add(new Table(tableName, columns, indexes, fks));
      }
    }
    return tables;
  }

  //  Thu thập columns

  private List<Column> collectColumns(DatabaseMetaData meta,
      String catalog,
      String schema,
      String tableName) throws SQLException {
    List<Column> columns = new ArrayList<>();
    List<String> pkColumns = collectPrimaryKeyColumns(meta, catalog, schema, tableName);

    try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
      while (rs.next()) {
        String columnName = rs.getString("COLUMN_NAME");
        String typeName   = rs.getString("TYPE_NAME");
        boolean nullable  = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
        boolean isPk      = pkColumns.contains(columnName);
        columns.add(new Column(columnName, typeName, nullable, isPk));
      }
    }
    return columns;
  }

  //  Thu thập primary key columns

  private List<String> collectPrimaryKeyColumns(DatabaseMetaData meta,
      String catalog,
      String schema,
      String tableName) throws SQLException {
    List<String> pkColumns = new ArrayList<>();
    try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, tableName)) {
      while (rs.next()) {
        pkColumns.add(rs.getString("COLUMN_NAME"));
      }
    }
    return pkColumns;
  }

  //  Thu thập indexes

  private List<Index> collectIndexes(DatabaseMetaData meta,
      String catalog,
      String schema,
      String tableName) throws SQLException {
    List<Index> indexes = new ArrayList<>();
    List<String> currentIndexColumns = new ArrayList<>();
    String currentIndexName = null;
    boolean currentUnique = false;

    try (ResultSet rs = meta.getIndexInfo(catalog, schema, tableName, false, false)) {
      while (rs.next()) {
        String indexName = rs.getString("INDEX_NAME");
        if (indexName == null) continue; // bỏ qua table statistics

        String columnName = rs.getString("COLUMN_NAME");
        boolean unique    = !rs.getBoolean("NON_UNIQUE");

        if (!indexName.equals(currentIndexName)) {
          // Lưu index trước nếu có
          if (currentIndexName != null) {
            indexes.add(new Index(currentIndexName,
                new ArrayList<>(currentIndexColumns), currentUnique));
          }
          currentIndexName = indexName;
          currentUnique = unique;
          currentIndexColumns = new ArrayList<>();
        }
        currentIndexColumns.add(columnName);
      }
      // Lưu index cuối cùng
      if (currentIndexName != null) {
        indexes.add(new Index(currentIndexName,
            new ArrayList<>(currentIndexColumns), currentUnique));
      }
    }
    return indexes;
  }

  //  Thu thập foreign keys

  private List<ForeignKey> collectForeignKeys(DatabaseMetaData meta,
      String catalog,
      String schema,
      String tableName) throws SQLException {
    List<ForeignKey> fks = new ArrayList<>();

    try (ResultSet rs = meta.getImportedKeys(catalog, schema, tableName)) {
      while (rs.next()) {
        String fkName      = rs.getString("FK_NAME");
        String fkColumn    = rs.getString("FKCOLUMN_NAME");
        String pkTable     = rs.getString("PKTABLE_NAME");
        String pkColumn    = rs.getString("PKCOLUMN_NAME");
        fks.add(new ForeignKey(fkName, fkColumn, pkTable, pkColumn));
      }
    }
    return fks;
  }

  //  Helper

  private String getSchema(Connection connection) {
    try {
      return connection.getSchema();
    } catch (Exception e) {
      return null; // một số DB không support getSchema()
    }
  }
}