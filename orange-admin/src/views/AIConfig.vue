<template>
  <div class="config-page">
    <header class="page-heading">
      <div>
        <h3>AI 调用设置</h3>
        <p>互动工具通过全局并发门禁、Gitee 独占槽位和 DeepSeek 兜底统一调度；密钥原文不会由管理接口返回。</p>
      </div>
      <el-button :loading="poolLoading" @click="loadPool">刷新运行状态</el-button>
    </header>

    <el-alert
      v-if="!encryptionReady"
      title="尚未配置 AI_CONFIG_ENCRYPTION_KEY，动态 API Key 暂时不能保存"
      type="warning"
      :closable="false"
      show-icon
    />

    <section class="pool-panel" v-loading="poolLoading">
      <div class="pool-summary">
        <div>
          <strong>互动 AI 调用池</strong>
          <span>{{ pool.enabled ? '已启用' : '安全旁路（沿用旧配置）' }}</span>
        </div>
        <el-tag :type="pool.enabled ? 'success' : 'warning'">
          {{ pool.globalRunning || 0 }} / {{ pool.globalMax || 0 }} 运行中
        </el-tag>
      </div>
      <el-alert
        v-if="!pool.enabled"
        title="调用池尚未启用；完成全部槽位连通测试和灰度验证后，再在服务器设置 AI_TOOL_POOL_ENABLED=true。"
        type="warning"
        :closable="false"
        show-icon
      />
      <div class="pool-stats">
        <span>Gitee：{{ pool.giteeRunning || 0 }}</span>
        <span>DeepSeek 兜底：{{ pool.fallbackRunning || 0 }}</span>
        <span>已配置槽位：{{ pool.configuredSlots || 0 }}</span>
        <span>全局上限：{{ pool.globalMax || 0 }}</span>
        <span>
          自动重试：{{ pool.autoRetryEnabled
            ? (pool.autoRetryMaxAttempts > 0 ? `最多 ${pool.autoRetryMaxAttempts} 次` : '持续到成功')
            : '未启用' }}
        </span>
      </div>
      <el-table :data="pool.slots || []" size="small" empty-text="尚未配置 Gitee Key">
        <el-table-column prop="slotNo" label="槽位" width="72" />
        <el-table-column prop="fingerprint" label="Key 指纹" min-width="140" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="slotTagType(row.status)">{{ slotStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="right">
          <template #default="{ row }">
            <el-button size="small" :loading="testingSlot === row.slotNo" @click="testSlot(row)">短请求测试</el-button>
            <el-button
              size="small"
              :type="row.status === 'DISABLED' ? 'success' : 'danger'"
              plain
              @click="toggleSlot(row)"
            >
              {{ row.status === 'DISABLED' ? '启用' : '停用' }}
            </el-button>
            <el-button
              v-if="row.status === 'INVALID' || row.status === 'THROTTLED'"
              size="small"
              plain
              @click="resetSlot(row)"
            >恢复状态</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="recent-heading">最近调用（不记录 Prompt、响应正文或密钥原文）</div>
      <el-table :data="recentCalls" size="small" max-height="280" empty-text="暂无调用记录">
        <el-table-column prop="startedAt" label="开始时间" min-width="170" />
        <el-table-column label="调用用户" min-width="175">
          <template #default="{ row }">
            <div class="caller-cell">
              <strong>{{ row.userDisplayName || '系统任务' }}</strong>
              <span v-if="row.accountId">
                {{ row.username ? `@${row.username}` : '' }} · ID {{ row.accountId }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="purpose" label="用途" min-width="165" />
        <el-table-column prop="attemptNo" label="尝试" width="68">
          <template #default="{ row }">第 {{ row.attemptNo || 1 }} 次</template>
        </el-table-column>
        <el-table-column prop="provider" label="提供方" width="105" />
        <el-table-column prop="slotNo" label="槽位" width="68" />
        <el-table-column label="结果" width="105">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'SUCCEEDED' ? 'success' : row.status === 'RETRY_WAIT' ? 'warning' : 'danger'">
              {{ row.status === 'RETRY_WAIT' ? '自动重试中' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时(ms)" width="105" />
        <el-table-column prop="errorSummary" label="错误摘要" min-width="160" show-overflow-tooltip />
      </el-table>
    </section>

    <div class="config-grid" v-loading="loading">
      <section v-for="item in editableItems" :key="item.key" class="config-panel">
        <div class="panel-heading">
          <div>
            <strong>{{ item.title }}</strong>
            <span>{{ item.data.source === 'REDIS' ? 'Redis 动态配置' : '环境变量兜底' }}</span>
          </div>
          <el-tag size="small" :type="item.data.source === 'REDIS' ? 'success' : 'info'">
            {{ item.data.version || 'env' }}
          </el-tag>
        </div>

        <el-form label-position="top">
          <el-form-item label="Endpoint">
            <el-input v-model="item.edit.endpoint" :disabled="!item.editing" />
          </el-form-item>
          <el-form-item label="API Key">
            <el-input
              v-model="item.edit.apiKey"
              :disabled="!item.editing"
              :placeholder="item.data.apiKey || '未配置'"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item label="Model">
            <el-input v-model="item.edit.model" :disabled="!item.editing" />
          </el-form-item>
        </el-form>

        <div class="panel-actions">
          <el-button :loading="item.testing" @click="test(item)">测试连接</el-button>
          <el-button v-if="!item.editing" type="primary" @click="item.editing = true">编辑</el-button>
          <template v-else>
            <el-button @click="cancel(item)">取消</el-button>
            <el-button type="primary" :loading="item.saving" @click="save(item)">保存新版本</el-button>
          </template>
        </div>
      </section>

      <section class="config-panel fixed-panel">
        <div class="panel-heading">
          <div><strong>RAG 知识库 Embedding</strong><span>启动时固定配置</span></div>
          <el-tag size="small" type="warning">需重启</el-tag>
        </div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="Endpoint">{{ embedding.endpoint || '未配置' }}</el-descriptions-item>
          <el-descriptions-item label="API Key">{{ embedding.apiKey || '未配置' }}</el-descriptions-item>
          <el-descriptions-item label="Model">{{ embedding.model || '未配置' }}</el-descriptions-item>
          <el-descriptions-item label="来源">服务器环境变量</el-descriptions-item>
        </el-descriptions>
        <p class="fixed-note">为避免向量维度不一致，管理端不能动态切换 Embedding。修改服务器环境变量后，需要重启应用并验收向量检索。</p>
        <el-button :loading="embeddingTesting" @click="testEmbedding">检查配置状态</el-button>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'

const loading = ref(false)
const poolLoading = ref(false)
const pool = ref({})
const recentCalls = ref([])
const testingSlot = ref(null)
const embeddingTesting = ref(false)
const embedding = ref({})
const items = reactive([
  { key: 'toolFallback', title: '互动工具 DeepSeek 兜底', data: {}, edit: {}, editing: false, saving: false, testing: false },
  { key: 'text', title: '定时文本生成与翻译', data: {}, edit: {}, editing: false, saving: false, testing: false },
  { key: 'audit', title: 'AI 托管审核', data: {}, edit: {}, editing: false, saving: false, testing: false },
])
const editableItems = computed(() => items)
const encryptionReady = computed(() => items.every(item => item.data.encryptionReady !== false))

const applyData = (item, data) => {
  item.data = data || {}
  item.edit = { endpoint: data?.endpoint || '', model: data?.model || '', apiKey: '' }
}

const loadData = async () => {
  loading.value = true
  try {
    const response = await request.get('/admin/ai-config')
    for (const item of items) applyData(item, response.data?.[item.key])
    embedding.value = response.data?.embedding || {}
  } finally {
    loading.value = false
  }
}

const loadPool = async () => {
  poolLoading.value = true
  try {
    const [overview, calls] = await Promise.all([
      request.get('/admin/ai-tool-pool'),
      request.get('/admin/ai-tool-pool/calls', { params: { limit: 50 } }),
    ])
    pool.value = overview.data || {}
    recentCalls.value = calls.data || []
  } finally {
    poolLoading.value = false
  }
}

const testSlot = async row => {
  testingSlot.value = row.slotNo
  try {
    const response = await request.post(`/admin/ai-tool-pool/slots/${row.slotNo}/test`, {}, { timeout: 130000 })
    ElMessage.success(response.data?.message || `槽位 ${row.slotNo} 连接成功`)
  } finally {
    testingSlot.value = null
    await loadPool()
  }
}

const toggleSlot = async row => {
  const action = row.status === 'DISABLED' ? 'enable' : 'disable'
  await request.post(`/admin/ai-tool-pool/slots/${row.slotNo}/${action}`)
  ElMessage.success(`槽位 ${row.slotNo} 已${action === 'enable' ? '启用' : '停用'}`)
  await loadPool()
}

const resetSlot = async row => {
  await request.post(`/admin/ai-tool-pool/slots/${row.slotNo}/reset`)
  ElMessage.success(`槽位 ${row.slotNo} 状态已恢复`)
  await loadPool()
}

const slotTagType = status => ({
  AVAILABLE: 'success',
  BUSY: 'warning',
  THROTTLED: 'warning',
  INVALID: 'danger',
  DISABLED: 'danger',
}[status] || 'info')
const slotStatusText = status => ({
  AVAILABLE: '可用',
  BUSY: '调用中',
  THROTTLED: '限流中',
  INVALID: '密钥失效',
  DISABLED: '已停用',
}[status] || status || '未知')

const cancel = item => {
  item.editing = false
  applyData(item, item.data)
}

const save = async item => {
  item.saving = true
  try {
    await request.post('/admin/ai-config/save', {
      prefix: `ai:${item.key}`,
      endpoint: item.edit.endpoint,
      model: item.edit.model,
      apiKey: item.edit.apiKey,
    })
    ElMessage.success('已保存为新配置版本，新任务将使用该版本')
    item.editing = false
    await loadData()
  } finally {
    item.saving = false
  }
}

const test = async item => {
  item.testing = true
  try {
    const response = await request.post('/admin/ai-config/test', { type: item.key }, { timeout: 130000 })
    ElMessage.success(response.data?.message || '连接成功')
  } finally {
    item.testing = false
  }
}

const testEmbedding = async () => {
  embeddingTesting.value = true
  try {
    const response = await request.post('/admin/ai-config/test', { type: 'embedding' })
    ElMessage.success(response.data?.message || '检查完成')
  } finally {
    embeddingTesting.value = false
  }
}

onMounted(() => Promise.all([loadData(), loadPool()]))
</script>

<style scoped>
.config-page { display: grid; gap: 16px; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-heading h3 { margin: 0; color: #303133; font-size: 18px; }
.page-heading p { margin: 5px 0 0; color: #727985; font-size: 13px; }
.pool-panel { padding: 18px; border: 1px solid #e2e5ea; border-radius: 8px; background: #fff; display: grid; gap: 14px; }
.pool-summary { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.pool-summary strong, .pool-summary span { display: block; }
.pool-summary strong { color: #303133; font-size: 15px; }
.pool-summary span { margin-top: 4px; color: #8b929d; font-size: 12px; }
.pool-stats { display: flex; flex-wrap: wrap; gap: 10px 24px; color: #606975; font-size: 13px; }
.recent-heading { margin-top: 4px; color: #303133; font-size: 14px; font-weight: 600; }
.caller-cell strong, .caller-cell span { display: block; }
.caller-cell strong { color: #303133; font-size: 12px; }
.caller-cell span { margin-top: 2px; color: #9098a3; font-size: 11px; }
.config-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.config-panel { min-width: 0; padding: 18px; border: 1px solid #e2e5ea; border-radius: 8px; background: #fff; }
.panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.panel-heading strong, .panel-heading span { display: block; }
.panel-heading strong { color: #303133; font-size: 15px; }
.panel-heading span { margin-top: 4px; color: #8b929d; font-size: 12px; }
.panel-actions { display: flex; justify-content: flex-end; gap: 8px; min-height: 32px; }
.fixed-note { margin: 14px 0; color: #606975; font-size: 12px; line-height: 1.65; }
@media (max-width: 1100px) { .config-grid { grid-template-columns: 1fr 1fr; } }
@media (max-width: 720px) { .config-grid { grid-template-columns: 1fr; } }
</style>
