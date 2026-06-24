package com.dbquality.report;

import com.dbquality.rule.Finding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cache kết quả từ scheduled analysis job.
 * API endpoints (/report, /findings) đọc từ store này thay vì
 * trigger tính toán mới mỗi request.
 *
 * <p>Lifecycle:</p>
 * <ul>
 *   <li>Khởi tạo rỗng khi app start</li>
 *   <li>ScheduledAnalysisJob update mỗi lần chạy</li>
 *   <li>API endpoint chỉ đọc, không modify</li>
 * </ul>
 */
public class AnalysisResultStore {

  private volatile List<Finding> findings = Collections.emptyList();
  private volatile int score = 100;
  private volatile long lastAnalyzedAt = 0;
  private volatile long lastAnalysisDurationMs = 0;
  private volatile boolean firstAnalysisDone = false;


  public synchronized void update(List<Finding> findings, int score,
      long analysisDurationMs) {
    this.findings = Collections.unmodifiableList(new ArrayList<>(findings));
    this.score = score;
    this.lastAnalyzedAt = System.currentTimeMillis();
    this.lastAnalysisDurationMs = analysisDurationMs;
    this.firstAnalysisDone = true;
  }


  public List<Finding> getFindings() {
    return findings;
  }

  public int getScore() {
    return score;
  }

  public long getLastAnalyzedAt() {
    return lastAnalyzedAt;
  }

  public long getLastAnalysisDurationMs() {
    return lastAnalysisDurationMs;
  }

  public boolean isFirstAnalysisDone() {
    return firstAnalysisDone;
  }

  public long getSecondsSinceLastAnalysis() {
    if (lastAnalyzedAt == 0) return -1;
    return (System.currentTimeMillis() - lastAnalyzedAt) / 1000;
  }
}