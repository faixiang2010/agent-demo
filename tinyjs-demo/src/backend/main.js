// src/backend/main.js — NEXUS control console.
// Real host telemetry (CPU / uptime / interfaces / runtime) plus a simulated
// orbital operations feed, streamed to the page with app.push.

const SETTINGS_KEY = 'settings';
const TICK_MS = 1600;
const MAX_LOGS = 400;
const SERIES = 48;

const DEFAULT_SETTINGS = { motion: true, compact: false, telemetry: true };

let host = null; // the app handle from init()
let timer = null;
let seq = 0;
let logSeq = 0;
let taskSeq = 4473;

let session = null;
let settings = { ...DEFAULT_SETTINGS };

let logs = [];
let flushed = [];
let queue = [];
let nodes = [];
let series = [];

let cpuPrev = null;
let lastCores = [];
let mem = 48;
let tx = 64;
let rx = 118;

const clamp = (n, lo, hi) => Math.min(hi, Math.max(lo, n));
const rnd = (lo, hi) => lo + Math.random() * (hi - lo);

// ── host facts ──────────────────────────────────────────────────────────────

function systemInfo() {
  const cpus = tjs.system.cpus || [];
  const ifaces = (tjs.system.networkInterfaces || []).filter((n) => !n.internal);
  return {
    host: tjs.hostName,
    platform: tjs.env.OS || 'unknown',
    arch: tjs.env.PROCESSOR_ARCHITECTURE || 'unknown',
    cpuModel: (cpus[0] && cpus[0].model ? cpus[0].model.trim() : 'Unknown processor'),
    cores: cpus.length,
    uptime: Math.round(tjs.system.uptime || 0),
    load: (tjs.system.loadAvg || [0, 0, 0]).map((n) => Number(n.toFixed(2))),
    runtime: tjs.version,
    quickjs: (tjs.versions && tjs.versions.quickjs) || 'unknown',
    pid: tjs.pid,
    ifaces: ifaces.slice(0, 4).map((n) => ({ name: n.name, address: n.address, mac: n.mac })),
  };
}

function cpuSnapshot() {
  return (tjs.system.cpus || []).map((c) => {
    const t = c.times;
    const total = t.user + t.nice + t.sys + t.idle + t.irq;
    return { total, busy: total - t.idle };
  });
}

function readCpu() {
  const cur = cpuSnapshot();
  const prev = cpuPrev;
  cpuPrev = cur;
  if (!prev || prev.length !== cur.length) return { overall: 0, cores: cur.map(() => 0) };
  const cores = cur.map((c, i) => {
    const total = c.total - prev[i].total;
    if (total <= 0) return 0;
    return clamp(Math.round(((c.busy - prev[i].busy) / total) * 100), 0, 100);
  });
  const overall = cores.length ? Math.round(cores.reduce((a, b) => a + b, 0) / cores.length) : 0;
  return { overall, cores };
}

// ── simulated operations feed ───────────────────────────────────────────────

const NODE_SEED = [
  { id: 'NODE-01', name: '近地中继站', region: 'LEO · 412 km', base: 24 },
  { id: 'NODE-03', name: '赤道地面站', region: 'GND · 基里巴斯', base: 36 },
  { id: 'NODE-07', name: '深空测控阵', region: 'DSN · 马德里', base: 58 },
  { id: 'NODE-12', name: '极轨观测舱', region: 'PO · 891 km', base: 42 },
];

const TASK_POOL = [
  '深空影像归档',
  '轨道参数解算',
  '中继链路自检',
  '遥测数据落盘',
  '星图特征匹配',
  '推进剂遥测校准',
  '太阳风样本回传',
];

const LOG_TEMPLATES = [
  ['info', 'telemetry', () => `遥测帧 #${1000 + (seq % 9000)} 已写入集群 · ${(rnd(18, 96)).toFixed(1)} KB`],
  ['info', 'uplink', () => `${tjs.hostName}: 链路心跳正常 · RTT ${Math.round(rnd(16, 42))} ms`],
  ['ok', 'core', () => `缓存重排完成 · 释放 ${Math.round(rnd(48, 220))} MB`],
  ['info', 'orbital', () => `NODE-${String(1 + Math.floor(Math.random() * 12)).padStart(2, '0')} 轨道递推收敛 · 误差 ${rnd(0.2, 1.4).toFixed(2)} m`],
  ['info', 'core', () => `任务调度器分配 ${Math.round(rnd(2, 9))} 个工作线程`],
  ['warn', 'uplink', () => `链路抖动 · 重传 ${Math.round(rnd(1, 4))} 个数据包`],
  ['warn', 'orbital', () => `NODE-07 负载越过阈值 · ${Math.round(rnd(76, 88))}%`],
  ['warn', 'telemetry', () => `时间戳漂移 ${Math.round(rnd(3, 12))} ms · 已补偿`],
  ['alert', 'auth', () => `检测到未授权接入尝试 · 来源 203.0.113.${Math.round(rnd(2, 250))} · 已阻断`],
  ['alert', 'core', () => `ECC 校验失败 · 扇区 0x${Math.floor(rnd(0x1000, 0xffff)).toString(16)} 已重映射`],
];

