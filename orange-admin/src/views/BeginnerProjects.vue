<template>
  <div class="admin-beginner-page">
    <header class="page-heading">
      <div><h3>新手项目</h3><p>管理候选仓库、确定性评分、公开状态与本周精选；执行过程统一记录在抓取任务中。</p></div>
      <div class="heading-actions">
        <el-button-group class="weekly-action-group">
          <el-button :icon="List" @click="$router.push('/ingestion-runs?pipelineType=BEGINNER_DISCOVERY')">查看运行记录</el-button>
          <el-button type="warning" :icon="Star" @click="openWeeklyEditor">管理本周精选</el-button>
        </el-button-group>
        <el-button type="primary" :icon="Refresh" :loading="starting" @click="startDiscovery">刷新项目库</el-button>
      </div>
    </header>

    <section class="summary-strip">
      <div><span>公开项目</span><strong>{{ summary.PUBLISHED || 0 }}</strong></div>
      <div><span>待补充说明</span><strong>{{ summary.CANDIDATE || 0 }}</strong></div>
      <div><span>已隐藏</span><strong>{{ summary.HIDDEN || 0 }}</strong></div>
      <div><span>失效项目</span><strong>{{ summary.INVALID || 0 }}</strong></div>
    </section>

    <section class="toolbar">
      <el-input v-model="filters.keyword" clearable placeholder="搜索仓库或中文说明" @keyup.enter="search"><template #prefix><Search /></template></el-input>
      <el-select v-model="filters.status" clearable placeholder="全部状态" @change="search"><el-option v-for="item in statuses" :key="item" :label="statusText(item)" :value="item" /></el-select>
      <el-button :icon="Search" @click="search">查询</el-button>
      <el-button :icon="Star" @click="openWeeklyEditor">选择本周精选</el-button>
    </section>

    <el-table v-loading="loading" :data="records" class="project-table" empty-text="暂无新手项目">
      <el-table-column label="项目" min-width="260"><template #default="{ row }"><div class="repo-cell"><strong>{{ row.fullName }}</strong><span>{{ row.descriptionZh || row.descriptionOriginal || '等待生成中文说明' }}</span></div></template></el-table-column>
      <el-table-column label="评分" width="150"><template #default="{ row }"><div class="score-cell"><b>{{ row.editorialScore || '-' }}</b><span>精选分</span><small>技术 {{ row.beginnerScore }}/100</small></div></template></el-table-column>
      <el-table-column label="技术" width="150"><template #default="{ row }"><span>{{ row.primaryLanguage || '-' }}</span><small class="block-muted">{{ row.packageManager || row.techStack || '-' }}</small></template></el-table-column>
      <el-table-column label="环境" min-width="170"><template #default="{ row }"><div class="risk-tags"><el-tag v-if="!hasRisks(row)" size="small" type="success" effect="plain">轻量</el-tag><el-tag v-if="row.requiresDocker" size="small" type="warning">Docker</el-tag><el-tag v-if="row.requiresDatabase" size="small" type="warning">数据库</el-tag><el-tag v-if="row.requiresSecrets" size="small" type="danger">密钥</el-tag></div></template></el-table-column>
      <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="最近核验" width="165"><template #default="{ row }">{{ formatTime(row.lastVerifiedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="240" fixed="right"><template #default="{ row }"><el-button text type="primary" @click="showDetail(row.id)">详情</el-button><el-button text @click="verify(row)">重新核验</el-button><el-dropdown @command="command => changeStatus(row, command)"><el-button text>状态<el-icon><ArrowDown /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="PUBLISHED">公开</el-dropdown-item><el-dropdown-item command="CANDIDATE">转为候选</el-dropdown-item><el-dropdown-item command="HIDDEN">隐藏</el-dropdown-item><el-dropdown-item command="INVALID">标记失效</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></el-table-column>
    </el-table>
    <div class="pagination"><el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="load" /></div>

    <el-drawer v-model="detailVisible" title="项目核验详情" size="min(720px, 96vw)">
      <template v-if="detail.project">
        <div class="detail-title"><span>{{ detail.project.fullName }}</span><h3>{{ detail.project.descriptionZh || detail.project.repositoryName }}</h3><a :href="detail.project.htmlUrl" target="_blank" rel="noopener noreferrer">打开 GitHub</a></div>
        <el-descriptions :column="2" border><el-descriptions-item label="技术门槛分">{{ detail.project.beginnerScore }}/100</el-descriptions-item><el-descriptions-item label="编辑精选分">{{ detail.project.editorialScore || '未评估' }}</el-descriptions-item><el-descriptions-item label="Vibe Coding 适配">{{ rating(detail.project.vibeCodingFit) }}</el-descriptions-item><el-descriptions-item label="成果展示价值">{{ rating(detail.project.showcaseValue) }}</el-descriptions-item><el-descriptions-item label="改造空间">{{ rating(detail.project.extensionPotential) }}</el-descriptions-item><el-descriptions-item label="范围清晰度">{{ rating(detail.project.scopeClarity) }}</el-descriptions-item><el-descriptions-item label="内容传播价值">{{ rating(detail.project.contentPotential) }}</el-descriptions-item><el-descriptions-item label="规则版本">{{ detail.project.scoreVersion }}</el-descriptions-item><el-descriptions-item label="它是什么" :span="2">{{ detail.project.whatIsIt || '-' }}</el-descriptions-item><el-descriptions-item label="适合原因" :span="2">{{ detail.project.beginnerReason || '-' }}</el-descriptions-item><el-descriptions-item label="改造方向" :span="2">{{ detail.project.modificationIdeas || '-' }}</el-descriptions-item><el-descriptions-item label="主要风险" :span="2">{{ detail.project.mainRisks || '-' }}</el-descriptions-item><el-descriptions-item label="最终建议" :span="2">{{ detail.project.finalRecommendation || '-' }}</el-descriptions-item></el-descriptions>
        <h4>评分证据</h4><el-table :data="detail.evidence || []" size="small"><el-table-column prop="dimensionKey" label="维度" width="120" /><el-table-column prop="score" label="得分" width="80" /><el-table-column prop="evidenceText" label="证据" min-width="260" /></el-table>
      </template>
    </el-drawer>

    <el-dialog v-model="weeklyVisible" title="管理本周精选" width="min(880px, 96vw)" destroy-on-close>
      <div class="weekly-heading">
        <div><strong>{{ weekly.weekStart || '本周' }}</strong><span>当前来源：{{ weekly.selectionSource === 'ADMIN' ? '管理员手动选择' : weekly.selectionSource === 'ALGORITHM' ? '算法生成' : '尚未生成' }}</span></div>
        <el-button :loading="weeklySaving" @click="rebuildWeekly">恢复算法精选</el-button>
      </div>
      <div class="weekly-layout" v-loading="weeklyLoading">
        <section class="weekly-selected">
          <h4>展示顺序 <small>{{ selectedIds.length }}/3</small></h4>
          <div v-for="(project, index) in selectedProjects" :key="project.id" class="selected-project">
            <b>{{ index + 1 }}</b><div><strong>{{ project.fullName }}</strong><span>{{ project.descriptionZh || '暂无中文说明' }}</span></div>
            <el-button-group><el-button :icon="ArrowUp" :disabled="index === 0" title="上移" @click="movePick(index, -1)" /><el-button :icon="ArrowDown" :disabled="index === selectedIds.length - 1" title="下移" @click="movePick(index, 1)" /><el-button :icon="Delete" title="移除" @click="removePick(project.id)" /></el-button-group>
          </div>
          <el-empty v-if="!selectedProjects.length" :image-size="64" description="从右侧选择 3 个项目" />
        </section>
        <section class="weekly-candidates">
          <h4>已公开候选</h4>
          <el-input v-model="weeklyKeyword" clearable placeholder="搜索项目" :prefix-icon="Search" />
          <div class="candidate-list">
            <button v-for="project in filteredCandidates" :key="project.id" type="button" class="candidate-row" :class="{ selected: selectedIds.includes(project.id) }" @click="togglePick(project.id)">
              <span><strong>{{ project.fullName }}</strong><small>精选分 {{ project.editorialScore }} · 技术分 {{ project.beginnerScore }}</small></span><el-icon><Check v-if="selectedIds.includes(project.id)" /><Plus v-else /></el-icon>
            </button>
          </div>
        </section>
      </div>
      <template #footer><el-button @click="weeklyVisible = false">取消</el-button><el-button type="primary" :loading="weeklySaving" :disabled="selectedIds.length !== 3" @click="saveWeeklyPicks">发布本周精选</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowDown, ArrowUp, Check, Delete, List, Plus, Refresh, Search, Star } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'

