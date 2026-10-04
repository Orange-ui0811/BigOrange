<template>
  <div class="rag-lab">
    <div class="page-head">
      <div>
        <h3>RAG 对比实验室</h3>
        <p>在不影响现有知识库的前提下，独立验证新版文档处理和检索效果。</p>
      </div>
      <div class="head-actions">
        <el-tag :type="status.enabled ? 'success' : 'info'">{{ status.enabled ? '实验已启用' : '实验未启用' }}</el-tag>
        <el-button :loading="loading" @click="load">刷新状态</el-button>
        <el-select v-model="rebuildAdapter" style="width:170px" :disabled="hasBuildingGeneration">
          <el-option label="HNSW 混合检索" value="HNSW_HYBRID" />
          <el-option label="精确余弦基线" value="EXACT" />
        </el-select>
        <el-button :loading="rebuilding" :disabled="!status.enabled || hasBuildingGeneration" @click="rebuildIndex">构建新索引并切换</el-button>
        <el-button :loading="evaluating" :disabled="!status.enabled" @click="runEvaluation">运行固定评测</el-button>
        <el-button :loading="benchmarking" :disabled="!status.enabled" @click="runBenchmark">运行 60 题基准</el-button>
      </div>
    </div>

    <el-alert v-if="!status.enabled" type="info" :closable="false" show-icon
      title="B 版当前处于关闭状态。设置 RAG_B_ENABLED=true 后重启后端即可开始实验，现有用户入口不会改变。" />

    <el-collapse v-if="generations.length" class="generation-panel">
      <el-collapse-item :title="`B 版索引代际（当前：${activeGeneration?.generationKey || '无'}）`">
        <el-table :data="generations" size="small">
          <el-table-column prop="generationKey" label="代际" min-width="190" />
          <el-table-column prop="adapterType" label="检索适配器" width="140" />
          <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.active ? 'success' : row.status === 'FAILED' ? 'danger' : 'info'">{{ row.active ? '使用中' : row.status }}</el-tag></template></el-table-column>
          <el-table-column prop="documentCount" label="文档" width="80" />
          <el-table-column prop="chunkCount" label="片段" width="80" />
          <el-table-column prop="lastError" label="说明" min-width="220" show-overflow-tooltip />
        </el-table>
      </el-collapse-item>
    </el-collapse>

    <el-row :gutter="16" class="workspace">
      <el-col :xs="24" :lg="10">
        <el-card shadow="never">
          <template #header><strong>实验资料</strong></template>
          <el-upload :before-upload="upload" :show-file-list="false" accept=".md,.txt" :http-request="() => {}" :disabled="!status.enabled">
            <el-button type="primary" :loading="uploading" :disabled="!status.enabled">上传实验文档</el-button>
          </el-upload>
          <div class="hint">支持 UTF-8 的 Markdown 或文本文件，最大 1 MB。上传后会显示真实处理状态。</div>
          <div class="legacy-import">
            <el-input-number v-model="legacyDocumentId" :min="1" :controls="false" placeholder="原知识库文档 ID" />
            <el-button :loading="importing" :disabled="!status.enabled || !legacyDocumentId" @click="importLegacy">导入到 B 版</el-button>
          </div>
          <div class="hint">导入只读取原知识库，不会修改原文档或原向量。</div>
          <div class="legacy-import">
            <el-input v-model="githubRepository" placeholder="GitHub：owner/repository" clearable />
            <el-button :loading="importingGithub" :disabled="!status.enabled || !githubRepository.trim()" @click="syncGithub">同步 README</el-button>
          </div>
          <div class="hint">再次同步会比较完整 README 哈希；没有变化时跳过，有变化时建立新版本。</div>

          <el-table :data="documents" v-loading="loading" size="small" class="document-table" empty-text="暂无实验文档">
            <el-table-column prop="title" label="文档" min-width="160" show-overflow-tooltip />
            <el-table-column label="状态" width="92">
              <template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'FAILED'" text type="primary" @click="retry(row)">重试</el-button>
                <el-button text type="danger" @click="remove(row)">下架</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="14">
        <el-card shadow="never">
          <template #header><strong>试搜与证据预览</strong></template>
          <el-input v-model="query" type="textarea" :rows="3" placeholder="输入一个真实业务问题，例如：这个项目如何用 Docker 部署？" />
          <div class="constraints">
            <el-input v-model="languages" placeholder="硬条件：语言，例如 Java,Vue" clearable />
            <el-input-number v-model="maxDurationDays" :min="1" :controls="false" placeholder="最长天数" />
            <el-select v-model="difficulty" clearable placeholder="难度硬条件">
              <el-option label="入门" value="入门" /><el-option label="中等" value="中等" /><el-option label="进阶" value="进阶" />
            </el-select>
          </div>
          <div class="requirement-options">
            <el-checkbox v-model="avoidDocker">不能依赖 Docker</el-checkbox>
            <el-checkbox v-model="avoidDatabase">不能依赖数据库</el-checkbox>
            <el-checkbox v-model="avoidSecrets">不能依赖密钥</el-checkbox>
            <el-checkbox v-model="beginnerPreferred">新手优先</el-checkbox>
            <el-checkbox v-model="careerPreferred">求职优先</el-checkbox>
          </div>
          <div class="search-actions">
            <el-select v-model="sourceTypes" multiple collapse-tags placeholder="限定资料来源" style="width:260px">
              <el-option v-for="item in sources" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-button type="primary" :loading="searching" :disabled="!status.enabled || !query.trim()" @click="runSearch">开始试搜</el-button>
            <el-button :loading="answering" :disabled="!status.enabled || !query.trim()" @click="previewAnswer">回答与引用校验</el-button>
            <el-button :loading="comparing" :disabled="!status.enabled || !query.trim()" @click="compare">对比 A / B</el-button>
          </div>

          <el-empty v-if="trace && !trace.hits?.length" description="没有找到满足条件的有效证据" />
          <div v-for="(hit, index) in trace?.hits || []" :key="hit.chunkId" class="evidence">
            <div class="evidence-head">
              <strong>{{ index + 1 }}. {{ hit.title }}</strong>
              <span>相关度 {{ Number(hit.score || 0).toFixed(3) }}</span>
            </div>
            <div class="evidence-meta">{{ sourceLabel(hit.sourceType) }} · {{ hit.sectionPath || '未命名章节' }} · 字符 {{ hit.startOffset }}–{{ hit.endOffset }}</div>
            <div class="evidence-meta">检索路径：{{ hit.retrievalRoute }}</div>
            <div class="evidence-content">{{ hit.content }}</div>
          </div>

          <el-descriptions v-if="trace" :column="2" border size="small" class="trace">
            <el-descriptions-item label="索引版本">{{ trace.generation }}</el-descriptions-item>
            <el-descriptions-item label="检索状态">{{ traceStatusText }}</el-descriptions-item>
            <el-descriptions-item label="回答策略">{{ trace.answerPolicy }}</el-descriptions-item>
            <el-descriptions-item label="候选数量">{{ trace.candidateCount }}</el-descriptions-item>
            <el-descriptions-item label="扫描片段">{{ trace.scannedCount }}</el-descriptions-item>
            <el-descriptions-item label="最终证据">{{ trace.selectedCount }}</el-descriptions-item>
            <el-descriptions-item label="检索实现">{{ trace.adapter }}</el-descriptions-item>
            <el-descriptions-item label="排除原因">{{ excludedText }}</el-descriptions-item>
          </el-descriptions>
          <el-card v-if="answerPreview" shadow="never" class="answer-preview">
            <div class="evidence-head">
              <strong>回答预览</strong>
              <el-tag :type="answerPreview.publishable ? 'success' : 'danger'">{{ answerPreview.publishable ? '引用校验通过' : '不可发布' }}</el-tag>
            </div>
            <div class="answer-content">{{ answerPreview.answer }}</div>
            <div v-if="answerPreview.citationValidation" class="hint">
              已支持 {{ answerPreview.citationValidation.supportedSentences }} / {{ answerPreview.citationValidation.checkedSentences }} 个事实句。
            </div>
            <el-table v-if="answerPreview.citationValidation?.sentences?.length" :data="answerPreview.citationValidation.sentences" size="small">
              <el-table-column prop="sentence" label="句子" min-width="260" show-overflow-tooltip />
              <el-table-column label="引用" width="100"><template #default="{ row }">{{ row.citations?.map(v => `C${v}`).join(', ') || '无' }}</template></el-table-column>
              <el-table-column prop="reason" label="校验结果" width="170" />
            </el-table>
          </el-card>
          <el-row v-if="comparison" :gutter="12" class="comparison">
            <el-col :span="12" v-for="side in ['a', 'b']" :key="side">
              <el-card shadow="never">
                <strong>{{ side.toUpperCase() }} 版 · {{ comparison[side]?.durationMs }} ms</strong>
                <div class="hint">返回 {{ comparison[side]?.hits?.length || 0 }} 条<span v-if="comparison[side]?.error"> · {{ comparison[side].error }}</span></div>
                <div v-for="(item, index) in comparison[side]?.hits || []" :key="item.id || item.chunkId" class="compact-hit">
                  {{ index + 1 }}. {{ item.title || item.metadata?.docTitle || item.id }}
                </div>
              </el-card>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="evaluation" shadow="never" class="evaluation-panel">
      <template #header><strong>固定评测 · {{ evaluation.datasetVersion }}</strong></template>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="题目数">{{ evaluation.summary?.caseCount }}</el-descriptions-item>
        <el-descriptions-item label="A 版通过率">{{ percent(evaluation.summary?.aPassRate) }}</el-descriptions-item>
        <el-descriptions-item label="B 版通过率">{{ percent(evaluation.summary?.bPassRate) }}</el-descriptions-item>
        <el-descriptions-item label="B 引用就绪率">{{ percent(evaluation.summary?.bCitationReadyRate) }}</el-descriptions-item>
        <el-descriptions-item label="A P50 / P95">{{ evaluation.summary?.aP50Ms }} / {{ evaluation.summary?.aP95Ms }} ms</el-descriptions-item>
        <el-descriptions-item label="B P50 / P95">{{ evaluation.summary?.bP50Ms }} / {{ evaluation.summary?.bP95Ms }} ms</el-descriptions-item>
        <el-descriptions-item label="B 可追溯率">{{ percent(evaluation.summary?.bTraceabilityRate) }}</el-descriptions-item>
        <el-descriptions-item v-if="evaluation.summary?.sourceGoldCaseCount" label="A 文档召回@K">{{ percent(evaluation.summary?.aSourceRecallAtK) }}</el-descriptions-item>
        <el-descriptions-item v-if="evaluation.summary?.sourceGoldCaseCount" label="B 文档召回@K">{{ percent(evaluation.summary?.bSourceRecallAtK) }}</el-descriptions-item>
        <el-descriptions-item v-if="evaluation.summary?.refusalExpectedCount" label="B 拒答准确率">{{ percent(evaluation.summary?.bRefusalAccuracy) }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="evaluation.cases || []" size="small" style="margin-top:12px">
        <el-table-column prop="key" label="用例" width="170" />
        <el-table-column prop="question" label="问题" min-width="260" />
        <el-table-column label="A" width="100"><template #default="{ row }">{{ row.a?.passed ? '通过' : '失败' }} · {{ row.a?.durationMs }}ms</template></el-table-column>
        <el-table-column label="B" width="100"><template #default="{ row }">{{ row.b?.passed ? '通过' : '失败' }} · {{ row.b?.durationMs }}ms</template></el-table-column>
        <el-table-column label="回答策略" width="190"><template #default="{ row }">{{ row.b?.answerPolicy }}</template></el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteRagLabDocument, getRagLabStatus, listRagLabDocuments,
  retryRagLabDocument, searchRagLab, uploadRagLabDocument, importLegacyDocument, importGithubReadme, compareRagRetrieval, previewRagAnswer,
  listRagGenerations, rebuildRagGeneration, runRagEvaluation,
  runRagBenchmark,
} from '../api/ragLab'

