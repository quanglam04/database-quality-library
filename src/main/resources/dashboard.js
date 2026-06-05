let currentPage = 0;
let allFindings = [];
const PAGE_SIZE = 10;

async function loadData() {
  const dot = document.getElementById('refreshDot');
  if (dot) { dot.classList.add('active'); setTimeout(() => dot.classList.remove('active'), 600); }

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

    const lastUpdate = document.getElementById('lastUpdate');
    if (lastUpdate) {
      lastUpdate.innerHTML = 'Last updated: ' + new Date().toLocaleTimeString() +
        ' <span class="refresh-indicator" id="refreshDot"></span>';
    }

    if (report.aiInsights === '__LOADING__') {
        document.getElementById('aiInsightsSection').style.display = 'block';
        document.getElementById('aiInsightsContent').innerHTML = `
            <div style="display:flex; align-items:center; gap:10px; color:#64748b; padding:12px 0">
                <div style="width:16px; height:16px; border:2px solid #3b82f6;
                            border-top-color:transparent; border-radius:50%;
                            animation:spin 0.8s linear infinite"></div>
                Đang phân tích với AI, vui lòng chờ...
            </div>`;
    } else if (report.aiInsights) {
        document.getElementById('aiInsightsSection').style.display = 'block';
        const cleaned = report.aiInsights.replace(/\n{2,}/g, '\n\n').trim();
        document.getElementById('aiInsightsContent').innerHTML = marked.parse(cleaned);
    }
  } catch (e) {
    document.getElementById('findingsList').innerHTML =
      '<div class="error">Failed to load data: ' + e.message + '</div>';
  }
}

async function loadAIContext() {
  try {
    const res  = await fetch('/ai-context');
    const data = await res.json();
    document.getElementById('aiContextBlock').textContent = data.aiContext;
  } catch (e) {
    document.getElementById('aiContextBlock').textContent = 'Failed to load: ' + e.message;
  }
}


async function refreshAI() {
    const btn = document.getElementById('refreshAIBtn');
    btn.disabled = true;
    btn.textContent = '⏳ Đang reset...';

    try {
        await fetch('/ai-refresh', { method: 'POST' });
        // Trigger loadData ngay để hiện loading state
        await loadData();
    } catch (e) {
        console.error('AI refresh failed:', e);
    } finally {
        btn.disabled = false;
        btn.textContent = '🔄 Refresh AI';
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
  badge.classList.remove('pulse');
  if (score >= 80) {
    badge.style.background = '#14532d'; badge.style.color = '#86efac';
  } else if (score >= 60) {
    badge.style.background = '#422006'; badge.style.color = '#fcd34d';
  } else {
    badge.style.background = '#450a0a'; badge.style.color = '#fca5a5';
    badge.classList.add('pulse');
  }
}

function updateMetrics(m) {
  document.getElementById('totalSQL').textContent  = m.totalSQLIntercepted ?? 0;
  document.getElementById('slowCount').textContent = m.slowQueryCount ?? 0;
  document.getElementById('nPlusOne').textContent  = m.nPlusOneDetected ?? 0;
  document.getElementById('errorRate').textContent = (m.errorRate ?? 0).toFixed(1) + '%';

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

  const entries = Object.entries(m.topTablesByQueryFrequency ?? {});
  document.getElementById('topTablesBody').innerHTML = entries.length === 0
    ? '<tr><td colspan="2" class="empty">No data</td></tr>'
    : entries.map(([t, c]) => `<tr><td>${t}</td><td>${c}</td></tr>`).join('');
}

function updateFindings(findings) {
  if (!findings || findings.length === 0) {
    document.getElementById('findingsList').innerHTML =
      '<div class="empty">✅ No findings — looking good!</div>';
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
  const end   = start + PAGE_SIZE;
  const page  = allFindings.slice(start, end);
  const total = allFindings.length;

  document.getElementById('findingsList').innerHTML = page.map(f => `
    <div class="finding-item">
      <span class="badge ${f.severity}">${f.severity}</span>
      <div class="finding-content">
        <div class="finding-rule">${f.rule}${f.table ? ' · ' + f.table : ''}${f.column ? '.' + f.column : ''}</div>
        <div class="finding-msg">${f.message}</div>
        ${f.recommendation ? `<div class="finding-rec">💡 ${f.recommendation}</div>` : ''}
        ${f.calledFrom && f.calledFrom !== 'Schema analysis — no call site'
          ? `<div class="finding-rec" style="color:#94a3b8">📍 ${f.calledFrom}</div>`
          : f.calledFrom === 'Schema analysis — no call site'
          ? `<div class="finding-rec" style="color:#475569">🗂️ ${f.calledFrom}</div>`
          : ''}
      </div>
    </div>`).join('');

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
            border:1px solid #334155; padding:6px 12px; border-radius:6px; cursor:pointer;
            transition:background 0.2s" onmouseover="this.style.background='#334155'"
            onmouseout="this.style.background='#1e293b'">← Trước</button>` : ''}
        ${hasNext ? `<button onclick="nextPage()" style="background:#3b82f6; color:white;
            border:none; padding:6px 12px; border-radius:6px; cursor:pointer;
            transition:background 0.2s" onmouseover="this.style.background='#2563eb'"
            onmouseout="this.style.background='#3b82f6'">Tiếp →</button>` : ''}
      </div>
    </div>`;
}

function switchTab(tab) {
  // Ẩn tất cả panels
  document.querySelectorAll('.tab-panel').forEach(p => p.style.display = 'none');
  // Bỏ active tất cả buttons
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));

  // Hiện panel được chọn
  document.getElementById('tab-' + tab).style.display = 'block';
  // Active button được chọn
  event.target.classList.add('active');
}

function nextPage() { currentPage++; renderPage(); }
function prevPage() { currentPage--; renderPage(); }

loadData();
loadAIContext();
setInterval(loadData, 5000);