const statuses = ['CANDIDATE', 'VERIFYING', 'PUBLISHED', 'HIDDEN', 'INVALID']
const records = ref([]), total = ref(0), page = ref(1), pageSize = 20
const loading = ref(false), starting = ref(false), detailVisible = ref(false)
const weeklyVisible = ref(false), weeklyLoading = ref(false), weeklySaving = ref(false), weeklyKeyword = ref('')
const filters = reactive({ keyword: '', status: '' })
const detail = reactive({ project: null, evidence: [] })
const summary = reactive({})
const weekly = reactive({ weekStart: '', selectionSource: 'NONE' })
const weeklyCandidates = ref([]), selectedIds = ref([])
const selectedProjects = computed(() => selectedIds.value.map(id => weeklyCandidates.value.find(item => item.id === id)).filter(Boolean))
const filteredCandidates = computed(() => { const keyword = weeklyKeyword.value.trim().toLowerCase(); return keyword ? weeklyCandidates.value.filter(item => `${item.fullName} ${item.descriptionZh || ''}`.toLowerCase().includes(keyword)) : weeklyCandidates.value })
const statusText = value => ({ CANDIDATE: '候选', VERIFYING: '核验中', PUBLISHED: '已公开', HIDDEN: '已隐藏', INVALID: '已失效' }[value] || value)
const statusType = value => ({ PUBLISHED: 'success', CANDIDATE: 'warning', VERIFYING: 'primary', HIDDEN: 'info', INVALID: 'danger' }[value] || 'info')
const rating = value => value ? `${value}/5` : '未评估'
const hasRisks = row => row.requiresDocker || row.requiresDatabase || row.requiresSecrets || row.requiresExternalService
const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 16) : '未核验'
const load = async () => { loading.value = true; try { const r = await request.get('/admin/beginner-projects', { params: { ...filters, page: page.value, size: pageSize } }); records.value = r.data?.records || []; total.value = Number(r.data?.total || 0) } finally { loading.value = false } }
const loadSummary = async () => { const values = await Promise.all(statuses.map(status => request.get('/admin/beginner-projects', { params: { status, page: 1, size: 1 } }))); statuses.forEach((status, index) => { summary[status] = Number(values[index].data?.total || 0) }) }
const search = () => { page.value = 1; load() }
const showDetail = async id => { const r = await request.get(`/admin/beginner-projects/${id}`); detail.project = r.data?.project; detail.evidence = r.data?.evidence || []; detailVisible.value = true }
const startDiscovery = async () => { await ElMessageBox.confirm('将从 GitHub 获取候选项目、执行确定性评分并批量生成中文说明。任务在后台运行。', '刷新新手项目库', { type: 'info' }); starting.value = true; try { const r = await request.post('/admin/ingestion/runs', { pipelineType: 'BEGINNER_DISCOVERY', limit: 60 }); ElMessage.success(r.data?.started ? `任务已创建 #${r.data.runId}` : `已有任务运行中 #${r.data.runId}`) } finally { starting.value = false } }
const verify = async row => { const r = await request.post(`/admin/beginner-projects/${row.id}/verify`); ElMessage.success(r.data?.started ? '核验任务已创建' : '新手项目任务正在运行') }
const openWeeklyEditor = async () => { weeklyVisible.value = true; weeklyLoading.value = true; try { const [snapshot, candidates] = await Promise.all([request.get('/admin/beginner-projects/weekly-picks'), request.get('/admin/beginner-projects', { params: { status: 'PUBLISHED', page: 1, size: 100 } })]); weekly.weekStart = snapshot.data?.weekStart || ''; weekly.selectionSource = snapshot.data?.selectionSource || 'NONE'; const current = snapshot.data?.projects || []; const available = candidates.data?.records || []; weeklyCandidates.value = [...current, ...available.filter(item => !current.some(selected => selected.id === item.id))]; selectedIds.value = current.map(item => item.id) } finally { weeklyLoading.value = false } }
const togglePick = id => { if (selectedIds.value.includes(id)) { removePick(id); return } if (selectedIds.value.length >= 3) { ElMessage.warning('本周精选最多选择 3 个项目'); return } selectedIds.value.push(id) }
const removePick = id => { selectedIds.value = selectedIds.value.filter(value => value !== id) }
const movePick = (index, offset) => { const target = index + offset; if (target < 0 || target >= selectedIds.value.length) return; const values = [...selectedIds.value]; [values[index], values[target]] = [values[target], values[index]]; selectedIds.value = values }
const saveWeeklyPicks = async () => { weeklySaving.value = true; try { const r = await request.put('/admin/beginner-projects/weekly-picks', { projectIds: selectedIds.value }); weekly.selectionSource = r.data?.selectionSource || 'ADMIN'; ElMessage.success('本周精选已发布，后续自动刷新不会覆盖'); weeklyVisible.value = false } finally { weeklySaving.value = false } }
const rebuildWeekly = async () => { await ElMessageBox.confirm('将清除当前手动顺序，并按最新评分重新选择 3 个项目。', '恢复算法精选', { type: 'warning' }); weeklySaving.value = true; try { const r = await request.post('/admin/beginner-projects/weekly-picks/restore-algorithm'); weekly.selectionSource = r.data?.selectionSource || 'ALGORITHM'; selectedIds.value = (r.data?.projects || []).map(item => item.id); ElMessage.success('已恢复算法精选') } finally { weeklySaving.value = false } }
const changeStatus = async (row, status) => { let hiddenReason = ''; if (status === 'HIDDEN') { const result = await ElMessageBox.prompt('请记录隐藏原因，便于后续恢复和审计。', '隐藏项目', { inputPattern: /\S+/, inputErrorMessage: '请输入隐藏原因' }); hiddenReason = result.value } await request.put(`/admin/beginner-projects/${row.id}/status`, { status, hiddenReason }); ElMessage.success('状态已更新'); await Promise.all([load(), loadSummary()]) }
onMounted(() => Promise.all([load(), loadSummary()]))
</script>

