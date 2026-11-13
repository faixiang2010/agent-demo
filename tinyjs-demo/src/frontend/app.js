// src/frontend/app.js — NEXUS console page.
// Login → left nav + right views, on top of tiny.api (real host telemetry
// streamed from the backend process).

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

const TITLES = { overview: '总览', monitor: '系统监控', logs: '运行日志', settings: '终端设置' };
const VIEWS = ['overview', 'monitor', 'logs', 'settings'];
const DEFAULT_SETTINGS = { motion: true, compact: false, telemetry: true };

const state = {
  user: null,
  since: 0,
  token: '',
  host: '',
  settings: { ...DEFAULT_SETTINGS },
  series: [],
  logs: [],
  nodes: [],
  queue: [],
  coreCells: [],
  coreRows: [],
  lastCores: null,
  logIds: 0,
  view: 'overview',
  filter: 'all',
  autoscroll: true,
  unseen: 0,
  offTelemetry: null,
  busy: false,
  fullscreen: false,
  latency: 22,
  hover: -1,
  menuBound: false,
  saveTimer: 0,
  fxRaf: 0,
  resizeRaf: 0,
  clockTimer: 0,
};

const clamp = (n, lo, hi) => Math.min(hi, Math.max(lo, n));
const delay = (ms) => new Promise((r) => setTimeout(r, ms));
const pct = (n) => Math.round(Number(n) || 0);

function el(tag, cls, text) {
  const node = document.createElement(tag);
  if (cls) node.className = cls;
  if (text !== undefined) node.textContent = text;
  return node;
}

function pad(n, w = 2) {
  return String(n).padStart(w, '0');
}

function timeOf(ts) {
  const d = new Date(ts);
  return pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
}

function clockOf(d = new Date()) {
  return (
    d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' +
    pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds())
  );
}

function elapsed(ms) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  return (h ? h + 'h ' : '') + pad(m) + 'm ' + pad(s) + 's';
}

function uptimeText(sec) {
  const d = Math.floor(sec / 86400);
  const h = Math.floor((sec % 86400) / 3600);
  const m = Math.floor((sec % 3600) / 60);
  return (d ? d + ' 天 ' : '') + pad(h) + ':' + pad(m);
}

// ── ambient canvas ──────────────────────────────────────────────────────────

const fx = $('#fx');
let fxStars = [];
let fxMouse = { x: 0, y: 0, tx: 0, ty: 0 };
let fxSize = { w: 0, h: 0, dpr: 1 };

function fxResize() {
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  fxSize = { w: fx.clientWidth || window.innerWidth, h: fx.clientHeight || window.innerHeight, dpr };
  fx.width = Math.round(fxSize.w * dpr);
  fx.height = Math.round(fxSize.h * dpr);
  const count = Math.round((fxSize.w * fxSize.h) / 16000);
  fxStars = Array.from({ length: clamp(count, 40, 120) }, () => ({
    x: Math.random(),
    y: Math.random(),
    z: 0.25 + Math.random() * 0.75,
    r: 0.4 + Math.random() * 1.2,
    v: 0.00004 + Math.random() * 0.00012,
    blink: Math.random() * Math.PI * 2,
  }));
}