const status = ref({ enabled: false })
const documents = ref([])
const loading = ref(false)
const uploading = ref(false)
const importing = ref(false)
const importingGithub = ref(false)
const legacyDocumentId = ref(null)
const githubRepository = ref('')
const searching = ref(false)
const comparing = ref(false)
const answering = ref(false)
const query = ref('')
const sourceTypes = ref([])
const languages = ref('')
const maxDurationDays = ref(null)
const difficulty = ref('')
const avoidDocker = ref(false)
const avoidDatabase = ref(false)
const avoidSecrets = ref(false)
const beginnerPreferred = ref(false)
const careerPreferred = ref(false)
const trace = ref(null)
const comparison = ref(null)
const answerPreview = ref(null)
const generations = ref([])
const rebuilding = ref(false)
const evaluating = ref(false)
const benchmarking = ref(false)
const evaluation = ref(null)
const rebuildAdapter = ref('HNSW_HYBRID')
let refreshTimer = null
const sources = [
  { value: 'MANUAL', label: '手动上传' }, { value: 'GITHUB', label: 'GitHub README' },
  { value: 'BEGINNER_PROJECT', label: '新手项目' }, { value: 'CAREER_PROJECT', label: '职业项目' },
  { value: 'JOB_POSTING', label: '有效岗位' },
]
const activeGeneration = computed(() => generations.value.find(item => item.active))
const hasBuildingGeneration = computed(() => generations.value.some(item => item.status === 'BUILDING'))

