<template>
  <main class="device-page">
    <header class="page-head">
      <button type="button" class="back-button" title="返回设置" @click="$router.push('/me/settings')">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <div>
        <span>VOICE AGENT ACCESS</span>
        <h2>智能设备</h2>
        <p>为 OrangeVC 小智连接器签发独立、可撤销的最小权限凭据。</p>
      </div>
    </header>

    <section class="connect-band">
      <div>
        <h3>连接新设备</h3>
        <p>绑定码有效 10 分钟，只能使用一次。</p>
      </div>
      <div class="connect-form">
        <el-input v-model="deviceName" maxlength="64" placeholder="设备名称" />
        <el-button type="primary" :loading="creating" @click="createCode">
          <el-icon><Link /></el-icon>生成绑定码
        </el-button>
      </div>
    </section>

    <section v-if="linkCode" class="code-band" aria-live="polite">
      <div>
        <span>一次性绑定码</span>
        <strong>{{ linkCode.code }}</strong>
        <small>{{ formatTime(linkCode.expiresAt) }} 失效</small>
      </div>
      <button type="button" class="copy-button" title="复制绑定命令" @click="copyCommand">
        <el-icon><CopyDocument /></el-icon>
      </button>
      <code>{{ bindCommand }}</code>
    </section>

    <el-alert v-if="!secureConnection" class="https-alert" type="warning" :closable="false"
      title="当前地址未启用 HTTPS"
      description="公网设备授权只接受 HTTPS；请在本机 127.0.0.1 完成联调，或等待正式域名证书启用后再远程绑定。" />

    <section class="device-list">
      <div class="section-heading">
        <div><span>BOUND DEVICES</span><h3>已绑定设备</h3></div>
        <el-button circle title="刷新" :loading="loading" @click="loadDevices"><el-icon><Refresh /></el-icon></el-button>
      </div>

      <el-empty v-if="!loading && !devices.length" description="暂无已绑定设备" />
      <article v-for="device in devices" :key="device.id" class="device-row">
        <div class="device-icon"><el-icon><Monitor /></el-icon></div>
        <div class="device-main">
          <strong>{{ device.deviceName }}</strong>
          <span>凭据尾号 {{ device.tokenHint }} · {{ statusText(device.status) }}</span>
        </div>
        <div class="device-time">
          <span>最近使用</span><b>{{ formatTime(device.lastUsedAt) }}</b>
        </div>
        <el-button circle type="danger" plain title="撤销设备" @click="revoke(device)">
          <el-icon><Delete /></el-icon>
        </el-button>
      </article>
    </section>

    <section class="task-list">
      <div class="section-heading">
        <div><span>RECENT ACTIONS</span><h3>最近语音任务</h3></div>
        <el-button circle title="刷新任务" :loading="tasksLoading" @click="loadTasks"><el-icon><Refresh /></el-icon></el-button>
      </div>

      <el-empty v-if="!tasksLoading && !tasks.length" description="暂无语音任务" />
      <article v-for="task in tasks" :key="task.id" class="task-row">
        <div class="task-status">
          <el-tag size="small" :type="statusType(task.status)" effect="plain">{{ statusText(task.status) }}</el-tag>
          <span>{{ actionText(task.actionKey) }}</span>
        </div>
        <div class="task-main">
          <strong>{{ task.actionSummary }}</strong>
          <span>{{ task.spokenSummary || '暂无结果说明' }}</span>
        </div>
        <div class="task-meta">
          <b>{{ task.quotaCost || 0 }} 点</b>
          <span>{{ formatTime(task.createTime) }}</span>
          <el-button v-if="task.result" link type="primary" @click="showResult(task)">查看结果</el-button>
          <el-button v-if="terminalStatuses.includes(task.status)" link type="danger"
            @click="removeTask(task)">删除记录</el-button>
        </div>
      </article>
    </section>

    <el-dialog v-model="resultVisible" :title="selectedTask?.actionSummary || '任务结果'" width="min(760px, 92vw)">
      <div class="result-meta">
        <el-tag size="small" :type="statusType(selectedTask?.status)" effect="plain">
          {{ statusText(selectedTask?.status) }}
        </el-tag>
        <span>{{ actionText(selectedTask?.actionKey) }}</span>
      </div>
      <pre class="result-content">{{ resultText }}</pre>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Connection as Link, CopyDocument, Delete, Monitor, Refresh } from '@element-plus/icons-vue'
