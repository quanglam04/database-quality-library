package com.dbquality.explain.impl;

import com.dbquality.constant.Constant.DatabaseName;
import com.dbquality.constant.Severity;
import com.dbquality.explain.ExplainParser;
import com.dbquality.explain.ExplainResult;
import com.dbquality.rule.Finding;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * ExplainParser cho SQL Server — parse output của {@code SET SHOWPLAN_XML ON}.
 * Phát hiện: Table Scan, Clustered Index Scan, Index Scan, Key Lookup, Hash Match.
 *
 * <p>Cách dùng trong ứng dụng:</p>
 * <pre>
 *   SET SHOWPLAN_XML ON;
 *   GO
 *   SELECT * FROM orders WHERE status = 'PENDING';
 *   GO
 *   SET SHOWPLAN_XML OFF;
 * </pre>
 *
 * <p>Lưu ý: {@code SHOWPLAN_XML ON} không thực thi query thật —
 * chỉ trả về execution plan dự kiến. Dùng {@code STATISTICS XML ON}
 * nếu muốn plan thực tế sau khi query chạy.</p>
 */
public class SQLServerExplainParser implements ExplainParser {

  private static final String DB_TYPE = "SQL Server";

  @Override
  public boolean supports(String databaseProductName) {
    if (databaseProductName == null) return false;
    String upper = databaseProductName.toUpperCase();
    return upper.contains(DatabaseName.SQlServer)
        || upper.contains("MICROSOFT SQL SERVER")
        || upper.contains("SQL SERVER");
  }

  @Override
  public ExplainResult parse(String explainOutput) {
    List<Finding> findings = new ArrayList<>();
    if (explainOutput == null || explainOutput.isBlank()) {
      return new ExplainResult(findings, explainOutput, DB_TYPE);
    }

    try {
      Document doc = parseXml(explainOutput);
      doc.getDocumentElement().normalize();

      // Lấy tất cả RelOp nodes — mỗi RelOp là 1 operation trong plan
      NodeList relOps = doc.getElementsByTagName("RelOp");
      for (int i = 0; i < relOps.getLength(); i++) {
        Element relOp = (Element) relOps.item(i);
        analyzeRelOp(relOp, findings);
      }

    } catch (Exception e) {
      // Parse thất bại — trả về result rỗng không có findings
    }

    return new ExplainResult(findings, explainOutput, DB_TYPE);
  }

