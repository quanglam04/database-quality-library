package com.dbquality.core;

import com.dbquality.collector.SQLContext;
import com.dbquality.collector.SQLRecord;
import com.dbquality.config.QualityConfig;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.time.Instant;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrap PreparedStatement gốc để intercept việc thực thi SQL.
 * Capture SQL text, parameters, execution time, stack trace, và success/failure.
 */
public class QualityPreparedStatement implements PreparedStatement {

  private final PreparedStatement original;
  private final String sql;
  private final SQLContext sqlContext;
  private final QualityConfig config;
  private final Map<Integer, Object> parameters = new HashMap<>();

  public QualityPreparedStatement(PreparedStatement original, String sql,
      SQLContext sqlContext, QualityConfig config) {
    this.original = original;
    this.sql = sql;
    this.sqlContext = sqlContext;
    this.config = config;
  }

  // ── Capture stack trace ───────────────────────────────────────────

  private static final List<String> INTERNAL_PREFIXES = List.of(
      "java.", "javax.", "sun.", "jdk.", "com.sun.",
      "org.junit.", "org.opentest4j.",
      "org.apache.maven.", "org.apache.surefire.",
      "com.intellij.",
      "org.springframework.",
      "com.zaxxer.", "org.apache.commons.dbcp.", "c3p0.",
      "com.mysql.", "org.postgresql.", "org.h2.",
      "com.microsoft.sqlserver.", "org.mariadb.", "org.sqlite.",
      "com.dbquality.core.QualityDataSource.",    // ← thêm dấu . ở cuối
      "com.dbquality.core.QualityConnection.",    // ← thêm dấu . ở cuối
      "com.dbquality.core.QualityPreparedStatement",
      "com.dbquality.collector.",
      "com.dbquality.config."
  );

  private String captureCalledFrom() {
    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
    for (StackTraceElement frame : stack) {
      String className = frame.getClassName();
      boolean isInternal = INTERNAL_PREFIXES.stream()
          .anyMatch(className::startsWith);
      if (!isInternal) {
        return className + ":" + frame.getLineNumber()
            + " -> " + frame.getMethodName() + "()";
      }
    }
    return "unknown";
  }

  // ── Record SQL execution ──────────────────────────────────────────

  private void record(long executionTime, boolean success, String errorMessage) {
    sqlContext.add(SQLRecord.builder()
        .sql(sql)
        .parameters(new HashMap<>(parameters))
        .executionTime(executionTime)
        .timestamp(Instant.now())
        .calledFrom(captureCalledFrom())
        .success(success)
        .errorMessage(errorMessage)
        .build());
  }

  // ── Intercept execute methods ─────────────────────────────────────

  @Override
  public ResultSet executeQuery() throws SQLException {
    long start = System.currentTimeMillis();
    try {
      ResultSet result = original.executeQuery();
      record(System.currentTimeMillis() - start, true, null);
      return result;
    } catch (SQLException e) {
      record(System.currentTimeMillis() - start, false, e.getMessage());
      throw e;
    }
  }

  @Override
  public int executeUpdate() throws SQLException {
    long start = System.currentTimeMillis();
    try {
      int result = original.executeUpdate();
      record(System.currentTimeMillis() - start, true, null);
      return result;
    } catch (SQLException e) {
      record(System.currentTimeMillis() - start, false, e.getMessage());
      throw e;
    }
  }

  @Override
  public boolean execute() throws SQLException {
    long start = System.currentTimeMillis();
    try {
      boolean result = original.execute();
      record(System.currentTimeMillis() - start, true, null);
      return result;
    } catch (SQLException e) {
      record(System.currentTimeMillis() - start, false, e.getMessage());
      throw e;
    }
  }

  // ── Capture parameters ────────────────────────────────────────────

  @Override
  public void setNull(int i, int sqlType) throws SQLException {
    parameters.put(i, null);
    original.setNull(i, sqlType);
  }

  @Override
  public void setBoolean(int i, boolean x) throws SQLException {
    parameters.put(i, x);
    original.setBoolean(i, x);
  }

  @Override
  public void setByte(int i, byte x) throws SQLException {
    parameters.put(i, x);
    original.setByte(i, x);
  }

  @Override
  public void setShort(int i, short x) throws SQLException {
    parameters.put(i, x);
    original.setShort(i, x);
  }

  @Override
  public void setInt(int i, int x) throws SQLException {
    parameters.put(i, x);
    original.setInt(i, x);
  }

  @Override
  public void setLong(int i, long x) throws SQLException {
    parameters.put(i, x);
    original.setLong(i, x);
  }

  @Override
  public void setFloat(int i, float x) throws SQLException {
    parameters.put(i, x);
    original.setFloat(i, x);
  }

