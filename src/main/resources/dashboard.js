let currentPage = 0;
let allFindings = [];
let filteredFindings = [];
const PAGE_SIZE = 10;

async function loadData() {
  const dot = document.getElementById('refreshDot');
  if (dot) { dot.classList.add('active'); setTimeout(() => dot.classList.remove('active'), 600); }

  try {
    const [metricsRes, findingsRes, reportRes, slowRes, projectRes, statusRes] = await Promise.all([
      fetch('/metrics'),
      fetch('/findings'),
      fetch('/report'),
      fetch('/slow-queries'),
      fetch('/project-info'),
      fetch('/analysis-status')
    ]);

    const metrics     = await metricsRes.json();
    const findings    = await findingsRes.json();
    const report      = await reportRes.json();
    const slowQueries = await slowRes.json();
    const projectInfo = await projectRes.json();
    const status      = await statusRes.json();


    updateSlowQueries(slowQueries);
    updateProjectInfo(projectInfo);
    updateMetrics(metrics);
    updateTopTables(report.metrics?.topTablesByQueryFrequency);
    updateFindings(findings);
    updateScore(metrics.score ?? report.overallScore);
    updateAnalysisStatus(status);

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

//  Analysis Status

function updateAnalysisStatus(status) {
  const lastEl = document.getElementById('lastAnalysisLabel');
  const nextEl = document.getElementById('nextAnalysisLabel');
  if (!status) return;

  if (!status.firstAnalysisDone) {
    lastEl.textContent = 'Last analysis: chưa chạy';
    nextEl.textContent = status.scheduledEnabled
      ? 'Next: chuẩn bị chạy lần đầu...'
      : 'Scheduled: disabled (manual only)';
    return;
  }

  lastEl.textContent = 'Last analysis: ' + formatRelativeTime(status.secondsSinceLastAnalysis);
  if (status.scheduledEnabled && status.nextScheduledInSeconds >= 0) {
    nextEl.textContent = 'Next: in ' + formatDuration(status.nextScheduledInSeconds);
  } else {
    nextEl.textContent = 'Scheduled: disabled';
  }
}

function formatRelativeTime(seconds) {
  if (seconds < 0) return '--';
  if (seconds < 60) return seconds + 's ago';
  if (seconds < 3600) return Math.floor(seconds / 60) + 'm ago';
  if (seconds < 86400) return Math.floor(seconds / 3600) + 'h ago';
  return Math.floor(seconds / 86400) + 'd ago';
}

function formatDuration(seconds) {
  if (seconds < 60) return seconds + 's';
  if (seconds < 3600) return Math.floor(seconds / 60) + 'm';
  if (seconds < 86400) return Math.floor(seconds / 3600) + 'h';
  return Math.floor(seconds / 86400) + 'd';
}

async function triggerAnalyzeNow() {
  const btn = document.getElementById('analyzeNowBtn');
  btn.disabled = true;
  btn.textContent = ' Analyzing...';
  try {
    await fetch('/analyze-now', { method: 'POST' });
    // Đợi 1 chút để job chạy xong rồi reload data
    setTimeout(() => loadData(), 1500);
  } catch (e) {
    console.error('Analyze trigger failed:', e);
  } finally {
    setTimeout(() => {
      btn.disabled = false;
      btn.textContent = '⚡ Run Analysis Now';
    }, 1500);
  }
}

//  Collected Queries Tab

async function loadCollectedQueries() {
  const container = document.getElementById('collectedQueriesList');
  container.innerHTML = '<div class="empty">Loading...</div>';
  try {
    const res = await fetch('/collected-queries');
    const queries = await res.json();
    renderCollectedQueries(queries);
  } catch (e) {
    container.innerHTML = '<div class="error">Failed: ' + e.message + '</div>';
  }
}

function renderCollectedQueries(queries) {
  const container = document.getElementById('collectedQueriesList');
  if (!queries || queries.length === 0) {
    container.innerHTML = '<div class="empty">Chưa có SQL nào được intercept</div>';
    return;
  }

  container.innerHTML = `
    <div style="margin-bottom:12px; font-size:12px; color:#64748b">
      Tổng <b style="color:#e2e8f0">${queries.length}</b> unique SQL patterns
    </div>
    <table style="width:100%; border-collapse:collapse">
      <thead>
        <tr style="border-bottom:1px solid #334155">
          <th style="text-align:left; padding:8px; font-size:11px; color:#64748b">SQL Pattern</th>
          <th style="text-align:right; padding:8px; font-size:11px; color:#64748b; width:80px">Count</th>
          <th style="text-align:right; padding:8px; font-size:11px; color:#64748b; width:80px">Avg ms</th>
          <th style="text-align:right; padding:8px; font-size:11px; color:#64748b; width:80px">Min/Max</th>
          <th style="text-align:left; padding:8px; font-size:11px; color:#64748b; width:280px">Called From</th>
        </tr>
      </thead>
      <tbody>
        ${queries.map(q => `
          <tr style="border-bottom:1px solid #1e293b">
            <td style="padding:8px; font-size:11px; font-family:monospace; color:#94a3b8;
                       max-width:400px; word-break:break-all">${escapeHtml(truncate(q.sqlPattern, 200))}</td>
            <td style="padding:8px; text-align:right; font-size:12px; color:#e2e8f0">${q.callCount}</td>
            <td style="padding:8px; text-align:right; font-size:12px; color:#fdba74">${q.avgDurationMs}</td>
            <td style="padding:8px; text-align:right; font-size:11px; color:#64748b">${q.minDurationMs}/${q.maxDurationMs}</td>
            <td style="padding:8px; font-size:11px; color:#64748b; word-break:break-all">${escapeHtml(q.calledFrom)}</td>
          </tr>
        `).join('')}
      </tbody>
    </table>`;
}

function truncate(s, n) {
  if (!s) return '';
  return s.length <= n ? s : s.substring(0, n) + '...';
}

//  Schema Snapshot Tab (giờ render dạng ER Diagram)
//  Giữ tên function loadSchemaSnapshot để tương thích với các chỗ gọi cũ
async function loadSchemaSnapshot() {
  return loadERDiagram();
}

//  ER Diagram Tab (dùng mermaid.js, tận dụng lại /schema-snapshot)

async function loadERDiagram() {
  const container = document.getElementById('erdContainer');
  container.innerHTML = '<div class="empty">Loading...</div>';
  try {
    const res = await fetch('/schema-snapshot');
    const tables = await res.json();
    await renderERDiagram(tables);
  } catch (e) {
    container.innerHTML = '<div class="error">Failed: ' + e.message + '</div>';
  }
}

function mapColumnTypeForMermaid(type) {
  if (!type) return 'unknown';

  // Bỏ phần (n) hoặc (n,m) trước, vd: VARCHAR(100) -> VARCHAR
  let t = type.split('(')[0].trim().toLowerCase();

  // Mermaid không chấp nhận khoảng trắng trong tên kiểu (token bị cắt giữa chừng)
  // Map các kiểu nhiều từ của PostgreSQL/MySQL về 1 từ duy nhất
  const typeMap = {
    'double precision': 'double',
    'character varying': 'varchar',
    'character': 'char',
    'timestamp without time zone': 'timestamp',
    'timestamp with time zone': 'timestamptz',
    'time without time zone': 'time',
    'time with time zone': 'timetz'
  };

  if (typeMap[t]) return typeMap[t];

  // Fallback an toàn: nếu vẫn còn khoảng trắng (kiểu lạ chưa map),
  // nối lại bằng dấu gạch dưới thay vì để mermaid parse lỗi
  return t.replace(/\s+/g, '_');
}

function buildMermaidERSyntax(tables) {
  let lines = ['erDiagram'];

  tables.forEach(t => {
    (t.foreignKeys || []).forEach(fk => {
      lines.push(
        `  ${fk.referencedTable.toUpperCase()} ||--o{ ${t.name.toUpperCase()} : "${fk.column}"`
      );
    });
  });

  tables.forEach(t => {
    lines.push(`  ${t.name.toUpperCase()} {`);
    t.columns.forEach(c => {
      const type = mapColumnTypeForMermaid(c.type);
      const pk = c.primaryKey ? 'PK' : '';
      const isFk = (t.foreignKeys || []).some(fk => fk.column === c.name);
      const fk = isFk ? 'FK' : '';
      const keyLabel = [pk, fk].filter(Boolean).join(',');

      // Kiểm tra cột có index không (kể cả composite)
      const matchedIndexes = (t.indexes || []).filter(
        idx => (idx.columns || []).includes(c.name)
      );
      const hasIndex = matchedIndexes.length > 0;

      // Ghép các icon thành chú thích cuối dòng:
      //   🔑 = PK | 🔗 = FK | ❗ = NOT NULL | 📇 = có index (không phải PK)
      const icons = [];
      if (c.primaryKey) icons.push('🔑');
      if (isFk) icons.push('🔗');
      if (!c.nullable && !c.primaryKey) icons.push('❗'); // PK ngầm định NOT NULL
      if (hasIndex && !c.primaryKey) icons.push('📇');

      let commentText = icons.join(' ');
      if (hasIndex && !c.primaryKey) {
        commentText += ' ' + matchedIndexes.map(i => i.name).join(', ');
      }
      const comment = commentText ? `"${commentText.trim()}"` : '';

      lines.push(`    ${type} ${c.name} ${keyLabel} ${comment}`.trimEnd());
    });
    lines.push('  }');
  });

  return lines.join('\n');
}

async function renderERDiagram(tables) {
  const container = document.getElementById('erdContainer');

  if (!tables || tables.length === 0) {
    container.innerHTML = '<div class="empty">No tables collected</div>';
    return;
  }

  const syntax = buildMermaidERSyntax(tables);

  if (!window._mermaidInitialized) {
    window.mermaid.initialize({
      startOnLoad: false,
      theme: 'dark',
      themeVariables: {
        fontSize: '13px',
        primaryColor: '#1e293b',
        primaryTextColor: '#e2e8f0',
        primaryBorderColor: '#334155',
        lineColor: '#64748b',
        secondaryColor: '#0f172a',
        tertiaryColor: '#0f172a'
      },
      er: {
        // Tăng padding ngang trong mỗi cell để chữ không bị cắt sát viền
        entityPadding: 20,
        // Tăng minEntityWidth để mermaid chừa đủ chỗ cho cột dài (vd: "indexed: ...")
        minEntityWidth: 120
      }
    });
    window._mermaidInitialized = true;
  }

  try {
    const { svg } = await window.mermaid.render('erd-svg-' + Date.now(), syntax);
    container.innerHTML = svg;
    // Nếu zoom mode đang bật, gắn lại event cho SVG vừa render
    if (window._zoomModeActive) applyZoomMode(true);
  } catch (e) {
    container.innerHTML = '<div class="error">Render failed: ' + e.message +
      '<pre style="margin-top:8px; font-size:11px; color:#64748b">' +
      escapeHtml(syntax) + '</pre></div>';
  }
}

//  Zoom mode cho ER Diagram — bật/tắt chế độ phóng to phần SVG theo chuột

function toggleZoomMode() {
  window._zoomModeActive = !window._zoomModeActive;
  const btn = document.getElementById('zoomToggleBtn');

  if (window._zoomModeActive) {
    btn.style.background = '#0f4c81';
    btn.style.color = 'white';
    btn.style.borderColor = '#0f4c81';
    applyZoomMode(true);
  } else {
    btn.style.background = '#1e293b';
    btn.style.color = '#94a3b8';
    btn.style.borderColor = '#334155';
    applyZoomMode(false);
  }
}

function applyZoomMode(enable) {
  const container = document.getElementById('erdContainer');
  const svg = container.querySelector('svg');
  if (!svg) return;

  if (enable) {
    container.style.cursor = 'zoom-in';
    svg.style.transformOrigin = '0 0';
    svg.style.transition = 'transform 0.1s ease-out';
    container.onmousemove = handleZoomMove;
    container.onmouseleave = handleZoomLeave;
  } else {
    container.style.cursor = 'default';
    svg.style.transform = '';
    svg.style.transformOrigin = '';
    svg.style.transition = '';
    container.onmousemove = null;
    container.onmouseleave = null;
  }
}

function handleZoomMove(e) {
  const container = document.getElementById('erdContainer');
  const svg = container.querySelector('svg');
  if (!svg) return;

  // Vị trí chuột tương đối trong container
  const rect = container.getBoundingClientRect();
  const x = e.clientX - rect.left + container.scrollLeft;
  const y = e.clientY - rect.top + container.scrollTop;

  const zoomLevel = 2; // phóng to 2x
  // Dịch chuyển sao cho điểm dưới chuột vẫn nằm dưới chuột sau khi scale
  const tx = -(x * (zoomLevel - 1));
  const ty = -(y * (zoomLevel - 1));

  svg.style.transform = `translate(${tx}px, ${ty}px) scale(${zoomLevel})`;
}

function handleZoomLeave() {
  const svg = document.getElementById('erdContainer').querySelector('svg');
  if (svg) svg.style.transform = '';
}

//  Tab switching

function switchTab(tab) {
  document.querySelectorAll('.tab-panel').forEach(p => p.style.display = 'none');
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));

  document.getElementById('tab-' + tab).style.display = 'block';
  event.target.classList.add('active');

  // Lazy load nội dung khi switch sang tab tương ứng
  if (tab === 'queries') loadCollectedQueries();
  if (tab === 'schema') loadERDiagram();
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

async function refreshAI() {
  const btn = document.getElementById('refreshAIBtn');
  btn.disabled = true;
  btn.textContent = '⏳ Đang reset...';
  try {
    await fetch('/ai-refresh', { method: 'POST' });
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
    setTimeout(() => btn.textContent = ' Copy', 2000);
  });
}