<style scoped>
.admin-beginner-page{display:grid;gap:14px}.page-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.page-heading h3{margin:0;color:#303133;font-size:19px}.page-heading p{margin:6px 0 0;color:#747c88;font-size:13px}.heading-actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px}.weekly-action-group{display:inline-flex}.summary-strip{display:grid;grid-template-columns:repeat(4,1fr);border:1px solid #e3e6eb;border-radius:8px;background:#fff}.summary-strip div{padding:15px 18px}.summary-strip div+div{border-left:1px solid #ebeef5}.summary-strip span,.summary-strip strong{display:block}.summary-strip span{color:#8b929d;font-size:12px}.summary-strip strong{margin-top:5px;color:#303133;font-size:23px}.toolbar{display:grid;grid-template-columns:minmax(240px,1fr) 170px auto auto;gap:8px;padding:12px;border:1px solid #e3e6eb;border-radius:8px;background:#fff}.project-table{border:1px solid #e3e6eb;border-radius:8px}.repo-cell strong,.repo-cell span{display:block}.repo-cell span{max-width:430px;margin-top:5px;overflow:hidden;color:#7b8490;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.score-cell b{color:#e65100;font-size:20px}.score-cell span,.block-muted{color:#9299a3;font-size:11px}.block-muted{display:block;margin-top:4px}.risk-tags{display:flex;flex-wrap:wrap;gap:4px}.pagination{display:flex;justify-content:flex-end}.detail-title{margin-bottom:18px}.detail-title span{color:#e65100;font-size:12px;font-weight:700}.detail-title h3{margin:6px 0}.detail-title a{color:#e65100;font-size:12px}.detail-title+h4,h4{margin:22px 0 10px}
.weekly-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}.weekly-heading strong,.weekly-heading span{display:block}.weekly-heading span{margin-top:4px;color:#818895;font-size:12px}.weekly-layout{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1.15fr);gap:14px}.weekly-layout section{min-height:390px;padding:14px;border:1px solid #e3e6eb;border-radius:8px}.weekly-layout h4{display:flex;justify-content:space-between;margin:0 0 12px}.weekly-layout h4 small{color:#e65100}.selected-project{display:grid;grid-template-columns:30px minmax(0,1fr) auto;align-items:center;gap:10px;padding:11px 0;border-bottom:1px solid #ebeef5}.selected-project>b{display:grid;width:26px;height:26px;place-items:center;border-radius:50%;background:#fff1e8;color:#d94b00}.selected-project strong,.selected-project span{display:block}.selected-project span{margin-top:4px;overflow:hidden;color:#8a919c;font-size:11px;text-overflow:ellipsis;white-space:nowrap}.candidate-list{max-height:340px;margin-top:10px;overflow:auto}.candidate-row{display:flex;width:100%;align-items:center;justify-content:space-between;padding:11px 10px;border:0;border-bottom:1px solid #ebeef5;background:#fff;color:#303133;text-align:left;cursor:pointer}.candidate-row:hover{background:#fafafa}.candidate-row.selected{background:#fff6f0;color:#d94b00}.candidate-row strong,.candidate-row small{display:block}.candidate-row small{margin-top:4px;color:#9299a3}
@media(max-width:760px){.page-heading,.heading-actions{align-items:stretch;flex-direction:column}.weekly-action-group{display:flex}.weekly-action-group :deep(.el-button){flex:1}.summary-strip{grid-template-columns:1fr 1fr}.summary-strip div:nth-child(3){border-left:0;border-top:1px solid #ebeef5}.summary-strip div:nth-child(4){border-top:1px solid #ebeef5}.toolbar{grid-template-columns:1fr}.project-table{overflow-x:auto}}
@media(max-width:760px){.weekly-layout{grid-template-columns:1fr}.weekly-layout section{min-height:auto}.candidate-list{max-height:280px}}
</style>
