let currentPage = 0;
let allFindings = [];
const PAGE_SIZE = 10;

async function loadData() {
  const dot = document.getElementById('refreshDot');
  if (dot) { dot.classList.add('active'); setTimeout(() => dot.classList.remove('active'), 600); }

  try {
    const [metricsRes, findingsRes, reportRes, slowRes, projectRes, trendRes] = await Promise.all([
      fetch('/metrics'),
      fetch('/findings'),
      fetch('/report'),
      fetch('/slow-queries'),
      fetch('/project-info'),
      fetch('/metrics-trend')
    ]);

    const metrics     = await metricsRes.json();
    const findings    = await findingsRes.json();
    const report      = await reportRes.json();
    const slowQueries = await slowRes.json();
    const projectInfo = await projectRes.json();
    const trend = await trendRes.json();

    updateSlowQueries(slowQueries);
    updateProjectInfo(projectInfo);
    updateMetrics(metrics);
    updateFindings(findings);
    updateScore(report.overallScore);
    updateLatencyTrend(trend);

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
    btn.textContent = 'Copied!';
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

function updateSlowQueries(slowQueries) {
  const container = document.getElementById('slowQueriesList');
  if (!slowQueries || slowQueries.length === 0) {
    container.innerHTML = '<div class="empty"> No slow queries detected</div>';
    return;
  }

  container.innerHTML = slowQueries.map((item, idx) => {
    const r = item.record;
    const hasExplain = item.explainResult != null;
    const findings = hasExplain ? item.explainResult.findings : [];
    const severityBadge = findings.length > 0
      ? `<span class="badge HIGH" style="font-size:10px">${findings.length} issue(s)</span>`
      : hasExplain
        ? `<span style="font-size:11px; color:#22d3ee">✓ Plan OK</span>`
        : '';

    return `
      <div class="finding-item" style="flex-direction:column; gap:8px">
        <div style="display:flex; align-items:flex-start; gap:12px">
          <span class="badge HIGH" style="white-space:nowrap; flex-shrink:0">
            ${r.executionTime}ms
          </span>
          <div style="flex:1; min-width:0">
            <div style="font-size:12px; color:#e2e8f0; font-family:monospace;
                        word-break:break-all; margin-bottom:4px">${escapeHtml(r.sql)}</div>
            <div style="font-size:11px; color:#64748b">
              📍 ${escapeHtml(r.calledFrom || 'unknown')}
            </div>
          </div>
          <div style="display:flex; align-items:center; gap:8px; flex-shrink:0">
            ${severityBadge}
            ${hasExplain ? `
              <button onclick="openExplainModal(${idx})"
                      style="background:#1e3a5f; color:#93c5fd; border:1px solid #1e40af;
                             padding:4px 10px; border-radius:5px; cursor:pointer;
                             font-size:11px; transition:background 0.2s"
                      onmouseover="this.style.background='#1e40af'"
                      onmouseout="this.style.background='#1e3a5f'">
                🔍 EXPLAIN
              </button>` : '<span style="font-size:11px; color:#475569">No plan</span>'}
          </div>
        </div>
      </div>`;
  }).join('');

  // Lưu data để modal dùng
  window._slowQueryData = slowQueries;
}

function openExplainModal(idx) {
  const item = window._slowQueryData[idx];
  if (!item || !item.explainResult) return;

  document.getElementById('explainModalSQL').textContent = item.record.sql;

  // Hiện findings
  const findings = item.explainResult.findings;
  const findingsEl = document.getElementById('explainModalFindings');
  if (findings && findings.length > 0) {
    findingsEl.innerHTML = findings.map(f => `
      <div style="display:flex; gap:8px; align-items:flex-start; margin-bottom:6px">
        <span class="badge ${f.severity}" style="font-size:10px">${f.severity}</span>
        <div>
          <div style="font-size:13px; color:#e2e8f0">${escapeHtml(f.message)}</div>
          <div style="font-size:12px; color:#22d3ee">💡 ${escapeHtml(f.recommendation)}</div>
        </div>
      </div>`).join('');
  } else {
    findingsEl.innerHTML = '<div style="color:#22d3ee; font-size:13px; margin-bottom:8px"> No issues detected in execution plan</div>';
  }

  // Raw JSON — format đẹp
  try {
    const parsed = JSON.parse(item.explainResult.rawOutput);
    document.getElementById('explainModalRaw').textContent =
      JSON.stringify(parsed, null, 2);
  } catch {
    document.getElementById('explainModalRaw').textContent =
      item.explainResult.rawOutput;
  }

  const modal = document.getElementById('explainModal');
  modal.style.display = 'flex';
}

function closeExplainModal() {
  document.getElementById('explainModal').style.display = 'none';
}

// Đóng modal khi click outside
document.addEventListener('click', function(e) {
  const modal = document.getElementById('explainModal');
  if (e.target === modal) closeExplainModal();
});

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g,'&amp;').replace(/</g,'&lt;')
            .replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function updateProjectInfo(info) {
  const container = document.getElementById('projectInfoContent');
  if (!info) { container.innerHTML = '<div class="empty">No data</div>'; return; }

  const uptime = formatUptime(info.uptimeSeconds);
  const heapPct = info.heapMemoryMaxMb > 0
    ? Math.round(info.heapMemoryUsedMb / info.heapMemoryMaxMb * 100) : 0;

  container.innerHTML = `
    <div class="grid-2" style="margin-bottom:16px">

      <!-- Database -->
      <div class="card">
        <div class="section-title" style="margin-bottom:16px"> Database</div>
        ${infoRow('Product',    info.dbProductName)}
        ${infoRow('Version',    info.dbProductVersion)}
        ${infoRow('User',       info.dbUsername)}
        ${infoRow('URL',        info.dbUrl, true)}
        ${infoRow('Driver',     info.driverName)}
        ${infoRow('Driver Ver', info.driverVersion)}
        ${info.dbMaxConnections > 0
            ? infoRow('Max Connections', info.dbMaxConnections)
            : ''}
      </div>

      <!-- Application -->
      <div class="card">
        <div class="section-title" style="margin-bottom:16px"> Application</div>
        ${infoRow('Framework',   info.framework
            + (info.frameworkVersion ? ' ' + info.frameworkVersion : ''))}
        ${infoRow('ORM',         info.ormFramework)}
        ${infoRow('Conn Pool',   info.connectionPool)}
        ${infoRow('Java',        info.javaVersion + ' — ' + info.javaVendor)}
        ${infoRow('JVM',         info.jvmName)}
        ${infoRow('OS',          info.osName + ' ' + info.osVersion)}
        ${infoRow('CPU Cores',   info.availableProcessors)}
      </div>

    </div>

    <div class="grid-2">

      <!-- Memory -->
      <div class="card">
        <div class="section-title" style="margin-bottom:16px"> JVM Memory</div>
        ${infoRow('Heap Used',  info.heapMemoryUsedMb + ' MB')}
        ${infoRow('Heap Max',   info.heapMemoryMaxMb + ' MB')}
        <div style="margin-top:8px">
          <div style="display:flex; justify-content:space-between;
                      font-size:12px; color:#64748b; margin-bottom:4px">
            <span>Heap usage</span><span>${heapPct}%</span>
          </div>
          <div style="background:#0f172a; border-radius:4px; height:8px">
            <div style="height:8px; border-radius:4px; transition:width 0.5s;
                        background:${heapPct > 80 ? '#ef4444' : heapPct > 60 ? '#f59e0b' : '#22d3ee'};
                        width:${heapPct}%"></div>
          </div>
        </div>
      </div>

      <!-- DB Quality Library -->
      <div class="card">
        <div class="section-title" style="margin-bottom:16px"> DB Quality Library</div>
        ${infoRow('Version',        info.libraryVersion)}
        ${infoRow('Dashboard Port', info.dashboardPort)}
        ${infoRow('Uptime',         uptime)}
      </div>

    </div>`;
}

function infoRow(label, value, mono) {
  if (value == null || value === '' || value === 'null') return '';
  return `
    <div style="display:flex; justify-content:space-between; align-items:flex-start;
                padding:5px 0; border-bottom:1px solid #1e293b; gap:12px">
      <span style="font-size:12px; color:#64748b; flex-shrink:0">${label}</span>
      <span style="font-size:12px; color:#e2e8f0; text-align:right; word-break:break-all;
                   ${mono ? 'font-family:monospace; color:#94a3b8' : ''}">${value}</span>
    </div>`;
}

function formatUptime(seconds) {
  if (!seconds) return '0s';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (h > 0) return `${h}h ${m}m ${s}s`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

function updateLatencyTrend(buckets) {
  const container = document.getElementById('latencyTrendChart');
  if (!buckets || buckets.length === 0) {
    container.innerHTML = '<div class="empty">No trend data yet — wait for more queries</div>';
    return;
  }
  if (buckets.length < 2) {
    container.innerHTML = '<div class="empty">Collecting data... (' + buckets.length + ' bucket so far)</div>';
    return;
  }

  const W = container.clientWidth || 800;
  const H = 160;
  const PAD = { top: 16, right: 16, bottom: 32, left: 48 };
  const chartW = W - PAD.left - PAD.right;
  const chartH = H - PAD.top - PAD.bottom;

  const p99Values = buckets.map(b => b.p99);
  const p95Values = buckets.map(b => b.p95);
  const p50Values = buckets.map(b => b.p50);
  const maxVal    = Math.max(...p99Values, 1);

  const xStep = chartW / (buckets.length - 1);
  const yScale = v => chartH - (v / maxVal * chartH);

  const toPath = values => values.map((v, i) =>
    (i === 0 ? 'M' : 'L') + (PAD.left + i * xStep).toFixed(1) + ',' + (PAD.top + yScale(v)).toFixed(1)
  ).join(' ');

  // X-axis labels — show first, middle, last
  const labelIndices = [0, Math.floor(buckets.length / 2), buckets.length - 1];
  const xLabels = labelIndices.map(i => {
    const t = new Date(buckets[i].bucketStart);
    const hh = String(t.getHours()).padStart(2, '0');
    const mm = String(t.getMinutes()).padStart(2, '0');
    const ss = String(t.getSeconds()).padStart(2, '0');
    return `<text x="${(PAD.left + i * xStep).toFixed(1)}" y="${H - 4}"
      fill="#64748b" font-size="10" text-anchor="middle">${hh}:${mm}:${ss}</text>`;
  }).join('');

  // Y-axis labels
  const yLabels = [0, Math.round(maxVal / 2), maxVal].map(v => {
    const y = PAD.top + yScale(v);
    return `<text x="${PAD.left - 6}" y="${y.toFixed(1)}"
      fill="#64748b" font-size="10" text-anchor="end" dominant-baseline="middle">${v}ms</text>`;
  }).join('');

  // Tooltip points — invisible circles for hover
  const p99Points = buckets.map((b, i) => {
    const cx = (PAD.left + i * xStep).toFixed(1);
    const cy = (PAD.top + yScale(b.p99)).toFixed(1);
    const t  = new Date(b.bucketStart);
    const label = `${String(t.getHours()).padStart(2,'0')}:${String(t.getMinutes()).padStart(2,'0')}:${String(t.getSeconds()).padStart(2,'0')} — P99:${b.p99}ms P95:${b.p95}ms P50:${b.p50}ms (${b.queryCount} queries)`;
    return `<circle cx="${cx}" cy="${cy}" r="4" fill="#ef4444" opacity="0.8">
      <title>${label}</title>
    </circle>`;
  }).join('');

  container.innerHTML = `
    <svg width="100%" height="${H}" viewBox="0 0 ${W} ${H}" xmlns="http://www.w3.org/2000/svg">
      <!-- Grid lines -->
      ${[0, 0.25, 0.5, 0.75, 1].map(r => {
        const y = (PAD.top + chartH * (1 - r)).toFixed(1);
        return `<line x1="${PAD.left}" y1="${y}" x2="${PAD.left + chartW}" y2="${y}"
          stroke="#1e293b" stroke-width="1"/>`;
      }).join('')}

      <!-- Lines -->
      <path d="${toPath(p50Values)}" fill="none" stroke="#3b82f6" stroke-width="1.5" opacity="0.6"/>
      <path d="${toPath(p95Values)}" fill="none" stroke="#f59e0b" stroke-width="1.5" opacity="0.6"/>
      <path d="${toPath(p99Values)}" fill="none" stroke="#ef4444" stroke-width="2"/>

      <!-- Data points P99 -->
      ${p99Points}

      <!-- Axes -->
      <line x1="${PAD.left}" y1="${PAD.top}" x2="${PAD.left}" y2="${PAD.top + chartH}"
        stroke="#334155" stroke-width="1"/>
      <line x1="${PAD.left}" y1="${PAD.top + chartH}" x2="${PAD.left + chartW}" y2="${PAD.top + chartH}"
        stroke="#334155" stroke-width="1"/>

      <!-- Labels -->
      ${yLabels}
      ${xLabels}

      <!-- Legend -->
      <line x1="${W - 120}" y1="12" x2="${W - 105}" y2="12" stroke="#3b82f6" stroke-width="2"/>
      <text x="${W - 100}" y="16" fill="#64748b" font-size="10">P50</text>
      <line x1="${W - 80}" y1="12" x2="${W - 65}" y2="12" stroke="#f59e0b" stroke-width="2"/>
      <text x="${W - 60}" y="16" fill="#64748b" font-size="10">P95</text>
      <line x1="${W - 40}" y1="12" x2="${W - 25}" y2="12" stroke="#ef4444" stroke-width="2"/>
      <text x="${W - 20}" y="16" fill="#64748b" font-size="10">P99</text>
    </svg>`;
}

function exportAIContext() {
  const text = document.getElementById('aiContextBlock').textContent;
  if (!text || text.trim() === 'Loading...') {
    alert('No AI context available yet');
    return;
  }

  const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
  const url  = URL.createObjectURL(blob);
  const a    = document.createElement('a');
  a.href     = url;
  a.download = 'ai-context_' + new Date().toISOString().replace(/[:.]/g, '-') + '.txt';
  a.click();
  URL.revokeObjectURL(url);
}

function nextPage() { currentPage++; renderPage(); }
function prevPage() { currentPage--; renderPage(); }

loadData();
loadAIContext();
setInterval(loadData, 5000);