  @Override
  public void setDouble(int i, double x) throws SQLException {
    parameters.put(i, x);
    original.setDouble(i, x);
  }

  @Override
  public void setBigDecimal(int i, BigDecimal x) throws SQLException {
    parameters.put(i, x);
    original.setBigDecimal(i, x);
  }

  @Override
  public void setString(int i, String x) throws SQLException {
    parameters.put(i, x);
    original.setString(i, x);
  }

  @Override
  public void setBytes(int i, byte[] x) throws SQLException {
    parameters.put(i, x);
    original.setBytes(i, x);
  }

  @Override
  public void setDate(int i, Date x) throws SQLException {
    parameters.put(i, x);
    original.setDate(i, x);
  }

  @Override
  public void setTime(int i, Time x) throws SQLException {
    parameters.put(i, x);
    original.setTime(i, x);
  }

  @Override
  public void setTimestamp(int i, Timestamp x) throws SQLException {
    parameters.put(i, x);
    original.setTimestamp(i, x);
  }

  @Override
  public void setObject(int i, Object x) throws SQLException {
    parameters.put(i, x);
    original.setObject(i, x);
  }

  @Override
  public void setObject(int i, Object x, int targetSqlType) throws SQLException {
    parameters.put(i, x);
    original.setObject(i, x, targetSqlType);
  }

