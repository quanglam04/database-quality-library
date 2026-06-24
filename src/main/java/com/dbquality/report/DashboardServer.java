package com.dbquality.report;

import com.dbquality.analysis.ScheduledAnalysisJob;
import com.dbquality.collector.DDLCollector;
import com.dbquality.collector.ProjectInfoCollector;
import com.dbquality.collector.QueryMetricsStore;
import com.dbquality.config.QualityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.function.Supplier;

/**
 * Embedded HTTP dashboard server.
 *
 * <ul>
 *   <li>Realtime endpoints (/metrics, /collected-queries) đọc từ {@link QueryMetricsStore} — nhẹ, gọi mỗi 5s</li>
 *   <li>Analysis endpoints (/report, /findings) đọc từ {@link AnalysisResultStore} — cache scheduled</li>
 *   <li>Endpoint /analyze-now trigger manual analysis qua {@link ScheduledAnalysisJob}</li>
 * </ul>
 *
 * <p>Endpoint list:</p>
 * <ul>
 *   <li><b>GET /</b> — Dashboard HTML</li>
 *   <li><b>GET /metrics</b> — Realtime metrics (JSON)</li>
 *   <li><b>GET /collected-queries</b> — Tất cả SQL pattern + metrics (JSON)</li>
 *   <li><b>GET /schema-snapshot</b> — DDL schema đã thu thập (JSON)</li>
 *   <li><b>GET /findings</b> — Findings từ analysis gần nhất (cache)</li>
 *   <li><b>GET /report</b> — Báo cáo đầy đủ (cache)</li>
 *   <li><b>GET /slow-queries</b> — Top slow queries (cache)</li>
 *   <li><b>GET /ai-context</b> — AI-ready context (cache)</li>
 *   <li><b>GET /project-info</b> — JVM, DB info (realtime)</li>
 *   <li><b>GET /metrics-trend</b> — Latency trend theo bucket 30s (realtime)</li>
 *   <li><b>GET /analysis-status</b> — Trạng thái analysis job (lastRun, nextRun)</li>
 *   <li><b>POST /analyze-now</b> — Trigger manual analysis</li>
 *   <li><b>POST /ai-refresh</b> — Reset AI cache</li>
 * </ul>
 */
public class DashboardServer {

  private final QualityConfig config;
  private final QueryMetricsStore metricsStore;
  private final AnalysisResultStore resultStore;
  private final ScheduledAnalysisJob analysisJob;
  private final ReportBuilder reportBuilder;
  private final ObjectMapper mapper;
  private final Supplier<Connection> connectionSupplier;
  private final ProjectInfoCollector projectInfoCollector;
  private HttpServer server;

  public DashboardServer(QualityConfig config,
      QueryMetricsStore metricsStore,
      AnalysisResultStore resultStore,
      ScheduledAnalysisJob analysisJob,
      ReportBuilder reportBuilder,
      Supplier<Connection> connectionSupplier) {
    this.config = config;
    this.metricsStore = metricsStore;
    this.resultStore = resultStore;
    this.analysisJob = analysisJob;
    this.reportBuilder = reportBuilder;
    this.connectionSupplier = connectionSupplier;
    this.mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    this.projectInfoCollector = new ProjectInfoCollector(
        System.currentTimeMillis(), config.getDashboardPort());
  }

  public void start() throws IOException {
    server = HttpServer.create(
        new InetSocketAddress(config.getDashboardPort()), 0);

    server.createContext("/", this::handleDashboard);

    // Realtime endpoints (read directly from metricsStore)
    server.createContext("/metrics", this::handleMetrics);
    server.createContext("/collected-queries", this::handleCollectedQueries);
    server.createContext("/schema-snapshot", this::handleSchemaSnapshot);
    server.createContext("/project-info", this::handleProjectInfo);

    // Analysis endpoints (read from resultStore cache)
    server.createContext("/findings", this::handleFindings);
    server.createContext("/report", this::handleReport);
    server.createContext("/slow-queries", this::handleSlowQueries);
    server.createContext("/ai-context", this::handleAIContext);
    server.createContext("/analysis-status", this::handleAnalysisStatus);

    // Trigger endpoints
    server.createContext("/analyze-now", this::handleAnalyzeNow);
    server.createContext("/ai-refresh", this::handleAIRefresh);

    // Static assets
    server.createContext("/dashboard.js", this::handleDashboardJS);
    server.createContext("/dashboard.css", this::handleDashboardCSS);

    server.start();
    System.out.println("[DB Quality] Dashboard running at http://localhost:"
        + config.getDashboardPort());
  }