const statusText = value => ({ PENDING: '等待处理', PROCESSING: '处理中', READY: '可检索', FAILED: '失败', DELETED: '已下架' }[value] || value)
const statusType = value => ({ PENDING: 'warning', PROCESSING: 'primary', READY: 'success', FAILED: 'danger', DELETED: 'info' }[value] || 'info')
const sourceLabel = value => sources.find(item => item.value === value)?.label || value
const excludedText = computed(() => {
  const values = Object.entries(trace.value?.excludedReasons || {})
  return values.length ? values.map(([reason, count]) => `${reason}：${count}`).join('，') : '无'
})
const traceStatusText = computed(() => ({ USED: '证据充分，可带引用回答', NO_MATCH: '没有匹配资料', INSUFFICIENT_EVIDENCE: '资料不足，停止生成' }[trace.value?.status] || trace.value?.status || '未检索'))

const load = async () => {
  if (refreshTimer) clearTimeout(refreshTimer)
  loading.value = true
  try {
    const [statusResponse, documentResponse, generationResponse] = await Promise.all([getRagLabStatus(), listRagLabDocuments(), listRagGenerations()])
    status.value = statusResponse.data || { enabled: false }
    documents.value = documentResponse.data || []
    generations.value = generationResponse.data || []
  } finally {
    loading.value = false
    if (documents.value.some(item => ['PENDING', 'PROCESSING'].includes(item.status)) || hasBuildingGeneration.value) {
      refreshTimer = setTimeout(load, 3000)
    }
  }
}

