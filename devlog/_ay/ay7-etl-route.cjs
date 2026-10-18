const http=require('http'),fs=require('fs')
const TOKEN=fs.readFileSync('devlog/_ay/ay5-token-pc-web.txt','utf8').trim()
function call(port,p){return new Promise(r=>{const q=http.request({host:'127.0.0.1',port,path:encodeURI(p),method:'GET',headers:{Authorization:'Bearer '+TOKEN},timeout:12000},s=>{let b='';s.on('data',c=>b+=c);s.on('end',()=>r({port,p,status:s.statusCode,body:b.replace(/\s+/g,' ').slice(0,130)}))});q.on('error',e=>r({port,p,status:'ERR',body:String(e.message)}));q.on('timeout',()=>{q.destroy();r({port,p,status:'ERR',body:'timeout'})});q.end()})}
const sleep=ms=>new Promise(r=>setTimeout(r,ms))
;(async()=>{
  for(const [port,p] of [[8080,'/api/etl/operator/list'],[8084,'/api/etl/operator/list'],[8080,'/api/etl/operator/jdbc/schema?dsId=1'],[8084,'/api/etl/operator/jdbc/schema?dsId=1'],[8080,'/api/etl/task/page?page=1&size=1'],[8080,'/api/token/validate'],[8081,'/token/validate'],[8084,'/api/etl/task/page?page=1&size=1']]){
    const r=await call(port,p);console.log(`${r.port} ${r.status} ${p} | ${r.body.slice(0,110)}`);await sleep(1300)
  }
})()