  public void stop() {
    if (server != null) server.stop(0);
  }

  // ── Realtime handlers (read from metricsStore) ──────────────────

  /**
   * Build realtime metrics từ metricsStore (không qua rule engine).
   * Gọi mỗi 5s từ frontend cho UI Overview tab.
   */
  private void handleMetrics(HttpExchange exchange) throws IOException {
    try {
      var metrics = buildRealtimeMetrics();
      String json = mapper.writeValueAsString(metrics);
      sendResponse(exchange, 200, "application/json", json);
    } catch (Exception e) {
      sendResponse(exchange, 500, "application/json",
          "{\"error\": \"" + e.getMessage() + "\"}");
    }
  }

  /**
   * Trả về danh sách tất cả SQL pattern đã thu thập + metrics.
   * Đây là input transparency theo feedback mentor — user thấy
   * data nào đang được rule engine phân tích.
   */
  private void handleCollectedQueries(HttpExchange exchange) throws IOException {
    try {
      var queries = metricsStore.getAllMetrics().stream()
          .sorted((a, b) -> Long.compare(b.getCallCount(), a.getCallCount()))
          .map(m -> java.util.Map.of(
              "sqlPattern", m.getSqlPattern(),
              "callCount", m.getCallCount(),
              "avgDurationMs", String.format("%.2f", m.getAvgDurationMs()),
              "minDurationMs", m.getMinDurationMs(),
              "maxDurationMs", m.getMaxDurationMs(),
              "totalDurationMs", m.getTotalDurationMs(),
              "calledFrom", m.getMostFrequentCalledFrom(),
              "lastSeenAt", m.getLastSeenAt()
          ))
          .toList();
      String json = mapper.writeValueAsString(queries);
      sendResponse(exchange, 200, "application/json", json);
    } catch (Exception e) {
      sendResponse(exchange, 500, "application/json",
          "{\"error\": \"" + e.getMessage() + "\"}");
    }
  }

  /**
   * Trả về DDL schema đã thu thập.
   * Input transparency theo feedback mentor.
   */
  private void handleSchemaSnapshot(HttpExchange exchange) throws IOException {
    try (Connection conn = connectionSupplier.get()) {
      var ddlContext = new DDLCollector().collect(conn);
      var snapshot = ddlContext.getTables().stream()
          .map(t -> java.util.Map.of(
              "name", t.getName(),
              "columns", t.getColumns().stream()
                  .map(c -> java.util.Map.of(
                      "name", c.getName(),
                      "type", c.getType(),
                      "nullable", c.isNullable(),
                      "primaryKey", c.isPrimaryKey()
                  )).toList(),
              "indexes", t.getIndexes().stream()
                  .map(i -> java.util.Map.of(
                      "name", i.getName(),
                      "columns", i.getColumns()
                  )).toList(),
              "foreignKeys", t.getForeignKeys() == null ? java.util.List.of()
                  : t.getForeignKeys().stream()
                      .map(fk -> java.util.Map.of(
                          "name", fk.getName(),
                          "column", fk.getColumn(),
                          "referencedTable", fk.getReferencedTable(),
                          "referencedColumn", fk.getReferencedColumn()
                      )).toList()
          ))
          .toList();
      String json = mapper.writeValueAsString(snapshot);
      sendResponse(exchange, 200, "application/json", json);
    } catch (Exception e) {
      sendResponse(exchange, 500, "application/json",
          "{\"error\": \"" + e.getMessage() + "\"}");
    }
  }

