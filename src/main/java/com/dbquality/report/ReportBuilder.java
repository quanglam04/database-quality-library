package com.dbquality.report;

import com.dbquality.ai.LLMProvider;
import com.dbquality.ai.LLMProviderFactory;
import com.dbquality.collector.DDLCollector;
import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;
import com.dbquality.collector.SQLRecord;
import com.dbquality.config.QualityConfig;
import com.dbquality.constant.Constant;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.rule.Finding;
import com.dbquality.rule.RuleEngine;
import com.dbquality.rule.Severity;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tổng hợp toàn bộ dữ liệu thu thập được thành QualityReport.
 * Bao gồm findings từ rule engine, metrics, scoring tổng thể.
 */
public class ReportBuilder {

  private final QualityConfig config;
  private final DDLCollector ddlCollector;
  private final RuleEngine ruleEngine;
  private final LLMProvider llmProvider;
  private volatile String cachedAiInsights = null;
  private volatile boolean aiCallInProgress = false;

  public ReportBuilder(QualityConfig config) {
    this.config = config;
    this.ddlCollector = new DDLCollector();
    this.ruleEngine = RuleEngine.withDefaultRules(
        config.getSlowQueryThresholdMs(),
        config.getNPlusOneThreshold()
    );
    this.llmProvider = LLMProviderFactory.create(config);
  }

