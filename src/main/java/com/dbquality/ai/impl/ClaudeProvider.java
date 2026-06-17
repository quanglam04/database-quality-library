package com.dbquality.ai.impl;

import com.dbquality.ai.LLMProvider;
import com.dbquality.constant.Constant;
import com.dbquality.util.AIProviderUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * LLM Provider implementation cho Anthropic Claude.
 */
public class ClaudeProvider implements LLMProvider {

  private static final String API_URL = Constant.CLAUDE_API_URL;
  private static final String ANTHROPIC_VERSION = Constant.ANTHROPIC_VERSION;

  private final String apiKey;
  private final String model;
  private final ObjectMapper mapper;
  private final HttpClient httpClient;

  public ClaudeProvider(String apiKey, String model) {
    this.apiKey = apiKey;
    this.model = model;
    this.mapper = new ObjectMapper();
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
  }

  @Override
  public String call(String prompt) {
    try {
      String requestBody = mapper.writeValueAsString(java.util.Map.of(
          "model", model,
          "max_tokens", Constant.DEFAULT_MAX_TOKENS,
          "messages", java.util.List.of(
              java.util.Map.of("role", "user", "content", prompt)
          )
      ));

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(API_URL))
          .header("Content-Type", "application/json")
          .header("x-api-key", apiKey)
          .header("anthropic-version", ANTHROPIC_VERSION)
          .POST(HttpRequest.BodyPublishers.ofString(requestBody))
          .timeout(Duration.ofSeconds(120))
          .build();

      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        return AIProviderUtil.parseErrorMessage("Claude", response.statusCode(), response.body());
      }

      JsonNode json = mapper.readTree(response.body());
      return json.path("content").get(0).path("text").asText();

    } catch (Exception e) {
      return "Claude call failed: " + e.getMessage();
    }
  }

  @Override
  public boolean isAvailable() {
    return apiKey != null && !apiKey.isBlank();
  }

  @Override
  public String getProviderName() {
    return "Claude";
  }

}