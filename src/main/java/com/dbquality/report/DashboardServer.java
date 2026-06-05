package com.dbquality.report;

import com.dbquality.collector.DDLCollector;
import com.dbquality.collector.SQLContext;
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
import java.sql.SQLException;

/**
 * Embedded HTTP server cung cấp dashboard realtime.
 *
 * <p>Sử dụng JDK built-in HttpServer nên không cần thêm dependency.</p>
 *
 * <p>Các endpoint được hỗ trợ:</p>
 *
 * <ul>
 *   <li><b>GET /</b> - Dashboard HTML</li>
 *   <li><b>GET /metrics</b> - Metrics realtime (JSON)</li>
 *   <li><b>GET /findings</b> - Findings realtime (JSON)</li>
 *   <li><b>GET /report</b> - Báo cáo đầy đủ (JSON)</li>
 * </ul>
 */
public class DashboardServer {

  private final QualityConfig config;
  private final SQLContext sqlContext;
  private final ReportBuilder reportBuilder;
  private final ObjectMapper mapper;
  private final java.util.function.Supplier<Connection> connectionSupplier;

  private HttpServer server;

  /**
   * @param config               cấu hình thư viện — dùng để lấy port và các tham số khác
   * @param sqlContext           SQL records đã thu thập — phản ánh realtime khi endpoints được gọi
   * @param connectionSupplier   supplier trả về JDBC connection để thu thập DDL khi build report;
   *                             sẽ được đóng tự động sau mỗi request
   */
  public DashboardServer(QualityConfig config,
      SQLContext sqlContext,
      java.util.function.Supplier<Connection> connectionSupplier) {
    this.config = config;
    this.sqlContext = sqlContext;
    this.connectionSupplier = connectionSupplier;
    this.reportBuilder = new ReportBuilder(config);
    this.mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  /**
   * Khởi động HTTP server trên port đã cấu hình.
   */
  public void start() throws IOException {
    server = HttpServer.create(
        new InetSocketAddress(config.getDashboardPort()), 0);

    server.createContext("/", this::handleDashboard);
    server.createContext("/metrics", this::handleMetrics);
    server.createContext("/findings", this::handleFindings);
    server.createContext("/report", this::handleReport);
    server.createContext("/ai-context", this::handleAIContext);
    server.createContext("/dashboard.js", this::handleDashboardJS);
    server.createContext("/dashboard.css", this::handleDashboardCSS);
    server.createContext("/ai-refresh", this::handleAIRefresh);
    server.createContext("/slow-queries", this::handleSlowQueries);

    server.start();
    System.out.println("[DB Quality] Dashboard running at http://localhost:"
        + config.getDashboardPort());
  }

  /**
   * Dừng HTTP server.
   */
  public void stop() {
    if (server != null) server.stop(0);
  }

  // ── Handlers ──────────────────────────────────────────────────────

  private void handleDashboard(HttpExchange exchange) throws IOException {
    try {
      String html = buildDashboardHTML();
      sendResponse(exchange, 200, "text/html", html);
    } catch (IOException e) {
      sendResponse(exchange, 500, "text/html",
          "<h1>Error loading dashboard: " + e.getMessage() + "</h1>");
    }
  }

  private void handleMetrics(HttpExchange exchange) throws IOException {
    try {
      QualityReport report = buildReport();
      String json = mapper.writeValueAsString(report.getMetrics());
      sendResponse(exchange, 200, "application/json", json);
    } catch (Exception e) {
      sendResponse(exchange, 500, "application/json",
          "{\"error\": \"" + e.getMessage() + "\"}");
    }
  }

  private void handleFindings(HttpExchange exchange) throws IOException {
    try {
      QualityReport report = buildReport();
      java.util.List<com.dbquality.rule.Finding> all = new java.util.ArrayList<>();
      all.addAll(report.getDdlFindings());
      all.addAll(report.getSqlFindings());
      String json = mapper.writeValueAsString(all);
      sendResponse(exchange, 200, "application/json", json);
    } catch (Exception e) {
      sendResponse(exchange, 500, "application/json",
          "{\"error\": \"" + e.getMessage() + "\"}");
    }
  }

  private void handleAIRefresh(HttpExchange exchange) throws IOException {
    if (!"POST".equals(exchange.getRequestMethod())) {
      sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
      return;
    }
    reportBuilder.resetAiCache();
    sendResponse(exchange, 200, "application/json", "{\"status\":\"ok\"}");
  }

  private void handleReport(HttpExchange exchange) throws IOException {
    try {
      QualityReport report = buildReport();
      String json = mapper.writeValueAsString(report);
      sendResponse(exchange, 200, "application/json", json);
    } catch (Exception e) {
      sendResponse(exchange, 500, "application/json",
          "{\"error\": \"" + e.getMessage() + "\"}");
    }
  }

  private void handleAIContext(HttpExchange exchange) throws IOException {
    try {
      QualityReport report = buildReport();
      String json = mapper.writeValueAsString(
          java.util.Map.of("aiContext", report.getAiReadyContext()));
      sendResponse(exchange, 200, "application/json", json);
    } catch (Exception e) {
      sendResponse(exchange, 500, "application/json",
          "{\"error\": \"" + e.getMessage() + "\"}");
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
      String js = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
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

  // ── Helper ────────────────────────────────────────────────────────

  private QualityReport buildReport() throws SQLException {
    try (Connection conn = connectionSupplier.get()) {
      return reportBuilder.build(conn, sqlContext);
    }
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

  // ── Dashboard HTML ────────────────────────────────────────────────

  private String buildDashboardHTML() throws IOException {
    try (java.io.InputStream is = getClass()
        .getClassLoader()
        .getResourceAsStream("dashboard.html")) {
      if (is == null) {
        throw new IOException("dashboard.html not found in classpath");
      }
      return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
  }

  private void handleSlowQueries(HttpExchange exchange) throws IOException {
    try (java.sql.Connection conn = connectionSupplier.get()) {
      ReportBuilder builder = new ReportBuilder(config);
      QualityReport report = builder.build(conn, sqlContext);
      String json = mapper.writeValueAsString(report.getSlowQueries());
      sendResponse(exchange, 200, "application/json", json);
    } catch (Exception e) {
      sendResponse(exchange, 500, "application/json",
          "{\"error\":\"" + e.getMessage() + "\"}");
    }
  }
}