const upload = async file => {
  uploading.value = true
  try {
    const response = await uploadRagLabDocument(file)
    if (response.code === 1) { ElMessage.success('已进入处理队列'); await load() }
  } finally { uploading.value = false }
  return false
}

const retry = async row => {
  const response = await retryRagLabDocument(row.id)
  if (response.code === 1) { ElMessage.success('已重新进入处理队列'); await load() }
}

const importLegacy = async () => {
  importing.value = true
  try {
    const response = await importLegacyDocument(legacyDocumentId.value)
    if (response.code === 1) { ElMessage.success(response.data?.status === 'UNCHANGED' ? 'B 版已经是最新内容' : '已进入 B 版处理队列'); await load() }
  } finally { importing.value = false }
}

const syncGithub = async () => {
  importingGithub.value = true
  try {
    const response = await importGithubReadme(githubRepository.value.trim())
    if (response.code === 1) {
      ElMessage.success(response.data?.status === 'UNCHANGED' ? 'README 没有变化' : 'README 已进入统一处理队列')
      await load()
    }
  } finally { importingGithub.value = false }
}

const remove = async row => {
  await ElMessageBox.confirm(`下架实验文档“${row.title}”？`, '下架确认', { type: 'warning' })
  const response = await deleteRagLabDocument(row.id)
  if (response.code === 1) { ElMessage.success('已从 B 版检索中排除'); await load() }
}