function fxLoop(t) {
  const ctx = fx.getContext('2d');
  const { w, h, dpr } = fxSize;
  if (!w || !h) {
    state.fxRaf = requestAnimationFrame(fxLoop);
    return;
  }
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, w, h);

  fxMouse.x += (fxMouse.tx - fxMouse.x) * 0.05;
  fxMouse.y += (fxMouse.ty - fxMouse.y) * 0.05;

  for (const s of fxStars) {
    s.y -= s.v * (0.4 + s.z);
    if (s.y < -0.02) {
      s.y = 1.02;
      s.x = Math.random();
    }
    const px = (s.x + fxMouse.x * 0.012 * s.z) * w;
    const py = (s.y + fxMouse.y * 0.012 * s.z) * h;
    const alpha = (0.16 + s.z * 0.5) * (0.7 + 0.3 * Math.sin(t / 900 + s.blink));
    ctx.beginPath();
    ctx.arc(px, py, s.r * s.z, 0, Math.PI * 2);
    ctx.fillStyle = s.z > 0.8 ? `rgba(150, 240, 255, ${alpha})` : `rgba(120, 180, 210, ${alpha * 0.7})`;
    ctx.fill();
  }

  // a few drifting telemetry motes
  const motes = 5;
  for (let i = 0; i < motes; i++) {
    const p = ((t / 26000 + i / motes) % 1);
    const x = (0.12 + i * 0.19) * w + Math.sin(t / 3000 + i) * 18;
    const y = h - p * h;
    ctx.beginPath();
    ctx.arc(x, y, 1.2, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(63, 224, 255, ${0.20 * (1 - p)})`;
    ctx.fill();
  }

  state.fxRaf = requestAnimationFrame(fxLoop);
}

function startFx() {
  if (state.fxRaf) return;
  if (!state.settings.motion) return;
  state.fxRaf = requestAnimationFrame(fxLoop);
}

function stopFx() {
  if (state.fxRaf) cancelAnimationFrame(state.fxRaf);
  state.fxRaf = 0;
}

// ── clock ──────────────────────────────────────────────────────────────────

function startClock() {
  const tick = () => {
    const now = new Date();
    $('#frame-clock').textContent = timeOf(now);
    const mission = $('#mission-clock');
    if (mission) mission.textContent = clockOf(now);
    if (state.since) {
      const node = $('#session-elapsed');
      if (node) node.textContent = elapsed(Date.now() - state.since);
    }
  };
  tick();
  state.clockTimer = setInterval(tick, 1000);
}

// ── login ──────────────────────────────────────────────────────────────────

function setError(msg) {
  const node = $('#auth-error');
  node.textContent = msg;
  node.classList.remove('shake');
  void node.offsetWidth;
  if (msg) node.classList.add('shake');
}

function resetBoot() {
  $('#boot').hidden = true;
  $('#boot-bar').style.width = '0%';
  $$('#boot-steps li').forEach((li) => {
    li.classList.remove('active', 'done');
    li.querySelector('.state').textContent = '待执行';
  });
  $('#submit').disabled = false;
}

function setBar(v) {
  $('#boot-bar').style.width = v + '%';
}

async function bootStep(index, stateText) {
  const li = $('#boot-steps li[data-step="' + index + '"]');
  if (!li) return;
  li.classList.add('active');
  li.querySelector('.state').textContent = '执行中';
  await delay(420);
  li.classList.remove('active');
  li.classList.add('done');
  li.querySelector('.state').textContent = stateText || 'OK';
}

async function authenticate(event) {
  event.preventDefault();
  if (state.busy) return;

  const account = $('#account').value.trim();
  const password = $('#password').value;
  setError('');
  if (!account) {
    setError('请输入操作员编号。');
    $('#account').focus();
    return;
  }
  if (password.length < 6) {
    setError('访问密钥至少需要 6 位字符。');
    $('#password').focus();
    return;
  }

  state.busy = true;
  const submit = $('#submit');
  submit.disabled = true;
  $('#boot').hidden = false;
  setBar(6);
  await delay(160);

  try {
    await bootStep(0);
    setBar(34);
    await bootStep(1);
    setBar(64);
    const session = await tiny.api.call('login', { account, password });
    await bootStep(2, '就绪');
    setBar(100);
    state.busy = false;
    await delay(300);
    await enterConsole(session);
  } catch (err) {
    state.busy = false;
    resetBoot();
    setError(typeof err === 'string' ? err : err.message || '连接失败，请重试。');
  }
}

async function enterConsole(session) {
  state.user = session.user;
  state.since = session.since;
  state.token = session.token;

  const boot = tiny.api.call('bootstrap');

  $('#login').classList.add('leaving');  $('#wipe').classList.add('on');
  await delay(380);

  $('#login').hidden = true;
  const shell = $('#shell');
  shell.hidden = false;
  shell.classList.add('enter');
  $('#wipe').classList.remove('on');
  stopFx();
  setView('overview');
  installMenu();

  const data = await boot;
  applyBootstrap(data);
  subscribe();
  setTimeout(() => shell.classList.remove('enter'), 900);

  requestAnimationFrame(() => {
    drawChart();
    drawSparks();
  });
}

function applyBootstrap(data) {
  state.settings = { ...DEFAULT_SETTINGS, ...(data.settings || {}) };
  state.series = Array.isArray(data.series) ? data.series.slice() : [];
  state.queue = Array.isArray(data.queue) ? data.queue : [];
  state.nodes = Array.isArray(data.nodes) ? data.nodes : [];
  state.logs = Array.isArray(data.logs) ? data.logs.slice() : [];
  state.host = data.system ? data.system.host : '';
  state.logIds = state.logs.reduce((max, l) => Math.max(max, l.id || 0), 0);

  if (state.token) {
    $('#session-token').textContent = 'NX-' + state.token.slice(0, 4) + '-' + state.token.slice(4, 8) + '-' + state.token.slice(8, 12);
  }
  renderOperator();
  renderSystem(data.system || {});
  applySettings();
  syncSettingsInputs();

  if (state.nodes.length) {
    const avg = Math.round(state.nodes.reduce((a, n) => a + (n.latency || 0), 0) / state.nodes.length);
    state.latency = avg;
    updateUplink();
  }

  renderQueue(state.queue);
  renderNodes(state.nodes);
  renderFeed(state.logs.slice(-7).reverse());
  renderLogs();
  updateBadge();

  const last = state.series[state.series.length - 1];
  const cores = Array.isArray(data.cores) && data.cores.length
    ? data.cores
    : new Array((data.system && data.system.cores) || 8).fill(0);
  state.lastCores = cores;
  renderCores(cores.map((v) => ({ v })));
  renderCoreGrid(cores);
  if (last) renderSample(last);
}

function subscribe() {
  if (state.offTelemetry) return;
  state.offTelemetry = tiny.api.on('telemetry', onTelemetry);
}

const onTelemetry = (payload) => {
  if (!state.settings.telemetry || !payload) return;

  if (Array.isArray(payload.logs) && payload.logs.length) {
    for (const entry of payload.logs) {
      if (!entry || entry.id <= state.logIds) continue;
      state.logIds = entry.id;
      state.logs.push(entry);
      appendLogRow(entry);
      prependFeed(entry);
      if (state.view !== 'logs') state.unseen++;
    }
    if (state.logs.length > 400) state.logs = state.logs.slice(-400);
    updateBadge();
    updateLogCount();
  }

  if (payload.sample) {
    state.series.push(payload.sample);
    if (state.series.length > 48) state.series.shift();
    renderSample(payload.sample);
  }
  if (Array.isArray(payload.cores) && payload.cores.length) {
    state.lastCores = payload.cores;
    renderCores(payload.cores.map((v) => ({ v })));
    renderCoreGrid(payload.cores);
  }
  if (Array.isArray(payload.queue)) {
    state.queue = payload.queue;
    renderQueue(payload.queue);
  }
  if (Array.isArray(payload.nodes) && payload.nodes.length) {
    state.nodes = payload.nodes;
    renderNodes(payload.nodes);
    const avg = Math.round(payload.nodes.reduce((a, n) => a + (n.latency || 0), 0) / payload.nodes.length);
    state.latency = avg;
    updateUplink();
  }
};

function renderOperator() {
  const user = state.user || {};
  $('#operator-name').textContent = user.name || 'OPERATOR';
  $('#operator-role').textContent = user.role || '深空指挥员';
  $('#operator-clearance').textContent = user.clearance || 'OMEGA';
  $('#avatar-initials').textContent = user.initials || 'NX';
  $('#profile-name').textContent = user.name || 'OPERATOR';
  $('#profile-role').textContent = user.role || '深空指挥员';
  $('#profile-initials').textContent = user.initials || 'NX';
  $('#profile-clearance').textContent = user.clearance || 'OMEGA';
  $('#session-since').textContent = timeOf(state.since || Date.now());
  $('#session-host').textContent = state.host || 'localhost';
}

function renderSystem(sys) {
  const list = $('#sysinfo');
  if (!list) return;
  list.replaceChildren();
  const rows = [
    ['主机名', sys.host || 'unknown'],
    ['平台', sys.platform || 'unknown'],
    ['架构', sys.arch || 'unknown'],
    ['处理器', sys.cpuModel || 'unknown'],
    ['核心 / 线程', (sys.cores || 0) + ' threads'],
    ['系统运行时长', uptimeText(sys.uptime || 0)],
    ['系统负载', (sys.load || []).join('  /  ') || 'n/a'],
    ['txiki.js 运行时', sys.runtime || 'unknown'],
    ['QuickJS 引擎', sys.quickjs || 'unknown'],
    ['后端进程 PID', String(sys.pid || '--')],
  ];
  for (const [key, value] of rows) {
    const row = el('div');
    row.append(el('dt', null, key), el('dd', null, value));
    list.append(row);
  }

  const ifaces = $('#ifaces');
  ifaces.replaceChildren();
  const items = sys.ifaces || [];
  if (!items.length) {
    ifaces.append(el('p', 'iface-empty', '未检测到活动的网络接口。'));
  }
  for (const item of items) {
    const box = el('div', 'iface');
    const top = el('div', 'iface-top');
    top.append(el('b', null, item.name), el('span', null, 'UP'));
    box.append(top, el('p', null, item.address + ' · ' + item.mac));
    ifaces.append(box);
  }

  $('#core-count').textContent = (sys.cores || 0) + ' 线程';
  $('#core-meta').textContent = (sys.cores || 0) + ' THREADS';
  $('#session-runtime').textContent = sys.runtime || '--';
  $('#session-uptime').textContent = 'HOST UP ' + uptimeText(sys.uptime || 0);
}

// ── metrics ────────────────────────────────────────────────────────────────

function flash(node) {
  node.classList.remove('flash');
  void node.offsetWidth;
  node.classList.add('flash');
}

function renderSample(sample) {
  const cpu = pct(sample.cpu);
  const mem = pct(sample.mem);
  const cpuNode = $('#kpi-cpu');
  if (cpuNode.textContent !== String(cpu)) {
    cpuNode.textContent = cpu;
    flash(cpuNode.parentElement);
  }
  $('#kpi-mem').textContent = mem;
  $('#kpi-nodes').textContent = sample.nodes || 12;
  $('#kpi-tx').textContent = Math.round(sample.rx || 0);
  $('#kpi-cpu-foot').textContent = (state.lastCores ? state.lastCores.length : '--') + ' 线程 · 实时采样';
  $('#kpi-mem-foot').textContent = '合成指标 · 模拟采样';
  $('#kpi-nodes-foot').textContent = '中继在线';
  $('#kpi-tx-foot').textContent = '峰值 ' + Math.max(...state.series.map((s) => s.rx || 0), 0) + ' MB/s · 60s 窗口';

  $('#lg-rx').textContent = Math.round(sample.rx || 0) + ' MB/s';
  $('#lg-tx').textContent = Math.round(sample.tx || 0) + ' MB/s';

  updateGauge(cpu, mem);
  drawChart();
  drawSparks();
}

function updateGauge(cpu, mem) {
  const c1 = 2 * Math.PI * 86;
  const c2 = 2 * Math.PI * 66;
  const cpuRing = $('#g-cpu');
  const memRing = $('#g-mem');
  cpuRing.style.strokeDasharray = String(c1);
  memRing.style.strokeDasharray = String(c2);
  cpuRing.style.strokeDashoffset = String(c1 * (1 - clamp(cpu, 0, 100) / 100));
  memRing.style.strokeDashoffset = String(c2 * (1 - clamp(mem, 0, 100) / 100));
  $('#gauge-cpu').replaceChildren(document.createTextNode(String(cpu)), el('small', null, '%'));
  $('#gauge-mem').textContent = 'MEM ' + mem + '%';
}

function renderCores(cores) {
  const list = $('#core-bars');
  const values = cores.slice(0, 8);
  if (state.coreRows.length !== values.length) {
    list.replaceChildren();
    state.coreRows = values.map((_, i) => {
      const row = el('div', 'core-row');
      const track = el('div', 'core-track');
      const fill = el('i');
      const label = el('b', null, '0');
      track.append(fill);
      row.append(el('span', null, 'C' + i), track, label);
      list.append(row);
      return { fill, label };
    });
  }
  values.forEach((core, i) => {
    const row = state.coreRows[i];
    if (!row) return;
    const value = pct(core.v);
    row.fill.style.width = clamp(value, 0, 100) + '%';
    row.fill.style.background =
      value > 78 ? 'var(--amber)' : value > 55 ? 'var(--cyan)' : 'var(--cyan-soft)';
    row.label.textContent = String(value);
  });
}

function renderCoreGrid(cores) {
  const grid = $('#core-grid');
  const values = cores.slice(0, 32);
  if (grid.children.length !== values.length) {
    grid.replaceChildren();
    state.coreCells = values.map((_, i) => {
      const cell = el('div', 'core-cell');
      const bar = el('div', 'bar');
      const fill = el('i');
      bar.append(fill);
      cell.append(bar, el('span', null, 'C' + i), el('b', null, '0%'));
      grid.append(cell);
      return { cell, fill, label: cell.querySelector('b') };
    });
  }
  values.forEach((v, i) => {
    const cell = state.coreCells[i];
    if (!cell) return;
    const value = pct(v);
    cell.fill.style.height = clamp(value, 0, 100) + '%';
    cell.label.textContent = value + '%';
    cell.cell.classList.toggle('hot', value > 78);
  });
}

function renderQueue(queue) {
  const body = $('#queue-body');
  const ids = queue.map((t) => t.id).join(',');
  if (body.dataset.ids !== ids) {
    body.replaceChildren();
    for (const task of queue) {
      const row = el('div', 'q-row');
      row.dataset.id = task.id;
      const name = el('div', 'q-name');
      name.append(el('b', null, task.name), el('span', null, task.id));
      const status = el('span', 'q-status');
      const bar = el('div', 'q-bar');
      const fill = el('i');
      bar.append(fill);
      row.append(name, status, bar, el('span', 'q-eta'));
      body.append(row);
    }
    body.dataset.ids = ids;
  }
  for (const task of queue) {
    const row = body.querySelector('[data-id="' + task.id + '"]');
    if (!row) continue;
    const status = row.querySelector('.q-status');
    status.className = 'q-status ' + task.status;
    status.textContent = task.status === 'running' ? '运行中' : task.status === 'queued' ? '排队' : '完成';
    row.querySelector('.q-bar i').style.width = clamp(task.progress || 0, 0, 100) + '%';
    row.querySelector('.q-eta').textContent = task.eta || '--';
  }
  const running = queue.filter((t) => t.status === 'running').length;
  $('#queue-meta').textContent = running + ' RUNNING · ' + queue.length + ' TOTAL';
}

function renderNodes(nodes) {
  const box = $('#cluster');
  box.replaceChildren();
  for (const node of nodes) {
    const card = el('div', 'node ' + (node.status === 'degraded' ? 'degraded' : ''));
    const top = el('div', 'node-top');
    top.append(
      el('b', null, node.id),
      el('span', null, node.name + ' · ' + node.region),
      el('span', 'lat', Math.round(node.latency) + ' ms'),
    );
    const bar = el('div', 'node-bar');
    const track = el('div', 'track');
    const fill = el('i');
    fill.style.width = clamp(node.load, 0, 100) + '%';
    track.append(fill);
    bar.append(el('em', 'node-state', node.status === 'degraded' ? 'DEGRADED' : 'ONLINE'), track, el('em', null, Math.round(node.load) + '%'));
    card.append(top, bar);
    box.append(card);
  }
  const online = nodes.filter((n) => n.status !== 'degraded').length;
  $('#cluster-meta').textContent = online + ' / ' + nodes.length + ' ONLINE';
}

function updateUplink() {
  const bars = $$('#uplink-bars i');
  const latency = state.latency || 0;
  const lit = latency < 30 ? 6 : latency < 50 ? 5 : latency < 70 ? 4 : 3;
  bars.forEach((bar, i) => bar.classList.toggle('lit', i < lit));
  $('#uplink-latency').textContent = latency + ' ms';
  const node = $('#uplink-state');
  node.textContent = latency < 45 ? '加密链路稳定' : '链路延迟升高';
  node.style.color = latency < 45 ? '' : 'var(--amber)';
}

// ── charts ─────────────────────────────────────────────────────────────────

function fit(canvas, height) {
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const width = canvas.clientWidth || 0;
  const h = height || canvas.clientHeight || 0;
  if (!width || !h) return null;
  if (canvas.width !== Math.round(width * dpr) || canvas.height !== Math.round(h * dpr)) {
    canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(h * dpr);
  }
  const ctx = canvas.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  return { ctx, width, height: h };
}

function drawChart() {
  const canvas = $('#throughput');
  if (!canvas || state.view !== 'overview') return;
  const box = fit(canvas);
  if (!box) return;
  const { ctx, width, height } = box;
  ctx.clearRect(0, 0, width, height);

  const series = state.series;
  const padL = 40;
  const padR = 14;
  const padT = 12;
  const padB = 22;
  const plotW = width - padL - padR;
  const plotH = height - padT - padB;

  const maxValue = Math.max(
    60,
    ...series.map((s) => Math.max(s.rx || 0, s.tx || 0)),
  );
  const step = maxValue > 240 ? 100 : maxValue > 120 ? 50 : 25;
  const top = Math.ceil(maxValue / step) * step;

  ctx.font = '11px "Cascadia Mono", Consolas, monospace';
  ctx.textBaseline = 'middle';
  for (let i = 0; i <= 4; i++) {
    const y = padT + (plotH / 4) * i;
    ctx.beginPath();
    ctx.moveTo(padL, y);
    ctx.lineTo(width - padR, y);
    ctx.strokeStyle = i === 4 ? 'rgba(96,214,255,.16)' : 'rgba(96,214,255,.07)';
    ctx.lineWidth = 1;
    ctx.stroke();
    ctx.fillStyle = 'rgba(109,146,166,.8)';
    ctx.textAlign = 'right';
    ctx.fillText(String(Math.round(top - (top / 4) * i)), padL - 8, y);
  }

  if (series.length < 2) return;

  const xOf = (i) => padL + (plotW * i) / (series.length - 1);
  const yOf = (v) => padT + plotH * (1 - v / top);

  const area = ctx.createLinearGradient(0, padT, 0, padT + plotH);
  area.addColorStop(0, 'rgba(63, 224, 255, .22)');
  area.addColorStop(1, 'rgba(63, 224, 255, 0)');

  ctx.beginPath();
  series.forEach((s, i) => {
    const x = xOf(i);
    const y = yOf(s.rx || 0);
    if (i === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.lineTo(xOf(series.length - 1), padT + plotH);
  ctx.lineTo(xOf(0), padT + plotH);
  ctx.closePath();
  ctx.fillStyle = area;
  ctx.fill();

  const line = (key, color, glow) => {
    ctx.beginPath();
    series.forEach((s, i) => {
      const x = xOf(i);
      const y = yOf(s[key] || 0);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.strokeStyle = color;
    ctx.lineWidth = 1.5;
    ctx.shadowColor = glow;
    ctx.shadowBlur = 9;
    ctx.stroke();
    ctx.shadowBlur = 0;
  };
  line('tx', 'rgba(47,123,255,.85)', 'rgba(47,123,255,.55)');
  line('rx', 'rgba(63,224,255,.95)', 'rgba(63,224,255,.65)');

  // last point marker
  const last = series[series.length - 1];
  const lx = xOf(series.length - 1);
  const ly = yOf(last.rx || 0);
  ctx.beginPath();
  ctx.arc(lx, ly, 2.6, 0, Math.PI * 2);
  ctx.fillStyle = '#d8fbff';
  ctx.shadowColor = 'rgba(63,224,255,.9)';
  ctx.shadowBlur = 10;
  ctx.fill();
  ctx.shadowBlur = 0;

  // time labels
  ctx.fillStyle = 'rgba(109,146,166,.75)';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'top';
  [0, Math.floor(series.length / 3), Math.floor((series.length * 2) / 3), series.length - 1].forEach((idx) => {
    if (idx < 0 || !series[idx]) return;
    const x = xOf(idx);
    ctx.fillText(timeOf(series[idx].ts).slice(3), clamp(x, padL + 16, width - padR - 16), padT + plotH + 7);
  });

  // hover crosshair
  if (state.hover >= 0 && state.hover < series.length && state.hoverActive) {
    const hx = xOf(state.hover);
    const s = series[state.hover];
    ctx.beginPath();
    ctx.moveTo(hx, padT);
    ctx.lineTo(hx, padT + plotH);
    ctx.strokeStyle = 'rgba(63,224,255,.28)';
    ctx.setLineDash([3, 4]);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.beginPath();
    ctx.arc(hx, yOf(s.rx || 0), 3, 0, Math.PI * 2);
    ctx.fillStyle = 'var(--cyan)';
    ctx.fillStyle = '#3fe0ff';
    ctx.fill();
    const tip = $('#chart-tip');
    tip.hidden = false;
    tip.innerHTML = '';
    tip.append(document.createTextNode(timeOf(s.ts) + '  '));
    const rx = el('b', null, '↓ ' + Math.round(s.rx || 0) + ' MB/s');
    const tx = el('i', null, '  ↑ ' + Math.round(s.tx || 0) + ' MB/s');
    tip.append(rx, tx);
    const left = clamp(hx + 14, 8, width - tip.offsetWidth - 8);
    tip.style.left = left + 'px';
  } else {
    $('#chart-tip').hidden = true;
  }
}

function drawSparks() {
  const canvases = $$('.spark');
  const keys = ['cpu', 'mem', 'nodes', 'rx'];
  canvases.forEach((canvas, index) => {
    const key = keys[index];
    const box = fit(canvas);
    if (!box) return;
    const { ctx, width, height } = box;
    ctx.clearRect(0, 0, width, height);
    const values = state.series.slice(-28).map((s) => Number(s[key]) || 0);
    if (values.length < 2) return;
    const max = Math.max(...values);
    const min = Math.min(...values);
    const span = Math.max(1, max - min);
    const xOf = (i) => (width * i) / (values.length - 1);
    const yOf = (v) => height - 4 - ((v - min) / span) * (height - 8);

    ctx.beginPath();
    values.forEach((v, i) => {
      const x = xOf(i);
      const y = yOf(v);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.strokeStyle = key === 'mem' ? 'rgba(47,123,255,.75)' : 'rgba(63,224,255,.7)';
    ctx.lineWidth = 1.2;
    ctx.stroke();

    ctx.lineTo(width, height);
    ctx.lineTo(0, height);
    ctx.closePath();
    const g = ctx.createLinearGradient(0, 0, 0, height);
    g.addColorStop(0, key === 'mem' ? 'rgba(47,123,255,.18)' : 'rgba(63,224,255,.16)');
    g.addColorStop(1, 'rgba(63,224,255,0)');
    ctx.fillStyle = g;
    ctx.fill();

    const lx = xOf(values.length - 1);
    const ly = yOf(values[values.length - 1]);
    ctx.beginPath();
    ctx.arc(lx, ly, 1.8, 0, Math.PI * 2);
    ctx.fillStyle = key === 'mem' ? '#5b93ff' : '#3fe0ff';
    ctx.fill();
  });
}

function bindChartHover() {
  const wrap = document.querySelector('.chart-wrap');
  const canvas = $('#throughput');
  if (!wrap || !canvas) return;
  wrap.addEventListener('pointermove', (event) => {
    const rect = canvas.getBoundingClientRect();
    const padL = 40;
    const padR = 14;
    const plotW = rect.width - padL - padR;
    const x = event.clientX - rect.left;
    if (x < padL - 4 || x > rect.width - padR + 4) {
      state.hoverActive = false;
      drawChart();
      return;
    }
    const ratio = clamp((x - padL) / plotW, 0, 1);
    state.hover = Math.round(ratio * (state.series.length - 1));
    state.hoverActive = true;
    drawChart();
  });
  wrap.addEventListener('pointerleave', () => {
    state.hoverActive = false;
    drawChart();
  });
}

// ── feed & logs ────────────────────────────────────────────────────────────

function prependFeed(entry) {
  const list = $('#feed');
  if (!list) return;
  const item = el('li', entry.level);
  item.append(el('time', null, timeOf(entry.ts)), el('span', 'dot'), el('p', null, entry.msg));
  list.prepend(item);
  while (list.children.length > 8) list.lastElementChild.remove();
}

function renderFeed(entries) {
  const list = $('#feed');
  list.replaceChildren();
  for (const entry of entries) {
    const item = el('li', entry.level);
    item.append(el('time', null, timeOf(entry.ts)), el('span', 'dot'), el('p', null, entry.msg));
    list.append(item);
  }
}

function logMatches(entry) {
  if (state.filter === 'all') return true;
  if (state.filter === 'info') return entry.level === 'info' || entry.level === 'ok';
  return entry.level === state.filter;
}

function makeLogRow(entry, fresh) {
  const row = el('div', 'log-line ' + entry.level + (fresh ? ' fresh' : ''));
  row.append(
    el('time', null, '[' + timeOf(entry.ts) + ']'),
    el('span', 'lv', String(entry.level).toUpperCase()),
    el('span', 'src', entry.src),
    el('span', 'msg', entry.msg),
  );
  return row;
}

function appendLogRow(entry) {
  if (!logMatches(entry)) return;
  const stream = $('#log-stream');
  if (!stream) return;
  const row = makeLogRow(entry, true);
  stream.append(row);
  while (stream.children.length > 300) stream.firstElementChild.remove();
  if (state.autoscroll) stream.scrollTop = stream.scrollHeight;
  updateLogCount();
}

function renderLogs() {
  const stream = $('#log-stream');
  if (!stream) return;
  stream.replaceChildren();
  const visible = state.logs.filter(logMatches).slice(-300);
  for (const entry of visible) stream.append(makeLogRow(entry, false));
  if (!visible.length) {
    stream.append(el('p', 'log-empty', '// 暂无匹配的日志条目'));
  }
  if (state.autoscroll) stream.scrollTop = stream.scrollHeight;
  updateLogCount();
}

function updateLogCount() {
  const node = $('#log-count');
  if (node) node.textContent = String(state.logs.length);
}

function updateBadge() {
  const badge = $('#log-badge');
  if (!badge) return;
  if (state.view === 'logs') state.unseen = 0;
  badge.hidden = state.unseen === 0;
  badge.textContent = state.unseen > 99 ? '99+' : String(state.unseen);
}

// ── views / nav ────────────────────────────────────────────────────────────

function setView(name) {
  if (!VIEWS.includes(name)) return;
  state.view = name;
  for (const section of $$('.view')) {
    const active = section.dataset.view === name;
    section.hidden = !active;
    if (active) {
      section.classList.remove('anim');
      void section.offsetWidth;
      section.classList.add('anim');
    }
  }
  for (const item of $$('.nav-item')) {
    const active = item.dataset.view === name;
    item.classList.toggle('active', active);
    if (active) item.setAttribute('aria-current', 'page');
    else item.removeAttribute('aria-current');
  }
  $('#view-title').textContent = TITLES[name];
  tiny.win.setTitle('NEXUS // ' + TITLES[name]);
  if (name === 'logs') {
    state.unseen = 0;
    updateBadge();
    renderLogs();
  }
  if (name === 'overview') {
    requestAnimationFrame(() => {
      drawChart();
      drawSparks();
      const last = state.series[state.series.length - 1];
      if (last) renderSample(last);
    });
  }
  if (name === 'monitor' && state.nodes.length) renderNodes(state.nodes);
}

function installMenu() {
  tiny.menu.set([
    {
      title: '视图',
      items: [
        { id: 'goto-overview', label: '总览' },
        { id: 'goto-monitor', label: '系统监控' },
        { id: 'goto-logs', label: '运行日志' },
        { id: 'goto-settings', label: '终端设置' },
      ],
    },
    {
      title: '账户',
      items: [{ id: 'account-logout', label: '断开连接' }],
    },
  ]);
  if (state.menuBound) return;
  state.menuBound = true;
  tiny.menu.on((id) => {
    const name = String(id);
    if (!name.startsWith('goto-') && name !== 'account-logout') return;
    if ($('#shell').hidden) return;
    if (name.startsWith('goto-')) setView(name.slice(5));
    if (name === 'account-logout') logout();
  });
}

// ── settings ───────────────────────────────────────────────────────────────

function syncSettingsInputs() {
  $('#pref-motion').checked = state.settings.motion;
  $('#pref-compact').checked = state.settings.compact;
  $('#pref-telemetry').checked = state.settings.telemetry;
}

function applySettings() {
  document.body.classList.toggle('reduce-motion', !state.settings.motion);
  document.body.classList.toggle('compact', state.settings.compact);
  const chip = $('#live-chip');
  chip.classList.toggle('paused', !state.settings.telemetry);
  chip.lastChild.textContent = state.settings.telemetry ? 'TELEMETRY LIVE' : 'TELEMETRY PAUSED';
  if (state.settings.motion) startFx();
  else {
    stopFx();
    const ctx = fx.getContext('2d');
    ctx && ctx.clearRect(0, 0, fx.width, fx.height);
  }
  requestAnimationFrame(() => {
    drawChart();
    drawSparks();
  });
}

function persistSettings() {
  clearTimeout(state.saveTimer);
  const note = $('#save-state');
  note.textContent = '保存中…';
  state.saveTimer = setTimeout(async () => {
    try {
      const result = await tiny.api.call('saveSettings', { settings: state.settings });
      note.textContent = result && result.stored === false ? '仅本次会话生效' : '已保存 · ' + timeOf(Date.now());
    } catch {
      note.textContent = '保存失败 · 仅本次会话生效';
    }
  }, 260);
}

function bindSettings() {
  const map = [
    ['#pref-motion', 'motion'],
    ['#pref-compact', 'compact'],
    ['#pref-telemetry', 'telemetry'],
  ];
  for (const [selector, key] of map) {
    $(selector).addEventListener('change', (event) => {
      state.settings[key] = event.target.checked;
      applySettings();
      persistSettings();
      if (key === 'telemetry' && event.target.checked) subscribe();
    });
  }
  $('#reset-prefs').addEventListener('click', () => {
    state.settings = { ...DEFAULT_SETTINGS };
    syncSettingsInputs();
    applySettings();
    persistSettings();
    $('#save-state').textContent = '已恢复默认值';
  });
}

// ── logout ─────────────────────────────────────────────────────────────────

async function logout() {
  let sure = true;
  try {
    sure = await tiny.dialog.confirm('断开与轨道控制台的连接？', {
      detail: '当前会话将被锁定，需要重新验证身份才能再次接入。',
      ok: '断开连接',
    });
  } catch {
    sure = true;
  }
  if (!sure) return;

  try {
    await tiny.api.call('logout');
  } catch {
    /* the session is local to the backend; keep going */
  }

  if (state.offTelemetry) {
    state.offTelemetry();
    state.offTelemetry = null;
  }
  $('#wipe').classList.add('on');
  await delay(220);

  $('#shell').hidden = true;
  $('#shell').classList.remove('enter');
  const login = $('#login');
  login.hidden = false;
  login.classList.remove('leaving');
  resetBoot();
  setError('');
  state.busy = false;
  state.view = 'overview';
  state.logs = [];
  state.series = [];
  state.nodes = [];
  state.queue = [];
  state.unseen = 0;
  state.since = 0;
  state.token = '';
  state.lastCores = null;
  updateBadge();
  tiny.win.setTitle('NEXUS // 身份验证');
  startFx();
  $('#wipe').classList.remove('on');
  setTimeout(() => $('#password').focus(), 120);
}

// ── boot ───────────────────────────────────────────────────────────────────

function init() {
  document.body.classList.remove('booting');

  $('#login-form').addEventListener('submit', authenticate);

  $('#reveal').addEventListener('click', () => {
    const input = $('#password');
    const button = $('#reveal');
    const showing = input.type === 'text';
    input.type = showing ? 'password' : 'text';
    button.setAttribute('aria-pressed', String(!showing));
    button.setAttribute('aria-label', showing ? '显示密钥' : '隐藏密钥');
    button.querySelector('use').setAttribute('href', showing ? '#i-eye' : '#i-eye-off');
  });

  for (const item of $$('.nav-item')) {
    item.addEventListener('click', () => setView(item.dataset.view));
  }

  $('#collapse').addEventListener('click', () => {
    const shell = $('#shell');
    shell.classList.toggle('collapsed');
    $('#collapse').setAttribute('aria-expanded', String(!shell.classList.contains('collapsed')));
    requestAnimationFrame(() => {
      drawChart();
      drawSparks();
    });
  });

  $('#logout').addEventListener('click', logout);
  $('#logout-2').addEventListener('click', logout);

  $('#fullscreen').addEventListener('click', async () => {
    state.fullscreen = !state.fullscreen;
    try {
      await tiny.win.setFullscreen(state.fullscreen);
    } catch {
      state.fullscreen = !state.fullscreen;
    }
  });

  $('#autoscroll').addEventListener('change', (event) => {
    state.autoscroll = event.target.checked;
    if (state.autoscroll) {
      const stream = $('#log-stream');
      stream.scrollTop = stream.scrollHeight;
    }
  });

  for (const chip of $$('.chip')) {
    chip.addEventListener('click', () => {
      state.filter = chip.dataset.level;
      $$('.chip').forEach((c) => c.classList.toggle('active', c === chip));
      renderLogs();
    });
  }

  $('#clear-logs').addEventListener('click', () => {
    state.logs = [];
    renderLogs();
  });

  $('#export-logs').addEventListener('click', async () => {
    try {
      const path = await tiny.dialog.saveFile({ types: ['log'], name: 'nexus-telemetry' });
      if (!path) return;
      const lines = state.logs.map(
        (entry) => '[' + timeOf(entry.ts) + '] ' + String(entry.level).toUpperCase().padEnd(5) + ' ' + entry.src.padEnd(10) + ' ' + entry.msg,
      );
      await tiny.api.call('exportLogs', { path, lines });
      $('#save-state').textContent = '日志已导出';
    } catch (err) {
      console.error(err);
    }
  });

  bindSettings();
  bindChartHover();
  startClock();
  fxResize();
  startFx();

  window.addEventListener('resize', () => {
    if (state.resizeRaf) return;
    state.resizeRaf = requestAnimationFrame(() => {
      state.resizeRaf = 0;
      fxResize();
      drawChart();
      drawSparks();
    });
  });

  window.addEventListener('pointermove', (event) => {
    fxMouse.tx = (event.clientX / window.innerWidth - 0.5) * 2;
    fxMouse.ty = (event.clientY / window.innerHeight - 0.5) * 2;
  });

  tiny.win.setTitle('NEXUS // 身份验证');
  setTimeout(() => $('#account').focus(), 300);
}

init();