  private void handleProjectInfo(HttpExchange exchange) throws IOException {
    try (Connection conn = connectionSupplier.get()) {
      var info = projectInfoCollector.collect(conn);
      String json = mapper.writeValueAsString(info);
      sendResponse(exchange, 200, "application/json", json);
    } catch (Exception e) {
      sendResponse(exchange, 500, "application/json",
          "{\"error\":\"" + e.getMessage() + "\"}");
    }
  }

  //  Analysis handlers
  /**
   * Findings từ analysis gần nhất.
   * Chỉ trả cache — không trigger analysis mới.
   */
  private void handleFindings(HttpExchange exchange) throws IOException {
    try {
      String json = mapper.writeValueAsString(resultStore.getFindings());
      sendResponse(exchange, 200, "application/json", json);
    } catch (Exception e) {
      sendResponse(exchange, 500, "application/json",
          "{\"error\": \"" + e.getMessage() + "\"}");
    }
  }

  /**
   * Báo cáo đầy đủ — kết hợp realtime metrics + cached findings.
   */
  private void handleReport(HttpExchange exchange) throws IOException {
    try (Connection conn = connectionSupplier.get()) {
      QualityReport report = reportBuilder.build(conn, metricsStore);
      String json = mapper.writeValueAsString(report);
      sendResponse(exchange, 200, "application/json", json);
    } catch (Exception e) {
      sendResponse(exchange, 500, "application/json",
          "{\"error\": \"" + e.getMessage() + "\"}");
    }
  }

  private void handleSlowQueries(HttpExchange exchange) throws IOException {
    try (Connection conn = connectionSupplier.get()) {
      QualityReport report = reportBuilder.build(conn, metricsStore);
      String json = mapper.writeValueAsString(report.getSlowQueries());
      sendResponse(exchange, 200, "application/json", json);
    } catch (Exception e) {
      sendResponse(exchange, 500, "application/json",
          "{\"error\":\"" + e.getMessage() + "\"}");
    }
  }

  private void handleAIContext(HttpExchange exchange) throws IOException {
    try (Connection conn = connectionSupplier.get()) {
      QualityReport report = reportBuilder.build(conn, metricsStore);
      String json = mapper.writeValueAsString(
          java.util.Map.of("aiContext", report.getAiReadyContext()));
      sendResponse(exchange, 200, "application/json", json);
    } catch (Exception e) {
      sendResponse(exchange, 500, "application/json",
          "{\"error\": \"" + e.getMessage() + "\"}");
    }
  }

  /**
   * Status của analysis job — cho dashboard hiển thị:
   * - Last analysis: 2h ago
   * - Next scheduled: in 22h
   * - Currently running: yes/no
   */
  private void handleAnalysisStatus(HttpExchange exchange) throws IOException {
    try {
      long lastAt = resultStore.getLastAnalyzedAt();
      long now = System.currentTimeMillis();
      long secondsSinceLast = resultStore.getSecondsSinceLastAnalysis();
      long nextInSeconds = lastAt > 0
          ? Math.max(0, (lastAt + config.getAnalysisIntervalMs() - now) / 1000)
          : -1;

      var status = new java.util.LinkedHashMap<String, Object>();
      status.put("firstAnalysisDone", resultStore.isFirstAnalysisDone());
      status.put("lastAnalyzedAt", lastAt);
      status.put("secondsSinceLastAnalysis", secondsSinceLast);
      status.put("lastAnalysisDurationMs", resultStore.getLastAnalysisDurationMs());
      status.put("nextScheduledInSeconds", nextInSeconds);
      status.put("scheduledEnabled", config.isAnalysisScheduled());
      status.put("intervalMs", config.getAnalysisIntervalMs());

      String json = mapper.writeValueAsString(status);
      sendResponse(exchange, 200, "application/json", json);
    } catch (Exception e) {
      sendResponse(exchange, 500, "application/json",
          "{\"error\":\"" + e.getMessage() + "\"}");
    }
  }

  // Trigger handlers