const runSearch = async () => {
  searching.value = true
  try {
    const response = await searchRagLab(searchPayload())
    if (response.code === 1) trace.value = response.data
  } finally { searching.value = false }
}

const compare = async () => {
  comparing.value = true
  try {
    const response = await compareRagRetrieval({ query: query.value.trim(), topK: 8, threshold: 0.55 })
    if (response.code === 1) comparison.value = response.data
  } finally { comparing.value = false }
}

const previewAnswer = async () => {
  answering.value = true
  try {
    const response = await previewRagAnswer(searchPayload())
    if (response.code === 1) answerPreview.value = response.data
  } finally { answering.value = false }
}

const rebuildIndex = async () => {
  await ElMessageBox.confirm('系统会先构建并校验新索引，成功后才切换。构建期间当前索引继续可用。', '重建 B 版索引', { type: 'info' })
  rebuilding.value = true
  try {
    const response = await rebuildRagGeneration(rebuildAdapter.value)
    if (response.code === 1) { ElMessage.success('新索引已进入后台构建队列'); await load() }
  } finally { rebuilding.value = false }
}

const runEvaluation = async () => {
  evaluating.value = true
  try {
    const response = await runRagEvaluation()
    if (response.code === 1) evaluation.value = response.data
  } finally { evaluating.value = false }
}

const runBenchmark = async () => {
  benchmarking.value = true
  try {
    const response = await runRagBenchmark()
    if (response.code === 1) evaluation.value = response.data
  } finally { benchmarking.value = false }
}

const percent = value => `${(Number(value || 0) * 100).toFixed(1)}%`

const searchPayload = () => ({
  query: query.value.trim(), topK: 8, threshold: 0.55, sourceTypes: sourceTypes.value,
  requiredLanguages: languages.value.split(/[,，]/).map(value => value.trim()).filter(Boolean),
  maxDurationDays: maxDurationDays.value, difficulty: difficulty.value || null,
  allowDocker: avoidDocker.value ? false : null,
  allowDatabase: avoidDatabase.value ? false : null,
  allowSecrets: avoidSecrets.value ? false : null,
  beginnerPreferred: beginnerPreferred.value,
  careerPreferred: careerPreferred.value,
})

onMounted(load)
onBeforeUnmount(() => { if (refreshTimer) clearTimeout(refreshTimer) })
</script>

<style scoped>
.page-head { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; margin-bottom:16px; }
.page-head h3 { margin:0 0 6px; }
.page-head p, .hint, .evidence-meta { color:#737b88; font-size:13px; }
.page-head p { margin:0; }
.head-actions { display:flex; align-items:center; gap:10px; }
.generation-panel { margin-top:16px; }
.workspace { margin-top:16px; }
.hint { margin:10px 0 16px; line-height:1.6; }
.legacy-import { display:flex; gap:8px; }
.document-table { width:100%; }
.search-actions { display:flex; justify-content:flex-end; gap:10px; margin:12px 0 18px; }
.constraints { display:grid; grid-template-columns:2fr 1fr 1fr; gap:8px; margin-top:10px; }
.requirement-options { display:flex; flex-wrap:wrap; gap:12px; margin-top:10px; }
.evidence { border:1px solid #e5e7eb; border-radius:8px; padding:14px; margin-bottom:10px; }
.evidence-head { display:flex; justify-content:space-between; gap:12px; }
.evidence-head span { color:#e65100; font-size:13px; white-space:nowrap; }
.evidence-meta { margin:6px 0 10px; }
.evidence-content { white-space:pre-wrap; line-height:1.7; color:#303640; }
.trace { margin-top:18px; }
.comparison { margin-top:16px; }
.answer-preview { margin-top:16px; }
.evaluation-panel { margin-top:16px; }
.answer-content { white-space:pre-wrap; line-height:1.8; margin-top:12px; }
.compact-hit { margin-top:8px; padding-top:8px; border-top:1px solid #ebeef5; font-size:13px; }
@media (max-width: 991px) { .workspace :deep(.el-col) { margin-bottom:16px; } }
</style>
