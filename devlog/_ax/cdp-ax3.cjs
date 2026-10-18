/**
 * AX3 判据：打包后的桌面端里，导出不弹保存框、直接落盘并回字节数。
 * 用法：node devlog/_ax/cdp-ax3.cjs [port]
 * 只读探针 + 一次真实导出（写 Downloads 目录），不碰用户会话、不发鉴权请求。
 */
const PORT = Number(process.argv[2] || 9231);

async function pageTargets() {
  const list = await (await fetch(`http://127.0.0.1:${PORT}/json`)).json();
  return list.filter((t) => t.type === 'page');
}

function connect(wsUrl) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(wsUrl);
    ws.addEventListener('open', () => resolve(ws));
    ws.addEventListener('error', (e) => reject(new Error(String(e.message || e.type))));
  });
}

function makeClient(ws) {
  let id = 0;
  const pending = new Map();
  ws.addEventListener('message', (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) {
      const p = pending.get(m.id);
      pending.delete(m.id);
      m.error ? p.rej(new Error(JSON.stringify(m.error))) : p.res(m.result);
    }
  });
  const send = (method, params) =>
    new Promise((resolve, reject) => {
      const mid = ++id;
      pending.set(mid, { res: resolve, rej: reject });
      ws.send(JSON.stringify({ id: mid, method, params: params || {} }));
    });
  return {
    send,
    async eval(label, expression, timeoutMs = 20000) {
      const t0 = Date.now();
      try {
        const r = await Promise.race([
          send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true, timeout: timeoutMs }),
          new Promise((res) => setTimeout(() => res(null), timeoutMs + 2000)),
        ]);
        if (r === null) {
          console.log(JSON.stringify({ label, ok: false, err: `timeout ${timeoutMs}ms`, ms: Date.now() - t0 }));
          return null;
        }
        if (r.exceptionDetails) {
          const d = r.exceptionDetails;
          const msg = d.exception ? d.exception.description || String(d.exception.value) : d.text;
          console.log(JSON.stringify({ label, ok: false, err: String(msg).slice(0, 300), ms: Date.now() - t0 }));
          return null;
        }
        console.log(JSON.stringify({ label, ok: true, value: r.result && r.result.value, ms: Date.now() - t0 }));
        return r.result && r.result.value;
      } catch (e) {
        console.log(JSON.stringify({ label, ok: false, err: e.message, ms: Date.now() - t0 }));
        return null;
      }
    },
  };
}

async function main() {
  const list = await pageTargets();
  console.log(JSON.stringify({ targets: list.map((t) => t.url) }));
  const page = list.find((t) => /app\.asar[/\\]dist[/\\]index\.html/.test(t.url)) || list[0];
  if (!page) {
    console.log(JSON.stringify({ fatal: 'no page target' }));
    process.exit(1);
  }
  const c = makeClient(await connect(page.webSocketDebuggerUrl));
  await c.send('Runtime.enable');

  await c.eval('bridge-shape', `(JSON.stringify({
    hasBridge: !!window.electronAPI,
    exportKeys: window.electronAPI ? Object.keys(window.electronAPI.export||{}) : [],
    hasPrint: window.electronAPI ? Object.keys(window.electronAPI.print||{}) : []
  }))`);

  await c.eval('export-pdf', `(async()=>{
    const r = await window.electronAPI.export.pdf({fileName:'ax3-packaged', landscape:true});
    return JSON.stringify(r);
  })()`, 40000);

  await c.eval('export-png', `(async()=>{
    const r = await window.electronAPI.export.png({fileName:'ax3-packaged'});
    return JSON.stringify(r);
  })()`, 40000);

  await c.eval('export-recent', `(async()=>{
    const r = await window.electronAPI.export.recent();
    return JSON.stringify(r);
  })()`, 20000);

  process.exit(0);
}

main().catch((e) => {
  console.error('FATAL', e.message);
  process.exit(1);
});
