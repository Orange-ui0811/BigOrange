<template>
  <section class="customer-runs">
    <header class="run-header">
      <div><p class="eyebrow">客服 · 执行观测</p><h1>每次回答，都有迹可循</h1><p class="muted">查看执行路径、证据流向与调用用量。检查点仅用于诊断，不会重放请求。</p></div>
      <el-button @click="load" :loading="loading">刷新运行</el-button>
    </header>
    <form class="filters" @submit.prevent="search">
      <el-input v-model="filters.runId" placeholder="查找运行 ID" aria-label="运行 ID" clearable />
      <el-select v-model="filters.engine" placeholder="全部编排" aria-label="执行引擎" clearable><el-option label="旧编排" value="LEGACY"/><el-option label="LangGraph4j" value="LANGGRAPH4J"/></el-select>
      <el-select v-model="filters.status" placeholder="全部状态" aria-label="运行状态" clearable><el-option v-for="s in statuses" :key="s" :label="labels[s] || s" :value="s"/></el-select>
      <el-input v-model="filters.outcome" placeholder="业务结果" aria-label="业务结果" clearable />
      <el-date-picker v-model="range" type="datetimerange" start-placeholder="开始时间" end-placeholder="结束时间" value-format="YYYY-MM-DD HH:mm:ss" aria-label="运行时间范围" />
      <el-button native-type="submit" type="primary">筛选</el-button>
    </form>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-skeleton v-if="loading && !rows.length" :rows="6" animated />
    <el-table v-else :data="rows" class="run-table" @row-click="open">
      <el-table-column label="运行" min-width="245"><template #default="{row}"><button class="run-link" @click.stop="open(row)">{{ row.id }}</button><small>{{ row.created_at }}</small></template></el-table-column>
      <el-table-column prop="execution_engine" label="编排" width="145" />
      <el-table-column label="执行状态" width="110"><template #default="{row}"><span :class="['status', row.status]">{{ labels[row.status] || row.status }}</span></template></el-table-column>
      <el-table-column prop="business_outcome" label="业务结果" min-width="160" />
      <el-table-column prop="error_code" label="异常" min-width="160" />
      <template #empty><div class="empty">没有符合筛选条件的运行。可以清空筛选，或先在客服发起一次对话。</div></template>
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="prev, pager, next, total" @current-change="load" />
    <el-drawer v-model="opened" class="customer-run-drawer" title="运行诊断" size="min(900px, 96vw)" @closed="clearDetail">
      <el-skeleton v-if="detailLoading" :rows="8" animated />
      <el-alert v-else-if="detailError" :title="detailError" type="error" :closable="false" />
      <template v-else-if="detail">
        <div class="identity"><code>{{ detail.id }}</code><span class="status">{{ labels[detail.status] || detail.status }}</span></div>
        <dl class="facts"><div><dt>编排</dt><dd>{{ detail.execution_engine || 'LEGACY' }}</dd></div><div><dt>图版本</dt><dd>{{ detail.graph_version || '未使用图' }}</dd></div><div><dt>Prompt</dt><dd>{{ detail.prompt_version }}</dd></div><div><dt>知识代际</dt><dd>{{ detail.generation_key || '本轮未检索' }}</dd></div><div><dt>业务结果</dt><dd>{{ detail.businessOutcome }}</dd></div><div><dt>费用</dt><dd>{{ detail.metrics?.costUsd === 'UNKNOWN' ? '未知 · 价格或用量尚不完整' : detail.metrics?.costUsd ?? '未记录' }}</dd></div></dl>
        <el-tabs v-model="tab">
          <el-tab-pane label="节点路径" name="nodes">
            <p v-if="!detail.nodes?.length" class="empty">此运行没有节点记录。历史请求不会补造执行路径。</p>
            <ol v-else class="timeline"><li v-for="(n,index) in detail.nodes" :key="n.id"><span class="number">{{ String(index+1).padStart(2,'0') }}</span><div><div class="node-title"><strong>{{ nodeNames[n.node_id] || n.node_id }}</strong><span>{{ n.duration_ms == null ? '进行中' : `${n.duration_ms} ms` }}</span></div><p class="muted">{{ n.node_id }} → {{ n.next_node_id }} · {{ n.status }}</p><p v-if="n.error_code" class="failure">{{ n.error_code }}</p><details><summary>条件、候选与证据摘要</summary><pre>{{ pretty(n.summary_json) }}</pre></details></div></li></ol>
          </el-tab-pane>
          <el-tab-pane label="模型与工具" name="calls"><h3>模型尝试</h3><pre>{{ pretty(detail.metrics) }}</pre><h3>检索阶段耗时</h3><pre>{{ pretty(detail.retrievalTimings) }}</pre><h3>工具步骤</h3><el-table :data="detail.steps"><el-table-column prop="tool_name" label="步骤"/><el-table-column prop="status" label="结果"/><el-table-column prop="created_at" label="时间"/></el-table></el-tab-pane>
          <el-tab-pane label="检查点" name="checkpoints"><p class="muted">仅查看保存位置与身份摘要；不提供恢复、编辑或重放。</p><el-table :data="checkpoints"><el-table-column prop="sequence_id" label="顺序" width="80"/><el-table-column prop="node_id" label="节点"/><el-table-column prop="next_node_id" label="下一节点"/><el-table-column type="expand"><template #default="{row}"><pre>{{ pretty(row.summary) }}</pre></template></el-table-column></el-table></el-tab-pane>
          <el-tab-pane label="受控原文" name="raw"><p class="muted">每次查看都会记录账号、范围与理由。原文保留 30 天，不包含认证头、密钥或模型内部推理。</p><el-alert v-if="!detail.rawAccessEnabled" title="当前环境未开启原文查看" type="info" :closable="false"/><el-form v-else @submit.prevent="readRaw"><el-form-item label="载荷"><el-select v-model="payloadId" placeholder="选择一次调用或证据" class="full"><el-option v-for="p in detail.payloads" :key="p.id" :label="`${p.kind} · ${p.created_at} · ${p.id.slice(0,8)}`" :value="p.id"/></el-select></el-form-item><el-form-item label="查看理由"><el-input v-model="reason" maxlength="500" placeholder="说明正在排查的问题"/></el-form-item><el-button native-type="submit" type="primary" :disabled="!payloadId || !reason.trim()" :loading="rawLoading">记录审计并查看</el-button></el-form><p v-if="rawError" role="alert" class="failure">{{ rawError }}</p><pre v-if="rawContent">{{ rawContent }}</pre><p v-if="!detail.payloads?.length" class="empty">无可用原文：可能未采集或已过期。</p></el-tab-pane>
          <el-tab-pane label="编排开销" name="overhead"><p class="muted">分别记录图创建与检查点写入。历史请求没有采集时不补造耗时。</p><pre v-if="detail.orchestrationTimings?.length">{{ pretty(detail.orchestrationTimings) }}</pre><p v-else class="empty">此运行没有单独记录编排开销。</p></el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
  </section>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import request from '../api/request'