  /**
   * Build report đầy đủ từ connection và SQL context hiện tại.
   *
   * @param connection JDBC connection để thu thập DDL
   * @param sqlContext SQL records đã được thu thập
   * @return QualityReport đầy đủ
   */
  public QualityReport build(Connection connection, SQLContext sqlContext)
      throws SQLException {

    // Thu thập DDL
    DDLContext ddlContext = ddlCollector.collect(connection);

    // Chạy rule engine
    List<Finding> findings = ruleEngine.analyze(ddlContext, sqlContext);

    // Tính metrics
    MetricsReport metrics = buildMetrics(sqlContext, findings);

    // Tính score
    int score = calculateScore(findings);

    // Build AI-ready context
    String aiContext = buildAIContext(ddlContext, sqlContext, findings, metrics);
    boolean hasEnoughData = !findings.isEmpty() || !sqlContext.getRecords().isEmpty();
    if (llmProvider != null && llmProvider.isAvailable()
        && cachedAiInsights == null
        && !aiCallInProgress
        && hasEnoughData) {
      aiCallInProgress = true;
      final String aiContextFinal = aiContext;
      new Thread(() -> {
        try {
          System.out.println("[DB Quality] Calling " + llmProvider.getProviderName() + "...");
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
            .filter(f -> isDDLFinding(f))
            .collect(Collectors.toList()))
        .sqlFindings(findings.stream()
            .filter(f -> !isDDLFinding(f))
            .collect(Collectors.toList()))
        .topSlowQueries(sqlContext.getRecords().stream()
            .filter(r -> r.getExecutionTime() >= config.getSlowQueryThresholdMs())
            .sorted((a, b) -> Long.compare(b.getExecutionTime(), a.getExecutionTime()))
            .limit(10)
            .collect(Collectors.toList()))
        .metrics(metrics)
        .aiReadyContext(aiContext)
        .aiInsights(aiInsights)
        .build();
  }

  /**
   * Reset cache AI để trigger gọi lại LLM ở lần build() tiếp theo.
   * Được gọi khi người dùng bấm nút "Refresh AI" trên dashboard.
   */
  public void resetAiCache() {
    if (!aiCallInProgress) {
      cachedAiInsights = null;
    }
  }

  // ── Metrics ───────────────────────────────────────────────────────

  private MetricsReport buildMetrics(SQLContext sqlContext, List<Finding> findings) {
    List<Long> times = sqlContext.getRecords().stream()
        .map(r -> r.getExecutionTime())
        .sorted()
        .collect(Collectors.toList());

    long p50 = percentile(times, 50);
    long p95 = percentile(times, 95);
    long p99 = percentile(times, 99);

    int total = sqlContext.getRecords().size();
    int failed = (int) sqlContext.getRecords().stream()
        .filter(r -> !r.isSuccess()).count();
    int slow = (int) sqlContext.getRecords().stream()
        .filter(r -> r.getExecutionTime() >= config.getSlowQueryThresholdMs()).count();
    int nPlusOne = (int) sqlContext.getRecords().stream()
        .filter(r -> isApplicationSQL(r.getSql()))
        .collect(Collectors.groupingBy(SQLRecord::getSql))
        .entrySet().stream()
        .filter(e -> e.getValue().size() > config.getNPlusOneThreshold())
        .count();


    double errorRate = total > 0 ? (double) failed / total * 100 : 0;

    // Top tables by query frequency
    Map<String, Long> tableFrequency = sqlContext.getRecords().stream()
        .flatMap(r -> extractTableNames(r.getSql()).stream())
        .collect(Collectors.groupingBy(
            t -> t.toUpperCase(),
            Collectors.counting()
        ));

    // Convert Long -> Integer cho MetricsReport
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
        .totalSQLIntercepted(total)
        .slowQueryCount(slow)
        .nPlusOneDetected(nPlusOne)
        .p50Latency(p50)
        .p95Latency(p95)
        .p99Latency(p99)
        .errorRate(errorRate)
        .topTablesByQueryFrequency(topTables)
        .build();
  }

  // ── Scoring ───────────────────────────────────────────────────────

  /**
   * Tính điểm chất lượng tổng thể từ 0-100.
   *
   * Mỗi finding trừ điểm theo severity.
   *
   */
  private int calculateScore(List<Finding> findings) {
    if (findings.isEmpty()) return 100;

    // Đếm số findings theo từng severity
    long critical = findings.stream().filter(f -> f.getSeverity() == Severity.CRITICAL).count();
    long high     = findings.stream().filter(f -> f.getSeverity() == Severity.HIGH).count();
    long medium   = findings.stream().filter(f -> f.getSeverity() == Severity.MEDIUM).count();
    long warning  = findings.stream().filter(f -> f.getSeverity() == Severity.WARNING).count();

    // Trừ điểm theo severity nhưng có giới hạn tối đa mỗi loại
    int deduction = 0;
    deduction += Math.min(critical * 20, 60); // tối đa -60 cho CRITICAL
    deduction += Math.min(high     * 10, 30); // tối đa -30 cho HIGH
    deduction += Math.min(medium   *  3, 15); // tối đa -15 cho MEDIUM
    deduction += Math.min(warning  *  1,  5); // tối đa -5  cho WARNING

    return Math.max(0, 100 - deduction);
  }

  // ── AI context ──────────────────────────────────────────────

  private String buildAIContext(DDLContext ddl, SQLContext sql,
      List<Finding> findings, MetricsReport metrics) {
    StringBuilder sb = new StringBuilder();

    // Prompt
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
    sql.getRecords().stream()
        .sorted((a, b) -> Long.compare(b.getExecutionTime(), a.getExecutionTime()))
        .limit(5)
        .forEach(r -> sb.append("- ").append(r.getExecutionTime())
            .append("ms: ").append(r.getSql()).append("\n")
            .append("  Called from: ").append(r.getCalledFrom()).append("\n"));

    return sb.toString();
  }

  // ── Helpers ───────────────────────────────────────────────────────

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

    // Regex tìm table name sau FROM/JOIN/INTO/UPDATE
    // Word boundary \b đảm bảo không match partial word
    // Loại bỏ subquery. không lấy nếu sau keyword là dấu (
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
        "(?:FROM|JOIN|INTO|UPDATE)\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
        java.util.regex.Pattern.CASE_INSENSITIVE
    );

    java.util.regex.Matcher matcher = pattern.matcher(sql);
    while (matcher.find()) {
      String tableName = matcher.group(1);
      // Bỏ qua SQL keywords bị nhận nhầm thành table name
      if (!isSQLKeyword(tableName)) {
        tables.add(tableName);
      }
    }
    return tables;
  }

  private boolean isSQLKeyword(String word) {
    java.util.Set<String> keywords = java.util.Set.of(
        "SELECT", "WHERE", "AND", "OR", "NOT", "IN", "IS",
        "NULL", "SET", "VALUES", "ON", "AS", "BY", "ORDER",
        "GROUP", "HAVING", "LIMIT", "OFFSET", "INNER", "LEFT",
        "RIGHT", "OUTER", "CROSS", "NATURAL", "FULL"
    );
    return keywords.contains(word.toUpperCase());
  }

  private boolean isApplicationSQL(String sql) {
    if (sql == null) return false;
    String upper = sql.trim().toUpperCase();
    return upper.startsWith("SELECT")
        || upper.startsWith("INSERT")
        || upper.startsWith("UPDATE")
        || upper.startsWith("DELETE");
  }
}