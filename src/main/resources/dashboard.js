let currentPage = 0;
const PAGE_SIZE = 10;
let allFindings = [];

async function loadData() {
  try {
      const [metricsRes, findingsRes, reportRes] = await Promise.all([
          fetch('/metrics'),
          fetch('/findings'),
          fetch('/report')
      ]);
      const metrics  = await metricsRes.json();
      const findings = await findingsRes.json();
      const report   = await reportRes.json();

      updateMetrics(metrics);
      updateFindings(findings);
      updateScore(report.overallScore);
      document.getElementById('lastUpdate').textContent =
          'Last updated: ' + new Date().toLocaleTimeString();

      if (report.aiInsights) {
      document.getElementById('aiInsightsSection').style.display = 'block';
      document.getElementById('aiInsightsContent').textContent = report.aiInsights;
    }
  } catch (e) {
      document.getElementById('findingsList').innerHTML =
          '<div class="error">Failed to load data: ' + e.message + '</div>';
  }
}

async function loadAIContext() {
          try {
              const res = await fetch('/ai-context');
              const data = await res.json();
              document.getElementById('aiContextBlock').textContent = data.aiContext;
          } catch (e) {
              document.getElementById('aiContextBlock').textContent = 'Failed to load: ' + e.message;
          }
      }

      function copyAIContext() {
          const text = document.getElementById('aiContextBlock').textContent;
          navigator.clipboard.writeText(text).then(() => {
              const btn = event.target;
              btn.textContent = '✅ Copied!';
              setTimeout(() => btn.textContent = '📋 Copy', 2000);
          });
      }


function updateScore(score) {
  const badge = document.getElementById('scoreBadge');
  badge.textContent = 'Score: ' + score + '/100';
  if (score >= 80)      { badge.style.background = '#14532d'; badge.style.color = '#86efac'; }
  else if (score >= 60) { badge.style.background = '#422006'; badge.style.color = '#fcd34d'; }
  else                  { badge.style.background = '#450a0a'; badge.style.color = '#fca5a5'; }
}

function updateMetrics(m) {
  document.getElementById('totalSQL').textContent   = m.totalSQLIntercepted ?? 0;
  document.getElementById('slowCount').textContent  = m.slowQueryCount ?? 0;
  document.getElementById('nPlusOne').textContent   = m.nPlusOneDetected ?? 0;
  document.getElementById('errorRate').textContent  =
      (m.errorRate ?? 0).toFixed(1) + '%';

  // Latency bars
  const max = Math.max(m.p99Latency ?? 1, 1);
  document.getElementById('latencyBars').innerHTML = `
      <div class="latency-row">
          <span class="latency-label">P50</span>
          <div class="latency-bar-wrap">
              <div class="latency-bar" style="width:${(m.p50Latency/max*100).toFixed(1)}%"></div>
          </div>
          <span class="latency-value">${m.p50Latency ?? 0}ms</span>
      </div>
      <div class="latency-row">
          <span class="latency-label">P95</span>
          <div class="latency-bar-wrap">
              <div class="latency-bar" style="width:${(m.p95Latency/max*100).toFixed(1)}%;background:#f59e0b"></div>
          </div>
          <span class="latency-value">${m.p95Latency ?? 0}ms</span>
      </div>
      <div class="latency-row">
          <span class="latency-label">P99</span>
          <div class="latency-bar-wrap">
              <div class="latency-bar" style="width:100%;background:#ef4444"></div>
          </div>
          <span class="latency-value">${m.p99Latency ?? 0}ms</span>
      </div>`;

  // Top tables
  const entries = Object.entries(m.topTablesByQueryFrequency ?? {});
  if (entries.length === 0) {
      document.getElementById('topTablesBody').innerHTML =
          '<tr><td colspan="2" class="empty">No data</td></tr>';
  } else {
      document.getElementById('topTablesBody').innerHTML =
          entries.map(([t, c]) =>
              `<tr><td>${t}</td><td>${c}</td></tr>`).join('');
  }
}

function updateFindings(findings) {
           if (!findings || findings.length === 0) {
               document.getElementById('findingsList').innerHTML =
                   '<div class="empty"> No findings — looking good!</div>';
               return;
           }
           const order = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, WARNING: 3 };
           allFindings = findings.sort((a, b) =>
               (order[a.severity] ?? 9) - (order[b.severity] ?? 9));
           const maxPage = Math.max(0, Math.ceil(allFindings.length / PAGE_SIZE) - 1);
           currentPage = Math.min(currentPage, maxPage);
           renderPage();
}
function renderPage() {
          const start = currentPage * PAGE_SIZE;
          const end = start + PAGE_SIZE;
          const page = allFindings.slice(start, end);
          const total = allFindings.length;

          document.getElementById('findingsList').innerHTML = page.map(f => `
              <div class="finding-item">
                  <span class="badge ${f.severity}">${f.severity}</span>
                  <div class="finding-content">
                      <div class="finding-rule">${f.rule}${f.table ? ' · ' + f.table : ''}${f.column ? '.' + f.column : ''}</div>
                      <div class="finding-msg">${f.message}</div>
                      ${f.recommendation ? `<div class="finding-rec">💡 ${f.recommendation}</div>` : ''}
                      ${f.calledFrom && f.calledFrom !== 'Schema analysis — no call site'
                          ? `<div class="finding-rec" style="color:#94a3b8"> ${f.calledFrom}</div>`
                          : f.calledFrom === 'Schema analysis — no call site'
                          ? `<div class="finding-rec" style="color:#475569">️ ${f.calledFrom}</div>`
                          : ''}
                  </div>
              </div>`).join('');

          // Pagination controls
          const hasNext = end < total;
          const hasPrev = currentPage > 0;
          document.getElementById('findingsList').innerHTML += `
              <div style="display:flex; align-items:center; justify-content:space-between;
                          margin-top:12px; padding-top:12px; border-top:1px solid #334155;">
                  <span style="font-size:12px; color:#64748b">
                      Hiển thị ${start + 1}–${Math.min(end, total)} / ${total} findings
                  </span>
                  <div style="display:flex; gap:8px;">
                      ${hasPrev ? `<button onclick="prevPage()" style="background:#1e293b; color:#e2e8f0;
                          border:1px solid #334155; padding:6px 12px; border-radius:6px; cursor:pointer">
                          ← Trước</button>` : ''}
                      ${hasNext ? `<button onclick="nextPage()" style="background:#3b82f6; color:white;
                          border:none; padding:6px 12px; border-radius:6px; cursor:pointer">
                          Tiếp →</button>` : ''}
                  </div>
              </div>`;
      }

      function nextPage() {
          currentPage++;
          renderPage();
      }

      function prevPage() {
          currentPage--;
          renderPage();
      }

// Load ngay khi mở và auto refresh mỗi 5 giây
loadData();
loadAIContext();
setInterval(loadData, 5000);
