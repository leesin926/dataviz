/** Scratch: snapshot the Electron renderer state (URL, storage keys, DOM heading) + PNG via CDP. */
const PORT = 9231;

async function page() {
  const list = await (await fetch(`http://127.0.0.1:${PORT}/json`)).json();
  const p = list.filter((t) => t.type === 'page' && /localhost:3300/.test(t.url));
  return p;
}

function connect(wsUrl) {
  return new Promise((res, rej) => {
    const ws = new WebSocket(wsUrl);
    ws.addEventListener('open', () => res(ws));
    ws.addEventListener('error', (e) => rej(new Error(String(e.message || e.type))));
  });
}

function rpc(ws) {
  let id = 0;
  const pend = new Map();
  ws.addEventListener('message', (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pend.has(m.id)) {
      const p = pend.get(m.id);
      pend.delete(m.id);
      m.error ? p.rej(new Error(JSON.stringify(m.error))) : p.res(m.result);
    }
  });
  return (method, params) =>
    new Promise((resolve, reject) => {
      const mid = ++id;
      pend.set(mid, { res: resolve, rej: reject });
      ws.send(JSON.stringify({ id: mid, method, params: params || {} }));
    });
}

(async () => {
  const pages = await page();
  console.log('TARGETS', JSON.stringify(pages.map((p) => ({ url: p.url, title: p.title })), null, 1));
  for (const p of pages) {
    const ws = await connect(p.webSocketDebuggerUrl);
    const call = rpc(ws);
    await call('Runtime.enable');
    const ev = await call('Runtime.evaluate', {
      expression:
        "JSON.stringify({url:location.href,token:localStorage.getItem('dataviz_access_token')?'PRESENT':'none',lsKeys:Object.keys(localStorage),apiBase:localStorage.getItem('dataviz_api_base'),h1:document.querySelector('h1')?.textContent?.trim(),headings:[...document.querySelectorAll('h2')].map(x=>x.textContent.trim()),body:document.body.innerText.replace(/\\s+/g,' ').slice(0,400)})",
      returnByValue: true,
    });
    console.log('STATE', p.title, ev.result.value);
    try {
      await call('Page.enable');
      const shot = await call('Page.captureScreenshot', { format: 'png' });
      const fs = require('fs');
      const file = `E:/wowowo/devlog/_aw/electron-shot-${p.id.slice(0, 6)}.png`;
      fs.writeFileSync(file, Buffer.from(shot.data, 'base64'));
      console.log('SHOT', file, shot.data.length);
    } catch (e) {
      console.log('SHOT_FAIL', e.message);
    }
    ws.close();
  }
})().catch((e) => {
  console.error('FATAL', e.message);
  process.exit(1);
});
