package com.dbquality.ai.impl;

import com.dbquality.ai.LLMProvider;
import com.dbquality.ai.LLMResponse;
import com.dbquality.constant.Constant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * LLM Provider implementation cho Google Gemini.
 */
public class GeminiProvider implements LLMProvider {

  private static final String API_URL = Constant.GEMINI_API_URL;

  private final String apiKey;
  private final String model;
  private final ObjectMapper mapper;
  private final HttpClient httpClient;

  public GeminiProvider(String apiKey, String model) {
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
    return "Gemini";
  }

  private LLMResponse callWithResponse(String prompt) {
    try {
      String requestBody = mapper.writeValueAsString(java.util.Map.of(
          "contents", java.util.List.of(
              java.util.Map.of("parts", java.util.List.of(
                  java.util.Map.of("text", prompt)
              ))
          )
      ));

      String url = String.format(API_URL, model, apiKey);
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(requestBody))
          .timeout(Duration.ofSeconds(60))
          .build();

      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        return LLMResponse.failure("Gemini API error: " + response.statusCode()
            + " — " + response.body());
      }

      JsonNode json = mapper.readTree(response.body());
      String content = json.path("candidates").get(0)
          .path("content").path("parts").get(0)
          .path("text").asText();
      return LLMResponse.success(content);

    } catch (Exception e) {
      return LLMResponse.failure("Gemini call failed: " + e.getMessage());
    }
  }
}