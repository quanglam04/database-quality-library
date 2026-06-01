package com.dbquality.constant;

public class Constant {

  // Provider identifiers
  public static final String PROVIDER_OPENAI = "openai";
  public static final String PROVIDER_CLAUDE = "claude";
  public static final String PROVIDER_GEMINI = "gemini";

  // API endpoint
  public static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
  public static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
  public static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

  // Claude specific
  public static final String ANTHROPIC_VERSION = "2023-06-01";

  // HTTP timeouts
  public static final int CONNECT_TIMEOUT_SECONDS = 30;
  public static final int REQUEST_TIMEOUT_SECONDS = 60;

  // LLM request defaults
  public static final int DEFAULT_MAX_TOKENS = 2000;
  public static final double DEFAULT_TEMPERATURE = 0.3;

  // Rule name
  public class RuleName {
    public static String MissingPrimaryKey = "MISSING_PRIMARY_KEY";
    public static String UnindexedForeignKey = "UNINDEXED_FOREIGN_KEY";
    public static String NullableRisk = "NULLABLE_RISK";
    public static String SuspiciousDataType = "SUSPICIOUS_DATA_TYPE";
    public static String UnusedIndex = "UNUSED_INDEX";
    public static String MissingIndexSuggestion = "MISSING_INDEX_SUGGESTION";
    public static String FullTableScanCandidate = "FULL_TABLE_SCAN_CANDIDATE";
    public static String NPlusOne = "N_PLUS_ONE";
    public static String SelectStar = "SELECT_STAR";
    public static String SlowQuery = "SLOW_QUERY";

  }
}
