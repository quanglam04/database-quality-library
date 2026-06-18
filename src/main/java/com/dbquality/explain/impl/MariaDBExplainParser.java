package com.dbquality.explain.impl;

import com.dbquality.constant.Constant.DatabaseName;
import com.dbquality.explain.ExplainParser;
import com.dbquality.explain.ExplainResult;
import com.dbquality.rule.Finding;
import com.dbquality.constant.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * ExplainParser cho MariaDB — parse output của {@code EXPLAIN FORMAT=JSON}.
 * Tương tự MySQL nhưng có thêm các field thực tế từ ANALYZE:
 * {@code r_rows} (actual rows đọc), {@code r_filtered} (actual filter %).
 */
public class MariaDBExplainParser implements ExplainParser {

  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public boolean supports(String databaseProductName) {
    if (databaseProductName == null) return false;
    return databaseProductName.toUpperCase().contains(DatabaseName.MariaDB);
  }

  @Override
  public ExplainResult parse(String explainOutput) {
    List<Finding> findings = new ArrayList<>();
    try {
      JsonNode root = mapper.readTree(explainOutput);
      JsonNode queryBlock = root.path("query_block");
      collectTableFindings(queryBlock, findings);
    } catch (Exception e) {
      // Parse thất bại — trả về result rỗng
    }
    return new ExplainResult(findings, explainOutput, "MariaDB");
  }

  private void collectTableFindings(JsonNode node, List<Finding> findings) {
    if (node == null || node.isMissingNode()) return;

    if (node.has("table_name")) {
      analyzeTableNode(node, findings);
      return;
    }

    if (node.has("table"))              collectTableFindings(node.get("table"), findings);
    if (node.has("nested_loop"))        for (JsonNode i : node.get("nested_loop"))    collectTableFindings(i, findings);
    if (node.has("ordering_operation")) collectTableFindings(node.get("ordering_operation"), findings);
    if (node.has("grouping_operation")) collectTableFindings(node.get("grouping_operation"), findings);
    if (node.has("duplicates_removal")) collectTableFindings(node.get("duplicates_removal"), findings);
  }

  private void analyzeTableNode(JsonNode tableNode, List<Finding> findings) {
    String tableName  = tableNode.path("table_name").asText("unknown");
    String accessType = tableNode.path("access_type").asText("").toUpperCase();
    boolean hasKey    = !tableNode.path("key").isMissingNode()
        && !tableNode.path("key").isNull();
    JsonNode possibleKeys = tableNode.path("possible_keys");

    // MariaDB có r_rows (actual rows) từ ANALYZE — dùng nếu có, fallback về rows
    long rows = tableNode.has("r_rows")
        ? tableNode.path("r_rows").asLong(0)
        : tableNode.path("rows").asLong(0);

    // Full Table Scan
    if ("ALL".equals(accessType)) {
      findings.add(Finding.builder()
          .rule("FULL_TABLE_SCAN")
          .severity(rows > 1000 ? Severity.HIGH : Severity.MEDIUM)
          .table(tableName)
          .message("Full Table Scan trên bảng `" + tableName + "` — đọc " + rows + " rows")
          .recommendation("Thêm index cho các cột trong WHERE/JOIN của câu query này")
          .calledFrom("Execution plan analysis")
          .build());
    }

    // Full Index Scan
    if ("INDEX".equals(accessType)) {
      findings.add(Finding.builder()
          .rule("FULL_INDEX_SCAN")
          .severity(Severity.MEDIUM)
          .table(tableName)
          .message("Full Index Scan trên bảng `" + tableName + "` — index được dùng nhưng scan " + rows + " rows")
          .recommendation("Cân nhắc composite index hoặc covering index cho câu query này")
          .calledFrom("Execution plan analysis")
          .build());
    }

    // Có possible_keys nhưng không dùng
    if (!hasKey && !possibleKeys.isMissingNode()
        && possibleKeys.isArray() && possibleKeys.size() > 0) {
      findings.add(Finding.builder()
          .rule("INDEX_NOT_USED")
          .severity(Severity.HIGH)
          .table(tableName)
          .message("Bảng `" + tableName + "` có " + possibleKeys.size()
              + " index khả dụng nhưng không có index nào được sử dụng")
          .recommendation("Kiểm tra điều kiện WHERE — có thể do implicit type cast hoặc function trên cột index")
          .calledFrom("Execution plan analysis")
          .build());
    }

    // MariaDB specific — r_filtered thấp (<10%) dù có index → index không selective
    if (hasKey && tableNode.has("r_filtered")) {
      double rFiltered = tableNode.path("r_filtered").asDouble(100.0);
      if (rFiltered < 10.0 && rows > 100) {
        findings.add(Finding.builder()
            .rule("LOW_INDEX_SELECTIVITY")
            .severity(Severity.WARNING)
            .table(tableName)
            .message("Index trên bảng `" + tableName + "` có selectivity thấp ("
                + String.format("%.1f", rFiltered) + "% rows qua filter)")
            .recommendation("Xem xét composite index với cột có cardinality cao hơn")
            .calledFrom("Execution plan analysis")
            .build());
      }
    }
  }
}