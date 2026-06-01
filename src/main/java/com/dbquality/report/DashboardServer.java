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
 * Sử dụng JDK built-in HttpServer — không cần dependency thêm.
 *
 * Endpoints:
 *   GET /          → HTML dashboard
 *   GET /metrics   → JSON metrics realtime
 *   GET /findings  → JSON findings realtime
 *   GET /report    → JSON report đầy đủ
 */
public class DashboardServer {

  private final QualityConfig config;
  private final SQLContext sqlContext;
  private final ReportBuilder reportBuilder;
  private final ObjectMapper mapper;
  private final java.util.function.Supplier<Connection> connectionSupplier;

  private HttpServer server;

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
    String html = buildDashboardHTML();
    sendResponse(exchange, 200, "text/html", html);
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

  private String buildDashboardHTML() {
    return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>DB Quality Dashboard</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
               background: #0f172a; color: #e2e8f0; min-height: 100vh; }
        header { background: #1e293b; padding: 20px 32px;
                 border-bottom: 1px solid #334155;
                 display: flex; align-items: center; gap: 12px; }
        header h1 { font-size: 20px; font-weight: 600; color: #f1f5f9; }
        header span { font-size: 12px; color: #64748b; }
        .score-badge { margin-left: auto; padding: 6px 16px;
                       border-radius: 20px; font-weight: 700; font-size: 14px; }
        .container { padding: 24px 32px; max-width: 1400px; margin: 0 auto; }
        .grid-4 { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }
        .grid-2 { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-bottom: 24px; }
        .card { background: #1e293b; border: 1px solid #334155;
                border-radius: 12px; padding: 20px; }
        .card h3 { font-size: 12px; color: #64748b; text-transform: uppercase;
                   letter-spacing: 0.05em; margin-bottom: 8px; }
        .card .value { font-size: 32px; font-weight: 700; color: #f1f5f9; }
        .card .sub { font-size: 12px; color: #64748b; margin-top: 4px; }
        .section-title { font-size: 16px; font-weight: 600; color: #f1f5f9;
                         margin-bottom: 12px; }
        .findings-list { display: flex; flex-direction: column; gap: 8px; }
        .finding-item { background: #0f172a; border: 1px solid #334155;
                        border-radius: 8px; padding: 12px 16px;
                        display: flex; align-items: flex-start; gap: 12px; }
        .badge { padding: 2px 8px; border-radius: 4px; font-size: 11px;
                 font-weight: 600; white-space: nowrap; }
        .badge.CRITICAL { background: #450a0a; color: #fca5a5; }
        .badge.HIGH     { background: #431407; color: #fdba74; }
        .badge.MEDIUM   { background: #422006; color: #fcd34d; }
        .badge.WARNING  { background: #1e3a5f; color: #93c5fd; }
        .finding-content { flex: 1; }
        .finding-rule { font-size: 12px; color: #64748b; margin-bottom: 4px; }
        .finding-msg { font-size: 14px; color: #e2e8f0; }
        .finding-rec { font-size: 12px; color: #22d3ee; margin-top: 4px; }
        .latency-bars { display: flex; flex-direction: column; gap: 8px; }
        .latency-row { display: flex; align-items: center; gap: 12px; }
        .latency-label { font-size: 12px; color: #64748b; width: 30px; }
        .latency-bar-wrap { flex: 1; background: #0f172a; border-radius: 4px; height: 8px; }
        .latency-bar { height: 8px; border-radius: 4px; background: #3b82f6; }
        .latency-value { font-size: 12px; color: #94a3b8; width: 60px; text-align: right; }
        table { width: 100%; border-collapse: collapse; }
        th { text-align: left; font-size: 11px; color: #64748b; padding: 8px 12px;
             text-transform: uppercase; border-bottom: 1px solid #334155; }
        td { padding: 10px 12px; font-size: 13px; border-bottom: 1px solid #1e293b; }
        .refresh-btn { background: #3b82f6; color: white; border: none;
                       padding: 8px 16px; border-radius: 6px; cursor: pointer;
                       font-size: 13px; margin-left: auto; display: block; }
        .refresh-btn:hover { background: #2563eb; }
        .empty { color: #475569; font-size: 14px; text-align: center; padding: 24px; }
        .error { color: #f87171; font-size: 14px; text-align: center; padding: 24px; }
    </style>
</head>
<body>
    <header>
        <div>
            <h1> DB Quality Dashboard</h1>
            <span id="lastUpdate">Loading...</span>
        </div>
        <div id="scoreBadge" class="score-badge">--</div>
    </header>

    <div class="container">
        <!-- Metrics cards -->
        <div class="grid-4">
            <div class="card">
                <h3>Total SQL</h3>
                <div class="value" id="totalSQL">--</div>
                <div class="sub">intercepted</div>
            </div>
            <div class="card">
                <h3>Slow Queries</h3>
                <div class="value" id="slowCount" style="color:#fdba74">--</div>
                <div class="sub">above threshold</div>
            </div>
            <div class="card">
                <h3>N+1 Detected</h3>
                <div class="value" id="nPlusOne" style="color:#fca5a5">--</div>
                <div class="sub">patterns</div>
            </div>
            <div class="card">
                <h3>Error Rate</h3>
                <div class="value" id="errorRate" style="color:#f87171">--</div>
                <div class="sub">of total queries</div>
            </div>
        </div>

        <div class="grid-2">
            <!-- Latency -->
            <div class="card">
                <h3 class="section-title" style="font-size:14px">Latency Percentiles</h3>
                <div class="latency-bars" id="latencyBars">
                    <div class="empty">Loading...</div>
                </div>
            </div>

            <!-- Top Tables -->
            <div class="card">
                <h3 class="section-title" style="font-size:14px">Top Tables by Query Frequency</h3>
                <table id="topTablesTable">
                    <thead><tr><th>Table</th><th>Queries</th></tr></thead>
                    <tbody id="topTablesBody"><tr><td colspan="2" class="empty">Loading...</td></tr></tbody>
                </table>
            </div>
        </div>

        <!-- Findings -->
        <div class="card">
            <div style="display:flex; align-items:center; margin-bottom:16px">
                <span class="section-title">Findings</span>
                <button class="refresh-btn" onclick="loadData()">↻ Refresh</button>
            </div>
            <div class="findings-list" id="findingsList">
                <div class="empty">Loading...</div>
            </div>
        </div>
        
        <!-- AI Context Section -->
                <div class="card" style="margin-top:16px">
                    <div style="display:flex; align-items:center; margin-bottom:16px">
                        <span class="section-title">🤖 AI-Ready Context</span>
                        <button onclick="copyAIContext()"\s
                                style="margin-left:auto; background:#6d28d9; color:white; border:none;
                                       padding:8px 16px; border-radius:6px; cursor:pointer; font-size:13px">
                            📋 Copy
                        </button>
                    </div>
                    <pre id="aiContextBlock"\s
                         style="background:#0f172a; border:1px solid #334155; border-radius:8px;
                                padding:16px; font-size:12px; color:#94a3b8; white-space:pre-wrap;
                                word-break:break-word; max-height:300px; overflow-y:auto; line-height:1.6">
                        Loading...
                    </pre>
                </div>
    </div>

    <script>
            let currentPage = 0;
            const PAGE_SIZE = 10;
            let allFindings = [];
        async function loadData() {
            try {
                const [metricsRes, findingsRes, reportRes] = await Promise.all([
                    fetch('/metrics'),
                    fetch('/findings'),
                    fetch('/report')
                ]);
                const metrics  = await metricsRes.json();
                const findings = await findingsRes.json();
                const report   = await reportRes.json();

                updateMetrics(metrics);
                updateFindings(findings);
                updateScore(report.overallScore);
                document.getElementById('lastUpdate').textContent =
                    'Last updated: ' + new Date().toLocaleTimeString();
            } catch (e) {
                document.getElementById('findingsList').innerHTML =
                    '<div class="error">Failed to load data: ' + e.message + '</div>';
            }
        }
        
        async function loadAIContext() {
                    try {
                        const res = await fetch('/ai-context');
                        const data = await res.json();
                        document.getElementById('aiContextBlock').textContent = data.aiContext;
                    } catch (e) {
                        document.getElementById('aiContextBlock').textContent = 'Failed to load: ' + e.message;
                    }
                }
                
                function copyAIContext() {
                    const text = document.getElementById('aiContextBlock').textContent;
                    navigator.clipboard.writeText(text).then(() => {
                        const btn = event.target;
                        btn.textContent = '✅ Copied!';
                        setTimeout(() => btn.textContent = '📋 Copy', 2000);
                    });
                }
                

        function updateScore(score) {
            const badge = document.getElementById('scoreBadge');
            badge.textContent = 'Score: ' + score + '/100';
            if (score >= 80)      { badge.style.background = '#14532d'; badge.style.color = '#86efac'; }
            else if (score >= 60) { badge.style.background = '#422006'; badge.style.color = '#fcd34d'; }
            else                  { badge.style.background = '#450a0a'; badge.style.color = '#fca5a5'; }
        }

        function updateMetrics(m) {
            document.getElementById('totalSQL').textContent   = m.totalSQLIntercepted ?? 0;
            document.getElementById('slowCount').textContent  = m.slowQueryCount ?? 0;
            document.getElementById('nPlusOne').textContent   = m.nPlusOneDetected ?? 0;
            document.getElementById('errorRate').textContent  =
                (m.errorRate ?? 0).toFixed(1) + '%';

            // Latency bars
            const max = Math.max(m.p99Latency ?? 1, 1);
            document.getElementById('latencyBars').innerHTML = `
                <div class="latency-row">
                    <span class="latency-label">P50</span>
                    <div class="latency-bar-wrap">
                        <div class="latency-bar" style="width:${(m.p50Latency/max*100).toFixed(1)}%"></div>
                    </div>
                    <span class="latency-value">${m.p50Latency ?? 0}ms</span>
                </div>
                <div class="latency-row">
                    <span class="latency-label">P95</span>
                    <div class="latency-bar-wrap">
                        <div class="latency-bar" style="width:${(m.p95Latency/max*100).toFixed(1)}%;background:#f59e0b"></div>
                    </div>
                    <span class="latency-value">${m.p95Latency ?? 0}ms</span>
                </div>
                <div class="latency-row">
                    <span class="latency-label">P99</span>
                    <div class="latency-bar-wrap">
                        <div class="latency-bar" style="width:100%;background:#ef4444"></div>
                    </div>
                    <span class="latency-value">${m.p99Latency ?? 0}ms</span>
                </div>`;

            // Top tables
            const entries = Object.entries(m.topTablesByQueryFrequency ?? {});
            if (entries.length === 0) {
                document.getElementById('topTablesBody').innerHTML =
                    '<tr><td colspan="2" class="empty">No data</td></tr>';
            } else {
                document.getElementById('topTablesBody').innerHTML =
                    entries.map(([t, c]) =>
                        `<tr><td>${t}</td><td>${c}</td></tr>`).join('');
            }
        }

        function updateFindings(findings) {
                     if (!findings || findings.length === 0) {
                         document.getElementById('findingsList').innerHTML =
                             '<div class="empty"> No findings — looking good!</div>';
                         return;
                     }
                     const order = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, WARNING: 3 };
                     allFindings = findings.sort((a, b) =>
                         (order[a.severity] ?? 9) - (order[b.severity] ?? 9));
                     currentPage = 0;
                     renderPage();
        }
        function renderPage() {
                    const start = currentPage * PAGE_SIZE;
                    const end = start + PAGE_SIZE;
                    const page = allFindings.slice(start, end);
                    const total = allFindings.length;
                
                    document.getElementById('findingsList').innerHTML = page.map(f => `
                        <div class="finding-item">
                            <span class="badge ${f.severity}">${f.severity}</span>
                            <div class="finding-content">
                                <div class="finding-rule">${f.rule}${f.table ? ' · ' + f.table : ''}${f.column ? '.' + f.column : ''}</div>
                                <div class="finding-msg">${f.message}</div>
                                ${f.recommendation ? `<div class="finding-rec">💡 ${f.recommendation}</div>` : ''}
                                ${f.calledFrom && f.calledFrom !== 'Schema analysis — no call site'
                                    ? `<div class="finding-rec" style="color:#94a3b8"> ${f.calledFrom}</div>`
                                    : f.calledFrom === 'Schema analysis — no call site'
                                    ? `<div class="finding-rec" style="color:#475569">️ ${f.calledFrom}</div>`
                                    : ''}
                            </div>
                        </div>`).join('');
                
                    // Pagination controls
                    const hasNext = end < total;
                    const hasPrev = currentPage > 0;
                    document.getElementById('findingsList').innerHTML += `
                        <div style="display:flex; align-items:center; justify-content:space-between;
                                    margin-top:12px; padding-top:12px; border-top:1px solid #334155;">
                            <span style="font-size:12px; color:#64748b">
                                Hiển thị ${start + 1}–${Math.min(end, total)} / ${total} findings
                            </span>
                            <div style="display:flex; gap:8px;">
                                ${hasPrev ? `<button onclick="prevPage()" style="background:#1e293b; color:#e2e8f0;
                                    border:1px solid #334155; padding:6px 12px; border-radius:6px; cursor:pointer">
                                    ← Trước</button>` : ''}
                                ${hasNext ? `<button onclick="nextPage()" style="background:#3b82f6; color:white;
                                    border:none; padding:6px 12px; border-radius:6px; cursor:pointer">
                                    Tiếp →</button>` : ''}
                            </div>
                        </div>`;
                }
                
                function nextPage() {
                    currentPage++;
                    renderPage();
                }
                
                function prevPage() {
                    currentPage--;
                    renderPage();
                }

        // Load ngay khi mở và auto refresh mỗi 5 giây
        loadData();
        loadAIContext();
        setInterval(loadData, 5000);
    </script>
</body>
</html>
""";
  }
}