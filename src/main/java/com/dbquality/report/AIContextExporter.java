package com.dbquality.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Export AI-ready context từ {@link QualityReport} ra file .txt.
 *
 * <p>Cho phép người dùng export prompt đã được cấu trúc sẵn ra file
 * để paste trực tiếp vào ChatGPT, Claude, hoặc Gemini mà không cần
 * bật AI integration trong config.</p>
 */
public class AIContextExporter {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  /**
   * Export AI-ready context ra file với tên tự động theo timestamp.
   *
   * @param context nội dung AI-ready context từ {@link QualityReport#getAiReadyContext()}
   * @param outputDir thư mục output; nếu null thì dùng thư mục hiện tại
   * @return đường dẫn file đã tạo
   * @throws IOException nếu không ghi được file
   */
  public Path export(String context, String outputDir) throws IOException {
    if (context == null || context.isBlank()) {
      throw new IllegalArgumentException("AI context is empty — run the app first to collect data");
    }

    String filename = "ai-context_" + LocalDateTime.now().format(FORMATTER) + ".txt";
    Path dir  = outputDir != null ? Paths.get(outputDir) : Paths.get(".");
    Path file = dir.resolve(filename);

    Files.createDirectories(dir);
    Files.writeString(file, context, StandardCharsets.UTF_8);

    return file;
  }

  /**
   * Export AI-ready context ra file tại thư mục hiện tại.
   *
   * @param context nội dung AI-ready context
   * @return đường dẫn file đã tạo
   * @throws IOException nếu không ghi được file
   */
  public Path export(String context) throws IOException {
    return export(context, null);
  }
}