package com.dbquality.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Chứa toàn bộ cấu hình của thư viện.
 * Đọc từ file application.properties hoặc dùng giá trị mặc định.
 */
public class QualityConfig {

  private boolean enabled;
  private long slowQueryThresholdMs;
  private int nPlusOneThreshold;
  private double samplingRate;
  private boolean aiEnabled;
  private String aiProvider;
  private String aiApiKey;
  private String aiModel;

  private QualityConfig() {}

  /**
   * Tạo config với toàn bộ giá trị mặc định.
   *
   * @return config mặc định
   */
  public static QualityConfig getDefault() {
    QualityConfig config = new QualityConfig();
    config.enabled = true;
    config.slowQueryThresholdMs = 500;
    config.nPlusOneThreshold = 10;
    config.samplingRate = 1.0;
    config.aiEnabled = false;
    config.aiProvider = "openai";
    config.aiApiKey = null;
    config.aiModel = "gpt-4o";
    return config;
  }

  /**
   * Đọc config từ file application.properties trên classpath.
   * Các property không có trong file sẽ dùng giá trị mặc định.
   *
   * @return config được load từ file
   */
  public static QualityConfig fromClasspath() {
    QualityConfig config = getDefault();
    try (InputStream is = QualityConfig.class
        .getClassLoader()
        .getResourceAsStream("application.properties")) {
      if (is == null) return config;
      Properties props = new Properties();
      props.load(is);
      config.enabled = Boolean.parseBoolean(
          props.getProperty("quality.enabled", "true"));
      config.slowQueryThresholdMs = Long.parseLong(
          props.getProperty("quality.slow-query-threshold-ms", "500"));
      config.nPlusOneThreshold = Integer.parseInt(
          props.getProperty("quality.n-plus-one-threshold", "10"));
      config.samplingRate = Double.parseDouble(
          props.getProperty("quality.sampling-rate", "1.0"));
      config.aiEnabled = Boolean.parseBoolean(
          props.getProperty("quality.ai.enabled", "false"));
      config.aiProvider = props.getProperty("quality.ai.provider", "openai");
      config.aiApiKey = props.getProperty("quality.ai.api-key", null);
      config.aiModel = props.getProperty("quality.ai.model", "gpt-4o");
    } catch (IOException e) {
      // Không load được file
    }
    return config;
  }

  // Getters
  public boolean isEnabled() { return enabled; }
  public long getSlowQueryThresholdMs() { return slowQueryThresholdMs; }
  public int getNPlusOneThreshold() { return nPlusOneThreshold; }
  public double getSamplingRate() { return samplingRate; }
  public boolean isAiEnabled() { return aiEnabled; }
  public String getAiProvider() { return aiProvider; }
  public String getAiApiKey() { return aiApiKey; }
  public String getAiModel() { return aiModel; }
}