function updateScore(score) {
  const valueEl = document.getElementById('scoreValue');
  const subEl = document.getElementById('scoreSub');
  if (!valueEl) return;

  valueEl.textContent = score + ' / 100';

  if (score >= 80) {
    valueEl.style.color = '#86efac';
    subEl.textContent = 'good';
  } else if (score >= 60) {
    valueEl.style.color = '#fcd34d';
    subEl.textContent = 'needs attention';
  } else {
    valueEl.style.color = '#fca5a5';
    subEl.textContent = 'critical issues';
  }
}

function updateMetrics(m) {
  document.getElementById('totalSQL').textContent  = m.totalSQLIntercepted ?? 0;
  document.getElementById('slowCount').textContent = m.slowQueryCount ?? 0;
  document.getElementById('nPlusOne').textContent  = m.nPlusOneDetected ?? 0;

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

  // topTablesByQueryFrequency không có trong realtime metrics — lấy từ report endpoint
  // Để đơn giản, có thể fetch riêng hoặc dùng từ /report. Giữ nguyên hiện tại.
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

  // Giữ filter hiện tại khi data refresh
  const severity = document.getElementById('severityFilter')?.value ?? 'ALL';
  filteredFindings = severity === 'ALL'
    ? allFindings
    : allFindings.filter(f => f.severity === severity);

  const maxPage = Math.max(0, Math.ceil(filteredFindings.length / PAGE_SIZE) - 1);
  currentPage = Math.min(currentPage, maxPage);
  renderPage();
}

