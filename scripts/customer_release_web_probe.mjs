// Static release-package HTTP smoke; no auth data, model requests or live routes.
import http from 'node:http'
import fs from 'node:fs/promises'
import path from 'node:path'
import assert from 'node:assert/strict'

const root='/webroot'
const server=http.createServer(async(req,res)=>{
  const url=new URL(req.url,'http://localhost')
  const relative=decodeURIComponent(url.pathname).replace(/^\/+/, '')
  if(relative.includes('..') || relative.includes('.env')){res.writeHead(404);res.end();return}
  let file=path.join(root,relative)
  try {
    if((await fs.stat(file)).isDirectory())file=path.join(file,'index.html')
    const bytes=await fs.readFile(file)
    const type=file.endsWith('.html')?'text/html':file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'application/octet-stream'
    res.writeHead(200,{'Content-Type':type});res.end(bytes)
  } catch {
    if(url.pathname.startsWith('/assets/') || url.pathname.startsWith('/admin/assets/') || path.extname(relative)){
      res.writeHead(404);res.end();return
    }
    const bytes=await fs.readFile(path.join(root,url.pathname.startsWith('/admin')?'admin/index.html':'index.html'))
    res.writeHead(200,{'Content-Type':'text/html'});res.end(bytes)
  }
})
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve))
const base=`http://127.0.0.1:${server.address().port}`
const checks=[]
try{
  for(const route of ['/customer','/admin/']){
    const response=await fetch(base+route);assert.equal(response.status,200)
    const html=await response.text()
    const assets=[...html.matchAll(/(?:src|href)="(\/[^"?#]+\.(?:js|css))"/g)].map(x=>x[1])
    assert.ok(assets.length>0)
    for(const asset of assets){const resource=await fetch(base+asset);assert.equal(resource.status,200);assert.ok((await resource.arrayBuffer()).byteLength>0)}
    checks.push({route,status:response.status,assets:assets.length})
  }
  for(const route of ['/.env','/assets/does-not-exist.js','/admin/assets/does-not-exist.js'])assert.equal((await fetch(base+route)).status,404)
  console.log(JSON.stringify({passed:true,checks,missingAssetsReturn404:true,modelCalls:0,
    scope:'HTTP smoke of immutable static bundles; not a production Nginx/TLS/SSE or authenticated-browser test'}))
}finally{server.closeAllConnections();await new Promise(resolve=>server.close(resolve))}
