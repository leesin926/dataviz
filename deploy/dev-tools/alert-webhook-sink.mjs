// 本地告警收件口：只用于验证 alert-service 的通知派发，收到什么就打什么。
// 用法：node deploy/dev-tools/alert-webhook-sink.mjs [port]   （默认 18099）
import { createServer } from 'node:http'

const port = Number(process.argv[2] ?? 18099)
const host = '127.0.0.1'

createServer((req, res) => {
  const chunks = []
  req.on('data', (c) => chunks.push(c))
  req.on('end', () => {
    const body = Buffer.concat(chunks).toString('utf8')
    console.log(`\n[${new Date().toISOString()}] ${req.method} ${req.url}`)
    try {
      console.log(JSON.stringify(JSON.parse(body), null, 2))
    } catch {
      console.log(body || '<empty>')
    }
    res.writeHead(200, { 'content-type': 'application/json' })
    res.end('{"errcode":0}')
  })
}).listen(port, host, () => console.log(`alert webhook sink listening on http://${host}:${port}/hook`))
