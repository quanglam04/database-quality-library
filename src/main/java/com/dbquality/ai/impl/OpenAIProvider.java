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
 * LLM Provider implementation cho OpenAI (GPT-4o, GPT-4...).
 */
public class OpenAIProvider implements LLMProvider {

  private static final String API_URL = "https://api.openai.com/v1/chat/completions";

  private final String apiKey;
  private final String model;
  private final ObjectMapper mapper;
  private final HttpClient httpClient;

  public OpenAIProvider(String apiKey, String model) {
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
    return "OpenAI";
  }

  private LLMResponse callWithResponse(String prompt) {
    try {
      String requestBody = mapper.writeValueAsString(java.util.Map.of(
          "model", model,
          "messages", java.util.List.of(
              java.util.Map.of("role", "user", "content", prompt)
          ),
          "max_tokens", 2000,
          "temperature", 0.3
      ));

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(API_URL))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + apiKey)
          .POST(HttpRequest.BodyPublishers.ofString(requestBody))
          .timeout(Duration.ofSeconds(60))
          .build();

      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        return LLMResponse.failure("OpenAI API error: " + response.statusCode()
            + " — " + response.body());
      }

      JsonNode json = mapper.readTree(response.body());
      String content = json.path("choices").get(0)
          .path("message").path("content").asText();
      return LLMResponse.success(content);

    } catch (Exception e) {
      return LLMResponse.failure("OpenAI call failed: " + e.getMessage());
    }
  }
}