import {
  createAgentLinkCode, deleteAgentTask, listAgentDevices, listAgentTasks, revokeAgentDevice
} from '../api/agentDevices'

const deviceName = ref('小智客户端')
const linkCode = ref(null)
const devices = ref([])
const tasks = ref([])
const creating = ref(false)
const loading = ref(false)
const tasksLoading = ref(false)
const resultVisible = ref(false)
const selectedTask = ref(null)
const terminalStatuses = ['SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED']
const configuredApiBase = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
const agentBaseUrl = new URL(configuredApiBase, window.location.origin).toString().replace(/\/$/, '')
const agentBase = new URL(agentBaseUrl)
const secureConnection = agentBase.protocol === 'https:' || ['127.0.0.1', 'localhost', '::1'].includes(agentBase.hostname)
const bindCommand = computed(() => linkCode.value
  ? `python -m orangevc_xiaozhi_connector configure-orangejd --base-url ${agentBaseUrl} --code ${linkCode.value.code}`
  : '')

const createCode = async () => {
  creating.value = true
  try {
    const response = await createAgentLinkCode(deviceName.value.trim() || '小智客户端')
    linkCode.value = response.data
    ElMessage.success('绑定码已生成')
  } finally { creating.value = false }
}

const loadDevices = async () => {
  loading.value = true
  try {
    const response = await listAgentDevices()
    devices.value = response.data || []
  } finally { loading.value = false }
}

const loadTasks = async () => {
  tasksLoading.value = true
  try {
    const response = await listAgentTasks()
    tasks.value = response.data || []
  } finally { tasksLoading.value = false }
}

const copyCommand = async () => {
  await navigator.clipboard.writeText(bindCommand.value)
  ElMessage.success('绑定命令已复制')
}

const revoke = async (device) => {
  await ElMessageBox.confirm(`撤销“${device.deviceName}”后，该设备将立即失去 OrangeVC 访问权限。`, '撤销设备', { type: 'warning' })
  await revokeAgentDevice(device.id)
  ElMessage.success('设备授权已撤销')
  loadDevices()
}

const removeTask = async (task) => {
  await ElMessageBox.confirm('删除后将无法在网页中恢复这条任务记录。', '删除任务记录', { type: 'warning' })
  await deleteAgentTask(task.id)
  if (selectedTask.value?.id === task.id) resultVisible.value = false
  await loadTasks()
  ElMessage.success('任务记录已删除')
}

const statusLabels = {
  ACTIVE: '有效', REVOKED: '已撤销', PREPARED: '待确认', QUEUED: '排队中',
  RUNNING: '执行中', SUCCEEDED: '已完成', FAILED: '失败', EXPIRED: '已过期', CANCELLED: '已取消'
}
const actionLabels = {
  'quota.balance.read': '配额查询',
  'launch_check.project.list': '上线体检列表',
  'launch_check.project.create': '创建上线体检',
  'launch_check.stage.generate': '生成体检阶段',
  'open_source.issue.list': '发现 Issue',
  'open_source.issue.detail': 'Issue 详情',
  'open_source.issue.job_match': 'Issue 岗位匹配',
  'open_source.issue.guide': '贡献指南',
  'trend.analysis.generate': '趋势分析',
  'trend.profile.generate': '智能趋势分析',
  'plan.phase.generate': '方案设计',
  'agent.task.list': '最近语音任务',
  'notification.unread.read': '未读通知',
  'leaderboard.github.list': '榜单排行'
}
const statusText = (status) => statusLabels[status] || status || '未知'
const actionText = (action) => actionLabels[action] || action
const statusType = (status) => ({ SUCCEEDED: 'success', FAILED: 'danger', RUNNING: 'warning', QUEUED: 'warning', PREPARED: 'info' }[status] || 'info')
const formatTime = (value) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '从未'
const resultText = computed(() => {
  const result = selectedTask.value?.result
  if (!result) return '暂无结果'
  if (typeof result === 'string') return result
  if (typeof result.content === 'string') return result.content
  if (typeof result.guide?.content === 'string') return result.guide.content
  return JSON.stringify(result, null, 2)
})
const showResult = (task) => {
  selectedTask.value = task
  resultVisible.value = true
}

