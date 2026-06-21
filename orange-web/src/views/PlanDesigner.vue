<template>
  <div class="plan-app">
    <div class="plan-main">
      <!-- 输入区 -->
      <div v-if="!started" class="plan-intro">
        <div class="intro-header">
          <span class="intro-icon">🍊</span>
          <div style="flex:1">
            <h3 style="margin:0;color:#5d4037">方案设计</h3>
            <p style="margin:4px 0 0;font-size:13px;color:#bcaaa4">AI 驱动五阶段项目规划，从想法到可执行计划</p>
          </div>
          <el-popover placement="bottom-end" width="320" trigger="click" v-if="hasToken">
            <template #reference>
              <el-button size="small" text>📋 记录 ({{ planHistory.length }})</el-button>
            </template>
            <div style="max-height:300px;overflow:auto">
              <div v-if="!planHistory.length" style="text-align:center;color:#bcaaa4;padding:20px">暂无记录</div>
              <div v-for="(h,i) in planHistory" :key="i" style="padding:8px 0;border-bottom:1px solid #f5f2ee;cursor:pointer;display:flex;justify-content:space-between" @click="loadPlan(h)">
                <div style="flex:1;min-width:0;font-size:12px">
                  <span style="color:#5d4037;font-weight:500">{{ (h.summary||'').substring(0,40) }}</span>
                  <span style="color:#bcaaa4;margin-left:6px;font-size:11px">{{ (h.createTime||'').substring(0,16) }}</span>
                </div>
                <el-button size="small" text type="danger" @click.stop="delPlanHistory(i,h.id)">✕</el-button>
              </div>
            </div>
            <div v-if="planHistory.length" style="margin-top:8px;text-align:right">
              <el-button size="small" type="danger" text @click="clearPlanHistory">清空全部</el-button>
            </div>
          </el-popover>
        </div>
        <el-input v-model="summary" type="textarea" :rows="4" placeholder="描述你的项目想法，越详细越好..." size="large" :autosize="{minRows:4,maxRows:12}" />
        <el-input v-model="extra" type="textarea" :rows="2" placeholder="补充信息（可选）：技术偏好、时间限制、特殊需求..." style="margin-top:12px" :autosize="{minRows:2,maxRows:8}" />
        <button class="orange-btn start-btn" @click="startPlan" :disabled="!summary.trim()">🍊 开始规划</button>
      </div>

      <!-- 阶段导航 + 内容 -->
      <template v-else>
        <div v-if="allDone && hasToken" style="text-align:center;margin-bottom:16px">
          <button class="orange-btn save-btn" @click="savePlan">💾 保存方案到我的记录</button>
        </div>
        <div class="phase-nav">
          <div v-for="(p, i) in phases" :key="i"
            class="phase-nav-item" :class="{ active: currentPhase === i+1, done: p.done, generating: p.generating }"
            @click="switchPhase(i+1)">
            <div class="phase-dot">{{ p.done ? '✓' : i+1 }}</div>
            <div>
              <div class="phase-nav-label">{{ p.title }}</div>
              <div class="phase-nav-status" v-if="p.generating">生成中...</div>
              <div class="phase-nav-status" v-else-if="!p.done && currentPhase !== i+1">待生成</div>
            </div>
          </div>
        </div>

        <div class="phase-content">
          <div class="phase-title">{{ phases[currentPhase-1]?.title }}</div>
          <div class="phase-subtitle">{{ phaseSubtitles[currentPhase-1] }}</div>

          <!-- 未生成状态 -->
          <div v-if="!phases[currentPhase-1]?.done && !phases[currentPhase-1]?.generating" class="card">
            <p style="color:#909399;margin:0">点击下方按钮，AI 将基于项目描述生成此阶段的分析内容。</p>
            <button class="orange-btn" @click="generatePhase(currentPhase)" style="margin-top:16px">🍊 生成本阶段</button>
          </div>

          <!-- 生成中 -->
          <div v-if="phases[currentPhase-1]?.generating" class="card" style="text-align:center;padding:40px">
            <span class="generating-spinner">🍊</span>
            <p style="color:#e65100;margin-top:8px">AI 正在分析和生成...</p>
          </div>

          <!-- 已生成 -->
          <div v-if="phases[currentPhase-1]?.done" class="card result-card">
            <div v-if="!editing" v-html="renderedContent" style="line-height:1.8;font-size:14px" @dblclick="startEdit"></div>
            <el-input v-else v-model="editContent" type="textarea" :rows="12" style="margin-bottom:12px" />
            <div style="margin-top:16px;display:flex;gap:8px">
              <template v-if="!editing">
                <button class="orange-btn" @click="generatePhase(currentPhase)">🔄 重新生成</button>
                <button class="nav-btn" @click="startEdit">✏️ 编辑</button>
                <button v-if="currentPhase < 4" class="nav-btn next" @click="switchPhase(currentPhase+1); autoGenerateNext()">下一步 →</button>
              </template>
              <template v-else>
                <button class="orange-btn" @click="saveEdit">💾 保存修改</button>
                <button class="nav-btn" @click="cancelEdit">取消</button>
              </template>
            </div>
          </div>

          <!-- 报错 -->
          <div v-if="phaseError" class="card" style="border-left:4px solid #dc2626">
            <p style="color:#dc2626">{{ phaseError }}</p>
            <button class="orange-btn" @click="generatePhase(currentPhase)" style="margin-top:8px">重试</button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import request from '../api/request'