const rows=ref([]), total=ref(0), page=ref(1), loading=ref(false), error=ref(''), range=ref([])
const filters=ref({runId:'',engine:'',status:'',outcome:''})
const opened=ref(false), detail=ref(null), detailLoading=ref(false), detailError=ref(''), checkpoints=ref([]), tab=ref('nodes')
const payloadId=ref(''), reason=ref(''), rawContent=ref(''), rawLoading=ref(false), rawError=ref('')
const labels={QUEUED:'等待中',RUNNING:'执行中',SUCCEEDED:'已完成',FAILED:'失败',CANCELLED:'已取消',TIMED_OUT:'已超时',INTERRUPTED:'已中断'}
const statuses=Object.keys(labels)
const nodeNames={load_context:'读取上下文',understand_turn:'理解本轮意图',reconcile_conditions:'校验与保存条件',direct_response:'直接回应',diagnose:'条件诊断',retrieve_knowledge:'检索知识',load_project_details:'回查项目身份',plan_recommendation:'规划检索',recall_candidates:'召回候选',review_purpose:'核对项目用途',select_projects:'条件筛选',retrieve_project_evidence:'检索项目证据',prepare_evidence:'准备证据',generate_claims:'生成事实说明',validate_citations:'校验引用',verify_claims:'核验事实支持',assemble_project_cards:'组装项目卡片',generate_advice:'生成实践建议',finalize_response:'整理回答',validate_response:'最终业务校验'}
function pretty(value){try{return JSON.stringify(typeof value==='string'?JSON.parse(value):value,null,2)}catch{return value}}
function unwrap(res){if(res.code!==1)throw new Error(res.msg || '读取失败');return res.data}
async function load(){loading.value=true;error.value='';try{const data=unwrap(await request.get('/admin/customer/runs',{params:{...filters.value,page:page.value,size:20,from:range.value?.[0],until:range.value?.[1]}}));rows.value=data.items;total.value=data.total}catch{error.value='无法加载运行列表，请检查服务后重试。'}finally{loading.value=false}}
function search(){page.value=1;load()}
let detailRequest=0
async function open(row){const ticket=++detailRequest;clearDetail();opened.value=true;detailLoading.value=true;try{const [a,b]=await Promise.all([request.get(`/admin/customer/runs/${row.id}/diagnostics`),request.get(`/admin/customer/runs/${row.id}/checkpoints`)]);if(ticket!==detailRequest)return;detail.value=unwrap(a);checkpoints.value=unwrap(b)}catch{if(ticket===detailRequest)detailError.value='无法加载此运行的诊断信息。'}finally{if(ticket===detailRequest)detailLoading.value=false}}
function clearDetail(){detail.value=null;detailError.value='';rawContent.value='';rawError.value='';rawLoading.value=false;payloadId.value='';reason.value='';tab.value='nodes'}
watch(opened,value=>{if(!value){detailRequest++;clearDetail();detailLoading.value=false}})
async function readRaw(){rawLoading.value=true;rawContent.value='';rawError.value='';const run=detail.value.id,ticket=detailRequest;try{const data=unwrap(await request.post(`/admin/customer/runs/${run}/raw-access`,{payloadId:payloadId.value,reason:reason.value}));if(ticket===detailRequest && detail.value?.id===run)rawContent.value=pretty(data.content_json)}catch{if(ticket===detailRequest)rawError.value='原文不可用、已过期或审计失败。此次未展示内容。'}finally{if(ticket===detailRequest)rawLoading.value=false}}
onMounted(load)
</script>

