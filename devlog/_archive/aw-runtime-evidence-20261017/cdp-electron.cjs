/**
 * Scratch CDP driver, stage 3: exercise the two things only the pane can prove
 * (camera button + its 3s auto-release; the screen window actually rendering),
 * then close ONLY the windows this script opened (window.close from inside each).
 * Usage: node devlog/_aw/cdp-electron.cjs stage3 [port]
 */
const PORT = Number(process.argv[3] || 9231);

async function targets() {
  const list = await (await fetch(`http://127.0.0.1:${PORT}/json`)).json();
  return list.filter((t) => t.type === 'page' && /localhost:3300/.test(t.url));
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
    async eval(label, expression, timeoutMs = 15000) {
      const t0 = Date.now();
      try {
        const r = await Promise.race([
          send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true, timeout: timeoutMs }),
          sleep(timeoutMs + 1000).then(() => {
            throw new Error(`timeout ${timeoutMs}ms`);
          }),
        ]);
        if (r.exceptionDetails) {
          const d = r.exceptionDetails;
          const msg = d.exception ? d.exception.description || String(d.exception.value) : d.text;
          console.log(JSON.stringify({ label, ok: false, err: String(msg).slice(0, 400), ms: Date.now() - t0 }));
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

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const list = await targets();
  console.log(JSON.stringify({ targets: list.map((t) => ({ url: t.url, title: t.title })) }));

  // 1) the pane's own camera button (component owns the 3s release timer)
  const home = list.find((t) => /#\/(screen-view)??.*/.test(t.url) && /#\/$|#\/\?/.test(t.url)) || list[0];
  const c = makeClient(await connect(home.webSocketDebuggerUrl));
  await c.send('Runtime.enable');
  console.log(JSON.stringify({ cameraHost: home.url }));
  await c.eval(
    'camera-button',
    `(async()=>{const card=[...document.querySelectorAll('.card')].find(x=>x.querySelector('h2')&&x.querySelector('h2').textContent.includes('摄像头'));
if(!card)return 'no camera card';
const b=[...card.querySelectorAll('button')].find(x=>/申请|预览|Camera|Preview/i.test(x.textContent));
if(!b)return 'no button';
const before=[...card.querySelectorAll('video')].length;
b.click();
await new Promise(r=>setTimeout(r,1200));
const mid={videos:card.querySelectorAll('video').length,err:(card.querySelector('.result.error')||{}).textContent};
await new Promise(r=>setTimeout(r,2200));
const after={videos:card.querySelectorAll('video').length,err:(card.querySelector('.result.error')||{}).textContent};
await new Promise(r=>setTimeout(r,1500));
const late={videos:card.querySelectorAll('video').length};
return JSON.stringify({before,midAt1200:mid,at3400:after,at4900:late});})()`,
    30000
  );

  // 2) the screen window opened by window:openScreen — does it render anything real?
  const screenWin = list.filter((t) => /screen-view/.test(t.url)).sort((a, b) => b.url.length - a.url.length);
  for (const t of screenWin.slice(0, 2)) {
    const sc = makeClient(await connect(t.webSocketDebuggerUrl));
    await sc.send('Runtime.enable');
    await sc.eval(`screen-window:${t.url}`, `(JSON.stringify({title:document.title,txt:document.body.innerText.replace(/\\s+/g,' ').slice(0,300),canvases:document.querySelectorAll('canvas').length,badges:[...document.querySelectorAll('.el-tag,.badge,.dv-badge')].map(x=>x.textContent.trim()).slice(0,6)}))`, 12000);
  }

  // 3) close only the windows this run created (ids 3/4 aux + the openScreen windows), never the main one
  const all = await targets();
  for (const t of all) {
    if (t.url === 'http://localhost:3300/login#/screen-view?target=6&offline=1') {
      console.log(JSON.stringify({ skip: 'user main window', url: t.url }));
      continue;
    }
    if (!/localhost:3300/.test(t.url)) continue;
    const w = makeClient(await connect(t.webSocketDebuggerUrl));
    await w.send('Runtime.enable');
    await w.eval(`close:${t.url}`, `(window.close(),'close called')`, 8000);
  }
  await sleep(2500);
  const rest = await targets();
  console.log(JSON.stringify({ remainingTargets: rest.map((t) => t.url + ' :: ' + t.title) }));
  console.log(JSON.stringify({ done: true }));
  process.exit(0);
}

main().catch((e) => {
  console.error('FATAL', e.message);
  process.exit(1);
});
