package com.dbquality.ai.impl;

import com.dbquality.ai.LLMProvider;

public class GrokProvider implements LLMProvider {

  @Override
  public String call(String prompt) {
    return "";
  }

  @Override
  public boolean isAvailable() {
    return false;
  }

  @Override
  public String getProviderName() {
    return "";
  }
}
