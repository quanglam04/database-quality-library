package com.dbquality.ai.impl;

import com.dbquality.ai.LLMProvider;
import com.dbquality.ai.LLMResponse;
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

  private static final String API_URL = "https://api.anthropic.com/v1/messages";
  private static final String ANTHROPIC_VERSION = "2023-06-01";

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
    LLMResponse response = callWithResponse(prompt);
    return response.isSuccess() ? response.getContent() : response.getErrorMessage();
  }

  @Override
  public boolean isAvailable() {
    return apiKey != null && !apiKey.isBlank();
  }

  @Override
  public String getProviderName() {
    return "Claude";
  }

  private LLMResponse callWithResponse(String prompt) {
    try {
      String requestBody = mapper.writeValueAsString(java.util.Map.of(
          "model", model,
          "max_tokens", 2000,
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
          .timeout(Duration.ofSeconds(60))
          .build();

      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        return LLMResponse.failure("Claude API error: " + response.statusCode()
            + " — " + response.body());
      }

      JsonNode json = mapper.readTree(response.body());
      String content = json.path("content").get(0)
          .path("text").asText();
      return LLMResponse.success(content);

    } catch (Exception e) {
      return LLMResponse.failure("Claude call failed: " + e.getMessage());
    }
  }
}