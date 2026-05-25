package com.dbquality.ai;

/**
 * Author: Trinh Quang Lam <br>
 * Created At: 25/06/2026 <br> <br>
 * Defines communication with an LLM provider (OpenAI, Claude, Gemini, etc.). <br>
 * The library builds structured prompts and delegates the actual API call to this interface. <br>
 * If the provider is unavailable, the library falls back to rule-based output silently. <br>
 */
public interface LLMProvider {

  /**
   * Sends a prompt to the LLM and returns the response.
   *
   * @param prompt  structured prompt built by the library (schema, slow queries, findings)
   * @return        plain text or JSON string response from the LLM
   */
  String call(String prompt);

  /**
   * Checks whether this provider is ready to use (e.g. API key is configured).
   *
   * @return true if the provider can accept requests
   */
  boolean isAvailable();

  /**
   * @return provider display name (e.g. "OpenAI", "Claude", "Gemini")
   */
  String getProviderName();
}