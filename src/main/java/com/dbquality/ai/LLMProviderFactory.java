package com.dbquality.ai;

import com.dbquality.ai.impl.ClaudeProvider;
import com.dbquality.ai.impl.GeminiProvider;
import com.dbquality.ai.impl.OpenAIProvider;
import com.dbquality.config.QualityConfig;

/**
 * Factory tạo {@link LLMProvider} phù hợp dựa trên cấu hình.
 *
 * <p>Được gọi nội bộ bởi {@link com.dbquality.report.ReportBuilder}.
 * Hỗ trợ ba provider: {@code openai} , {@code claude}, {@code gemini}.</p>
 *
 * <p>Nếu {@code quality.ai.enabled=false}, {@link #create(QualityConfig)} trả về {@code null}
 * và thư viện fallback về kết quả rule-based.</p>
 */
public class LLMProviderFactory {

  /**
   * Tạo LLMProvider phù hợp dựa trên config.
   *
   * @param config QualityConfig chứa thông tin provider, API key, model
   * @return LLMProvider tương ứng, hoặc null nếu AI không được enable
   */
  public static LLMProvider create(QualityConfig config) {
    if (!config.isAiEnabled()) return null;

    String apiKey = config.getAiApiKey();
    String model  = config.getAiModel();

    return switch (config.getAiProvider().toLowerCase()) {
      case "claude"  -> new ClaudeProvider(apiKey, model);
      case "gemini"  -> new GeminiProvider(apiKey, model);
      default        -> new OpenAIProvider(apiKey, model); // openai là default
    };
  }
}