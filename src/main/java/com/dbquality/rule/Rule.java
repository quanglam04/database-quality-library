package com.dbquality.rule;

import com.dbquality.collector.DDLContext;
import com.dbquality.collector.SQLContext;

/**
 * Author: Trinh Quang Lam <br>
 * Created At: 25/05/2026 <br><br>
 * Defines a single quality analysis rule for database interaction.<br>
 * Each rule checks one specific concern (e.g. missing PK, N+1 queries).<br>
 */
public interface Rule {

  /**
   * Analyzes database structure and runtime SQL to detect issues.
   *
   * @param ddl  database structure context (tables, columns, indexes, FKs)
   * @param sql  runtime SQL context (intercepted queries in current session)
   * @return     analysis result containing findings and recommendations
   */
  RuleResult analyze(DDLContext ddl, SQLContext sql);

  /**
   * @return rule identifier in UPPER_SNAKE_CASE (e.g. "MISSING_PRIMARY_KEY")
   */
  String getName();

  /**
   * @return default severity level: CRITICAL, HIGH, MEDIUM, or WARNING
   */
  Severity getSeverity();
}