<style scoped>
.customer-run-drawer{--accent:#c44e13;--ink:#292622;--muted:#78726b;--el-color-primary:#c44e13;color:var(--ink);font-family:Arial,"Microsoft YaHei",sans-serif}.customer-runs{--el-color-primary:#c44e13}
.customer-runs{--accent:#c44e13;--ink:#292622;--muted:#78726b;color:var(--ink);font-variant-numeric:tabular-nums}.run-header{display:flex;align-items:center;justify-content:space-between;gap:24px;margin-bottom:28px}.eyebrow{color:var(--accent);font-size:12px;font-weight:600;letter-spacing:.1em;margin:0 0 10px}h1{font-size:28px;letter-spacing:-.04em;margin:0 0 10px;font-weight:650}.muted{color:var(--muted);font-size:13px;line-height:1.65}.filters{display:flex;gap:12px;flex-wrap:wrap;padding:18px;background:#f5f2ee;border-radius:10px;margin-bottom:22px}.filters>.el-input{width:200px}.filters>.el-select{width:145px}.run-table{margin-bottom:20px}.run-link{background:none;border:0;padding:0;color:var(--accent);text-align:left;cursor:pointer;font-size:12px;font-family:monospace}.run-link:focus-visible{outline:2px solid var(--accent);outline-offset:4px}small{display:block;margin-top:8px;color:var(--muted)}.status{font-size:12px;background:#f1eee9;padding:5px 8px;border-radius:4px}.SUCCEEDED{color:#35715c;background:#edf5ef}.FAILED,.TIMED_OUT,.failure{color:#a23126}.identity{display:flex;gap:16px;align-items:center;flex-wrap:wrap;margin-bottom:24px}.facts{display:grid;grid-template-columns:1fr 1fr;gap:18px;background:#f8f6f3;padding:20px;border-radius:10px}dt{font-size:12px;color:var(--muted);margin-bottom:6px}dd{margin:0;font-size:13px;overflow-wrap:anywhere}.timeline{list-style:none;padding:12px 0}.timeline li{display:grid;grid-template-columns:38px 1fr;gap:16px;padding-bottom:26px}.number{color:var(--accent);border-top:2px solid var(--accent);padding-top:10px;font-size:13px}.node-title{display:flex;justify-content:space-between;gap:12px}.node-title span{color:var(--muted);font-size:12px}summary{font-size:12px;cursor:pointer;color:var(--accent)}pre{background:#f6f4f1;padding:16px;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.65;font-size:12px;max-height:550px;overflow:auto;border-radius:6px}.empty{padding:28px 16px;line-height:1.8;color:var(--muted);font-size:13px}.full{width:100%}@media(max-width:700px){.run-header{align-items:flex-start;flex-direction:column}h1{font-size:23px}.facts{grid-template-columns:1fr}.filters>*{width:100%!important}}
</style>