  /**
   * Trigger analysis ngay.
   * Cho nút "Run Analysis Now" trên dashboard.
   */
  private void handleAnalyzeNow(HttpExchange exchange) throws IOException {
    if (!"POST".equals(exchange.getRequestMethod())) {
      sendResponse(exchange, 405, "application/json",
          "{\"error\":\"Method not allowed\"}");
      return;
    }
    try {
      analysisJob.triggerNow();
      sendResponse(exchange, 200, "application/json",
          "{\"status\":\"triggered\"}");
    } catch (Exception e) {
      sendResponse(exchange, 500, "application/json",
          "{\"error\":\"" + e.getMessage() + "\"}");
    }
  }

  private void handleAIRefresh(HttpExchange exchange) throws IOException {
    if (!"POST".equals(exchange.getRequestMethod())) {
      sendResponse(exchange, 405, "application/json",
          "{\"error\":\"Method not allowed\"}");
      return;
    }
    reportBuilder.resetAiCache();
    sendResponse(exchange, 200, "application/json", "{\"status\":\"ok\"}");
  }

  //  Static assets

  private void handleDashboard(HttpExchange exchange) throws IOException {
    try {
      String html = buildDashboardHTML();
      sendResponse(exchange, 200, "text/html", html);
    } catch (IOException e) {
      sendResponse(exchange, 500, "text/html",
          "<h1>Error loading dashboard: " + e.getMessage() + "</h1>");
    }
  }

  private void handleDashboardJS(HttpExchange exchange) throws IOException {
    try (java.io.InputStream is = getClass()
        .getClassLoader()
        .getResourceAsStream("dashboard.js")) {
      if (is == null) {
        sendResponse(exchange, 404, "text/plain", "dashboard.js not found");
        return;
      }
      String js = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      sendResponse(exchange, 200, "application/javascript", js);
    }
  }

  private void handleDashboardCSS(HttpExchange exchange) throws IOException {
    try (java.io.InputStream is = getClass()
        .getClassLoader()
        .getResourceAsStream("dashboard.css")) {
      if (is == null) {
        sendResponse(exchange, 404, "text/plain", "dashboard.css not found");
        return;
      }
      String css = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      sendResponse(exchange, 200, "text/css", css);
    }
  }

  //  Helpers

  /**
   * Build realtime metrics — chỉ từ metricsStore, không qua rule engine.
   * Khác với /report (đọc analysis cache), endpoint này luôn fresh.
   */
  private java.util.Map<String, Object> buildRealtimeMetrics() {
    long total = metricsStore.getTotalExecutions();
    int uniquePatterns = metricsStore.getUniquePatternCount();

    // P50/P95/P99 approximation
    java.util.List<Long> times = new java.util.ArrayList<>();
    for (var m : metricsStore.getAllMetrics()) {
      long avg = (long) m.getAvgDurationMs();
      for (long i = 0; i < m.getCallCount(); i++) {
        times.add(avg);
      }
    }
    java.util.Collections.sort(times);

    int slowCount = (int) metricsStore.getAllMetrics().stream()
        .filter(m -> m.getMaxDurationMs() >= config.getSlowQueryThresholdMs())
        .count();

    int nPlusOne = (int) metricsStore.getAllMetrics().stream()
        .filter(m -> m.getCallCount() > config.getNPlusOneThreshold())
        .count();

    var result = new java.util.LinkedHashMap<String, Object>();
    result.put("totalSQLIntercepted", total);
    result.put("uniquePatterns", uniquePatterns);
    result.put("slowQueryCount", slowCount);
    result.put("nPlusOneDetected", nPlusOne);
    result.put("p50Latency", percentile(times, 50));
    result.put("p95Latency", percentile(times, 95));
    result.put("p99Latency", percentile(times, 99));
    result.put("errorRate", 0.0);
    result.put("score", resultStore.getScore());
    return result;
  }

  private long percentile(java.util.List<Long> sorted, int percentile) {
    if (sorted.isEmpty()) return 0;
    int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
    return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
  }

  private void sendResponse(HttpExchange exchange, int status,
      String contentType, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type",
        contentType + "; charset=utf-8");
    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
    exchange.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(bytes);
    }
  }

  private String buildDashboardHTML() throws IOException {
    try (java.io.InputStream is = getClass()
        .getClassLoader()
        .getResourceAsStream("dashboard.html")) {
      if (is == null) {
        throw new IOException("dashboard.html not found in classpath");
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}