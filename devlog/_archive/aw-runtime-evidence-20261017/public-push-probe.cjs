// 免登配置面 + 推送通道握手（AF1/AF2 的免登半区）
const http = require('http');
const crypto = require('crypto');

function req(port, path, headers = {}) {
  return new Promise((res) => {
    const r = http.get({ host: '127.0.0.1', port, path, headers, timeout: 8000 }, (resp) => {
      let b = '';
      resp.setEncoding('utf8');
      resp.on('data', (c) => (b += c));
      resp.on('end', () => res({ status: resp.statusCode, headers: resp.headers, body: b.slice(0, 200) }));
    });
    r.on('error', (e) => res({ status: 'ERR:' + e.code }));
    r.on('timeout', () => { r.destroy(); res({ status: 'TIMEOUT' }); });
  });
}

const KEY = 'screen.mourning.enabled';

(async () => {
  const g = await req(8080, `/api/admin/config/public/${encodeURIComponent(KEY)}`);
  console.log('GET /api/admin/config/public/{key} via gateway ->', g.status, g.body);

  const d = await req(8093, `/api/admin/config/public/${encodeURIComponent(KEY)}`);
  console.log('same on :8093 direct ->', d.status, d.body);

  // WebSocket 握手：带 Upgrade 头，期望 101
  for (const [label, port, path] of [['gateway 8080', 8080, '/api/admin/ws/public'], ['direct 8093', 8093, '/api/admin/ws/public']]) {
    await new Promise((done) => {
      const key = crypto.randomBytes(16).toString('base64');
      const r = http.request({
        host: '127.0.0.1', port, path, timeout: 8000,
        headers: { Connection: 'Upgrade', Upgrade: 'websocket', 'Sec-WebSocket-Key': key, 'Sec-WebSocket-Version': '13' },
      }, (resp) => {
        console.log(`WS handshake ${label} ->`, resp.statusCode, 'upgrade=' + resp.headers.upgrade, 'accept=' + (resp.headers['sec-websocket-accept'] ? 'yes' : 'no'));
        resp.destroy();
        done();
      });
      r.on('error', (e) => { console.log(`WS handshake ${label} -> ERR ` + e.code); done(); });
      r.on('timeout', () => { r.destroy(); console.log(`WS handshake ${label} -> TIMEOUT`); done(); });
      r.end();
    });
  }
})();
