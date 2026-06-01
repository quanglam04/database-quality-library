package com.dbquality.explain.impl;

import com.dbquality.explain.ExplainParser;
import com.dbquality.explain.ExplainResult;

public class MySQLExplainParser implements ExplainParser {

  @Override
  public ExplainResult parse(String explainOutput) {
    return null;
  }

  @Override
  public boolean supports(String databaseProductName) {
    return false;
  }
}