onMounted(() => { loadDevices(); loadTasks() })
</script>

<style scoped>
.device-page { max-width: 980px; margin: 0 auto; }
.page-head { display: flex; align-items: flex-start; gap: 16px; padding-bottom: 24px; border-bottom: 1px solid var(--border-orange); }
.page-head span, .section-heading span, .code-band span { color: var(--brand-strong); font: 800 11px/1 var(--font-mono); }
.page-head h2 { margin: 7px 0 5px; font-size: 30px; }
.page-head p, .connect-band p { margin: 0; color: var(--text-secondary); }
.back-button, .copy-button { display: grid; place-items: center; width: 38px; height: 38px; border: 1px solid var(--border-orange); background: #fff; color: var(--brand-strong); cursor: pointer; }
.connect-band { display: grid; grid-template-columns: 1fr minmax(360px, 1.2fr); gap: 28px; align-items: end; padding: 28px 0; }
.connect-band h3, .section-heading h3 { margin: 0 0 7px; font-size: 20px; }
.connect-form { display: grid; grid-template-columns: 1fr auto; gap: 10px; }
.code-band { display: grid; grid-template-columns: 1fr auto; gap: 14px 20px; padding: 22px; color: #fff7ed; background: #431d0c; }
.code-band > div { display: grid; gap: 7px; }
.code-band strong { font: 800 30px/1 var(--font-mono); letter-spacing: 0; }
.code-band small { color: #fed7aa; }
.code-band code { grid-column: 1 / -1; overflow-x: auto; padding: 12px; border: 1px solid rgba(253,186,116,.3); color: #ffedd5; white-space: nowrap; }
.copy-button { border-color: rgba(253,186,116,.4); background: transparent; color: #fff7ed; }
.https-alert { margin-top: 16px; }
.device-list { padding-top: 32px; }
.task-list { padding-top: 42px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.device-row { display: grid; grid-template-columns: 42px minmax(0, 1fr) 190px 40px; gap: 14px; align-items: center; min-height: 76px; padding: 12px 16px; border-top: 1px solid var(--border-orange); }
.device-row:last-child { border-bottom: 1px solid var(--border-orange); }
.device-icon { display: grid; place-items: center; width: 40px; height: 40px; color: var(--brand-strong); background: var(--brand-light); }
.device-main, .device-time { display: grid; gap: 5px; min-width: 0; }
.device-main span, .device-time span { color: var(--text-muted); font-size: 12px; }
.device-main strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.device-time b { font-size: 13px; font-weight: 600; }
.task-row { display: grid; grid-template-columns: 150px minmax(0, 1fr) 150px; gap: 18px; align-items: center; min-height: 86px; padding: 14px 16px; border-top: 1px solid var(--border-orange); }
.task-row:last-child { border-bottom: 1px solid var(--border-orange); }
.task-status, .task-main, .task-meta { display: grid; gap: 6px; min-width: 0; }
.task-status { justify-items: start; }
.task-status span, .task-main span, .task-meta span { color: var(--text-muted); font-size: 12px; }
.task-main strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-meta { justify-items: end; }
.task-meta b { color: var(--brand-strong); font-size: 13px; }
.result-meta { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; color: var(--text-secondary); }
.result-content { max-height: 58vh; margin: 0; padding: 18px; overflow: auto; border: 1px solid var(--border-orange); background: #fffaf5; color: var(--text-primary); font: 14px/1.75 var(--font-sans); white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 720px) {
  .connect-band { grid-template-columns: 1fr; }
  .connect-form { grid-template-columns: 1fr; }
  .device-row { grid-template-columns: 42px minmax(0, 1fr) 40px; }
  .device-time { display: none; }
  .task-row { grid-template-columns: 1fr auto; }
  .task-status { grid-column: 1 / -1; grid-template-columns: auto 1fr; align-items: center; }
  .task-meta { justify-items: end; }
}
</style>
