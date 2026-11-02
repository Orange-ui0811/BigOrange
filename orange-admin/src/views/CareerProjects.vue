<template>
  <div class="career-admin">
    <header class="page-heading">
      <div>
        <h3>职业成长项目</h3>
        <p>按真实岗位能力管理候选仓库、五维评分、公开状态与每周精选。</p>
      </div>
      <div class="heading-actions">
        <el-button :loading="starting" @click="startDiscovery">刷新项目库</el-button>
        <el-button type="primary" @click="openWeeklyEditor">管理本周精选</el-button>
        <el-button @click="$router.push('/ingestion-runs')">查看运行记录</el-button>
      </div>
    </header>

    <section class="summary-grid" aria-label="职业项目状态统计">
      <button v-for="item in statusCards" :key="item.value" type="button"
        :class="['summary-item', { active: filters.status === item.value }]" @click="selectStatus(item.value)">
        <strong>{{ summary[item.value] || 0 }}</strong><span>{{ item.label }}</span>
      </button>
    </section>

    <section class="toolbar">
      <el-input v-model="filters.keyword" clearable placeholder="搜索仓库、摘要或职业价值" @keyup.enter="search" />
      <el-select v-model="filters.status" clearable placeholder="全部状态" @change="search">
        <el-option v-for="item in statusCards" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button :icon="Search" @click="search">查询</el-button>
    </section>

    <el-table v-loading="loading" :data="records" empty-text="暂无职业项目">
      <el-table-column label="项目" min-width="250">
        <template #default="{ row }">
          <div class="project-name"><strong>{{ row.fullName }}</strong><span>{{ row.primaryLanguage || '未识别语言' }} · {{ row.stars }} Star</span></div>
        </template>
      </el-table-column>
      <el-table-column label="最佳岗位方向" min-width="170"><template #default="{ row }">{{ row.bestRoleName || '未分类' }}</template></el-table-column>
      <el-table-column label="职业评分" width="110"><template #default="{ row }"><b class="score">{{ row.overallScore }}</b> / 100</template></el-table-column>
      <el-table-column label="难度" width="110"><template #default="{ row }">{{ difficultyText(row.difficulty) }}</template></el-table-column>
      <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="255" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="showDetail(row.id)">详情</el-button>
          <el-button text @click="verify(row)">重新核验</el-button>
          <el-dropdown @command="value => changeStatus(row, value)">
            <el-button text>调整状态<el-icon><ArrowDown /></el-icon></el-button>
            <template #dropdown><el-dropdown-menu>
              <el-dropdown-item command="PUBLISHED">公开</el-dropdown-item>
              <el-dropdown-item command="CANDIDATE">转为候选</el-dropdown-item>
              <el-dropdown-item command="HIDDEN">隐藏</el-dropdown-item>
              <el-dropdown-item command="INVALID">标记失效</el-dropdown-item>
            </el-dropdown-menu></template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination-row"><el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="load" /></div>

    <el-drawer v-model="detailVisible" title="职业项目详情" size="min(780px, 96vw)">
      <div v-if="detail" class="detail-panel">
        <div class="detail-title"><div><small>{{ detail.fullName }}</small><h4>{{ detail.summaryZh || detail.repositoryName }}</h4></div><b>{{ detail.overallScore }}</b></div>
        <p>{{ detail.careerValue || detail.descriptionOriginal }}</p>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="主要语言">{{ detail.primaryLanguage || '-' }}</el-descriptions-item>
          <el-descriptions-item label="预计时间">{{ detail.estimatedDuration || '-' }}</el-descriptions-item>
          <el-descriptions-item label="可证明能力" :span="2">{{ detail.proofPoints || '-' }}</el-descriptions-item>
          <el-descriptions-item label="面试讨论点" :span="2">{{ detail.interviewTopics || '-' }}</el-descriptions-item>
          <el-descriptions-item label="差异化方向" :span="2">{{ detail.modificationPlan || '-' }}</el-descriptions-item>
          <el-descriptions-item label="主要风险" :span="2">{{ detail.mainRisks || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4 class="subheading">岗位五维评分</h4>
        <el-table :data="detail.roleScores || []" size="small">
          <el-table-column prop="roleName" label="岗位方向" min-width="150" />
          <el-table-column prop="roleRelevance" label="相关度" width="75" />
          <el-table-column prop="provableAbility" label="能力证明" width="85" />
          <el-table-column prop="knowledgeCoverage" label="知识覆盖" width="85" />
          <el-table-column prop="portfolioCompleteness" label="完整度" width="75" />
          <el-table-column prop="interviewValue" label="面试价值" width="85" />
          <el-table-column prop="totalScore" label="总分" width="65" />
        </el-table>
      </div>
    </el-drawer>

    <el-dialog v-model="weeklyVisible" title="管理职业榜单本周精选" width="min(760px, 94vw)">
      <div v-loading="weeklyLoading">
        <p class="weekly-note">固定选择 3 个已公开项目。人工发布后，本周自动任务不会覆盖。</p>
        <el-select v-model="selectedIds" multiple :multiple-limit="3" filterable placeholder="请选择 3 个项目" style="width:100%">
          <el-option v-for="item in weeklyCandidates" :key="item.id" :label="`${item.fullName} · ${item.bestRoleName} · ${item.overallScore}分`" :value="item.id" />
        </el-select>
        <ol class="selected-list"><li v-for="id in selectedIds" :key="id">{{ weeklyCandidates.find(item => item.id === id)?.fullName }}</li></ol>
      </div>
      <template #footer>
        <el-button :loading="weeklySaving" @click="restoreAlgorithm">恢复算法精选</el-button>
        <el-button type="primary" :disabled="selectedIds.length !== 3" :loading="weeklySaving" @click="saveWeekly">发布人工精选</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ArrowDown, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'

const statusCards = [
  { value: 'CANDIDATE', label: '候选' }, { value: 'VERIFYING', label: '核验中' },
  { value: 'PUBLISHED', label: '已公开' }, { value: 'HIDDEN', label: '已隐藏' },
  { value: 'INVALID', label: '已失效' },
]
const records = ref([]), total = ref(0), page = ref(1), loading = ref(false), starting = ref(false)
const pageSize = 20
const filters = reactive({ keyword: '', status: '' })
const summary = reactive(Object.fromEntries(statusCards.map(item => [item.value, 0])))
const detailVisible = ref(false), detail = ref(null)
const weeklyVisible = ref(false), weeklyLoading = ref(false), weeklySaving = ref(false)
const weeklyCandidates = ref([]), selectedIds = ref([])
const selectedCount = computed(() => selectedIds.value.length)

const statusText = value => statusCards.find(item => item.value === value)?.label || value
const statusType = value => ({ PUBLISHED: 'success', CANDIDATE: 'warning', VERIFYING: 'primary', HIDDEN: 'info', INVALID: 'danger' }[value] || 'info')
const difficultyText = value => ({ ENTRY: '入门', INTERMEDIATE: '中等', ADVANCED: '进阶' }[value] || value || '-')
const load = async () => { loading.value = true; try { const r = await request.get('/admin/career-projects', { params: { ...filters, page: page.value, size: pageSize } }); records.value = r.data?.records || []; total.value = Number(r.data?.total || 0) } finally { loading.value = false } }
const loadSummary = async () => { const values = await Promise.all(statusCards.map(item => request.get('/admin/career-projects', { params: { status: item.value, page: 1, size: 1 } }))); statusCards.forEach((item, index) => { summary[item.value] = Number(values[index].data?.total || 0) }) }
const search = () => { page.value = 1; load() }
const selectStatus = value => { filters.status = filters.status === value ? '' : value; search() }
const showDetail = async id => { const r = await request.get(`/admin/career-projects/${id}`); detail.value = r.data; detailVisible.value = true }
const startDiscovery = async () => { await ElMessageBox.confirm('将发现候选仓库、执行确定性职业评分，并在后台生成职业说明。', '刷新职业项目库', { type: 'info' }); starting.value = true; try { const r = await request.post('/admin/career-projects/refresh'); ElMessage.success(r.data?.started ? `任务已创建 #${r.data.runId}` : `已有任务运行中 #${r.data.runId}`) } finally { starting.value = false } }
const verify = async row => { const r = await request.post(`/admin/career-projects/${row.id}/verify`); ElMessage.success(r.data?.started ? '核验任务已创建' : '职业项目任务正在运行') }
const changeStatus = async (row, status) => { let hiddenReason = ''; if (status === 'HIDDEN' || status === 'INVALID') { const r = await ElMessageBox.prompt('请输入原因，便于后续审计和恢复。', status === 'HIDDEN' ? '隐藏项目' : '标记失效', { inputPattern: /\S+/, inputErrorMessage: '请输入原因' }); hiddenReason = r.value } await request.put(`/admin/career-projects/${row.id}/status`, { status, hiddenReason }); ElMessage.success('状态已更新'); await Promise.all([load(), loadSummary()]) }
const openWeeklyEditor = async () => { weeklyVisible.value = true; weeklyLoading.value = true; try { const [snapshot, candidates] = await Promise.all([request.get('/admin/career-projects/weekly-picks'), request.get('/admin/career-projects', { params: { status: 'PUBLISHED', page: 1, size: 100 } })]); const current = snapshot.data?.projects || []; const available = candidates.data?.records || []; weeklyCandidates.value = [...current, ...available.filter(item => !current.some(selected => selected.id === item.id))]; selectedIds.value = current.map(item => item.id) } finally { weeklyLoading.value = false } }
const saveWeekly = async () => { if (selectedCount.value !== 3) return; weeklySaving.value = true; try { await request.put('/admin/career-projects/weekly-picks', { projectIds: selectedIds.value }); ElMessage.success('职业榜单本周精选已发布'); weeklyVisible.value = false } finally { weeklySaving.value = false } }
const restoreAlgorithm = async () => { await ElMessageBox.confirm('将清除本周人工顺序，并按最新职业评分重新选择 3 个项目。', '恢复算法精选', { type: 'warning' }); weeklySaving.value = true; try { const r = await request.post('/admin/career-projects/weekly-picks/restore-algorithm'); selectedIds.value = (r.data?.projects || []).map(item => item.id); ElMessage.success('已恢复算法精选') } finally { weeklySaving.value = false } }
onMounted(() => Promise.all([load(), loadSummary()]))
</script>

<style scoped>
.career-admin{display:grid;gap:14px}.page-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.page-heading h3{margin:0;color:#303133;font-size:18px}.page-heading p{margin:5px 0 0;color:#747c88;font-size:13px}.heading-actions{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8px}.summary-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:8px}.summary-item{display:flex;align-items:baseline;justify-content:space-between;padding:14px;border:1px solid #e4e7ed;border-radius:7px;background:#fff;color:#606266;cursor:pointer}.summary-item strong{color:#0f766e;font-size:24px}.summary-item.active{border-color:#0f766e;background:#f0fdfa}.toolbar{display:grid;grid-template-columns:minmax(240px,1fr) 180px auto;gap:8px;padding:12px;border:1px solid #e4e7ed;border-radius:7px;background:#fff}.project-name{display:flex;flex-direction:column;gap:4px}.project-name span{color:#909399;font-size:12px}.score{color:#0f766e;font-size:20px}.pagination-row{display:flex;justify-content:flex-end}.detail-title{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.detail-title small{color:#0f766e}.detail-title h4{margin:5px 0 0;font-size:20px}.detail-title b{color:#0f766e;font-size:34px}.detail-panel>p{color:#606266;line-height:1.7}.subheading{margin:22px 0 10px}.weekly-note{margin-top:0;color:#606266}.selected-list{min-height:82px;padding:12px 12px 12px 32px;border:1px solid #ebeef5;border-radius:6px;color:#606266;line-height:1.8}@media(max-width:900px){.page-heading{flex-direction:column}.heading-actions{justify-content:flex-start}.summary-grid{grid-template-columns:repeat(2,1fr)}.toolbar{grid-template-columns:1fr}.summary-item:last-child{grid-column:1/-1}}
</style>
