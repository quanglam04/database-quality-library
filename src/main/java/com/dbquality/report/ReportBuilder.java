package com.dbquality.report;

import com.dbquality.ai.LLMProvider;
import com.dbquality.ai.LLMProviderFactory;
import com.dbquality.collector.DDLCollector;
import com.dbquality.collector.DDLContext;
import com.dbquality.collector.QueryMetric;
import com.dbquality.collector.QueryMetricsStore;
import com.dbquality.config.QualityConfig;
import com.dbquality.constant.Constant;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.constant.Severity;
import com.dbquality.explain.ExplainCache;
import com.dbquality.explain.ExplainResult;
import com.dbquality.rule.Finding;
import com.dbquality.rule.RuleEngine;
import com.dbquality.util.SQLFilter;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tổng hợp toàn bộ dữ liệu thu thập được thành QualityReport.
 *
 * <p>Sau Phase 3 refactor:</p>
 * <ul>
 *   <li>Input là {@link QueryMetricsStore} (aggregated) thay vì SQLContext</li>
 *   <li>EXPLAIN cho slow queries lấy từ {@link ExplainCache} (đã cache sẵn)</li>
 *   <li>Rule engine chạy qua scheduled job, ReportBuilder chỉ format output</li>
 * </ul>
 */
public class ReportBuilder {

  private final QualityConfig config;
  private final DDLCollector ddlCollector;
  private final RuleEngine ruleEngine;
  private final ExplainCache explainCache;
  private final LLMProvider llmProvider;
  private volatile String cachedAiInsights = null;
  private volatile boolean aiCallInProgress = false;

  public ReportBuilder(QualityConfig config, ExplainCache explainCache) {
    this.config = config;
    this.ddlCollector = new DDLCollector();
    this.explainCache = explainCache;
    this.ruleEngine = RuleEngine.withDefaultRules(
        config.getSlowQueryThresholdMs(),
        config.getNPlusOneThreshold(),
        explainCache
    );
    this.llmProvider = LLMProviderFactory.create(config);
  }

  // Constructor backward compat — không có ExplainCache
  public ReportBuilder(QualityConfig config) {
    this(config, null);
  }

  /**
   * Build report từ connection và metrics store.
   */
  public QualityReport build(Connection connection, QueryMetricsStore metricsStore)
      throws SQLException {

    // Thu thập DDL
    DDLContext ddlContext = ddlCollector.collect(connection);

    // Chạy rule engine
    List<Finding> findings = ruleEngine.analyze(ddlContext, metricsStore);

    // Tính metrics
    MetricsReport metrics = buildMetrics(metricsStore, findings);

    // Tính score
    int score = calculateScore(findings);

    // Build AI-ready context
    String aiContext = buildAIContext(ddlContext, metricsStore, findings, metrics);
    boolean hasEnoughData = !findings.isEmpty()
        || metricsStore.getUniquePatternCount() > 0;
    if (llmProvider != null && llmProvider.isAvailable()
        && cachedAiInsights == null
        && !aiCallInProgress
        && hasEnoughData) {
      aiCallInProgress = true;
      final String aiContextFinal = aiContext;
      new Thread(() -> {
        try {
          System.out.println("[DB Quality] Calling "
              + llmProvider.getProviderName() + "...");
          cachedAiInsights = llmProvider.call(aiContextFinal);
          System.out.println("[DB Quality] AI analysis complete.");
        } finally {
          aiCallInProgress = false;
        }
      }).start();
    }
    String aiInsights = cachedAiInsights;
    if (aiInsights == null && aiCallInProgress) {
      aiInsights = "__LOADING__";
    }

    return QualityReport.builder()
        .reportGeneratedAt(Instant.now())
        .overallScore(score)
        .ddlFindings(findings.stream()
            .filter(this::isDDLFinding)
            .collect(Collectors.toList()))
        .sqlFindings(findings.stream()
            .filter(f -> !isDDLFinding(f))
            .collect(Collectors.toList()))
        .slowQueries(buildSlowQueryReports(metricsStore))
        .metrics(metrics)
        .aiReadyContext(aiContext)
        .aiInsights(aiInsights)
        .build();
  }

  public void resetAiCache() {
    if (!aiCallInProgress) {
      cachedAiInsights = null;
    }
  }

  //  Metrics

