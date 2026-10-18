// 免登推送通道：真 Upgrade 握手（期望 101 + Sec-WebSocket-Accept）
const net = require('net');
const crypto = require('crypto');

function handshake(port, path, headers = {}) {
  return new Promise((done) => {
    const key = crypto.randomBytes(16).toString('base64');
    const expected = crypto.createHash('sha1').update(key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64');
    const lines = [`GET ${path} HTTP/1.1`, `Host: localhost:${port}`, 'Connection: Upgrade', 'Pragma: no-cache',
      'Cache-Control: no-cache', 'Upgrade: websocket', 'Sec-WebSocket-Version: 13', `Sec-WebSocket-Key: ${key}`];
    for (const [k, v] of Object.entries(headers)) lines.push(`${k}: ${v}`);
    const sock = net.connect(port, '127.0.0.1', () => sock.write(lines.join('\r\n') + '\r\n\r\n'));
    let buf = '';
    const timer = setTimeout(() => { sock.destroy(); done({ port, path, result: 'NO RESPONSE', buf: buf.slice(0, 160) }); }, 6000);
    sock.on('data', (d) => {
      buf += d.toString('latin1');
      if (buf.includes('\r\n\r\n') || buf.includes('\n\n')) {
        clearTimeout(timer); sock.destroy();
        const statusLine = buf.split(/\r?\n/)[0];
        const accept = /sec-websocket-accept:\s*([^\r\n]+)/i.exec(buf);
        done({ port, path, statusLine, acceptOk: !!accept && accept[1].trim() === expected });
      }
    });
    sock.on('error', (e) => { clearTimeout(timer); done({ port, path, result: 'ERR ' + e.code }); });
  });
}

(async () => {
  console.log(JSON.stringify(await handshake(8093, '/api/admin/ws/public'), null, 1));
  console.log(JSON.stringify(await handshake(8080, '/api/admin/ws/public'), null, 1));
  console.log(JSON.stringify(await handshake(8088, '/ws/screen/push?screenId=1'), null, 1));
})();
