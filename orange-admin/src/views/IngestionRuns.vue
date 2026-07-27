<template>
  <div class="runs-page">
    <header class="page-heading">
      <div><h3>抓取与翻译任务</h3><p>统一查看榜单、Issue 和 README 的执行进度、失败项与重试记录。</p></div>
      <div class="heading-actions">
        <el-button
          type="primary"
          :icon="RefreshRight"
          :loading="leaderboardRefreshing"
          @click="refreshLeaderboard"
        >
          刷新一次榜单
        </el-button>
        <el-button :icon="Refresh" :loading="loading" @click="loadRuns">刷新状态</el-button>
      </div>
    </header>

    <section class="toolbar">
      <el-select v-model="filters.pipelineType" clearable placeholder="任务类型" @change="search">
        <el-option v-for="option in pipelineOptions" :key="option.value" :label="option.label" :value="option.value" />
      </el-select>
      <el-select v-model="filters.status" clearable placeholder="运行状态" @change="search">
        <el-option v-for="status in statusOptions" :key="status" :label="statusText(status)" :value="status" />
      </el-select>
      <el-dropdown @command="startRun">
        <el-button type="primary">新建任务<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="option in pipelineOptions" :key="option.value" :command="option.value">
              {{ option.label }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </section>

    <el-table :data="records" v-loading="loading" class="runs-table" empty-text="暂无运行记录">
      <el-table-column label="任务" min-width="210">
        <template #default="{ row }">
          <div class="run-title"><strong>{{ pipelineText(row.pipelineType) }}</strong><span>#{{ row.id }} · {{ row.triggerSource }}</span></div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="进度" min-width="190">
        <template #default="{ row }">
          <el-progress :percentage="Number(row.progress || 0)" :status="row.status === 'FAILED' ? 'exception' : undefined" />
          <small>{{ row.successCount || 0 }} 成功 · {{ row.failedCount || 0 }} 失败 · {{ row.skippedCount || 0 }} 跳过</small>
        </template>
      </el-table-column>
      <el-table-column label="配置版本" min-width="145"><template #default="{ row }">{{ row.configVersion || 'env' }}</template></el-table-column>
      <el-table-column label="创建时间" width="170"><template #default="{ row }">{{ formatTime(row.createTime) }}</template></el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="showDetail(row)">详情</el-button>
          <el-button v-if="canRetry(row)" text @click="retryRun(row)">重试</el-button>
          <el-button v-if="canCancel(row)" text type="danger" @click="cancelRun(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination-row"><el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="loadRuns" /></div>

    <el-drawer v-model="detailVisible" title="运行详情" size="min(760px, 96vw)">
      <template v-if="detail.run">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务">{{ pipelineText(detail.run.pipelineType) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(detail.run.status) }}</el-descriptions-item>
          <el-descriptions-item label="配置版本">{{ detail.run.configVersion || 'env' }}</el-descriptions-item>
          <el-descriptions-item label="触发来源">{{ detail.run.triggerSource }}</el-descriptions-item>
          <el-descriptions-item label="错误摘要" :span="2">{{ detail.run.errorSummary || '无' }}</el-descriptions-item>
        </el-descriptions>
        <h4>执行明细</h4>
        <el-table :data="detail.items || []" size="small" empty-text="暂无明细">
          <el-table-column prop="stage" label="阶段" width="110" />
          <el-table-column prop="itemKey" label="项目" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column>
          <el-table-column prop="attempts" label="次数" width="70" />
          <el-table-column prop="lastError" label="错误" min-width="240" show-overflow-tooltip />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ArrowDown, Refresh, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'

const pipelineOptions = [
  { value: 'LEADERBOARD_FULL', label: 'GitHub 榜单完整刷新' },
  { value: 'ISSUE_FULL', label: 'Issue 完整抓取' },
  { value: 'ISSUE_VERIFY', label: 'Issue 状态核验' },
  { value: 'ISSUE_TRANSLATE', label: 'Issue 翻译' },
  { value: 'README_RETRY', label: 'README 失败恢复' },
]
const statusOptions = ['PENDING', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED', 'PARTIAL_FAILED', 'FAILED', 'CANCELLED']
const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(false)
const leaderboardRefreshing = ref(false)
const filters = reactive({ pipelineType: '', status: '' })
const detailVisible = ref(false)
const detail = reactive({ run: null, items: [] })

const pipelineText = value => pipelineOptions.find(item => item.value === value)?.label || value
const statusText = value => ({ PENDING: '等待执行', RUNNING: '执行中', RETRY_WAIT: '等待重试', SUCCEEDED: '成功', PARTIAL_FAILED: '部分失败', FAILED: '失败', CANCELLED: '已取消', SKIPPED: '已跳过' }[value] || value)
const statusType = value => ({ SUCCEEDED: 'success', RUNNING: 'primary', PENDING: 'info', RETRY_WAIT: 'warning', PARTIAL_FAILED: 'warning', FAILED: 'danger', CANCELLED: 'info' }[value] || 'info')
const formatTime = value => value ? String(value).replace('T', ' ').substring(0, 19) : ''
const canRetry = row => ['FAILED', 'PARTIAL_FAILED', 'CANCELLED'].includes(row.status)
const canCancel = row => ['PENDING', 'RUNNING', 'RETRY_WAIT'].includes(row.status)

const loadRuns = async () => {
  loading.value = true
  try {
    const response = await request.get('/admin/ingestion/runs', { params: { page: page.value, size: pageSize, ...filters } })
    records.value = response.data?.records || []
    total.value = Number(response.data?.total || 0)
  } finally { loading.value = false }
}
const search = () => { page.value = 1; loadRuns() }
const showDetail = async row => {
  const response = await request.get(`/admin/ingestion/runs/${row.id}`)
  detail.run = response.data?.run
  detail.items = response.data?.items || []
  detailVisible.value = true
}
const startRun = async pipelineType => {
  const response = await request.post('/admin/ingestion/runs', { pipelineType })
  ElMessage.success(response.data?.started ? '任务已创建' : `同类任务正在运行（#${response.data?.runId}）`)
  loadRuns()
}
const refreshLeaderboard = async () => {
  await ElMessageBox.confirm(
    '本次操作会重新抓取当前 GitHub 榜单并更新中文翻译，任务将在后台执行。',
    '刷新一次榜单',
    { confirmButtonText: '开始刷新', cancelButtonText: '取消', type: 'info' },
  )
  leaderboardRefreshing.value = true
  try {
    const response = await request.post('/admin/ingestion/runs', { pipelineType: 'LEADERBOARD_FULL' })
    const runId = response.data?.runId
    ElMessage.success(response.data?.started
      ? `榜单刷新任务已创建（#${runId}）`
      : `榜单刷新任务正在运行（#${runId}）`)
    filters.pipelineType = 'LEADERBOARD_FULL'
    filters.status = ''
    page.value = 1
    await loadRuns()
  } finally {
    leaderboardRefreshing.value = false
  }
}
const retryRun = async row => {
  const response = await request.post(`/admin/ingestion/runs/${row.id}/retry`)
  ElMessage.success(response.data?.started ? '重试任务已创建' : '同类任务正在运行')
  loadRuns()
}
const cancelRun = async row => {
  await ElMessageBox.confirm('取消后，正在执行的任务会在下一个安全检查点停止。', '确认取消')
  await request.post(`/admin/ingestion/runs/${row.id}/cancel`)
  ElMessage.success('已请求取消')
  loadRuns()
}
onMounted(loadRuns)
</script>

<style scoped>
.runs-page { display: grid; gap: 14px; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-heading h3 { margin: 0; color: #303133; font-size: 18px; }
.page-heading p { margin: 5px 0 0; color: #747c88; font-size: 13px; }
.heading-actions { display: flex; align-items: center; gap: 8px; }
.toolbar { display: flex; align-items: center; gap: 10px; padding: 12px; border: 1px solid #e3e6eb; border-radius: 8px; background: #fff; }
.toolbar :deep(.el-select) { width: 190px; }
.runs-table { border: 1px solid #e3e6eb; border-radius: 8px; }
.run-title strong, .run-title span { display: block; }
.run-title span, .el-progress + small { margin-top: 4px; color: #8b929d; font-size: 11px; }
.pagination-row { display: flex; justify-content: flex-end; }
h4 { margin: 20px 0 10px; color: #303133; }
@media (max-width: 700px) { .page-heading, .toolbar { align-items: stretch; flex-direction: column; }.heading-actions { display: grid; grid-template-columns: 1fr 1fr; }.toolbar :deep(.el-select) { width: 100%; } }
</style>