  private MetricsReport buildMetrics(QueryMetricsStore metricsStore,
      List<Finding> findings) {

    // Tổng số lần SQL được thực thi (tính cả lặp lại)
    long total = metricsStore.getTotalExecutions();

    // Tính P50/P95/P99 từ duration của TỪNG LẦN thực thi
    // Approximation: với mỗi metric, duration ~ uniform trong [min, max]
    // → expand thành callCount điểm theo avg để đủ data percentile
    List<Long> times = new ArrayList<>();
    for (QueryMetric m : metricsStore.getAllMetrics()) {
      long avg = (long) m.getAvgDurationMs();
      for (long i = 0; i < m.getCallCount(); i++) {
        times.add(avg);
      }
    }
    Collections.sort(times);

    long p50 = percentile(times, 50);
    long p95 = percentile(times, 95);
    long p99 = percentile(times, 99);

    // Slow query count: số pattern có max duration vượt threshold
    int slow = (int) metricsStore.getAllMetrics().stream()
        .filter(m -> m.getMaxDurationMs() >= config.getSlowQueryThresholdMs())
        .count();

    // N+1 count: số pattern có callCount > threshold
    int nPlusOne = (int) metricsStore.getAllMetrics().stream()
        .filter(m -> SQLFilter.isDMLStatement(m.getSqlPattern()))
        .filter(m -> m.getCallCount() > config.getNPlusOneThreshold())
        .count();

    // Error rate: QueryMetricsStore không track failed executions
    // (Phase 1 quyết định chỉ record success vào metrics).
    // Tạm thời set 0, có thể extend QueryMetric thêm failedCount nếu cần.
    double errorRate = 0;

    // Top tables by query frequency
    Map<String, Long> tableFrequency = new HashMap<>();
    for (QueryMetric m : metricsStore.getAllMetrics()) {
      for (String table : extractTableNames(m.getSqlPattern())) {
        tableFrequency.merge(table.toUpperCase(), m.getCallCount(), Long::sum);
      }
    }

    Map<String, Integer> topTables = tableFrequency.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(10)
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            e -> e.getValue().intValue(),
            (a, b) -> a,
            LinkedHashMap::new
        ));

    return MetricsReport.builder()
        .totalSQLIntercepted((int) total)
        .slowQueryCount(slow)
        .nPlusOneDetected(nPlusOne)
        .p50Latency(p50)
        .p95Latency(p95)
        .p99Latency(p99)
        .errorRate(errorRate)
        .topTablesByQueryFrequency(topTables)
        .build();
  }

  //  Scoring

  private int calculateScore(List<Finding> findings) {
    if (findings.isEmpty()) return 100;
    int deduction = 0;
    for (Severity severity : Severity.values()) {
      long count = findings.stream()
          .filter(f -> f.getSeverity() == severity)
          .count();
      deduction += Math.min(count * severity.getWeight(), severity.getMaxDeduction());
    }
    return Math.max(0, 100 - deduction);
  }

  //  AI context

  private String buildAIContext(DDLContext ddl, QueryMetricsStore metricsStore,
      List<Finding> findings, MetricsReport metrics) {
    StringBuilder sb = new StringBuilder();

    sb.append("Bạn là chuyên gia database và performance optimization cho hệ thống Java/Spring Boot.\n");
    sb.append("Dựa trên báo cáo chất lượng database dưới đây, hãy:\n");
    sb.append("1. Phân tích các vấn đề nghiêm trọng nhất và giải thích tại sao chúng nguy hiểm\n");
    sb.append("2. Đề xuất thứ tự ưu tiên fix theo impact với production (High/Medium/Low)\n");
    sb.append("3. Với mỗi vấn đề HIGH priority: cung cấp SQL fix cụ thể hoặc code Java mẫu\n");
    sb.append("4. Ước tính mức độ cải thiện performance sau khi fix (ví dụ: giảm X% query time)\n");
    sb.append("5. Nhận xét tổng thể và điểm cần theo dõi lâu dài\n");
    sb.append("---\n\n");

    sb.append(" DATABASE QUALITY REPORT \n\n");

    sb.append("## SCHEMA SUMMARY\n");
    sb.append("Tables: ").append(ddl.getTables().size()).append("\n");
    ddl.getTables().forEach(t ->
        sb.append("- ").append(t.getName())
            .append(" (").append(t.getColumns().size()).append(" columns")
            .append(t.hasPrimaryKey() ? "" : ", NO PRIMARY KEY")
            .append(")\n"));

    sb.append("\n## METRICS\n");
    sb.append("Total SQL intercepted: ").append(metrics.getTotalSQLIntercepted()).append("\n");
    sb.append("Slow queries: ").append(metrics.getSlowQueryCount()).append("\n");
    sb.append("N+1 detected: ").append(metrics.getNPlusOneDetected()).append("\n");
    sb.append("P50/P95/P99 latency: ")
        .append(metrics.getP50Latency()).append("ms / ")
        .append(metrics.getP95Latency()).append("ms / ")
        .append(metrics.getP99Latency()).append("ms\n");
    sb.append("Error rate: ").append(String.format("%.1f", metrics.getErrorRate())).append("%\n");

    sb.append("\n## TOP FINDINGS\n");
    findings.stream()
        .sorted(Comparator.comparing(f -> f.getSeverity().ordinal()))
        .limit(10)
        .forEach(f -> sb.append("- [").append(f.getSeverity()).append("] ")
            .append(f.getRule()).append(": ").append(f.getMessage()).append("\n"));

    sb.append("\n## TOP SLOW QUERIES\n");
    metricsStore.getAllMetrics().stream()
        .sorted((a, b) -> Long.compare(b.getMaxDurationMs(), a.getMaxDurationMs()))
        .limit(5)
        .forEach(m -> sb.append("- max ").append(m.getMaxDurationMs())
            .append("ms, avg ").append(String.format("%.1f", m.getAvgDurationMs()))
            .append("ms, count ").append(m.getCallCount())
            .append(": ").append(m.getSqlPattern()).append("\n")
            .append("  Called from: ").append(m.getMostFrequentCalledFrom()).append("\n"));

    return sb.toString();
  }

  //  Helpers

  private long percentile(List<Long> sorted, int percentile) {
    if (sorted.isEmpty()) return 0;
    int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
    return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
  }

  private boolean isDDLFinding(Finding f) {
    return f.getRule().equals(RuleName.MissingPrimaryKey)
        || f.getRule().equals(RuleName.UnindexedForeignKey)
        || f.getRule().equals(RuleName.NullableRisk)
        || f.getRule().equals(RuleName.SuspiciousDataType)
        || f.getRule().equals(RuleName.UnusedIndex)
        || f.getRule().equals(RuleName.MissingIndexSuggestion);
  }

  private List<String> extractTableNames(String sql) {
    List<String> tables = new ArrayList<>();
    if (sql == null) return tables;

    Pattern pattern = Pattern.compile(
        Constant.SQL_TABLE_NAME_PATTERN,
        Pattern.CASE_INSENSITIVE
    );

    java.util.regex.Matcher matcher = pattern.matcher(sql);
    while (matcher.find()) {
      String tableName = matcher.group(1);
      if (!SQLFilter.isSQLKeyword(tableName)) {
        tables.add(tableName);
      }
    }
    return tables;
  }

  /**
   * Build slow query reports từ metrics store + ExplainCache.
   * Không chạy EXPLAIN mới — chỉ đọc từ cache đã được warm-up
   * bởi ScheduledAnalysisJob.
   */
  private List<SlowQueryReport> buildSlowQueryReports(QueryMetricsStore metricsStore) {
    List<QueryMetric> topSlow = metricsStore.getAllMetrics().stream()
        .filter(m -> m.getMaxDurationMs() >= config.getSlowQueryThresholdMs())
        .sorted((a, b) -> Long.compare(b.getMaxDurationMs(), a.getMaxDurationMs()))
        .limit(10)
        .collect(Collectors.toList());

    if (topSlow.isEmpty()) return List.of();

    List<SlowQueryReport> result = new ArrayList<>();
    for (QueryMetric metric : topSlow) {
      ExplainResult explain = null;
      if (explainCache != null) {
        explain = explainCache.getOrCompute(metric.getSqlPattern()).orElse(null);
      }
      result.add(buildSlowQueryReport(metric, explain));
    }
    return result;
  }

  /**
   * Adapter: tạo SlowQueryReport từ QueryMetric.
   * SlowQueryReport hiện tại nhận SQLRecord — cần tạo SQLRecord giả từ metric.
   */
  private SlowQueryReport buildSlowQueryReport(QueryMetric metric, ExplainResult explain) {
    com.dbquality.collector.SQLRecord record = com.dbquality.collector.SQLRecord.builder()
        .sql(metric.getSqlPattern())
        .executionTime(metric.getMaxDurationMs())
        .calledFrom(metric.getMostFrequentCalledFrom())
        .success(true)
        .build();
    return new SlowQueryReport(record, explain);
  }
}