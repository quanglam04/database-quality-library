package com.dbquality.core;

import com.dbquality.collector.SQLContext;
import com.dbquality.config.QualityAutoConfiguration;
import com.dbquality.config.QualityConfig;

import com.dbquality.report.DashboardServer;
import com.dbquality.report.JSONExporter;
import com.dbquality.report.QualityReport;
import com.dbquality.report.ReportBuilder;
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
 *
 *
 * <p><b>Với Spring Boot:</b> {@link QualityAutoConfiguration}
 * tự động wrap DataSource — không cần khởi tạo thủ công.</p>
 *
 * <p>Khi khởi tạo, thư viện sẽ:</p>
 * <ul>
 *   <li>Khởi động embedded dashboard tại {@code http://localhost:9876} (nếu enabled)</li>
 *   <li>Đăng ký shutdown hook để auto-export JSON report khi app dừng (nếu enabled)</li>
 * </ul>
 */
public class QualityDataSource implements DataSource {

  private final DataSource original;
  private final QualityConfig config;
  private final SQLContext sqlContext;
  private DashboardServer dashboardServer;

  /**
   * Tạo QualityDataSource với config mặc định.
   *
   * <p>Dashboard bật tại port {@code 9876}, auto-export JSON khi shutdown.
   * Dùng {@link #QualityDataSource(DataSource, QualityConfig)} để tuỳ chỉnh.</p>
   *
   * @param original DataSource gốc cần wrap (HikariCP, c3p0, DBCP, v.v.)
   */
  public QualityDataSource(DataSource original) {
    this(original, QualityConfig.getDefault());
  }

  /**
   * Tạo QualityDataSource với config tuỳ chỉnh.
   *
   * @param original DataSource gốc cần wrap
   * @param config   cấu hình thư viện — dùng {@link QualityConfig#fromClasspath()} để đọc từ
   *                 {@code application.properties}, hoặc {@link QualityConfig#getTestDefault()}
   *                 trong unit test để tắt dashboard và export
   */
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

    if (config.isExportJsonEnabled()) {
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        try {
          exportJSON(config.getExportJsonPath());
        } catch (Exception e) {
          System.err.println("[DB Quality] Failed to export JSON report: "
              + e.getMessage());
        }
      }));
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

  /**
   * Export report ra file JSON tại đường dẫn chỉ định.
   *
   * @param filePath đường dẫn file output, ví dụ "report.json"
   */
  public void exportJSON(String filePath) throws Exception {
    try (java.sql.Connection conn = original.getConnection()) {
      ReportBuilder builder = new ReportBuilder(config);
      QualityReport report = builder.build(conn, sqlContext);
      new JSONExporter().toFile(report, filePath);
      System.out.println("[DB Quality] Report exported to " + filePath);
    }
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