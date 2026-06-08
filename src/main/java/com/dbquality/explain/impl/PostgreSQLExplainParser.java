package com.dbquality.explain.impl;

import com.dbquality.explain.ExplainParser;
import com.dbquality.explain.ExplainResult;
import com.dbquality.rule.Finding;
import com.dbquality.rule.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * ExplainParser cho PostgreSQL — parse output của {@code EXPLAIN (FORMAT JSON, ANALYZE)}.
 *
 * <p>Cấu trúc JSON của PostgreSQL khác hoàn toàn MySQL:
 * mảng ngoài cùng → {@code Plan} node → đệ quy qua {@code Plans} con.</p>
 *
 * <p>Phát hiện: Seq Scan (full table scan), Index Scan không selective,
 * nested loop với nhiều rows, và sort không dùng index.</p>
 */
public class PostgreSQLExplainParser implements ExplainParser {

  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public boolean supports(String databaseProductName) {
    if (databaseProductName == null) return false;
    return databaseProductName.toUpperCase().contains("POSTGRESQL");
  }

  @Override
  public ExplainResult parse(String explainOutput) {
    List<Finding> findings = new ArrayList<>();
    try {
      JsonNode root = mapper.readTree(explainOutput);
      // PostgreSQL trả về mảng — lấy phần tử đầu tiên
      JsonNode planWrapper = root.isArray() ? root.get(0) : root;
      JsonNode plan = planWrapper.path("Plan");
      collectPlanFindings(plan, findings);
    } catch (Exception e) {
      // Parse thất bại — trả về result rỗng
    }
    return new ExplainResult(findings, explainOutput, "PostgreSQL");
  }

  /**
   * Duyệt đệ quy Plan nodes.
   * Mỗi node có thể có {@code Plans} array chứa các sub-plans (JOIN, subquery...).
   */
  private void collectPlanFindings(JsonNode plan, List<Finding> findings) {
    if (plan == null || plan.isMissingNode()) return;

    analyzePlanNode(plan, findings);

    // Đệ quy vào sub-plans
    JsonNode subPlans = plan.path("Plans");
    if (subPlans.isArray()) {
      for (JsonNode subPlan : subPlans) {
        collectPlanFindings(subPlan, findings);
      }
    }
  }

  private void analyzePlanNode(JsonNode plan, List<Finding> findings) {
    String nodeType    = plan.path("Node Type").asText("");
    String relation    = plan.path("Relation Name").asText(null);
    long   actualRows  = plan.path("Actual Rows").asLong(0);
    long   planRows    = plan.path("Plan Rows").asLong(0);
    double actualLoops = plan.path("Actual Loops").asDouble(1);

    // Tổng rows thực tế = Actual Rows * Actual Loops
    long totalRows = (long) (actualRows * actualLoops);

    // ── Seq Scan — Full Table Scan ────────────────────────────────────
    if ("Seq Scan".equals(nodeType) && relation != null) {
      findings.add(Finding.builder()
          .rule("FULL_TABLE_SCAN")
          .severity(totalRows > 1000 ? Severity.HIGH : Severity.MEDIUM)
          .table(relation)
          .message("Seq Scan (Full Table Scan) trên bảng `" + relation
              + "` — đọc " + totalRows + " rows")
          .recommendation("Thêm index cho các cột trong WHERE/JOIN của câu query này")
          .calledFrom("Execution plan analysis")
          .build());
    }

    // ── Index Scan — có index nhưng scan nhiều rows ───────────────────
    if ("Index Scan".equals(nodeType) && relation != null && totalRows > 1000) {
      findings.add(Finding.builder()
          .rule("FULL_INDEX_SCAN")
          .severity(Severity.MEDIUM)
          .table(relation)
          .message("Index Scan trên bảng `" + relation
              + "` — scan " + totalRows + " rows qua index")
          .recommendation("Cân nhắc composite index hoặc covering index (Index Only Scan)")
          .calledFrom("Execution plan analysis")
          .build());
    }

    // ── Estimate lệch xa thực tế — planner không có đủ thống kê ──────
    if (planRows > 0 && actualRows > 0 && relation != null) {
      double ratio = (double) actualRows / planRows;
      if (ratio > 10 || ratio < 0.1) {
        findings.add(Finding.builder()
            .rule("PLANNER_ESTIMATE_MISMATCH")
            .severity(Severity.WARNING)
            .table(relation)
            .message("Planner ước tính " + planRows + " rows nhưng thực tế "
                + actualRows + " rows trên bảng `" + relation + "`")
            .recommendation("Chạy ANALYZE trên bảng để cập nhật thống kê cho query planner")
            .calledFrom("Execution plan analysis")
            .build());
      }
    }

    // ── Sort không dùng index ─────────────────────────────────────────
    if ("Sort".equals(nodeType)) {
      String sortMethod = plan.path("Sort Method").asText("");
      if (sortMethod.contains("external") || sortMethod.contains("disk")) {
        findings.add(Finding.builder()
            .rule("SORT_TO_DISK")
            .severity(Severity.HIGH)
            .message("Sort phải ghi xuống disk (`" + sortMethod
                + "`) — work_mem không đủ hoặc thiếu index cho ORDER BY")
            .recommendation("Tăng work_mem hoặc thêm index cho cột ORDER BY")
            .calledFrom("Execution plan analysis")
            .build());
      }
    }

    // ── Nested Loop với nhiều rows — tiềm ẩn N+1 ─────────────────────
    if ("Nested Loop".equals(nodeType) && totalRows > 10000) {
      findings.add(Finding.builder()
          .rule("NESTED_LOOP_LARGE")
          .severity(Severity.HIGH)
          .message("Nested Loop JOIN tạo ra " + totalRows
              + " rows — có thể gây N+1 hoặc thiếu index trên cột JOIN")
          .recommendation("Kiểm tra index trên cột JOIN, cân nhắc Hash Join hoặc Merge Join")
          .calledFrom("Execution plan analysis")
          .build());
    }
  }
}