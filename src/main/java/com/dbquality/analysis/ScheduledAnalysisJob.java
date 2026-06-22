package com.dbquality.analysis;

import com.dbquality.collector.DDLCollector;
import com.dbquality.collector.DDLContext;
import com.dbquality.collector.QueryMetricsStore;
import com.dbquality.report.AnalysisResultStore;
import com.dbquality.rule.Finding;
import com.dbquality.rule.RuleEngine;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background job chạy rule engine theo interval cố định.
 *
 * <p>Tách rời analysis khỏi hot path (interceptor):</p>
 * <ul>
 *   <li>Interceptor chỉ collect metrics — O(1) per query</li>
 *   <li>Job này chạy analysis nặng (rule engine, EXPLAIN) async</li>
 *   <li>API endpoints đọc từ {@link AnalysisResultStore} (cache)</li>
 * </ul>
 *
 * <p>Default interval: 24h — pattern findings không thay đổi nhanh
 * nên không cần realtime. User có thể config qua
 * {@code quality.analysis.interval}.</p>
 *
 * <p>Lần đầu tiên job chạy sau {@code initialDelaySeconds} (mặc định 10s)
 * để app có thời gian collect đủ data trước khi analysis lần đầu.</p>
 */
public class ScheduledAnalysisJob {

  private final DataSource dataSource;
  private final QueryMetricsStore metricsStore;
  private final AnalysisResultStore resultStore;
  private final RuleEngine ruleEngine;
  private final long intervalMs;
  private final long initialDelayMs;

  private ScheduledExecutorService scheduler;
  private volatile boolean running = false;

  public ScheduledAnalysisJob(DataSource dataSource,
      QueryMetricsStore metricsStore,
      AnalysisResultStore resultStore,
      RuleEngine ruleEngine,
      long intervalMs,
      long initialDelayMs) {
    this.dataSource = dataSource;
    this.metricsStore = metricsStore;
    this.resultStore = resultStore;
    this.ruleEngine = ruleEngine;
    this.intervalMs = intervalMs;
    this.initialDelayMs = initialDelayMs;
  }

  /**
   * Khởi động job. Lần đầu chạy sau {@code initialDelayMs},
   * sau đó lặp lại mỗi {@code intervalMs}.
   */
  public synchronized void start() {
    if (running) return;
    scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "db-quality-analysis-job");
      t.setDaemon(true);
      return t;
    });
    scheduler.scheduleWithFixedDelay(
        this::runAnalysis,
        initialDelayMs,
        intervalMs,
        TimeUnit.MILLISECONDS
    );
    running = true;
  }

  /**
   * Dừng job. Gọi khi app shutdown.
   */
  public synchronized void stop() {
    if (!running) return;
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
    running = false;
  }

  /**
   * Trigger analysis ngay lập tức — không đợi schedule tiếp theo.
   * Dùng cho endpoint POST /analyze-now hoặc nút "Run Analysis Now"
   * trên dashboard.
   */
  public void triggerNow() {
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.execute(this::runAnalysis);
    }
  }

  /**
   * Logic chính của job: snapshot input → run rules → cache result.
   * Wrap trong try-catch để 1 lần fail không kill scheduler.
   */
  private void runAnalysis() {
    long startTime = System.currentTimeMillis();
    try {
      // 1. Snapshot DDL
      DDLContext ddlContext;
      try (Connection conn = dataSource.getConnection()) {
        ddlContext = new DDLCollector().collect(conn);
      }

      // 2. Run rule engine — trả về List<Finding> trực tiếp
      List<Finding> findings = ruleEngine.analyze(ddlContext, metricsStore);

      // 3. Calculate score
      int score = calculateScore(findings);

      // 4. Update cache
      long duration = System.currentTimeMillis() - startTime;
      resultStore.update(findings, score, duration);

    } catch (Exception e) {
      System.err.println("[db-quality] Analysis job failed: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Tính overall score từ findings.
   * Logic giống ReportBuilder.calculateScore() cũ —
   * sẽ refactor chung sau khi remove ReportBuilder logic cũ.
   */
  private int calculateScore(List<Finding> findings) {
    if (findings.isEmpty()) return 100;
    int deduction = 0;
    for (var severity : com.dbquality.constant.Severity.values()) {
      long count = findings.stream()
          .filter(f -> f.getSeverity() == severity)
          .count();
      deduction += Math.min(count * severity.getWeight(),
          severity.getMaxDeduction());
    }
    return Math.max(0, 100 - deduction);
  }

  public boolean isRunning() {
    return running;
  }
}