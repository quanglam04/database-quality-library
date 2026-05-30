package com.dbquality.core;

import com.dbquality.collector.SQLContext;
import com.dbquality.config.QualityConfig;

import com.dbquality.report.DashboardServer;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * Entry point của thư viện.
 * Wrap DataSource gốc để intercept toàn bộ JDBC calls.
 * Ứng dụng sử dụng class này thay cho DataSource gốc mà không cần thay đổi code nghiệp vụ.
 */
public class QualityDataSource implements DataSource {

  private final DataSource original;
  private final QualityConfig config;
  private final SQLContext sqlContext;
  private DashboardServer dashboardServer;

  public QualityDataSource(DataSource original) {
    this(original, QualityConfig.getDefault());
  }

  public QualityDataSource(DataSource original, QualityConfig config) {
    this.original = original;
    this.config = config;
    this.sqlContext = new SQLContext();

    if(config.isDashboardEnabled()){
      this.dashboardServer = new DashboardServer(
          config,
          sqlContext,
          () -> {
            try { return original.getConnection(); }
            catch (Exception e) { throw new RuntimeException(e); }
          }
      );
      try {
        dashboardServer.start();
      } catch (Exception e) {
        System.err.println("[DB Quality] Failed to start dashboard: " + e.getMessage());
      }
    }
  }

  @Override
  public Connection getConnection() throws SQLException {
    return new QualityConnection(original.getConnection(), sqlContext, config);
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return new QualityConnection(original.getConnection(username, password), sqlContext, config);
  }

  /**
   * @return SQLContext chứa toàn bộ SQL đã được intercept trong session hiện tại
   */
  public SQLContext getSqlContext() {
    return sqlContext;
  }

  // Delegate các method còn lại về original

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return original.getLogWriter();
  }

  @Override
  public void setLogWriter(PrintWriter out) throws SQLException {
    original.setLogWriter(out);
  }

  @Override
  public void setLoginTimeout(int seconds) throws SQLException {
    original.setLoginTimeout(seconds);
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    return original.getLoginTimeout();
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return original.getParentLogger();
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    return original.unwrap(iface);
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return original.isWrapperFor(iface);
  }
}