function pushLog(level, src, msg, at) {
  const entry = { id: ++logSeq, ts: at || Date.now(), level, src, msg };
  logs.push(entry);
  if (logs.length > MAX_LOGS) logs.shift();
  flushed.push(entry);
  if (flushed.length > 40) flushed.shift();
  return entry;
}

function seedLogs() {
  const now = Date.now();
  for (let i = 14; i > 0; i--) {
    const t = LOG_TEMPLATES[Math.floor(Math.random() * 5)];
    pushLog(t[0], t[1], t[2](), now - i * 2600 - Math.round(rnd(0, 900)));
  }
  pushLog('ok', 'auth', '控制台自检完成 · 全部子系统中止符正常');
  flushed = [];
}

function resetQueue() {
  queue = [
    { id: 'TSK-4471', name: '深空影像归档', status: 'running', progress: 64, eta: '04:12' },
    { id: 'TSK-4472', name: '轨道参数解算', status: 'running', progress: 31, eta: '11:38' },
    { id: 'TSK-4473', name: '中继链路自检', status: 'queued', progress: 0, eta: '待调度' },
    { id: 'TSK-4468', name: '遥测数据落盘', status: 'done', progress: 100, eta: '完成' },
  ];
}

function resetNodes() {
  nodes = NODE_SEED.map((n) => ({
    id: n.id,
    name: n.name,
    region: n.region,
    load: Math.round(n.base + rnd(-6, 6)),
    latency: Math.round(rnd(18, 46)),
    status: 'online',
    done: 0,
  }));
}

function advanceQueue() {
  for (const task of queue) {
    if (task.status === 'done') {
      task.age = (task.age || 0) + 1;
      continue;
    }
    if (task.status !== 'running') continue;
    task.progress = clamp(task.progress + rnd(0.7, 2.4), 0, 100);
    const left = Math.round(((100 - task.progress) / 2.2) * 1.6);
    task.eta = `${String(Math.floor(left / 60)).padStart(2, '0')}:${String(left % 60).padStart(2, '0')}`;
    if (task.progress >= 100) {
      task.progress = 100;
      task.status = 'done';
      task.eta = '完成';
      task.age = 0;
      pushLog('ok', 'core', `任务 ${task.id} ${task.name} 完成 · 已归档`);
    }
  }
  // retire finished rows after a while, keep the board alive
  queue = queue.filter((t) => !(t.status === 'done' && t.age > 3));
  while (queue.filter((t) => t.status === 'running').length < 2) {
    const waiting = queue.find((t) => t.status === 'queued');
    if (waiting) {
      waiting.status = 'running';
      waiting.eta = '02:40';
      pushLog('info', 'core', `任务 ${waiting.id} ${waiting.name} 开始执行`);
      continue;
    }
    const name = TASK_POOL[Math.floor(Math.random() * TASK_POOL.length)];
    queue.push({
      id: 'TSK-' + ++taskSeq,
      name,
      status: 'queued',
      progress: 0,
      eta: '待调度',
    });
  }
}

function advanceNodes() {
  for (const node of nodes) {
    node.load = clamp(node.load + rnd(-7, 7), 8, 96);
    node.latency = clamp(node.latency + Math.round(rnd(-5, 5)), 12, 120);
    node.status = node.load > 84 ? 'degraded' : 'online';
    node.done = (node.done || 0) + 1;
  }
}

function maybeLog() {
  const roll = Math.random();
  const count = roll > 0.72 ? 2 : roll > 0.34 ? 1 : 0;
  for (let i = 0; i < count; i++) {
    const pick = LOG_TEMPLATES[Math.floor(Math.random() * LOG_TEMPLATES.length)];
    pushLog(pick[0], pick[1], pick[2]());
  }
}

// ── stream ──────────────────────────────────────────────────────────────────

function makeSample(cpu) {
  mem = clamp(mem + rnd(-2.2, 2.2), 34, 82);
  tx = clamp(tx + rnd(-9, 9), 12, 210);
  rx = clamp(rx + rnd(-14, 14), 26, 320);
  return {
    ts: Date.now(),
    cpu: cpu.overall,
    mem: Math.round(mem),
    tx: Math.round(tx),
    rx: Math.round(rx),
    nodes: 12,
  };
}

