// src/main.js — DeepSeek 站点包装器后端
//
// 由 `tinyjs wrap` 生成，在此基础上优化了导航策略：
//   - deepseek.com 及其子域 → 允许在应用内加载
//   - 外部 http(s) 链接       → 交给系统默认浏览器打开
//   - mailto:、tel: 等协议    → 交给系统处理
//
// tinyjs.json 的 "api" 已经按 origin 做了权限隔离：
//   - https://chat.deepseek.com 及子域 → wrapper 预设（通知、存储、find-in-page 等）
//   - 其他 origin                        → 仅窗口控制（关闭、最小化等）

const HOME = 'https://chat.deepseek.com';
const DEEPSEEK_SUFFIX = '.deepseek.com';

// DeepSeek 登录依赖的第三方 OAuth 域名，必须允许在应用内加载
// 微信扫码/微信登录 → open.weixin.qq.com
const ALLOWED_EXTRA_HOSTS = new Set([
  'open.weixin.qq.com',
]);

/**
 * 判断一个 URL 是否属于"应用内允许"的域名
 * - DeepSeek 自家域名（chat.deepseek.com + 所有 *.deepseek.com 子域）
 * - 登录依赖的第三方 OAuth 域名（微信等）
 * @param {string} urlStr
 * @returns {boolean}
 */
function isAllowedOrigin(urlStr) {
  try {
    const host = new URL(urlStr).hostname;
    if (host === 'chat.deepseek.com' || host.endsWith(DEEPSEEK_SUFFIX)) return true;
    if (ALLOWED_EXTRA_HOSTS.has(host)) return true;
    return false;
  } catch {
    return false;
  }
}

/**
 * 导航拦截钩子。在任何主框架 http(s) 导航前被调用，
 * 以及 mailto:/tel:/zoommtg: 等特殊协议导航时被调用。
 *
 * 返回值：
 *   - undefined → 允许在应用内加载
 *   - 'deny'    → 阻止
 *   - 'external' → 交给系统处理（浏览器、邮件客户端等）
 */
export function onNavigate(info) {
  if (info.kind === 'policy') {
    const url = info.url;

    // 非 http(s) 协议（mailto:、tel: 等）→ 交给系统
    if (!/^https?:$/i.test(new URL(url).protocol)) {
      console.log('[nav] external protocol →', url);
      return 'external';
    }

    // http(s) 链接但不属于允许域名 → 外部浏览器打开
    if (!isAllowedOrigin(url)) {
      console.log('[nav] external link →', url);
      return 'external';
    }

    // deepseek 自家链接 → 允许
    console.log('[nav] allow →', url);
    return undefined;
  }

  // 其他事件（start / commit / finish / fail / crash）仅做日志
  console.log(`[nav ${info.kind}]`, info.url || info.error || '');
}

/**
 * 下载事件日志
 */
export function onDownload(info) {
  console.log('[dl]', info.state, info.filename ?? info.url);
}

/**
 * 新窗口 / popup 事件日志。
 * popups 在 tinyjs.json 中已设为 "window"，会在同一应用的新窗口中打开。
 */
export function onWindowOpen(info) {
  console.log('[popup]', info.mode ?? info.kind, info.url);
}

/**
 * 应用初始化。
 * 这里只做日志输出，没有自定义 API 函数（tinyjs.json 的 api gate 已处理全部权限）。
 */
export function init(app) {
  console.log(`[deepseek-wrapper] 启动完成，目标：${HOME}`);
  console.log('[deepseek-wrapper] 导航策略：deepseek.com 内部加载，外部链接交给系统浏览器');
}