const summary = ref('')
const extra = ref('')
const started = ref(false)
const currentPhase = ref(1)
const phaseError = ref('')

const phases = reactive([
  { title: '需求理解', done: false, generating: false, content: '' },
  { title: '成功标准', done: false, generating: false, content: '' },
  { title: '约束风险', done: false, generating: false, content: '' },
  { title: '任务计划', done: false, generating: false, content: '' }
])
const phaseSubtitles = [
  '准确复述需求，识别核心模块，划定 MVP 边界',
  '定义可验证的成功标准和完成证据',
  '识别技术约束、业务风险和明确不做的事',
  '拆解为可落地的步骤，生成执行计划'
]

const renderedContent = computed(() => {
  const c = phases[currentPhase.value - 1]?.content || ''
  return marked.parse(c)
})

const startPlan = () => {
  if (!summary.value.trim()) return
  started.value = true
  setTimeout(() => generatePhase(1), 200)
}

const autoGenerateNext = () => {
  const p = phases[currentPhase.value - 1]
  if (p && !p.done) generatePhase(currentPhase.value)
}

const getPrevContents = (phase) => {
  const parts = []
  for (let i = 0; i < phase - 1; i++) {
    if (phases[i].done) {
      parts.push(`【阶段${i+1}：${phases[i].title}】\n${phases[i].content}`)
    }
  }
  return parts.join('\n\n')
}

const generatePhase = async (n) => {
  const p = phases[n - 1]
  p.generating = true; phaseError.value = ''
  try {
    const r = await request.post('/app/plan/generate', {
      phase: n,
      summary: summary.value,
      extra: extra.value,
      prevContents: getPrevContents(n)
    }, { timeout: 0 })
    if (r.code === 1 && r.data?.content) {
      p.content = r.data.content
      p.done = true
      // 自动生成下一阶段
      if (n < 4) {
        setTimeout(() => {
          phases[n].done || generatePhase(n + 1)
        }, 500)
      }
    } else {
      phaseError.value = r.msg || '生成失败'
    }
  } catch (e) {
    phaseError.value = '请求失败，请检查网络连接'
  } finally { p.generating = false }
}

const editing = ref(false)
const editContent = ref('')

const startEdit = () => {
  editContent.value = phases[currentPhase.value - 1].content
  editing.value = true
}
const saveEdit = () => {
  phases[currentPhase.value - 1].content = editContent.value
  editing.value = false
}
const cancelEdit = () => { editing.value = false }

const hasToken = computed(() => !!sessionStorage.getItem('appToken'))
const planHistory = ref([])
const allDone = computed(() => phases.every(p => p.done))

