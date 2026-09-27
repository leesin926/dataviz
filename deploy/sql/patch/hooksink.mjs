// 告警链路的本地收件口（#70 取证用，配套 2026-09-22-alert-live-demo.sql 步骤 4）。
//
// 为什么必须用局域网 IP 而不是 127.0.0.1 来配 notify_channel(911)：服务端出网地址有
// OutboundUrlGuard 拦回环段（SSRF 收口），配成 127.0.0.1 会以"禁止回环地址"记 FAILED ——
// 那是守卫在正常工作，不是链路坏了。
import http from 'node:http'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { writeFileSync } from 'node:fs'

const PORT = Number(process.argv[2] || 18099)
const LAST = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..', 'devlog', 'hooksink-last.json')
let received = 0

const server = http.createServer((req, res) => {
  const chunks = []
  req.on('data', (c) => chunks.push(c))
  req.on('end', () => {
    const body = Buffer.concat(chunks).toString('utf8')
    if (req.url === '/hook') {
      received++
      writeFileSync(LAST, JSON.stringify({ at: new Date().toISOString(), method: req.method, body }, null, 2))
      console.log(`RECEIVED #${received} -> ${LAST}`)
      console.log(body)
      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify({ ok: true, received }))
      return
    }
    if (req.url === '/state') {
      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify({ received }))
      return
    }
    res.writeHead(404)
    res.end()
  })
})

server.listen(PORT, '0.0.0.0', () => {
  const lan = Object.values(os.networkInterfaces())
    .flat()
    .filter((i) => i && i.family === 'IPv4' && !i.internal)
    .map((i) => i.address)
  console.log(`listening on 0.0.0.0:${PORT}`)
  if (!lan.length) {
    console.log('没有局域网 IPv4：127.0.0.1 会被服务端出网守卫拒掉，这条链路当场测不了')
  }
  lan.forEach((ip) => console.log(`  候选 URL: http://${ip}:${PORT}/hook`))
  console.log('把选中的那条写进渠道配置：')
  console.log(`  UPDATE db_alert.notify_channel SET config = '{"url":"http://<上面的IP>:${PORT}/hook"}' WHERE id = 911;`)
})
