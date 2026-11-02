<template>
  <div class="ai-records-page">
    <header class="records-hero">
      <div>
        <span class="records-eyebrow">AI RESULT ARCHIVE</span>
        <h2>AI 分析记录</h2>
        <p>所有付费 AI 工具成功生成的结果都会自动保存，可随时回来继续查看。</p>
      </div>
      <el-button :loading="loading" @click="loadRecords">刷新记录</el-button>
    </header>

    <section class="records-toolbar" aria-label="记录筛选">
      <div class="records-count">
        <strong>{{ total }}</strong>
        <span>条已保存结果</span>
      </div>
      <el-select v-model="businessType" aria-label="按 AI 工具筛选" @change="changeFilter">
        <el-option label="全部 AI 工具" value="" />
        <el-option v-for="item in toolOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
    </section>

    <section class="records-panel" v-loading="loading" aria-live="polite">
      <div v-if="!loading && !records.length" class="records-empty">
        <el-icon><Document /></el-icon>
        <h3>暂时没有 AI 分析记录</h3>
        <p>完成一次趋势分析、方案生成、岗位匹配、上线体检或开源分析后，结果会自动出现在这里。</p>
        <el-button type="primary" @click="$router.push('/ai')">前往 AI 工具</el-button>
      </div>

      <el-table
        v-else
        :data="records"
        class="records-table"
        row-class-name="record-row"
        @row-click="openRecord"
      >
        <el-table-column label="分析结果" min-width="300">
          <template #default="{ row }">
            <div class="record-main">
              <strong>{{ row.title }}</strong>
              <p>{{ row.summary || '分析结果已保存' }}</p>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="工具" width="150">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ toolName(row.businessType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="保存时间" width="175">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="128" align="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click.stop="openRecord(row)">查看</el-button>
            <el-button size="small" text type="danger" @click.stop="removeRecord(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > pageSize"
        v-model:current-page="page"
        class="records-pagination"
        layout="prev, pager, next"
        :page-size="pageSize"
        :total="total"
        @current-change="loadRecords"
      />
    </section>

    <el-drawer
      v-model="detailOpen"
      class="ai-record-drawer"
      size="min(920px, 96vw)"
      destroy-on-close
    >
      <template #header>
        <div class="record-detail-heading">
          <el-tag size="small" effect="plain">{{ toolName(current?.businessType) }}</el-tag>
          <h3>{{ current?.title || 'AI 分析结果' }}</h3>
          <span>{{ formatTime(current?.createTime) }}</span>
        </div>
      </template>

      <div v-loading="detailLoading" class="record-detail-body">
        <el-alert
          v-if="current?.corrupted"
          title="这条记录内容异常，暂时无法读取"
          type="error"
          :closable="false"
          show-icon
        />
        <AiRecordResult
          v-else-if="current?.result"
          :business-type="current.businessType"
          :result="current.result"
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { Document } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AiRecordResult from '../components/AiRecordResult.vue'
import { useAiRecordStore } from '../stores/aiRecordStore'

const toolOptions = [
  { value: 'TREND_V1', label: '趋势分析提问' },
  { value: 'TREND_V2', label: '个性化趋势分析' },
  { value: 'PLAN', label: '方案设计' },
  { value: 'JOB_MATCH', label: '岗位匹配' },
  { value: 'LAUNCH_CHECK', label: '上线体检' },
  { value: 'OPEN_SOURCE_MATCH', label: '开源 Issue 匹配' },
  { value: 'OPEN_SOURCE_GUIDE', label: '开源贡献指南' },
]

const recordStore = useAiRecordStore()
const { data: recordData, loading } = storeToRefs(recordStore)
const records = computed(() => recordData.value.records)
const total = computed(() => recordData.value.total)
const page = ref(1)
const pageSize = 20
const businessType = ref('')
const detailOpen = ref(false)
const detailLoading = ref(false)
const current = ref(null)

const toolName = value => toolOptions.find(item => item.value === value)?.label || 'AI 工具'
const formatTime = value => value ? String(value).replace('T', ' ').substring(0, 19) : ''
const loadRecords = async () => {
  await recordStore.load({ page: page.value, size: pageSize, businessType: businessType.value })
}

const changeFilter = () => {
  page.value = 1
  loadRecords()
}

const openRecord = async row => {
  detailOpen.value = true
  detailLoading.value = true
  current.value = { ...row }
  try {
    current.value = await recordStore.loadDetail(row.id)
  } finally {
    detailLoading.value = false
  }
}

const removeRecord = async row => {
  try {
    await ElMessageBox.confirm(`删除“${row.title}”？删除后无法恢复。`, '删除 AI 记录', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消',
    })
  } catch { return }
  await recordStore.remove(row.id)
  ElMessage.success('AI 记录已删除')
  if (records.value.length === 1 && page.value > 1) page.value -= 1
  await loadRecords()
}

onMounted(loadRecords)
</script>

<style scoped>
.ai-records-page {
  --record-line: #eadfd7;
  display: grid;
  gap: 16px;
}

.records-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 24px;
  border: 1px solid #f0cfad;
  border-radius: 14px;
  background: linear-gradient(135deg, #fff8ef, #fff);
}

.records-eyebrow {
  color: #bb6427;
  font: 800 11px/1.2 var(--font-mono);
  letter-spacing: .12em;
}

.records-hero h2 {
  margin: 7px 0 6px;
  color: #402718;
  font-family: var(--font-mono);
  font-size: 24px;
}

.records-hero p {
  max-width: 640px;
  margin: 0;
  color: #796457;
  font-size: 13px;
  line-height: 1.7;
}

.records-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border: 1px solid var(--record-line);
  border-radius: 12px;
  background: #fff;
}

.records-count {
  display: flex;
  align-items: baseline;
  gap: 7px;
}

.records-count strong { color: #d46621; font-size: 22px; }
.records-count span { color: #857267; font-size: 12px; }
.records-toolbar .el-select { width: 220px; }

.records-panel {
  min-height: 280px;
  padding: 8px;
  border: 1px solid var(--record-line);
  border-radius: 12px;
  background: #fff;
}

.record-main { min-width: 0; padding: 5px 0; }
.record-main strong { color: #443126; font-size: 14px; }
.record-main p {
  overflow: hidden;
  margin: 5px 0 0;
  color: #8a776c;
  font-size: 12px;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.records-table :deep(.record-row) { cursor: pointer; }
.records-table :deep(.record-row:hover td) { background: #fffaf5 !important; }
.records-pagination { justify-content: center; padding: 16px 0 8px; }

.records-empty {
  display: flex;
  min-height: 300px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  padding: 40px 20px;
  text-align: center;
}

.records-empty .el-icon { color: #e9a66c; font-size: 40px; }
.records-empty h3 { margin: 14px 0 6px; color: #4c362a; font-size: 16px; }
.records-empty p { max-width: 520px; margin: 0 0 18px; color: #8b786d; font-size: 13px; line-height: 1.7; }

.record-detail-heading h3 { margin: 8px 0 4px; color: #3f2b20; font-size: 18px; }
.record-detail-heading span { color: #938177; font-size: 12px; }
.record-detail-body { min-height: 240px; }
@media (max-width: 720px) {
  .records-hero { align-items: stretch; flex-direction: column; padding: 18px; }
  .records-toolbar { align-items: stretch; flex-direction: column; }
  .records-toolbar .el-select { width: 100%; }
  .records-panel { overflow-x: auto; }
  .records-table { min-width: 680px; }
}
</style>
