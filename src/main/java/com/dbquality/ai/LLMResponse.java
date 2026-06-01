package com.dbquality.ai;

/**
 * Kết quả trả về từ LLM provider.
 *
 * <p>Dùng các static factory methods để tạo instance:
 * {@link #success(String)} hoặc {@link #failure(String)}.</p>
 */
public class LLMResponse {

  private final String content;
  private final boolean success;
  private final String errorMessage;

  /**
   * @param content      nội dung phản hồi từ LLM; {@code null} nếu thất bại
   * @param success      {@code true} nếu cuộc gọi API thành công
   * @param errorMessage thông báo lỗi; {@code null} nếu thành công
   */
  public LLMResponse(String content, boolean success, String errorMessage) {
    this.content = content;
    this.success = success;
    this.errorMessage = errorMessage;
  }

  /**
   * Tạo LLMResponse thành công với nội dung phản hồi.
   *
   * @param content nội dung văn bản trả về từ LLM
   * @return LLMResponse với {@code success = true}
   */
  public static LLMResponse success(String content) {
    return new LLMResponse(content, true, null);
  }

  /**
   * Tạo LLMResponse thất bại với thông báo lỗi.
   *
   * @param errorMessage mô tả lỗi xảy ra (HTTP status, timeout, v.v.)
   * @return LLMResponse với {@code success = false} và {@code content = null}
   */
  public static LLMResponse failure(String errorMessage) {
    return new LLMResponse(null, false, errorMessage);
  }

  public String getContent() { return content; }
  public boolean isSuccess() { return success; }
  public String getErrorMessage() { return errorMessage; }
}