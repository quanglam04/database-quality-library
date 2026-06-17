package com.dbquality.util;

/**
 * Utility class cho các AI provider.
 * Dùng chung cho OpenAIProvider, ClaudeProvider, GeminiProvider.
 */
public class AIProviderUtil {

  private AIProviderUtil() {}

  /**
   * Chuyển HTTP status code thành thông báo lỗi thân thiện với người dùng.
   *
   * @param provider   tên provider (openai, claude, gemini)
   * @param statusCode HTTP status code từ API response
   * @param body       response body (reserved for future use)
   * @return chuỗi thông báo lỗi dạng "[provider] message"
   */
  public static String parseErrorMessage(String provider, int statusCode, String body) {
    String friendly = switch (statusCode) {
      // Client errors
      case 400 -> "Request không hợp lệ. Kiểm tra lại model và payload.";
      case 401, 403 -> "API key không hợp lệ hoặc hết hạn.";
      case 404 -> "Model không tồn tại. Kiểm tra lại quality.ai.model.";
      case 408 -> "Request timeout. Vui lòng thử lại sau.";
      case 413 -> "Request quá lớn. Giảm độ dài context hoặc max_tokens.";
      case 422 -> "Dữ liệu không hợp lệ. Kiểm tra lại cấu hình model.";
      case 429 -> "Đã vượt quota API. Vui lòng thử lại sau.";

      // Server errors
      case 500 -> "Lỗi internal server của AI provider. Vui lòng thử lại sau.";
      case 502 -> "AI provider tạm thời không khả dụng (Bad Gateway).";
      case 503 -> "Dịch vụ AI tạm thời quá tải. Vui lòng thử lại sau.";
      case 504 -> "AI provider không phản hồi (Gateway Timeout).";
      case 529 -> "AI provider đang quá tải (Overloaded). Vui lòng thử lại sau.";

      default -> statusCode >= 500
          ? "Lỗi server " + statusCode + ". Vui lòng thử lại sau."
          : statusCode >= 400
              ? "Lỗi client " + statusCode + ". Kiểm tra lại cấu hình."
              : "Lỗi " + statusCode + ". Vui lòng thử lại sau.";
    };
    return "[" + provider + "] " + friendly;
  }
}