function renderPage() {
  const start = currentPage * PAGE_SIZE;
  const end   = start + PAGE_SIZE;
  const page  = filteredFindings.slice(start, end);
  const total = filteredFindings.length;

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
                EXPLAIN
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

function updateTopTables(topTables) {
  const tbody = document.getElementById('topTablesBody');
  if (!tbody) return;

  const entries = Object.entries(topTables ?? {});
  tbody.innerHTML = entries.length === 0
    ? '<tr><td colspan="2" class="empty">No data</td></tr>'
    : entries.map(([t, c]) => `<tr><td>${t}</td><td>${c}</td></tr>`).join('');
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

function showChartTip(event, text) {
  const tip = document.getElementById('chartTooltip');
  if (!tip) return;
  tip.style.whiteSpace = 'pre-line';
  tip.style.maxWidth = '280px';
  tip.style.lineHeight = '1.6';
  tip.textContent = text;
  tip.style.display = 'block';

  // Lấy width thực sau khi render
  const tipWidth = tip.offsetWidth;
  const spaceRight = window.innerWidth - event.clientX;

  if (spaceRight < tipWidth + 20) {
    tip.style.left = (event.clientX - tipWidth - 4) + 'px';
  } else {
    tip.style.left = (event.clientX + 4) + 'px';
  }
  tip.style.top = (event.clientY - 8) + 'px';
}

function filterFindings() {
  const severity = document.getElementById('severityFilter').value;
  filteredFindings = severity === 'ALL'
    ? allFindings
    : allFindings.filter(f => f.severity === severity);
  currentPage = 0;
  renderPage();
}

function hideChartTip() {
  const tip = document.getElementById('chartTooltip');
  if (tip) tip.style.display = 'none';
}

function nextPage() { currentPage++; renderPage(); }
function prevPage() { currentPage--; renderPage(); }

loadData();
loadAIContext();
setInterval(() => {
    loadData();
    loadAIContext();
    if (document.getElementById('tab-queries').style.display !== 'none') {
        loadCollectedQueries();
    }
}, 5000);