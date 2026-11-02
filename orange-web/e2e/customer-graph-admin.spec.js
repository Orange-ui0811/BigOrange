import { test, expect } from '@playwright/test'

test('客服运行展示节点与审计原文，保持原导航',async({page})=>{
  let rawRequests=0
  await page.addInitScript(()=>localStorage.setItem('adminToken','synthetic-admin-token'))
  await page.route('**/admin/customer/runs**',async route=>{
    const path=new URL(route.request().url()).pathname
    let data
    if(path.endsWith('/raw-access')){
      rawRequests++
      expect(route.request().postDataJSON()).toEqual({payloadId:'payload-one',reason:'核对本轮引用'})
      data={content_json:'{"messages":[{"role":"user","content":"合成验收问题"}]}'}
    }else if(path.endsWith('/checkpoints'))data=[{sequence_id:1,node_id:'load_context',next_node_id:'understand_turn',summary:{next:'understand_turn'}}]
    else if(path.endsWith('/diagnostics'))data={id:'acceptance-run',status:'SUCCEEDED',execution_engine:'LANGGRAPH4J',graph_version:'customer-graph-v1',prompt_version:'customer-v4.19-composite-turns',businessOutcome:'DIALOGUE_COMPLETED',metrics:{costUsd:'UNKNOWN',modelCalls:[]},nodes:[{id:'node-one',node_id:'understand_turn',status:'SUCCEEDED',duration_ms:12,next_node_id:'reconcile_conditions',summary_json:'{"intent":"CHAT"}'}],steps:[],payloads:[{id:'payload-one',kind:'MODEL_REQUEST',created_at:'2026-10-07 12:00'}],rawAccessEnabled:true}
    else data={items:[{id:'acceptance-run',status:'SUCCEEDED',execution_engine:'LANGGRAPH4J',created_at:'2026-10-07 12:00',business_outcome:'DIALOGUE_COMPLETED'}],total:1}
    await route.fulfill({json:{code:1,data}})
  })
  await page.route('**/admin/config/trade-mode',route=>route.fulfill({json:{code:1,data:{enabled:false}}}))
  await page.goto('http://127.0.0.1:15174/admin/#/customer-runs')
  await expect(page.getByRole('heading',{name:'每次回答，都有迹可循'})).toBeVisible()
  await expect(page.getByText('RAG 实验室',{exact:true})).toBeVisible()
  await page.getByRole('button',{name:'acceptance-run'}).click()
  await expect(page.getByText('理解本轮意图',{exact:true})).toBeVisible()
  expect(rawRequests).toBe(0)
  await page.getByRole('tab',{name:'受控原文'}).click()
  await expect(page.getByRole('button',{name:'记录审计并查看'})).toBeDisabled()
  await page.getByText('选择一次调用或证据',{exact:true}).click()
  await page.getByRole('option').filter({hasText:'MODEL_REQUEST'}).click()
  await page.getByPlaceholder('说明正在排查的问题').fill('核对本轮引用')
  await page.getByRole('button',{name:'记录审计并查看'}).click()
  await expect(page.locator('pre').filter({hasText:'合成验收问题'})).toBeVisible()
  expect(rawRequests).toBe(1)
  await page.getByRole('tab',{name:'节点路径'}).click()
  await page.screenshot({path:'test-results/customer-admin/desktop.png',fullPage:true,animations:'disabled'})
  await page.setViewportSize({width:390,height:844})
  await expect(page.getByText('理解本轮意图',{exact:true})).toBeInViewport()
  await page.screenshot({path:'test-results/customer-admin/mobile.png',fullPage:true,animations:'disabled'})
})