  /**
   * Phân tích một RelOp element và tạo findings nếu có vấn đề.
   */
  private void analyzeRelOp(Element relOp, List<Finding> findings) {
    String logicalOp  = relOp.getAttribute("LogicalOp");
    String physicalOp = relOp.getAttribute("PhysicalOp");
    long estimatedRows = (long) Double.parseDouble(
        relOp.getAttribute("EstimateRows").isEmpty()
            ? "0" : relOp.getAttribute("EstimateRows"));

    String tableName = extractTableName(relOp);

    // Table Scan — full scan không dùng index
    if ("Table Scan".equalsIgnoreCase(physicalOp)
        || "Table Scan".equalsIgnoreCase(logicalOp)) {
      findings.add(Finding.builder()
          .rule("FULL_TABLE_SCAN")
          .severity(estimatedRows > 1000 ? Severity.HIGH : Severity.MEDIUM)
          .table(tableName)
          .message("Table Scan trên bảng `" + tableName
              + "` — ước tính đọc " + estimatedRows + " rows, không dùng index nào")
          .recommendation("Thêm index cho các cột trong WHERE/JOIN của câu query này")
          .calledFrom("Execution plan analysis")
          .build());
    }

    // Clustered Index Scan — scan toàn bộ clustered index (tương đương full table scan)
    if ("Clustered Index Scan".equalsIgnoreCase(physicalOp)) {
      findings.add(Finding.builder()
          .rule("FULL_TABLE_SCAN")
          .severity(estimatedRows > 1000 ? Severity.HIGH : Severity.MEDIUM)
          .table(tableName)
          .message("Clustered Index Scan trên bảng `" + tableName
              + "` — scan toàn bộ " + estimatedRows + " rows thay vì Index Seek")
          .recommendation("Cân nhắc thêm index trên cột WHERE để chuyển từ Scan sang Seek")
          .calledFrom("Execution plan analysis")
          .build());
    }

    // Index Scan — có dùng index nhưng scan toàn bộ
    if ("Index Scan".equalsIgnoreCase(physicalOp)) {
      String indexName = extractIndexName(relOp);
      findings.add(Finding.builder()
          .rule("FULL_INDEX_SCAN")
          .severity(Severity.MEDIUM)
          .table(tableName)
          .message("Index Scan trên `" + tableName + "` (index: " + indexName
              + ") — scan " + estimatedRows + " rows, không dùng Index Seek")
          .recommendation("Cân nhắc composite index hoặc covering index để chuyển sang Index Seek")
          .calledFrom("Execution plan analysis")
          .build());
    }

    // Key Lookup — thường xảy ra khi index không cover đủ cột cần SELECT
    if ("Key Lookup".equalsIgnoreCase(physicalOp)
        || "Key Lookup".equalsIgnoreCase(logicalOp)) {
      findings.add(Finding.builder()
          .rule("KEY_LOOKUP")
          .severity(Severity.MEDIUM)
          .table(tableName)
          .message("Key Lookup trên bảng `" + tableName
              + "` — SQL Server phải tra thêm clustered index để lấy cột còn thiếu")
          .recommendation(
              "Dùng covering index (INCLUDE các cột trong SELECT) để tránh Key Lookup")
          .calledFrom("Execution plan analysis")
          .build());
    }

    // Hash Match — join hoặc aggregate tốn memory, thường do thiếu index
    if ("Hash Match".equalsIgnoreCase(physicalOp)
        && estimatedRows > 1000) {
      findings.add(Finding.builder()
          .rule("HASH_MATCH_LARGE")
          .severity(Severity.MEDIUM)
          .table(tableName)
          .message("Hash Match trên " + estimatedRows
              + " rows — join/aggregate tốn memory, có thể do thiếu index")
          .recommendation(
              "Thêm index trên cột JOIN để chuyển Hash Match sang Nested Loops hoặc Merge Join")
          .calledFrom("Execution plan analysis")
          .build());
    }
  }

  /**
   * Lấy tên bảng từ RelOp — tìm trong child element có chứa Object attribute.
   */
  private String extractTableName(Element relOp) {
    // Tìm element con có attribute Table hoặc Object
    NodeList children = relOp.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (children.item(i) instanceof Element child) {
        // Object element chứa thông tin bảng
        NodeList objects = child.getElementsByTagName("Object");
        if (objects.getLength() > 0) {
          Element obj = (Element) objects.item(0);
          String table = obj.getAttribute("Table");
          if (!table.isEmpty()) {
            // SQL Server wrap tên bảng trong [], bỏ đi
            return table.replace("[", "").replace("]", "");
          }
        }
      }
    }
    return "unknown";
  }

  /**
   * Lấy tên index từ RelOp.
   */
  private String extractIndexName(Element relOp) {
    NodeList children = relOp.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (children.item(i) instanceof Element child) {
        NodeList objects = child.getElementsByTagName("Object");
        if (objects.getLength() > 0) {
          Element obj = (Element) objects.item(0);
          String index = obj.getAttribute("Index");
          if (!index.isEmpty()) {
            return index.replace("[", "").replace("]", "");
          }
        }
      }
    }
    return "unknown";
  }

  /**
   * Parse XML string thành DOM Document.
   */
  private Document parseXml(String xml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    // Tắt external entity để tránh XXE vulnerability
    factory.setFeature(
        "http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setNamespaceAware(false);
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(
        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }
}