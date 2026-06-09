package com.dbquality.explain.impl;

import com.dbquality.constant.Constant.DatabaseName;
import com.dbquality.explain.ExplainParser;
import com.dbquality.explain.ExplainResult;
import com.dbquality.rule.Finding;
import com.dbquality.rule.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * ExplainParser cho MySQL — parse output của {@code EXPLAIN FORMAT=JSON}.
 * Phát hiện: Full Table Scan, thiếu index, full index scan.
 */
public class MySQLExplainParser implements ExplainParser {

  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public boolean supports(String databaseProductName) {
    if (databaseProductName == null) return false;
    String upper = databaseProductName.toUpperCase();
    return upper.contains(DatabaseName.MySQL);
  }

  @Override
  public ExplainResult parse(String explainOutput) {
    List<Finding> findings = new ArrayList<>();
    try {
      JsonNode root = mapper.readTree(explainOutput);
      JsonNode queryBlock = root.path("query_block");
      collectTableFindings(queryBlock, findings);
    } catch (Exception e) {
      // Parse thất bại — trả về result rỗng không có findings
    }
    return new ExplainResult(findings, explainOutput, "MySQL");
  }

  /**
   * Duyệt đệ quy JsonNode để tìm tất cả table access nodes.
   * Xử lý cả single table, nested_loop (JOIN), ordering_operation, grouping_operation.
   */
  private void collectTableFindings(JsonNode node, List<Finding> findings) {
    if (node == null || node.isMissingNode()) return;

    // Table access node — có field "table_name"
    if (node.has("table_name")) {
      analyzeTableNode(node, findings);
      return;
    }

    // Xử lý các container nodes
    if (node.has("table")) {
      collectTableFindings(node.get("table"), findings);
    }
    if (node.has("nested_loop")) {
      for (JsonNode item : node.get("nested_loop")) {
        collectTableFindings(item, findings);
      }
    }
    if (node.has("ordering_operation")) {
      collectTableFindings(node.get("ordering_operation"), findings);
    }
    if (node.has("grouping_operation")) {
      collectTableFindings(node.get("grouping_operation"), findings);
    }
    if (node.has("duplicates_removal")) {
      collectTableFindings(node.get("duplicates_removal"), findings);
    }
  }

  /**
   * Phân tích một table access node và tạo findings nếu có vấn đề.
   */
  private void analyzeTableNode(JsonNode tableNode, List<Finding> findings) {
    String tableName  = tableNode.path("table_name").asText("unknown");
    String accessType = tableNode.path("access_type").asText("").toUpperCase();
    long   rows       = tableNode.path("rows_examined_per_scan").asLong(0);
    boolean hasKey    = !tableNode.path("key").isMissingNode()
        && !tableNode.path("key").isNull();
    JsonNode possibleKeys = tableNode.path("possible_keys");

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

    // Full Index Scan — có dùng index nhưng vẫn scan toàn bộ
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

    // Có possible_keys nhưng không dùng index nào
    if (!hasKey && !possibleKeys.isMissingNode() && possibleKeys.isArray()
        && possibleKeys.size() > 0) {
      findings.add(Finding.builder()
          .rule("INDEX_NOT_USED")
          .severity(Severity.HIGH)
          .table(tableName)
          .message("Bảng `" + tableName + "` có " + possibleKeys.size()
              + " index khả dụng nhưng không có index nào được sử dụng")
          .recommendation("Kiểm tra lại điều kiện WHERE — có thể do implicit type cast hoặc function trên cột index")
          .calledFrom("Execution plan analysis")
          .build());
    }
  }
}