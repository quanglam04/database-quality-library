package com.dbquality.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;

/**
 * Export QualityReport ra file JSON hoặc String.
 */
public class JSONExporter {

  private final ObjectMapper mapper;

  public JSONExporter() {
    this.mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  /**
   * Export report thành JSON string.
   *
   * @param report report cần export
   * @return JSON string
   */
  public String toJson(QualityReport report) throws IOException {
    return mapper.writeValueAsString(report);
  }

  /**
   * Export report ra file JSON.
   *
   * @param report   report cần export
   * @param filePath đường dẫn file output
   */
  public void toFile(QualityReport report, String filePath) throws IOException {
    mapper.writeValue(new File(filePath), report);
  }
}