  @Override public void clearParameters() throws SQLException { original.clearParameters(); }
  @Override public void addBatch() throws SQLException { original.addBatch(); }
  @Override public int[] executeBatch() throws SQLException { return original.executeBatch(); }
  @Override public ResultSetMetaData getMetaData() throws SQLException { return original.getMetaData(); }
  @Override public ParameterMetaData getParameterMetaData() throws SQLException { return original.getParameterMetaData(); }
  @Override public ResultSet getResultSet() throws SQLException { return original.getResultSet(); }
  @Override public int getUpdateCount() throws SQLException { return original.getUpdateCount(); }
  @Override public boolean getMoreResults() throws SQLException { return original.getMoreResults(); }
  @Override public void setFetchDirection(int direction) throws SQLException { original.setFetchDirection(direction); }
  @Override public int getFetchDirection() throws SQLException { return original.getFetchDirection(); }
  @Override public void setFetchSize(int rows) throws SQLException { original.setFetchSize(rows); }
  @Override public int getFetchSize() throws SQLException { return original.getFetchSize(); }
  @Override public int getResultSetConcurrency() throws SQLException { return original.getResultSetConcurrency(); }
  @Override public int getResultSetType() throws SQLException { return original.getResultSetType(); }
  @Override public void addBatch(String sql) throws SQLException { original.addBatch(sql); }
  @Override public void clearBatch() throws SQLException { original.clearBatch(); }
  @Override public Connection getConnection() throws SQLException { return original.getConnection(); }
  @Override public boolean getMoreResults(int current) throws SQLException { return original.getMoreResults(current); }
  @Override public ResultSet getGeneratedKeys() throws SQLException { return original.getGeneratedKeys(); }
  @Override public int executeUpdate(String sql) throws SQLException { return original.executeUpdate(sql); }
  @Override public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException { return original.executeUpdate(sql, autoGeneratedKeys); }
  @Override public int executeUpdate(String sql, int[] columnIndexes) throws SQLException { return original.executeUpdate(sql, columnIndexes); }
  @Override public int executeUpdate(String sql, String[] columnNames) throws SQLException { return original.executeUpdate(sql, columnNames); }
  @Override public boolean execute(String sql) throws SQLException { return original.execute(sql); }
  @Override public boolean execute(String sql, int autoGeneratedKeys) throws SQLException { return original.execute(sql, autoGeneratedKeys); }
  @Override public boolean execute(String sql, int[] columnIndexes) throws SQLException { return original.execute(sql, columnIndexes); }
  @Override public boolean execute(String sql, String[] columnNames) throws SQLException { return original.execute(sql, columnNames); }
  @Override public int getResultSetHoldability() throws SQLException { return original.getResultSetHoldability(); }
  @Override public boolean isClosed() throws SQLException { return original.isClosed(); }
  @Override public void setPoolable(boolean poolable) throws SQLException { original.setPoolable(poolable); }
  @Override public boolean isPoolable() throws SQLException { return original.isPoolable(); }
  @Override public void closeOnCompletion() throws SQLException { original.closeOnCompletion(); }
  @Override public boolean isCloseOnCompletion() throws SQLException { return original.isCloseOnCompletion(); }
  @Override public void close() throws SQLException { original.close(); }
  @Override public int getMaxFieldSize() throws SQLException { return original.getMaxFieldSize(); }
  @Override public void setMaxFieldSize(int max) throws SQLException { original.setMaxFieldSize(max); }
  @Override public int getMaxRows() throws SQLException { return original.getMaxRows(); }
  @Override public void setMaxRows(int max) throws SQLException { original.setMaxRows(max); }
  @Override public void setEscapeProcessing(boolean enable) throws SQLException { original.setEscapeProcessing(enable); }
  @Override public int getQueryTimeout() throws SQLException { return original.getQueryTimeout(); }
  @Override public void setQueryTimeout(int seconds) throws SQLException { original.setQueryTimeout(seconds); }
  @Override public void cancel() throws SQLException { original.cancel(); }
  @Override public SQLWarning getWarnings() throws SQLException { return original.getWarnings(); }
  @Override public void clearWarnings() throws SQLException { original.clearWarnings(); }
  @Override public void setCursorName(String name) throws SQLException { original.setCursorName(name); }
  @Override public ResultSet executeQuery(String sql) throws SQLException { return original.executeQuery(sql); }
  @Override public void setAsciiStream(int i, InputStream x, int length) throws SQLException { original.setAsciiStream(i, x, length); }
  @Override public void setUnicodeStream(int i, InputStream x, int length) throws SQLException { original.setUnicodeStream(i, x, length); }
  @Override public void setBinaryStream(int i, InputStream x, int length) throws SQLException { original.setBinaryStream(i, x, length); }
  @Override public void setCharacterStream(int i, Reader reader, int length) throws SQLException { original.setCharacterStream(i, reader, length); }
  @Override public void setRef(int i, Ref x) throws SQLException { original.setRef(i, x); }
  @Override public void setBlob(int i, Blob x) throws SQLException { original.setBlob(i, x); }
  @Override public void setClob(int i, Clob x) throws SQLException { original.setClob(i, x); }
  @Override public void setArray(int i, Array x) throws SQLException { original.setArray(i, x); }
  @Override public void setDate(int i, Date x, Calendar cal) throws SQLException { original.setDate(i, x, cal); }
  @Override public void setTime(int i, Time x, Calendar cal) throws SQLException { original.setTime(i, x, cal); }
  @Override public void setTimestamp(int i, Timestamp x, Calendar cal) throws SQLException { original.setTimestamp(i, x, cal); }
  @Override public void setNull(int i, int sqlType, String typeName) throws SQLException { original.setNull(i, sqlType, typeName); }
  @Override public void setURL(int i, URL x) throws SQLException { original.setURL(i, x); }
  @Override public void setRowId(int i, RowId x) throws SQLException { original.setRowId(i, x); }
  @Override public void setNString(int i, String value) throws SQLException { original.setNString(i, value); }
  @Override public void setNCharacterStream(int i, Reader value, long length) throws SQLException { original.setNCharacterStream(i, value, length); }
  @Override public void setNClob(int i, NClob value) throws SQLException { original.setNClob(i, value); }
  @Override public void setClob(int i, Reader reader, long length) throws SQLException { original.setClob(i, reader, length); }
  @Override public void setBlob(int i, InputStream inputStream, long length) throws SQLException { original.setBlob(i, inputStream, length); }
  @Override public void setNClob(int i, Reader reader, long length) throws SQLException { original.setNClob(i, reader, length); }
  @Override public void setSQLXML(int i, SQLXML xmlObject) throws SQLException { original.setSQLXML(i, xmlObject); }
  @Override public void setObject(int i, Object x, int t, int s) throws SQLException { original.setObject(i, x, t, s); }
  @Override public void setAsciiStream(int i, InputStream x, long length) throws SQLException { original.setAsciiStream(i, x, length); }
  @Override public void setBinaryStream(int i, InputStream x, long length) throws SQLException { original.setBinaryStream(i, x, length); }
  @Override public void setCharacterStream(int i, Reader reader, long length) throws SQLException { original.setCharacterStream(i, reader, length); }
  @Override public void setAsciiStream(int i, InputStream x) throws SQLException { original.setAsciiStream(i, x); }
  @Override public void setBinaryStream(int i, InputStream x) throws SQLException { original.setBinaryStream(i, x); }
  @Override public void setCharacterStream(int i, Reader reader) throws SQLException { original.setCharacterStream(i, reader); }
  @Override public void setNCharacterStream(int i, Reader value) throws SQLException { original.setNCharacterStream(i, value); }
  @Override public void setClob(int i, Reader reader) throws SQLException { original.setClob(i, reader); }
  @Override public void setBlob(int i, InputStream inputStream) throws SQLException { original.setBlob(i, inputStream); }
  @Override public void setNClob(int i, Reader reader) throws SQLException { original.setNClob(i, reader); }
  @Override public <T> T unwrap(Class<T> iface) throws SQLException { return original.unwrap(iface); }
  @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return original.isWrapperFor(iface); }
}