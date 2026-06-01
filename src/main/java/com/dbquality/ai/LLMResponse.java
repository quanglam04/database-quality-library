package com.dbquality.ai;

/**
 * Kết quả trả về từ LLM provider.
 */
public class LLMResponse {

  private final String content;
  private final boolean success;
  private final String errorMessage;

  public LLMResponse(String content, boolean success, String errorMessage) {
    this.content = content;
    this.success = success;
    this.errorMessage = errorMessage;
  }

  public static LLMResponse success(String content) {
    return new LLMResponse(content, true, null);
  }

  public static LLMResponse failure(String errorMessage) {
    return new LLMResponse(null, false, errorMessage);
  }

  public String getContent() { return content; }
  public boolean isSuccess() { return success; }
  public String getErrorMessage() { return errorMessage; }
}