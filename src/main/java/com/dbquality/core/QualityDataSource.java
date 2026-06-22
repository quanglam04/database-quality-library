package com.dbquality.core;

import com.dbquality.analysis.ScheduledAnalysisJob;
import com.dbquality.collector.QueryMetricsStore;
import com.dbquality.collector.SQLContext;
import com.dbquality.config.QualityAutoConfiguration;
import com.dbquality.config.QualityConfig;
import com.dbquality.report.AnalysisResultStore;
import com.dbquality.report.DashboardServer;
import com.dbquality.report.JSONExporter;
import com.dbquality.report.QualityReport;
import com.dbquality.report.ReportBuilder;
import com.dbquality.rule.RuleEngine;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * Entry point của thư viện.
 * Wrap DataSource gốc để intercept toàn bộ JDBC calls.
 *
 * <p><b>Với Spring Boot:</b> {@link QualityAutoConfiguration}
 * tự động wrap DataSource — không cần khởi tạo thủ công.</p>
 *
 * <p>Khi khởi tạo, thư viện sẽ:</p>
 * <ul>
 *   <li>Khởi tạo {@link QueryMetricsStore} và {@link AnalysisResultStore}</li>
 *   <li>Start {@link ScheduledAnalysisJob} chạy analysis theo interval</li>
 *   <li>Khởi động embedded dashboard tại {@code http://localhost:9876} (nếu enabled)</li>
 *   <li>Đăng ký shutdown hook để auto-export JSON report khi app dừng (nếu enabled)</li>
 * </ul>
 */
public class QualityDataSource implements DataSource {

  private final DataSource original;
  private final QualityConfig config;
  private final SQLContext sqlContext;
  private final QueryMetricsStore metricsStore;
  private final AnalysisResultStore resultStore;
  private final ScheduledAnalysisJob analysisJob;
  private DashboardServer dashboardServer;

  public QualityDataSource(DataSource original) {
    this(original, QualityConfig.getDefault());
  }

  public QualityDataSource(DataSource original, QualityConfig config) {
    this.original = original;
    this.config = config;
    this.sqlContext = new SQLContext();
    this.metricsStore = new QueryMetricsStore();
    this.resultStore = new AnalysisResultStore();

    RuleEngine ruleEngine = RuleEngine.withDefaultRules(
        config.getSlowQueryThresholdMs(),
        config.getNPlusOneThreshold()
    );
    this.analysisJob = new ScheduledAnalysisJob(
        this, // dùng chính QualityDataSource để job lấy connection có instrumentation
        metricsStore,
        resultStore,
        ruleEngine,
        config.getAnalysisIntervalMs(),
        config.getAnalysisInitialDelayMs()
    );
    if (config.isAnalysisScheduled()) {
      analysisJob.start();
    }

    // Dashboard
    if (config.isDashboardEnabled()) {
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

    // Shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      analysisJob.stop();
      if (config.isExportJsonEnabled()) {
        try {
          exportJSON(config.getExportJsonPath());
        } catch (Exception e) {
          System.err.println("[DB Quality] Failed to export JSON report: "
              + e.getMessage());
        }
      }
    }));
  }

  @Override
  public Connection getConnection() throws SQLException {
    return new QualityConnection(original.getConnection(), sqlContext, metricsStore, config);
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return new QualityConnection(
        original.getConnection(username, password), sqlContext, metricsStore, config);
  }

  public SQLContext getSqlContext() {
    return sqlContext;
  }

  public QueryMetricsStore getMetricsStore() {
    return metricsStore;
  }

  public AnalysisResultStore getResultStore() {
    return resultStore;
  }

  public ScheduledAnalysisJob getAnalysisJob() {
    return analysisJob;
  }

  public void exportJSON(String filePath) throws Exception {
    try (java.sql.Connection conn = original.getConnection()) {
      ReportBuilder builder = new ReportBuilder(config);
      QualityReport report = builder.build(conn, sqlContext);
      new JSONExporter().toFile(report, filePath);
      System.out.println("[DB Quality] Report exported to " + filePath);
    }
  }


  @Override public PrintWriter getLogWriter() throws SQLException { return original.getLogWriter(); }
  @Override public void setLogWriter(PrintWriter out) throws SQLException { original.setLogWriter(out); }
  @Override public void setLoginTimeout(int seconds) throws SQLException { original.setLoginTimeout(seconds); }
  @Override public int getLoginTimeout() throws SQLException { return original.getLoginTimeout(); }
  @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return original.getParentLogger(); }
  @Override public <T> T unwrap(Class<T> iface) throws SQLException { return original.unwrap(iface); }
  @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return original.isWrapperFor(iface); }
}