function tick() {
  seq++;
  const cpu = readCpu();
  lastCores = cpu.cores.slice();
  advanceNodes();
  advanceQueue();
  maybeLog();
  const sample = makeSample(cpu);
  series.push(sample);
  if (series.length > SERIES) series.shift();

  if (!host) return;
  try {
    host.push('telemetry', {
      sample,
      cores: cpu.cores,
      queue,
      nodes,
      logs: flushed,
    });
  } catch {
    // the window may be gone; keep the feed warm for the next session
  }
  flushed = [];
}

function backfill() {
  series = [];
  const now = Date.now();
  let c = 24;
  let m = 44;
  for (let i = SERIES; i > 0; i--) {
    c = clamp(c + rnd(-9, 9), 8, 92);
    m = clamp(m + rnd(-2.4, 2.4), 36, 74);
    series.push({
      ts: now - i * TICK_MS,
      cpu: Math.round(c + Math.sin(i / 3) * 6),
      mem: Math.round(m),
      tx: Math.round(60 + Math.sin(i / 4) * 26 + rnd(-10, 10)),
      rx: Math.round(120 + Math.sin(i / 5) * 40 + rnd(-16, 16)),
      nodes: 12,
    });
  }
}

export function init(app) {
  host = app;
  cpuSnapshot();
  resetQueue();
  resetNodes();
  backfill();
  seedLogs();
  readCpu(); // primes the delta so the first live sample is real

  app.store.get(SETTINGS_KEY).then((saved) => {
    if (saved && typeof saved === 'object') {
      settings = { ...DEFAULT_SETTINGS, ...saved };
    }
  });

  timer = setInterval(tick, TICK_MS);
  pushLog('ok', 'auth', 'NEXUS 控制台已就绪 · 等待操作员接入');
}

function initialsOf(name) {
  const cleaned = String(name).replace(/[^a-zA-Z0-9\u4e00-\u9fa5]/g, '');
  if (!cleaned) return 'NX';
  return cleaned.slice(0, 2).toUpperCase();
}

function token() {
  const hex = '0123456789ABCDEF';
  let out = '';
  for (let i = 0; i < 16; i++) out += hex[Math.floor(Math.random() * 16)];
  return out;
}

function liveUptime() {
  return Math.round(tjs.system.uptime || 0);
}

function settingsSnapshot() {
  return { ...settings };
}

export const api = {
  login(params) {
    const account = String(params && params.account ? params.account : '').trim();
    const key = String(params && params.password ? params.password : '');
    if (!account) throw new Error('请输入操作员编号。');
    if (key.length < 6) throw new Error('访问密钥至少需要 6 位字符。');

    session = {
      user: {
        name: account.toUpperCase(),
        initials: initialsOf(account),
        role: '深空指挥员',
        clearance: 'OMEGA',
        id: 'CMDR-001',
      },
      since: Date.now(),
      token: token(),
    };
    pushLog('ok', 'auth', `操作员 ${account} 已接入控制台 · 权限 OMEGA`);
    return session;
  },

  logout() {
    if (session) pushLog('info', 'auth', `操作员 ${session.user.name} 已断开连接`);
    session = null;
    return true;
  },

  bootstrap() {
    return {
      system: systemInfo(),
      series: series.slice(),
      queue: queue.map((t) => ({ ...t })),
      nodes: nodes.map((n) => ({ ...n })),
      logs: logs.slice(-60),
      cores: lastCores.slice(),
      settings: settingsSnapshot(),
      uptime: liveUptime(),
      serverTime: Date.now(),
      session: session ? { user: session.user, since: session.since, token: session.token } : null,
    };
  },

  async saveSettings(params) {
    const next = params && params.settings;
    if (!next || typeof next !== 'object') throw new Error('没有可保存的设置。');
    settings = {
      motion: next.motion !== false,
      compact: next.compact === true,
      telemetry: next.telemetry !== false,
    };
    const ok = await host.store.set(SETTINGS_KEY, settings);
    return { settings: settingsSnapshot(), stored: ok !== false };
  },

  async exportLogs(params) {
    const path = params && params.path;
    if (typeof path !== 'string' || !path) throw new Error('未选择保存位置。');
    const lines = Array.isArray(params.lines) ? params.lines : [];
    const header =
      '# NEXUS telemetry log\n# host: ' +
      tjs.hostName +
      '\n# exported: ' +
      new Date().toISOString() +
      '\n\n';
    await tjs.writeFile(path, new TextEncoder().encode(header + lines.join('\n') + '\n'));
    return true;
  },
};