const loadPlanHistory = async () => {
  if (!hasToken.value) return
  try { const r = await request.get('/app/plan/history'); if (r.code === 1) planHistory.value = r.data } catch {}
}
const savePlan = async () => {
  if (!hasToken.value || !allDone.value) return
  try {
    await request.post('/app/plan/history/save', {
      summary: summary.value, phase1: phases[0].content, phase2: phases[1].content,
      phase3: phases[2].content, phase4: phases[3].content
    })
    loadPlanHistory()
    ElMessage.success('方案已保存')
  } catch { ElMessage.error('保存失败') }
}
const loadPlan = (h) => {
  summary.value = h.summary || ''
  started.value = true
  if (h.phase1) { phases[0].content = h.phase1; phases[0].done = true }
  if (h.phase2) { phases[1].content = h.phase2; phases[1].done = true }
  if (h.phase3) { phases[2].content = h.phase3; phases[2].done = true }
  if (h.phase4) { phases[3].content = h.phase4; phases[3].done = true }
  currentPhase.value = 1
}
const delPlanHistory = async (i, id) => {
  try { await request.delete('/app/plan/history/' + id); loadPlanHistory() } catch {}
}
const clearPlanHistory = async () => {
  try { await request.delete('/app/plan/history/clear'); planHistory.value = [] } catch {}
}

onMounted(() => {
  loadPlanHistory()
  const imported = sessionStorage.getItem('plan_import')
  if (imported) {
    summary.value = imported
    sessionStorage.removeItem('plan_import')
  }
})

const switchPhase = (n) => {
  currentPhase.value = n
  phaseError.value = ''
  editing.value = false
}
</script>

<style scoped>
.plan-app { display: flex; min-height: calc(100vh - 136px); background: #fff8f0; border-radius: 16px; overflow: hidden; }
.plan-main { flex: 1; padding: 24px 32px; max-width: 900px; margin: 0 auto; overflow-y: auto; }

/* Intro */
.plan-intro { max-width: 640px; margin: 40px auto; }
.intro-header { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.intro-icon { font-size: 32px; }

/* Phase nav */
.phase-nav { display: flex; gap: 0; margin-bottom: 24px; border-bottom: 1px solid #ffe0b2; padding-bottom: 12px; }
.phase-nav-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 8px; cursor: pointer; flex: 1; font-size: 13px; transition: all .15s; }
.phase-nav-item:hover { background: #fff3e0; }
.phase-nav-item.active { background: #e65100; color: #fff; }
.phase-nav-item.done { color: #67C23A; }
.phase-nav-item.generating { color: #e65100; }
.phase-dot { width: 24px; height: 24px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; flex-shrink: 0; background: #ffe0b2; color: #e65100; }
.phase-nav-item.active .phase-dot { background: #fff; color: #e65100; }
.phase-nav-item.done .phase-dot { background: #67C23A; color: #fff; }
.phase-nav-label { font-weight: 600; }
.phase-nav-status { font-size: 11px; opacity: .7; }

.phase-content { max-width: 780px; }
.phase-title { font-size: 20px; font-weight: 700; color: #5d4037; }
.phase-subtitle { font-size: 13px; color: #bcaaa4; margin-bottom: 20px; }

.card { background: #fff; border-radius: 12px; padding: 20px 24px; box-shadow: 0 1px 6px rgba(0,0,0,.04); margin-bottom: 16px; border-left: 4px solid #e65100; }
.result-card { border-left-color: #ff9800; }

.orange-btn { background: linear-gradient(135deg, #e65100, #ff9800); color: #fff; border: none; border-radius: 24px; padding: 10px 24px; font-size: 14px; font-weight: 600; cursor: pointer; transition: all .2s; box-shadow: 0 2px 8px rgba(230,81,0,.2); }
.orange-btn:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(230,81,0,.3); }
.orange-btn:disabled { opacity: .5; cursor: not-allowed; }
.start-btn { padding: 14px 32px; font-size: 16px; margin-top: 16px; width: 100%; }

.nav-btn { background: #fff; color: #e65100; border: 1px solid #ffe0b2; border-radius: 24px; padding: 10px 20px; cursor: pointer; font-size: 13px; }
.nav-btn:hover { background: #fff3e0; }

.generating-spinner { font-size: 36px; display: inline-block; animation: spin 1.5s ease-in-out infinite; }
@keyframes spin { 0%,100% { transform: scale(1); } 50% { transform: scale(1.2) rotate(10deg); } }
</style>
