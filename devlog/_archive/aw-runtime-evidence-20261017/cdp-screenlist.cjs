/**
 * Scratch CDP probe: open an aux Electron window (it inherits the login state the user
 * established in the main window), switch to the 大屏与离线 tab and read the component-count
 * column, so the ScreenListVO componentCount fix is proven end-to-end in the running app.
 * Read-only: no click on any write/缓存/删除 button.
 */
const PORT = 9231;

async function targets() {
  const list = await (await fetch(`http://127.0.0.1:${PORT}/json`)).json();
  return list.filter((t) => t.type === 'page' && /localhost:3300/.test(t.url));
}
function connect(wsUrl) {
  return new Promise((res, rej) => {
    const ws = new WebSocket(wsUrl);
    ws.addEventListener('open', () => res(ws));
    ws.addEventListener('error', (e) => rej(new Error(String(e.message || e.type))));
  });
}
function client(ws) {
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
  const send = (method, params) =>
    new Promise((resolve, reject) => {
      const mid = ++id;
      pend.set(mid, { res: resolve, rej: reject });
      ws.send(JSON.stringify({ id: mid, method, params: params || {} }));
    });
  const ev = async (label, expression, ms = 15000) => {
    try {
      const r = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true, timeout: ms });
      if (r.exceptionDetails) {
        const d = r.exceptionDetails;
        console.log(JSON.stringify({ label, ok: false, err: String(d.exception?.description || d.text).slice(0, 300) }));
        return null;
      }
      console.log(JSON.stringify({ label, ok: true, value: r.result?.value }));
      return r.result?.value;
    } catch (e) {
      console.log(JSON.stringify({ label, ok: false, err: e.message }));
      return null;
    }
  };
  return { send, ev };
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const before = await targets();
  const host = client(await connect(before[0].webSocketDebuggerUrl));
  await host.send('Runtime.enable');
  const created = await host.ev('create-aux', `(window.electronAPI.window.create().then(id=>({id})))`);
  await sleep(2600);
  const after = await targets();
  const fresh = after.find((t) => !before.some((b) => b.id === t.id));
  console.log(JSON.stringify({ created, freshUrl: fresh && fresh.url }));
  const c = client(await connect(fresh.webSocketDebuggerUrl));
  await c.send('Runtime.enable');
  await c.ev('tab-click', `(()=>{const b=[...document.querySelectorAll('.el-tabs__item')].find(x=>/大屏与离线|Screens and offline/i.test(x.textContent));if(!b)return 'no tab';b.click();return 'clicked '+b.textContent.trim();})()`);
  await sleep(2500);
  await c.ev(
    'component-count-column',
    `(()=>{const card=[...document.querySelectorAll('.card')].find(x=>x.querySelector('h2')&&/已发布大屏/.test(x.querySelector('h2').textContent));if(!card)return 'no card';
const head=[...card.querySelectorAll('th')].map(x=>x.textContent.trim());
const rows=[...card.querySelectorAll('.el-table__body tbody tr')].map(tr=>[...tr.querySelectorAll('td')].map(td=>td.textContent.trim()));
return JSON.stringify({head,rows});})()`
  );
  await c.ev('close-aux', `(window.close(),'closed')`);
  await sleep(1200);
  console.log(JSON.stringify({ remaining: (await targets()).map((t) => t.url) }));
  process.exit(0);
})().catch((e) => {
  console.error('FATAL', e.message);
  process.exit(1);
});
