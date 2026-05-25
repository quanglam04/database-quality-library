package com.dbquality.explain;

/**
 * Author: Trinh Quang Lam <br>
 * Created At: 25/05/2026 <br><br>
 * Parses EXPLAIN output from a specific database vendor.<br>
 * Each database has a different EXPLAIN format, so each vendor needs its own parser.<br>
 * If no parser supports the current database, Execution Plan Analysis is silently skipped.<br>
 */
public interface ExplainParser {

  /**
   * Parses raw EXPLAIN output into a structured result.
   *
   * @param explainOutput  raw string returned by the EXPLAIN statement
   * @return               parsed result containing findings (full table scan, missing index, etc.)
   */
  ExplainResult parse(String explainOutput);

  /**
   * Checks whether this parser supports the given database.
   * The name is obtained from {@code DatabaseMetaData.getDatabaseProductName()}.
   *
   * @param databaseProductName  e.g. "MySQL", "PostgreSQL", "MariaDB", "Microsoft SQL Server"
   * @return                     true if this parser can handle the given database
   */
  boolean supports(String databaseProductName);
}