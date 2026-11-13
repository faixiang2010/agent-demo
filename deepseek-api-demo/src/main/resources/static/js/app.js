(function () {
  'use strict';

  const $ = (s) => document.querySelector(s);

  /** 接口定义中内置示例图的占位符 */
  const BUILTIN_IMAGE_TOKEN = '__BUILTIN_IMAGE__';
  /** 内置示例图地址（随应用打包，不依赖外网） */
  const BUILTIN_IMAGE_PATH = '/img/vision-sample.png';

  const state = {
    apiKey: localStorage.getItem('deepseek_key') || '',
    baseUrl: localStorage.getItem('deepseek_base_url') || 'https://api.deepseek.com',
    definitions: [],
    groups: new Map(),
    activeGroup: null,
    /** 各主题下最后选中的接口，切换 Tab 时恢复 */
    lastPicked: new Map(),
    current: null,
    stream: false,
    abortController: null,
    builtinImage: null,
    viewMode: 'json',
    chatParts: [],
    netInfo: null
  };

  // ============ 基础工具 ============
  function escapeHtml(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  /** 脱敏 API Key，避免在网络抽屉里暴露完整密钥 */
  function maskKey(k) {
    if (!k) return '(未配置)';
    if (k.length <= 10) return k.slice(0, 3) + '***';
    return k.slice(0, 8) + '****' + k.slice(-4);
  }

  function syntaxHighlight(json) {
    return escapeHtml(json).replace(
      /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g,
      function (match) {
        let cls = 'number';
        if (/^"/.test(match)) cls = /:$/.test(match) ? 'key' : 'string';
        else if (/true|false/.test(match)) cls = 'boolean';
        else if (/null/.test(match)) cls = 'null';
        return '<span class="' + cls + '">' + match + '</span>';
      }
    );
  }

  function toast(msg) {
    const t = $('#toast');
    t.textContent = msg;
    t.classList.remove('hidden');
    clearTimeout(t._timer);
    t._timer = setTimeout(() => t.classList.add('hidden'), 3200);
  }

  function el(tag, className, text) {
    const e = document.createElement(tag);
    if (className) e.className = className;
    if (text !== undefined) e.textContent = text;
    return e;
  }

  function blobToDataUrl(blob) {
    return new Promise((resolve, reject) => {
      const fr = new FileReader();
      fr.onload = () => resolve(fr.result);
      fr.onerror = () => reject(new Error('图片读取失败'));
      fr.readAsDataURL(blob);
    });
  }

  async function getBuiltinImage() {
    if (state.builtinImage) return state.builtinImage;
    const res = await fetch(BUILTIN_IMAGE_PATH);
    if (!res.ok) throw new Error('内置示例图加载失败');
    state.builtinImage = await blobToDataUrl(await res.blob());
    return state.builtinImage;
  }

  // ============ 初始化 ============
  async function init() {
    $('#apiKey').value = state.apiKey;
    $('#baseUrl').value = state.baseUrl;
    bindTopbar();
    bindResultButtons();
    bindDrawer();
    bindShortcuts();
    initSplitters();
    try {
      const res = await fetch('/api/definitions');
      state.definitions = await res.json();
      state.definitions.forEach((d) => {
        if (!state.groups.has(d.group)) state.groups.set(d.group, []);
        state.groups.get(d.group).push(d);
      });
      renderTabs();
      const remembered = localStorage.getItem('deepseek_group');
      const firstGroup = (remembered && state.groups.has(remembered))
        ? remembered
        : state.groups.keys().next().value;
      if (firstGroup) selectGroup(firstGroup);
    } catch (e) {
      toast('加载接口定义失败: ' + e.message);
    }
  }

  /** Ctrl/Cmd + Enter 快捷触发当前接口的「调用」。 */
  function bindShortcuts() {
    document.addEventListener('keydown', (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        e.preventDefault();
        const btn = document.querySelector('.action-bar .is-primary');
        if (btn && !btn.disabled) btn.click();
      }
    });
  }

  // ============ 可拖动分隔条 ============
  /** 结果区保留的最小宽度 */
  const RESULT_MIN_WIDTH = 320;
  /** 两条分隔条占用的总宽度 */
  const SPLITTER_TOTAL = 12;

  function isStackedLayout() {
    return window.matchMedia('(max-width: 980px)').matches;
  }

  /** 首次打开的默认宽度（窄屏略收窄，给结果区留空间） */
  function splitterDefault(which) {
    const narrow = window.innerWidth <= 1180;
    if (which === 'ep') return narrow ? 168 : 190;
    return narrow ? 360 : 440;
  }

  function splitterMin(which) {
    return which === 'ep' ? 130 : 300;
  }

  /** 当前窗口下该栏允许的最大宽度：保证结果区仍有最小可用宽度 */
  function splitterMax(which) {
    const ws = document.querySelector('.workspace').clientWidth;
    const epW = document.querySelector('.ep-pane').getBoundingClientRect().width;
    const edW = document.querySelector('.editor-pane').getBoundingClientRect().width;
    const max = which === 'ep'
      ? ws - edW - RESULT_MIN_WIDTH - SPLITTER_TOTAL
      : ws - epW - RESULT_MIN_WIDTH - SPLITTER_TOTAL;
    return Math.max(splitterMin(which), max);
  }

  /** 绑定一条分隔条：拖拽、键盘微调、双击复位、尺寸记忆 */
  function setupSplitter(handleId, which) {
    const handle = $('#' + handleId);
    const pane = document.querySelector(which === 'ep' ? '.ep-pane' : '.editor-pane');
    const storageKey = 'deepseek_split_' + which;
    const min = splitterMin(which);

    const currentWidth = () => pane.getBoundingClientRect().width;

    function apply(w) {
      const max = splitterMax(which);
      const next = Math.round(Math.min(Math.max(w, min), max));
      if (which === 'ep') pane.style.width = next + 'px';
      else pane.style.flexBasis = next + 'px';
      handle.setAttribute('aria-valuenow', String(next));
      handle.setAttribute('aria-valuemin', String(min));
      handle.setAttribute('aria-valuemax', String(max));
      return next;
    }

    function persist() {
      localStorage.setItem(storageKey, String(Math.round(currentWidth())));
    }

    // 恢复上次宽度，没有记录则用默认值
    const saved = parseInt(localStorage.getItem(storageKey), 10);
    apply(Number.isNaN(saved) ? splitterDefault(which) : saved);

    let dragging = false;
    let startX = 0;
    let startW = 0;

    handle.addEventListener('pointerdown', (e) => {
      if (isStackedLayout()) return;
      dragging = true;
      startX = e.clientX;
      startW = currentWidth();
      handle.classList.add('is-dragging');
      document.body.classList.add('is-resizing');
      handle.setPointerCapture(e.pointerId);
      // preventDefault 会取消默认聚焦，这里显式聚焦以支持键盘微调
      handle.focus();
      e.preventDefault();
    });

    handle.addEventListener('pointermove', (e) => {
      if (!dragging) return;
      apply(startW + (e.clientX - startX));
    });

    const finish = (e) => {
      if (!dragging) return;
      dragging = false;
      handle.classList.remove('is-dragging');
      document.body.classList.remove('is-resizing');
      persist();
      try { handle.releasePointerCapture(e.pointerId); } catch (err) { /* 已自动释放 */ }
    };
    handle.addEventListener('pointerup', finish);
    handle.addEventListener('pointercancel', finish);

    // 键盘：← / → 微调，按住 Shift 步长更大
    handle.addEventListener('keydown', (e) => {
      if (isStackedLayout()) return;
      if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
      e.preventDefault();
      const step = e.shiftKey ? 40 : 16;
      apply(currentWidth() + (e.key === 'ArrowRight' ? step : -step));
      persist();
    });

    // 双击复位到默认宽度
    handle.addEventListener('dblclick', () => {
      localStorage.removeItem(storageKey);
      apply(splitterDefault(which));
    });

    // 窗口缩放后重新收敛，避免结果区被挤没
    window.addEventListener('resize', () => {
      if (isStackedLayout()) return;
      apply(currentWidth());
    });
  }

  function initSplitters() {
    setupSplitter('splitterEp', 'ep');
    setupSplitter('splitterEditor', 'editor');
  }

  function bindTopbar() {
    $('#saveConfig').onclick = () => {
      state.apiKey = $('#apiKey').value.trim();
      state.baseUrl = $('#baseUrl').value.trim() || 'https://api.deepseek.com';
      localStorage.setItem('deepseek_key', state.apiKey);
      localStorage.setItem('deepseek_base_url', state.baseUrl);
      toast('配置已保存到浏览器本地');
    };
    $('#apiKey').oninput = () => { state.apiKey = $('#apiKey').value.trim(); };
    $('#baseUrl').oninput = () => {
      state.baseUrl = $('#baseUrl').value.trim() || 'https://api.deepseek.com';
    };
    $('#testKey').onclick = async () => {
      state.apiKey = $('#apiKey').value.trim();
      state.baseUrl = $('#baseUrl').value.trim() || 'https://api.deepseek.com';
      if (!state.apiKey) { toast('请先填写 API Key'); $('#apiKey').focus(); return; }
      try {
        const res = await fetch('/api/call', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            endpoint: 'list-models', apiKey: state.apiKey, baseUrl: state.baseUrl,
            stream: false, body: null, query: {}, path: {}
          })
        });
        const text = await res.text();
        const ok = res.status >= 200 && res.status < 300;
        toast(ok ? '连通成功，API Key 有效' : '连通失败，请检查 API Key');
        renderResult(res.status, text, 0);
      } catch (e) {
        toast('请求异常: ' + e.message);
      }
    };
  }

  function bindResultButtons() {
    $('#clearResult').onclick = () => {
      state.chatParts = [];
      state.netInfo = null;
      $('#chatView').innerHTML = '';
      $('#resultBox').textContent = '';
      resetResultMeta();
    };
    $('#stopCall').onclick = () => {
      if (state.abortController) state.abortController.abort();
    };
    $('#viewChatBtn').onclick = () => setViewMode('chat');
    $('#viewJsonBtn').onclick = () => setViewMode('json');
    $('#copyResult').onclick = async () => {
      let text = '';
      if (state.viewMode === 'chat' && !$('#chatView').classList.contains('hidden')) {
        text = state.chatParts.map((p) => {
          const who = p.type === 'user' ? '你' : p.type === 'reasoning' ? '思考' : p.type === 'tool' ? '工具' : 'AI';
          return '【' + who + '】\n' + p.text;
        }).join('\n\n');
      } else {
        text = $('#resultBox').innerText;
      }
      if (!text) { toast('暂无内容可复制'); return; }
      try {
        await navigator.clipboard.writeText(text);
        toast('已复制到剪贴板');
      } catch (e) {
        toast('复制失败，请手动选择文本');
      }
    };
  }

  // ============ 网络抽屉 ============
  function bindDrawer() {
    $('#netToggle').onclick = () => openDrawer();
    $('#netClose').onclick = () => closeDrawer();
    $('#drawerBackdrop').onclick = () => closeDrawer();
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && !$('#netDrawer').hidden) closeDrawer();
    });
  }

  function openDrawer() {
    if (!state.netInfo) {
      toast('还没有请求记录，先调用一次接口');
      return;
    }
    renderNetPanel(state.netInfo);
    $('#netDrawer').hidden = false;
    $('#drawerBackdrop').hidden = false;
    $('#netToggle').setAttribute('aria-expanded', 'true');
    $('#netClose').focus();
  }

  function closeDrawer() {
    $('#netDrawer').hidden = true;
    $('#drawerBackdrop').hidden = true;
    $('#netToggle').setAttribute('aria-expanded', 'false');
  }

  /** 主题 Tab 的短标签，避免长名称把 Tab 栏撑溢出 */
  const TAB_LABELS = {
    '基础信息': '基础信息',
    '对话补全': '对话补全',
    '多模态（视觉理解）': '多模态',
    '补全（FIM）': '补全',
    '网页检索': '网页检索',
    'Responses API': 'Responses',
    '文件管理': '文件管理',
    'Anthropic 兼容': 'Anthropic'
  };

  // ============ 主题 Tab ============
  function renderTabs() {
    const nav = $('#groupTabs');
    nav.innerHTML = '';
    state.groups.forEach((items, groupName) => {
      const tab = el('button', 'tab');
      tab.type = 'button';
      tab.setAttribute('role', 'tab');
      tab.setAttribute('title', groupName);
      tab.dataset.group = groupName;
      tab.appendChild(el('span', '', TAB_LABELS[groupName] || groupName));
      tab.appendChild(el('span', 'tab-count', String(items.length)));
      tab.onclick = () => selectGroup(groupName);
      nav.appendChild(tab);
    });
  }

  function selectGroup(groupName) {
    state.activeGroup = groupName;
    localStorage.setItem('deepseek_group', groupName);
    document.querySelectorAll('.tab').forEach((t) => {
      t.setAttribute('aria-selected', t.dataset.group === groupName ? 'true' : 'false');
    });
    renderEpList();
    const items = state.groups.get(groupName) || [];
    const remembered = state.lastPicked.get(groupName);
    const target = items.find((x) => x.id === remembered) || items[0];
    if (target) selectEndpoint(target);
  }

  function renderEpList() {
    const list = $('#epList');
    list.innerHTML = '';
    const items = state.groups.get(state.activeGroup) || [];
    $('#epHead').textContent = state.activeGroup + ' · ' + items.length;
    items.forEach((d) => {
      const btn = el('button', 'ep-item');
      btn.type = 'button';
      btn.setAttribute('role', 'option');
      btn.dataset.id = d.id;
      btn.appendChild(el('span', 'method ' + d.method.toLowerCase(), d.method));
      btn.appendChild(el('span', 'ep-name', d.name));
      btn.onclick = () => selectEndpoint(d);
      list.appendChild(btn);
    });
  }

  // ============ 选中接口 ============
  function selectEndpoint(d) {
    state.current = d;
    state.stream = false;
    state.viewMode = d.outputType === 'chat' ? 'chat' : 'json';
    state.chatParts = [];
    state.netInfo = null;
    state.lastPicked.set(state.activeGroup, d.id);

    document.querySelectorAll('.ep-item').forEach((x) => {
      x.setAttribute('aria-selected', x.dataset.id === d.id ? 'true' : 'false');
    });

    $('#apiName').textContent = d.name;
    $('#apiCategory').textContent = d.group;
    $('#apiMeta').textContent = d.method + '  ' + d.baseUrl + d.path;
    renderPlain(d);
    renderNotes(d);
    renderParamArea(d);

    $('#chatView').innerHTML = '';
    $('#resultBox').textContent = '';
    updateViewToggle(d);
    setViewMode(state.viewMode);
    resetResultMeta();
    closeDrawer();
  }

  function renderPlain(d) {
    const box = $('#plainDesc');
    if (d.plain) {
      box.textContent = d.plain;
      box.classList.remove('hidden');
    } else {
      box.classList.add('hidden');
    }
  }

  function renderNotes(d) {
    const box = $('#apiNotes');
    box.innerHTML = '';
    if (!d.notes || !d.notes.length) {
      box.classList.add('hidden');
      return;
    }
    box.classList.remove('hidden');
    d.notes.forEach((n) => box.appendChild(el('li', '', n)));
  }

  function updateViewToggle(d) {
    $('#viewToggle').classList.toggle('hidden', d.outputType !== 'chat');
  }

  function setViewMode(mode) {
    state.viewMode = mode;
    const isChat = mode === 'chat';
    $('#viewChatBtn').classList.toggle('active', isChat);
    $('#viewJsonBtn').classList.toggle('active', !isChat);
    $('#viewChatBtn').setAttribute('aria-pressed', String(isChat));
    $('#viewJsonBtn').setAttribute('aria-pressed', String(!isChat));
    $('#chatView').classList.toggle('hidden', !isChat);
    $('#resultBox').classList.toggle('hidden', isChat);
  }

  // ============ 参数区 ============
  function renderParamArea(d) {
    const area = $('#paramArea');
    area.innerHTML = '';
    if (d.contentType === 'multipart/form-data') {
      renderMultipartForm(area, d);
    } else if (d.method === 'GET' || d.method === 'DELETE') {
      renderQueryForm(area, d);
    } else {
      renderJsonForm(area, d);
    }
  }

  function buildField(p) {
    const wrap = el('div', 'field');
    wrap.appendChild(el('span', 'field-label', p.label + (p.required ? ' *' : '')));
    let input;
    if (p.type === 'select') {
      input = document.createElement('select');
      (p.options || []).forEach((o) => {
        const opt = document.createElement('option');
        opt.value = o;
        opt.textContent = o;
        if (o === String(p.defaultValue)) opt.selected = true;
        input.appendChild(opt);
      });
    } else {
      input = document.createElement('input');
      input.type = p.type === 'number' ? 'number' : 'text';
      input.value = p.defaultValue !== null && p.defaultValue !== undefined ? p.defaultValue : '';
      if (p.type === 'number') input.step = 'any';
      if (p.in === 'path') input.placeholder = 'file-api-...';
    }
    input.dataset.field = p.name;
    input.dataset.in = p.in;
    input.dataset.type = p.type;
    input.setAttribute('aria-label', p.label);
    wrap.appendChild(input);
    return wrap;
  }

  function buildSwitch(labelText, checked, onChange) {
    const label = el('label', 'switch');
    const cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.checked = checked;
    cb.onchange = () => onChange(cb.checked);
    label.appendChild(cb);
    label.appendChild(el('span', '', labelText));
    return label;
  }

  // ---------- JSON 接口 ----------
  function renderJsonForm(area, d) {
    const toolbar = el('div', 'toolbar');
    if (d.streamable) {
      toolbar.appendChild(buildSwitch('流式输出', false, (v) => { state.stream = v; }));
    }
    if (d.imageKind && d.imageKind !== 'fileRef') {
      toolbar.appendChild(el('span', 'hint-text', '可点「内置示例图」或上传本地图片，自动写入请求体'));
    }
    area.appendChild(toolbar);

    const bodyFields = d.params.filter((p) => p.in === 'body');
    if (bodyFields.length) {
      const grid = el('div', 'field-grid');
      bodyFields.forEach((p) => {
        const f = buildField(p);
        const input = f.querySelector('[data-field]');
        input.onchange = applyQuickFieldsToJson;
        grid.appendChild(f);
      });
      area.appendChild(grid);
    }

    if (d.imageKind && d.imageKind !== 'fileRef') {
      area.appendChild(buildImageBar());
    }

    const editor = document.createElement('textarea');
    editor.id = 'jsonBody';
    editor.className = 'json-editor';
    editor.spellcheck = false;
    editor.setAttribute('aria-label', '请求体 JSON');
    editor.value = JSON.stringify(d.example || {}, null, 2);
    editor.oninput = syncJsonToQuickFields;
    area.appendChild(editor);

    const actions = el('div', 'action-bar');
    const btnExample = el('button', 'btn ghost small', '填入示例');
    btnExample.type = 'button';
    btnExample.onclick = () => resetExample();
    actions.appendChild(btnExample);

    const btnFormat = el('button', 'btn ghost small', '格式化');
    btnFormat.type = 'button';
    btnFormat.onclick = () => {
      try { editor.value = JSON.stringify(JSON.parse(editor.value), null, 2); }
      catch (e) { toast('JSON 格式错误: ' + e.message); }
    };
    actions.appendChild(btnFormat);
    actions.appendChild(el('span', 'spacer'));
    actions.appendChild(el('span', 'hint-text', 'Ctrl+Enter 调用'));

    const btnCall = el('button', 'btn is-primary', '调用');
    btnCall.type = 'button';
    btnCall.onclick = callJson;
    actions.appendChild(btnCall);
    area.appendChild(actions);

    if (JSON.stringify(d.example || {}).includes(BUILTIN_IMAGE_TOKEN)) {
      resetExample();
    }
  }

  function buildImageBar() {
    const bar = el('div', 'image-bar');
    bar.appendChild(el('span', 'bar-label', '图片'));

    const btnBuiltin = el('button', 'btn ghost small', '内置示例图');
    btnBuiltin.type = 'button';
    btnBuiltin.onclick = async () => {
      try { applyImage(await getBuiltinImage(), '内置示例图'); }
      catch (e) { toast(e.message); }
    };
    bar.appendChild(btnBuiltin);

    const fileLabel = el('label', 'btn ghost small file-btn');
    fileLabel.appendChild(document.createTextNode('上传本地'));
    const fileInput = document.createElement('input');
    fileInput.type = 'file';
    fileInput.accept = 'image/jpeg,image/png,image/gif,image/webp';
    fileInput.style.display = 'none';
    fileInput.onchange = async () => {
      const f = fileInput.files && fileInput.files[0];
      if (!f) return;
      if (f.size > 32 * 1024 * 1024) { toast('图片超过 32 MiB，请压缩后再试'); return; }
      applyImage(await blobToDataUrl(f), f.name);
      fileInput.value = '';
    };
    fileLabel.appendChild(fileInput);
    bar.appendChild(fileLabel);

    const urlRow = el('div', 'image-url-row');
    const urlInput = document.createElement('input');
    urlInput.type = 'text';
    urlInput.className = 'image-url';
    urlInput.placeholder = '或粘贴公网图片 URL';
    urlInput.setAttribute('aria-label', '图片 URL');
    urlRow.appendChild(urlInput);

    const btnApply = el('button', 'btn ghost small', '应用');
    btnApply.type = 'button';
    btnApply.onclick = () => {
      const u = urlInput.value.trim();
      if (!u) { toast('请先输入图片 URL'); urlInput.focus(); return; }
      if (!/^https?:\/\//i.test(u)) { toast('图片 URL 必须以 http:// 或 https:// 开头'); return; }
      applyImage(u, '图片 URL');
    };
    urlRow.appendChild(btnApply);
    bar.appendChild(urlRow);

    const preview = el('div', 'image-preview');
    preview.id = 'imagePreview';
    bar.appendChild(preview);
    return bar;
  }

  function applyImage(url, label) {
    const editor = $('#jsonBody');
    if (!editor) return;
    let obj;
    try { obj = JSON.parse(editor.value || '{}'); }
    catch (e) { toast('当前 JSON 无法解析，无法写入图片'); return; }

    let applied = false;
    if (state.current.imageKind === 'responses') {
      const items = Array.isArray(obj.input) ? obj.input : [];
      for (const item of items) {
        if (Array.isArray(item.content)) {
          for (const block of item.content) {
            if (block.type === 'input_image') { block.image_url = url; applied = true; }
          }
        }
      }
      if (!applied && items.length) {
        items[0].content = items[0].content || [];
        items[0].content.push({ type: 'input_image', image_url: url, detail: 'low' });
        applied = true;
      }
    } else {
      const msgs = Array.isArray(obj.messages) ? obj.messages : [];
      for (const m of msgs) {
        if (!Array.isArray(m.content)) continue;
        for (const block of m.content) {
          if (block.type === 'image_url') {
            block.image_url = { url: url };
            applied = true;
          }
        }
      }
      if (!applied) {
        for (const m of msgs) {
          if (m.role === 'user') {
            if (!Array.isArray(m.content)) m.content = [{ type: 'text', text: String(m.content) }];
            m.content.push({ type: 'image_url', image_url: { url: url } });
            applied = true;
            break;
          }
        }
      }
    }

    if (!applied) { toast('未找到可写入的位置，请手动编辑 JSON'); return; }
    editor.value = JSON.stringify(obj, null, 2);
    renderImagePreview(url);
    toast('已应用' + label);
  }

  function renderImagePreview(url) {
    const box = $('#imagePreview');
    if (!box) return;
    box.innerHTML = '';
    if (!url || url.includes(BUILTIN_IMAGE_TOKEN)) return;
    const img = document.createElement('img');
    img.src = url;
    img.alt = '图片预览';
    img.onerror = () => { box.innerHTML = '<span class="preview-error">无法预览（可能不可公网访问）</span>'; };
    box.appendChild(img);
    if (url.startsWith('data:')) {
      const kb = Math.round((url.length * 3 / 4) / 1024);
      box.appendChild(el('span', 'preview-info', 'Base64 内联 · 约 ' + kb + ' KB'));
    } else {
      box.appendChild(el('span', 'preview-info', '外部 URL · 需 DeepSeek 可访问'));
    }
  }

  async function resetExample() {
    const d = state.current;
    const editor = $('#jsonBody');
    if (!editor) return;
    let text = JSON.stringify(d.example || {}, null, 2);
    if (text.includes(BUILTIN_IMAGE_TOKEN)) {
      try {
        const dataUrl = await getBuiltinImage();
        if (state.current !== d) return;
        text = text.split(BUILTIN_IMAGE_TOKEN).join(dataUrl);
        renderImagePreview(dataUrl);
      } catch (e) {
        toast(e.message);
      }
    }
    editor.value = text;
    syncJsonToQuickFields();
  }

  function applyQuickFieldsToJson() {
    const editor = $('#jsonBody');
    if (!editor) return;
    let obj;
    try { obj = JSON.parse(editor.value || '{}'); } catch (e) { return; }
    state.current.params.filter((p) => p.in === 'body').forEach((p) => {
      const input = document.querySelector('[data-field="' + p.name + '"]');
      if (!input) return;
      let v = input.value;
      if (input.dataset.type === 'number') v = v === '' ? null : Number(v);
      obj[p.name] = v;
    });
    editor.value = JSON.stringify(obj, null, 2);
  }

  function syncJsonToQuickFields() {
    const editor = $('#jsonBody');
    if (!editor) return;
    let obj;
    try { obj = JSON.parse(editor.value || '{}'); } catch (e) { return; }
    state.current.params.filter((p) => p.in === 'body').forEach((p) => {
      const input = document.querySelector('[data-field="' + p.name + '"]');
      if (!input || obj[p.name] === undefined || obj[p.name] === null) return;
      input.value = obj[p.name];
    });
  }

  // ---------- GET / DELETE ----------
  function renderQueryForm(area, d) {
    if (!d.params.length) {
      area.appendChild(el('p', 'hint-text', '该接口无需参数，直接点「调用」即可。'));
    } else {
      const grid = el('div', 'field-grid');
      d.params.forEach((p) => grid.appendChild(buildField(p)));
      area.appendChild(grid);
    }
    const actions = el('div', 'action-bar');
    actions.appendChild(el('span', 'spacer'));
    const btn = el('button', 'btn is-primary', '调用');
    btn.type = 'button';
    btn.onclick = callQuery;
    actions.appendChild(btn);
    area.appendChild(actions);
  }

  // ---------- multipart ----------
  function renderMultipartForm(area, d) {
    const grid = el('div', 'field-grid');
    d.params.forEach((p) => {
      if (p.in === 'file') {
        const f = el('div', 'field field-file');
        f.appendChild(el('span', 'field-label', p.label + (p.required ? ' *' : '')));
        const input = document.createElement('input');
        input.type = 'file';
        input.id = 'fileInput';
        input.accept = 'image/jpeg,image/png,image/gif,image/webp';
        input.setAttribute('aria-label', p.label);
        f.appendChild(input);
        grid.appendChild(f);
      } else {
        grid.appendChild(buildField(p));
      }
    });
    area.appendChild(grid);

    const actions = el('div', 'action-bar');
    const btnUpload = el('button', 'btn ghost small', '用内置示例图');
    btnUpload.type = 'button';
    btnUpload.onclick = async () => {
      try {
        const res = await fetch(BUILTIN_IMAGE_PATH);
        const blob = await res.blob();
        const file = new File([blob], 'vision-sample.png', { type: 'image/png' });
        const dt = new DataTransfer();
        dt.items.add(file);
        $('#fileInput').files = dt.files;
        toast('已选择内置示例图');
      } catch (e) {
        toast('加载内置示例图失败: ' + e.message);
      }
    };
    actions.appendChild(btnUpload);
    actions.appendChild(el('span', 'spacer'));

    const btn = el('button', 'btn is-primary', '上传');
    btn.type = 'button';
    btn.onclick = callUpload;
    actions.appendChild(btn);
    area.appendChild(actions);
  }

  // ============ 结果区 ============
  function resetResultMeta() {
    $('#statusBadge').className = 'badge hidden';
    $('#timeCost').textContent = '';
    $('#tokenUsage').textContent = '';
  }

  function setStatus(code) {
    const badge = $('#statusBadge');
    badge.classList.remove('hidden', 'ok', 'err');
    badge.textContent = typeof code === 'number' ? 'HTTP ' + code : code;
    badge.classList.add(typeof code === 'number' && code >= 200 && code < 300 ? 'ok' : 'err');
  }

  function setTime(ms) {
    $('#timeCost').textContent = ms.toFixed(0) + ' ms';
  }

  function setUsage(u) {
    if (!u) return;
    const inT = u.prompt_tokens !== undefined ? u.prompt_tokens : u.input_tokens;
    const outT = u.completion_tokens !== undefined ? u.completion_tokens : u.output_tokens;
    const total = u.total_tokens;
    const parts = [];
    if (inT !== undefined) parts.push('入 ' + inT);
    if (outT !== undefined) parts.push('出 ' + outT);
    if (total !== undefined) parts.push('共 ' + total);
    if (parts.length) $('#tokenUsage').textContent = 'tokens ' + parts.join(' / ');
  }

  function renderResult(status, body, elapsed) {
    setStatus(status);
    setTime(elapsed);
    const box = $('#resultBox');

    if (state.netInfo) {
      state.netInfo.status = status;
      state.netInfo.elapsed = Math.round(elapsed);
      state.netInfo.responseBody = body;
      if (!$('#netDrawer').hidden) renderNetPanel(state.netInfo);
    }

    let obj = null;
    try { obj = JSON.parse(body); } catch (e) { /* 非 JSON */ }

    if (obj) {
      box.innerHTML = syntaxHighlight(JSON.stringify(obj, null, 2));
      setUsage(obj.usage || (obj.response && obj.response.usage));
      const aiParts = extractChat(obj);
      if (aiParts && aiParts.length) state.chatParts = state.chatParts.concat(aiParts);
    } else {
      box.textContent = body;
    }
    renderChat(state.chatParts);
    if (state.viewMode === 'json') box.scrollTop = 0;
  }

  // ---- 对话提取 ----
  function extractChat(obj) {
    const parts = [];
    if (obj.choices && obj.choices[0]) {
      const c = obj.choices[0];
      if (c.message) {
        const m = c.message;
        if (m.reasoning_content) parts.push({ type: 'reasoning', text: m.reasoning_content });
        if (m.tool_calls && m.tool_calls.length) {
          m.tool_calls.forEach((tc) => parts.push({
            type: 'tool', text: '调用工具：' + tc.function.name + '\n参数：' + tc.function.arguments
          }));
        }
        if (m.content) parts.push({ type: 'assistant', text: m.content });
      } else if (c.text) {
        parts.push({ type: 'assistant', text: c.text });
      }
    }
    if (obj.output && Array.isArray(obj.output)) {
      obj.output.forEach((item) => {
        if (item.type === 'reasoning') {
          const t = (item.content || []).filter((b) => b.text).map((b) => b.text).join('');
          if (t) parts.push({ type: 'reasoning', text: t });
        } else if (item.type === 'message') {
          const t = (item.content || []).filter((b) => b.type === 'output_text' || b.text)
            .map((b) => b.text || '').join('');
          if (t) parts.push({ type: 'assistant', text: t });
        } else if (item.type === 'function_call') {
          parts.push({ type: 'tool', text: '调用工具：' + item.name + '\n参数：' + item.arguments });
        }
      });
    }
    if (obj.type === 'message' && Array.isArray(obj.content)) {
      obj.content.forEach((block) => {
        if (block.type === 'text') parts.push({ type: 'assistant', text: block.text });
        else if (block.type === 'thinking') parts.push({ type: 'reasoning', text: block.thinking });
        else if (block.type === 'tool_use') {
          parts.push({ type: 'tool', text: '调用工具：' + block.name + '\n参数：' + JSON.stringify(block.input) });
        }
      });
    }
    return parts.length ? parts : null;
  }

  function extractUserInput(body) {
    if (!body) return [];
    const parts = [];
    const pushText = (t) => { if (t) parts.push({ type: 'user', text: t }); };
    if (Array.isArray(body.messages)) {
      body.messages.forEach((m) => {
        if (m.role !== 'user') return;
        if (typeof m.content === 'string') pushText(m.content);
        else if (Array.isArray(m.content)) {
          m.content.forEach((b) => {
            if (b.type === 'text' || b.type === 'input_text') pushText(b.text);
            else if (b.type === 'image_url' || b.type === 'input_image') pushText('[图片]');
          });
        }
      });
    }
    if (body.input != null) {
      if (typeof body.input === 'string') pushText(body.input);
      else if (Array.isArray(body.input)) {
        body.input.forEach((item) => {
          if (item.role !== 'user' || !item.content) return;
          if (typeof item.content === 'string') pushText(item.content);
          else if (Array.isArray(item.content)) {
            item.content.forEach((b) => {
              if (b.type === 'input_text' || b.type === 'text') pushText(b.text);
              else if (b.type === 'input_image') pushText('[图片]');
            });
          }
        });
      }
    }
    return parts;
  }

  function renderChat(parts) {
    const view = $('#chatView');
    view.innerHTML = '';
    (parts || []).forEach((p) => view.appendChild(buildBubble(p)));
    view.scrollTop = view.scrollHeight;
  }

  function buildBubble(p) {
    const b = el('div', 'bubble ' + p.type);
    if (p.type !== 'user') {
      const labels = { assistant: 'AI', reasoning: '思考过程', tool: '工具调用' };
      b.appendChild(el('span', 'bubble-label', labels[p.type] || ''));
    }
    b.appendChild(document.createTextNode(p.text));
    return b;
  }

  // ---- 网络信息 ----
  function buildNetInfo(req) {
    const d = state.current;
    let url = state.baseUrl + d.path;
    if (req.path) {
      Object.keys(req.path).forEach((k) => {
        url = url.replace('{' + k + '}', encodeURIComponent(req.path[k]));
      });
    }
    if (req.query && Object.keys(req.query).length) {
      url += '?' + Object.keys(req.query)
        .map((k) => k + '=' + encodeURIComponent(req.query[k])).join('&');
    }
    const headers = { 'Content-Type': 'application/json' };
    if (state.apiKey) {
      if (d.authMode === 'x-api-key') headers['x-api-key'] = maskKey(state.apiKey);
      else headers['Authorization'] = 'Bearer ' + maskKey(state.apiKey);
    }
    if (d.extraHeaders) Object.assign(headers, d.extraHeaders);
    return {
      url, method: d.method,
      requestHeaders: headers,
      requestBody: req.body,
      responseHeaders: {},
      responseBody: '',
      status: null,
      elapsed: 0
    };
  }

  function renderNetPanel(net) {
    const esc = escapeHtml;
    const summarize = (obj) => {
      try { return JSON.stringify(obj, null, 2); } catch (e) { return String(obj); }
    };
    const rows = [
      ['请求 URL', net.url],
      ['请求方式', net.method],
      ['状态码', net.status != null ? net.status : '-'],
      ['耗时', net.elapsed ? net.elapsed + ' ms' : '-'],
      ['Trace ID', net.responseHeaders['x-ds-trace-id'] || net.responseHeaders['x-request-id'] || '-']
    ];
    let html = '<div class="net-grid">';
    rows.forEach(([k, v]) => {
      html += '<span class="k">' + esc(k) + '</span><span class="v">' + esc(String(v)) + '</span>';
    });
    html += '</div>';

    const sections = [
      ['请求头', summarize(net.requestHeaders)],
      ['请求参数', net.requestBody ? summarize(net.requestBody) : '(无)'],
      ['响应头', summarize(net.responseHeaders)],
      ['响应参数', net.responseBody || '(空)']
    ];
    sections.forEach(([title, content]) => {
      html += '<div class="net-section"><h3>' + esc(title) + '</h3>'
        + '<pre class="net-code">' + esc(content) + '</pre></div>';
    });
    $('#netBody').innerHTML = html;
  }

  async function doCall(req) {
    resetResultMeta();
    state.netInfo = buildNetInfo(req);
    $('#resultBox').textContent = '请求中…';
    renderChat(state.chatParts);
    if (state.abortController) state.abortController.abort();
    state.abortController = new AbortController();
    $('#stopCall').disabled = false;
    setPrimaryLoading(true);
    const start = performance.now();
    try {
      if (req.stream) await callStream(req, start);
      else await callNonStream(req, start);
    } catch (e) {
      if (e.name === 'AbortError') {
        setStatus('已停止');
        setTime(performance.now() - start);
      } else {
        setStatus('请求失败');
        setTime(performance.now() - start);
        $('#resultBox').textContent = '请求异常: ' + e.message;
        if (state.netInfo) {
          state.netInfo.status = 'ERR';
          state.netInfo.responseBody = e.message;
          if (!$('#netDrawer').hidden) renderNetPanel(state.netInfo);
        }
      }
    } finally {
      $('#stopCall').disabled = true;
      setPrimaryLoading(false);
    }
  }

  async function callNonStream(req, start) {
    const res = await fetch('/api/call', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
      signal: state.abortController.signal
    });
    const text = await res.text();
    if (state.netInfo) {
      state.netInfo.responseHeaders = {};
      res.headers.forEach((v, k) => { state.netInfo.responseHeaders[k] = v; });
    }
    renderResult(res.status, text, performance.now() - start);
  }

  async function callStream(req, start) {
    const res = await fetch('/api/call/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
      signal: state.abortController.signal
    });
    if (!res.ok) {
      renderResult(res.status, await res.text(), performance.now() - start);
      return;
    }
    setStatus(res.status);
    if (state.netInfo) {
      state.netInfo.responseHeaders = {};
      res.headers.forEach((v, k) => { state.netInfo.responseHeaders[k] = v; });
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';
    let reasoning = '';
    let content = '';
    let usage = null;
    let finished = false;
    const rawLines = [];

    while (!finished) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop();
      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed.startsWith('data:')) continue;
        rawLines.push(line);
        const payload = trimmed.slice(5).trim();
        if (!payload) continue;
        if (payload === '[DONE]') { finished = true; break; }
        let obj;
        try { obj = JSON.parse(payload); } catch (e) { continue; }
        const r = extractStream(obj);
        if (!r) continue;
        if (r.usage) {
          usage = usage || {};
          Object.assign(usage, r.usage);
        }
        if (r.done) { finished = true; break; }
        if (r.kind === 'reasoning') reasoning += r.text;
        else if (r.kind === 'text') content += r.text;
        renderChatStreaming(reasoning, content);
      }
    }

    state.chatParts = state.chatParts.filter((p) => p.type === 'user');
    if (reasoning) state.chatParts.push({ type: 'reasoning', text: reasoning });
    if (content) state.chatParts.push({ type: 'assistant', text: content });
    renderChat(state.chatParts);

    $('#resultBox').textContent = rawLines.join('\n');

    setTime(performance.now() - start);
    setUsage(usage);
    if (state.netInfo) {
      state.netInfo.status = res.status;
      state.netInfo.elapsed = Math.round(performance.now() - start);
      state.netInfo.responseBody = rawLines.join('\n');
      if (!$('#netDrawer').hidden) renderNetPanel(state.netInfo);
    }
  }

  function renderChatStreaming(reasoning, content) {
    const parts = state.chatParts.filter((p) => p.type === 'user');
    if (reasoning) parts.push({ type: 'reasoning', text: reasoning });
    if (content) parts.push({ type: 'assistant', text: content });
    renderChat(parts);
  }

  function extractStream(obj) {
    if (obj.type === 'response.output_text.delta') return { kind: 'text', text: obj.delta || '' };
    if (obj.type === 'response.reasoning_text.delta') return { kind: 'reasoning', text: obj.delta || '' };
    if (obj.type === 'response.completed') return { done: true, usage: obj.response && obj.response.usage };
    if (obj.type === 'response.incomplete' || obj.type === 'response.failed') return { done: true };

    if (obj.type === 'content_block_delta') {
      const d = obj.delta;
      if (d && d.type === 'text_delta') return { kind: 'text', text: d.text || '' };
      if (d && d.type === 'thinking_delta') return { kind: 'reasoning', text: d.thinking || '' };
    }
    if (obj.type === 'message_start') return { usage: obj.message && obj.message.usage };
    if (obj.type === 'message_delta') return { usage: obj.usage };
    if (obj.type === 'message_stop') return { done: true };

    if (obj.choices && obj.choices[0]) {
      const c = obj.choices[0];
      if (c.delta) {
        if (c.delta.reasoning_content) return { kind: 'reasoning', text: c.delta.reasoning_content };
        if (c.delta.content) return { kind: 'text', text: c.delta.content };
      }
      if (c.text) return { kind: 'text', text: c.text };
    }
    if (obj.usage) return { usage: obj.usage };
    return null;
  }

  // ============ 调用入口 ============
  function requireApiKey() {
    state.apiKey = $('#apiKey').value.trim();
    if (!state.apiKey) {
      toast('请先在顶部填写 API Key');
      $('#apiKey').focus();
      return false;
    }
    return true;
  }

  function setPrimaryLoading(loading) {
    const btn = document.querySelector('.action-bar .is-primary');
    if (!btn) return;
    if (loading) {
      if (!btn.dataset.origText) btn.dataset.origText = btn.textContent;
      btn.textContent = '请求中…';
      btn.disabled = true;
    } else {
      if (btn.dataset.origText) btn.textContent = btn.dataset.origText;
      btn.disabled = false;
    }
  }

  function callJson() {
    if (!requireApiKey()) return;
    const d = state.current;
    let body;
    try {
      body = JSON.parse($('#jsonBody').value || '{}');
    } catch (e) {
      toast('JSON 格式错误: ' + e.message);
      return;
    }
    if (d.streamable) body.stream = state.stream;
    else delete body.stream;

    state.chatParts = extractUserInput(body);
    const streamMode = d.streamable && state.stream;
    doCall({
      endpoint: d.id, apiKey: state.apiKey, baseUrl: state.baseUrl,
      stream: streamMode, body, query: {}, path: {}
    });
  }

  function callQuery() {
    if (!requireApiKey()) return;
    const d = state.current;
    const query = {};
    const path = {};
    for (const p of d.params) {
      const input = document.querySelector('[data-field="' + p.name + '"]');
      const v = input ? input.value.trim() : '';
      if (p.required && !v) {
        toast('请填写必填参数：' + p.label);
        if (input) input.focus();
        return;
      }
      if (p.in === 'path' && v) path[p.name] = v;
      else if (p.in === 'query' && v !== '') query[p.name] = v;
    }
    state.chatParts = [];
    doCall({
      endpoint: d.id, apiKey: state.apiKey, baseUrl: state.baseUrl,
      stream: false, body: null, query, path
    });
  }

  async function callUpload() {
    if (!requireApiKey()) return;
    const d = state.current;
    const fileInput = $('#fileInput');
    const file = fileInput && fileInput.files[0];
    if (!file) { toast('请选择要上传的图片文件'); return; }
    const purposeInput = document.querySelector('[data-field="purpose"]');
    const purpose = (purposeInput && purposeInput.value.trim()) || 'user_data';

    resetResultMeta();
    state.chatParts = [];
    state.netInfo = {
      url: state.baseUrl + d.path,
      method: d.method,
      requestHeaders: {
        'Content-Type': 'multipart/form-data',
        'Authorization': state.apiKey ? 'Bearer ' + maskKey(state.apiKey) : '(未配置)'
      },
      requestBody: {
        purpose: purpose,
        file: file.name + ' (' + Math.round(file.size / 1024) + ' KB)'
      },
      responseHeaders: {},
      responseBody: '',
      status: null,
      elapsed: 0
    };
    $('#resultBox').textContent = '上传中…';
    const start = performance.now();

    const fd = new FormData();
    fd.append('file', file);
    fd.append('endpoint', d.id);
    fd.append('apiKey', state.apiKey);
    fd.append('baseUrl', state.baseUrl);
    fd.append('purpose', purpose);

    setPrimaryLoading(true);
    try {
      const res = await fetch('/api/call/upload', { method: 'POST', body: fd });
      if (state.netInfo) {
        state.netInfo.responseHeaders = {};
        res.headers.forEach((v, k) => { state.netInfo.responseHeaders[k] = v; });
      }
      renderResult(res.status, await res.text(), performance.now() - start);
    } catch (e) {
      setStatus('上传失败');
      setTime(performance.now() - start);
      $('#resultBox').textContent = '上传异常: ' + e.message;
    } finally {
      setPrimaryLoading(false);
    }
  }

  init();
})();
