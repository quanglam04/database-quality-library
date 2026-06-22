package com.dbquality.rule.impl;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.QueryMetricsStore;
import com.dbquality.collector.model.Index;
import com.dbquality.collector.model.Table;
import com.dbquality.constant.Constant.RuleName;
import com.dbquality.constant.Severity;
import com.dbquality.explain.ExplainCache;
import com.dbquality.explain.ExplainResult;
import com.dbquality.rule.Finding;
import com.dbquality.rule.MetricsBasedRule;
import com.dbquality.rule.RuleResult;
import com.dbquality.util.SchemaFilter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phát hiện index không được DB engine sử dụng dựa trên EXPLAIN result thực tế.
 *
 * <p>Logic:</p>
 * <ol>
 *   <li>Duyệt toàn bộ EXPLAIN cache, extract tên index đã được dùng (field "key"
 *       trong MySQL, "Index Name" trong PostgreSQL)</li>
 *   <li>Với mỗi index trong DDL, check có xuất hiện trong set "used indexes" không</li>
 *   <li>Index không xuất hiện → flag là unused</li>
 * </ol>
 *
 * <p>Khác với heuristic cũ (chỉ check cột của index có xuất hiện trong WHERE
 * text), rule này dùng kết quả EXPLAIN thật — chính xác 100%, không có false
 * positive do match cột nhầm.</p>
 *
 * <p><b>Lưu ý:</b> Vẫn dựa trên session hiện tại — index có thể được dùng cho
 * báo cáo cuối tháng mà session này không cover. Recommendation luôn nhắc
 * user verify trước khi xóa.</p>
 */
public class UnusedIndexRule implements MetricsBasedRule {

  // Pattern lấy tên index từ EXPLAIN raw output
  // MySQL: "key": "idx_name"
  // PostgreSQL: "Index Name": "idx_name"
  private static final Pattern INDEX_NAME_PATTERN = Pattern.compile(
      "\"(?:key|Index Name|index_name)\"\\s*:\\s*\"([^\"]+)\"",
      Pattern.CASE_INSENSITIVE
  );

  private final ExplainCache explainCache;

  public UnusedIndexRule(ExplainCache explainCache) {
    this.explainCache = explainCache;
  }

  @Override
  public String getName() {
    return RuleName.UnusedIndex;
  }

  @Override
  public Severity getSeverity() {
    return Severity.WARNING;
  }

  @Override
  public RuleResult analyze(DDLContext ddl, QueryMetricsStore metricsStore) {
    List<Finding> findings = new ArrayList<>();

    // Bỏ qua nếu cache rỗng — không có data để phân tích
    if (explainCache.size() == 0) return new RuleResult(findings);

    // Bước 1: Extract tất cả index đã được DB engine dùng từ EXPLAIN cache
    Set<String> usedIndexes = collectUsedIndexes();

    // Bước 2: Duyệt DDL, flag index không xuất hiện
    for (Table table : ddl.getTables()) {
      if (SchemaFilter.isSystemTable(table.getName())) continue;

      for (Index index : table.getIndexes()) {
        if (isPrimaryKeyIndex(index.getName())) continue;
        if (isUniqueConstraintIndex(index.getName())) continue;

        if (!usedIndexes.contains(index.getName().toUpperCase())) {
          findings.add(Finding.builder()
              .rule(getName())
              .severity(getSeverity())
              .table(table.getName())
              .message("Index " + index.getName() + " trên bảng " + table.getName()
                  + " không được DB engine sử dụng trong session này "
                  + "(theo kết quả EXPLAIN của " + explainCache.size()
                  + " query patterns)")
              .recommendation("Verify trên production qua DB system view "
                  + "(MySQL: sys.schema_unused_indexes, "
                  + "PostgreSQL: pg_stat_user_indexes) trước khi xóa — "
                  + "index có thể được dùng cho query batch/report không có trong session này")
              .calledFrom("Schema analysis — no call site")
              .build());
        }
      }
    }

    return new RuleResult(findings);
  }

  /**
   * Duyệt toàn bộ EXPLAIN raw output để extract tên index đã được dùng.
   * Dùng regex match field "key" / "Index Name" trong JSON output.
   */
  private Set<String> collectUsedIndexes() {
    Set<String> indexes = new HashSet<>();
    for (ExplainResult result : explainCache.getAll().values()) {
      String raw = result.getRawOutput();
      if (raw == null) continue;

      Matcher matcher = INDEX_NAME_PATTERN.matcher(raw);
      while (matcher.find()) {
        String indexName = matcher.group(1);
        if (indexName != null && !indexName.isBlank()) {
          indexes.add(indexName.toUpperCase());
        }
      }
    }
    return indexes;
  }

  private boolean isPrimaryKeyIndex(String indexName) {
    if (indexName == null) return false;
    String upper = indexName.toUpperCase();
    return upper.contains("PRIMARY")
        || upper.startsWith("PK_")
        || upper.endsWith("_PKEY");
  }

  private boolean isUniqueConstraintIndex(String indexName) {
    if (indexName == null) return false;
    String upper = indexName.toUpperCase();
    return upper.startsWith("UK_")
        || upper.startsWith("UQ_")
        || upper.contains("UNIQUE");
  }
}