package com.dbquality.ai;

/**
 * Tạo lúc: 25/06/2026 <br>
 * Định nghĩa giao tiếp với một nhà cung cấp LLM (OpenAI, Claude, Gemini, v.v.). <br>
 * Thư viện xây dựng các prompt có cấu trúc và ủy quyền việc gọi API thực tế cho interface này. <br>
 * Nếu nhà cung cấp không khả dụng, thư viện sẽ tự động fallback sang kết quả dựa trên rule một cách âm thầm. <br>
 */
public interface LLMProvider {

  /**
   * Gửi một prompt tới LLM và trả về phản hồi.
   *
   * @param prompt  prompt có cấu trúc được xây dựng bởi thư viện (schema, slow queries, findings)
   * @return        phản hồi dạng văn bản thuần hoặc chuỗi JSON từ LLM
   */
  String call(String prompt);

  /**
   * Kiểm tra xem provider này đã sẵn sàng để sử dụng hay chưa (ví dụ: API key đã được cấu hình).
   *
   * @return true nếu provider có thể nhận request
   */
  boolean isAvailable();

  /**
   * @return tên hiển thị của provider (ví dụ: "OpenAI", "Claude", "Gemini")
   */
  String getProviderName();
}