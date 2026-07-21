<template>
  <div class="open-source-admin">
    <div class="page-heading">
      <div><h3>开源贡献</h3><p>管理 GitHub 数据源、中文内容、参与判断和自动刷新状态。</p></div>
      <div class="heading-actions"><el-button :icon="Refresh" :loading="loading" @click="loadData">刷新页面</el-button><el-button type="primary" :icon="Plus" @click="editRepository()">添加仓库</el-button></div>
    </div>

    <div class="summary-band">
      <div><span>Issue 总数</span><strong>{{ summary.total || 0 }}</strong></div>
      <div><span>前台展示</span><strong>{{ summary.published || 0 }}</strong></div>
      <div><span>已翻译</span><strong>{{ summary.translated || 0 }}</strong></div>
      <div><span>待翻译/失败</span><strong>{{ summary.translationPending || 0 }}</strong></div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="Issue 管理" name="issues">
        <div class="filter-row">
          <el-input v-model="filters.keyword" clearable placeholder="标题或仓库" @keyup.enter="search" />
          <el-select v-model="filters.published" clearable placeholder="展示状态"><el-option label="前台展示" :value="1" /><el-option label="已下架" :value="0" /></el-select>
          <el-select v-model="filters.translationStatus" clearable placeholder="翻译状态"><el-option label="已完成" value="DONE" /><el-option label="待翻译" value="PENDING" /><el-option label="失败" value="FAILED" /></el-select>
          <el-button type="primary" @click="search">查询</el-button>
        </div>
        <el-table :data="records" v-loading="loading" empty-text="暂无 Issue" class="data-table">
          <el-table-column label="Issue" min-width="300">
            <template #default="{ row }"><div class="issue-cell"><strong>{{ row.titleZh || row.titleOriginal }}</strong><span>{{ row.repositoryFullName }} #{{ row.issueNumber }}</span></div></template>
          </el-table-column>
          <el-table-column label="判断" width="130"><template #default="{ row }"><el-tag size="small" :type="statusType(row.participationStatus)">{{ statusText(row.participationStatus) }}</el-tag></template></el-table-column>
          <el-table-column label="难度" width="90"><template #default="{ row }">{{ difficultyText(row.difficulty) }}</template></el-table-column>
          <el-table-column label="翻译" width="100"><template #default="{ row }"><el-tag size="small" :type="row.translationStatus === 'DONE' ? 'success' : row.translationStatus === 'FAILED' ? 'danger' : 'warning'">{{ translationText(row.translationStatus) }}</el-tag></template></el-table-column>
          <el-table-column label="展示" width="80"><template #default="{ row }"><el-switch :model-value="Boolean(row.published)" @change="togglePublished(row, $event)" /></template></el-table-column>
          <el-table-column prop="githubUpdatedAt" label="GitHub 更新" width="165"><template #default="{ row }">{{ formatTime(row.githubUpdatedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="210" fixed="right"><template #default="{ row }"><el-button size="small" text type="primary" @click="editIssue(row)">编辑</el-button><el-button size="small" text @click="verifyIssue(row)">核验</el-button><el-button size="small" text @click="translateIssue(row)">翻译</el-button></template></el-table-column>
        </el-table>
        <div class="pagination-row"><el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="loadData" /></div>
      </el-tab-pane>

      <el-tab-pane :label="`仓库源 (${repositories.length})`" name="repositories">
        <el-table :data="repositories" v-loading="loading" class="data-table" empty-text="暂无仓库">
          <el-table-column label="仓库" min-width="240"><template #default="{ row }"><div class="issue-cell"><strong>{{ row.fullName }}</strong><span>★ {{ row.stars || 0 }} · {{ row.primaryLanguage || '未知' }}</span></div></template></el-table-column>
          <el-table-column prop="techStack" label="技术栈" min-width="180" show-overflow-tooltip />
          <el-table-column label="级别" width="90"><template #default="{ row }">{{ row.tier === 'CORE' ? '核心' : '扩展' }}</template></el-table-column>
          <el-table-column prop="priority" label="优先级" width="90" />
          <el-table-column label="同步" width="110"><template #default="{ row }"><el-tag size="small" :type="row.lastSyncStatus === 'SUCCESS' ? 'success' : row.lastSyncStatus === 'FAILED' ? 'danger' : 'info'">{{ row.lastSyncStatus || '未同步' }}</el-tag></template></el-table-column>
          <el-table-column label="启用" width="75"><template #default="{ row }"><el-switch :model-value="Boolean(row.enabled)" @change="toggleRepository(row, $event)" /></template></el-table-column>
          <el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button size="small" text type="primary" @click="editRepository(row)">编辑</el-button><el-button size="small" text :loading="syncingId === row.id" @click="refreshRepository(row)">同步</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="刷新状态" name="refresh">
        <div class="refresh-panel">
          <div><span>完整刷新</span><strong>{{ refreshStatus.fullRunning ? '运行中' : '空闲' }}</strong><small>每两天抓取仓库与 Issue，并批量生成中文标题和摘要。</small></div>
          <div><span>轻量核验</span><strong>{{ refreshStatus.verifyRunning ? '运行中' : '空闲' }}</strong><small>每 12 小时核验 open、assignee、关联 PR 和评论区认领线索。</small></div>
          <div><span>最近完成</span><strong>{{ formatTime(refreshStatus.lastFullFinish) || '尚未执行' }}</strong><small>保存 {{ refreshStatus.lastSaved || 0 }} 条，翻译 {{ refreshStatus.lastTranslated || 0 }} 条。</small></div>
          <div v-if="refreshStatus.lastFullError" class="error-block"><span>最近错误</span><strong>{{ refreshStatus.lastFullError }}</strong></div>
        </div>
        <div class="refresh-actions"><el-button type="primary" :disabled="refreshStatus.fullRunning" @click="triggerRefresh('full')">立即完整刷新</el-button><el-button :disabled="refreshStatus.verifyRunning" @click="triggerRefresh('verify')">立即核验现有 Issue</el-button></div>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="issueDrawer" title="编辑 Issue 展示信息" size="min(620px, 94vw)" destroy-on-close>
      <el-form v-if="issueForm" label-position="top">
        <el-form-item label="英文标题"><el-input :model-value="issueForm.titleOriginal" disabled /></el-form-item>
        <el-form-item label="中文标题"><el-input v-model="issueForm.titleZh" maxlength="500" /></el-form-item>
        <el-form-item label="一句中文摘要"><el-input v-model="issueForm.summaryZh" type="textarea" :rows="4" maxlength="900" /></el-form-item>
        <div class="form-grid"><el-form-item label="参与状态"><el-select v-model="issueForm.participationStatus"><el-option label="可尝试" value="TRY" /><el-option label="可能被认领" value="POSSIBLY_CLAIMED" /><el-option label="不建议" value="NOT_RECOMMENDED" /></el-select></el-form-item><el-form-item label="难度"><el-select v-model="issueForm.difficulty"><el-option label="入门" value="BEGINNER" /><el-option label="简单" value="EASY" /><el-option label="中等" value="MEDIUM" /></el-select></el-form-item></div>
        <el-form-item label="前台展示"><el-switch v-model="issueForm.published" /></el-form-item>
        <el-button type="primary" :loading="saving" @click="saveIssue">保存</el-button>
      </el-form>
    </el-drawer>

    <el-dialog v-model="repositoryDialog" :title="repositoryForm.id ? '编辑仓库源' : '添加仓库源'" width="min(560px, 94vw)" destroy-on-close>
      <el-form label-position="top"><el-form-item label="GitHub 仓库"><el-input v-model="repositoryForm.fullName" placeholder="owner/repository" /></el-form-item><div class="form-grid"><el-form-item label="级别"><el-select v-model="repositoryForm.tier"><el-option label="核心" value="CORE" /><el-option label="扩展" value="EXTENDED" /></el-select></el-form-item><el-form-item label="优先级"><el-input-number v-model="repositoryForm.priority" :min="1" :max="9999" /></el-form-item></div><el-form-item label="技术栈（可人工补充）"><el-input v-model="repositoryForm.techStack" /></el-form-item><div class="form-grid"><el-form-item label="系统要求"><el-input v-model="repositoryForm.systemRequirement" placeholder="例如 Windows / macOS / Linux" /></el-form-item><el-form-item label="内存要求"><el-input v-model="repositoryForm.memoryRequirement" placeholder="未明确可留空" /></el-form-item><el-form-item label="磁盘要求"><el-input v-model="repositoryForm.diskRequirement" placeholder="未明确可留空" /></el-form-item></div><el-form-item label="环境提示"><el-input v-model="repositoryForm.environmentNotes" type="textarea" :rows="3" maxlength="1000" /></el-form-item><el-form-item label="启用"><el-switch v-model="repositoryForm.enabled" /></el-form-item></el-form>
      <template #footer><el-button @click="repositoryDialog = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveRepository">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import request from '../api/request'

const activeTab = ref('issues'), loading = ref(false), saving = ref(false), syncingId = ref(null)
const records = ref([]), repositories = ref([]), summary = ref({}), refreshStatus = ref({})
const page = ref(1), total = ref(0), pageSize = 20
const filters = ref({ keyword: '', published: null, translationStatus: '' })
const issueDrawer = ref(false), issueForm = ref(null), repositoryDialog = ref(false)
const blankRepository = () => ({ id: null, fullName: '', tier: 'EXTENDED', priority: 100, enabled: true, techStack: '', systemRequirement: '', memoryRequirement: '', diskRequirement: '', environmentNotes: '' })
const repositoryForm = ref(blankRepository())
const ok = response => response?.code === 1
const formatTime = value => value ? String(value).replace('T', ' ').substring(0, 19) : ''
const statusText = value => ({ TRY: '可尝试', POSSIBLY_CLAIMED: '可能被认领', NOT_RECOMMENDED: '不建议' }[value] || '待判断')
const statusType = value => ({ TRY: 'success', POSSIBLY_CLAIMED: 'warning', NOT_RECOMMENDED: 'danger' }[value] || 'info')
const difficultyText = value => ({ BEGINNER: '入门', EASY: '简单', MEDIUM: '中等' }[value] || '待评估')
const translationText = value => ({ DONE: '已完成', PENDING: '待翻译', FAILED: '失败' }[value] || value)

const loadData = async () => {
  loading.value = true
  try { const response = await request.get('/admin/open-source', { params: { page: page.value, size: pageSize, ...filters.value } }); if (ok(response)) { records.value = response.data.records || []; repositories.value = response.data.repositories || []; summary.value = response.data.summary || {}; refreshStatus.value = response.data.refresh || {}; total.value = Number(response.data.total || 0) } }
  finally { loading.value = false }
}
const search = () => { page.value = 1; loadData() }
const editIssue = row => { issueForm.value = { ...row, published: Boolean(row.published) }; issueDrawer.value = true }
const saveIssue = async () => { saving.value = true; try { const response = await request.put(`/admin/open-source/issues/${issueForm.value.id}`, issueForm.value); if (ok(response)) { ElMessage.success('Issue 已保存'); issueDrawer.value = false; loadData() } } finally { saving.value = false } }
const togglePublished = async (row, value) => { const response = await request.put(`/admin/open-source/issues/${row.id}`, { published: value }); if (ok(response)) { row.published = value; ElMessage.success(value ? '已展示' : '已下架') } }
const verifyIssue = async row => { const response = await request.post(`/admin/open-source/issues/${row.id}/verify`, {}, { timeout: 90000 }); if (ok(response)) { ElMessage.success('核验完成'); loadData() } }
const translateIssue = async row => { const response = await request.post(`/admin/open-source/issues/${row.id}/translate`, {}, { timeout: 150000 }); if (ok(response)) { ElMessage.success('翻译完成'); loadData() } }
const editRepository = row => { repositoryForm.value = row ? { ...row, enabled: Boolean(row.enabled) } : blankRepository(); repositoryDialog.value = true }
const saveRepository = async () => { saving.value = true; try { const form = repositoryForm.value; const response = form.id ? await request.put(`/admin/open-source/repositories/${form.id}`, form) : await request.post('/admin/open-source/repositories', form); if (ok(response)) { ElMessage.success('仓库源已保存'); repositoryDialog.value = false; loadData() } } finally { saving.value = false } }
const toggleRepository = async (row, value) => { const response = await request.put(`/admin/open-source/repositories/${row.id}`, { ...row, enabled: value }); if (ok(response)) { row.enabled = value; ElMessage.success('仓库状态已更新') } }
const refreshRepository = async row => { syncingId.value = row.id; try { const response = await request.post(`/admin/open-source/repositories/${row.id}/refresh`, {}, { timeout: 120000 }); if (ok(response)) { ElMessage.success(`已同步 ${response.data.saved || 0} 条 Issue`); loadData() } } finally { syncingId.value = null } }
const triggerRefresh = async type => { const response = await request.post(`/admin/open-source/refresh/${type}`); if (ok(response)) { ElMessage.success(response.data.started ? '任务已启动' : '任务正在运行'); loadData() } }
onMounted(loadData)
</script>

<style scoped>
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }.page-heading h3 { margin: 0; color: #303133; font-size: 18px; }.page-heading p { margin: 5px 0 0; color: #909399; font-size: 13px; }.heading-actions { display: flex; gap: 8px; }
.summary-band { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-bottom: 16px; border: 1px solid #e4e7ed; border-radius: 6px; background: #fff; }.summary-band div { min-height: 84px; padding: 15px 18px; border-right: 1px solid #ebeef5; }.summary-band div:last-child { border-right: 0; }.summary-band span { display: block; color: #909399; font-size: 12px; }.summary-band strong { display: block; margin-top: 8px; color: #303133; font-size: 24px; }
.filter-row { display: grid; grid-template-columns: minmax(220px, 1fr) 150px 150px auto; gap: 10px; margin-bottom: 12px; }.filter-row :deep(.el-input__wrapper), .filter-row :deep(.el-select__wrapper) { min-height: 40px; }
.data-table { width: 100%; border: 1px solid #e4e7ed; border-radius: 6px; }.issue-cell strong, .issue-cell span { display: block; }.issue-cell span { margin-top: 4px; color: #909399; font-size: 11px; }.pagination-row { display: flex; justify-content: flex-end; margin-top: 16px; }
.refresh-panel { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1px; border: 1px solid #e4e7ed; background: #e4e7ed; }.refresh-panel > div { min-height: 130px; padding: 18px; background: #fff; }.refresh-panel span, .refresh-panel strong, .refresh-panel small { display: block; }.refresh-panel span { color: #909399; font-size: 12px; }.refresh-panel strong { margin: 10px 0; color: #303133; }.refresh-panel small { color: #606266; line-height: 1.6; }.refresh-panel .error-block { grid-column: 1 / -1; min-height: auto; }.error-block strong { color: #c45656; font-size: 13px; }.refresh-actions { display: flex; gap: 8px; margin-top: 16px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }.form-grid :deep(.el-select) { width: 100%; }
@media (max-width: 800px) { .summary-band { grid-template-columns: repeat(2, minmax(0, 1fr)); }.summary-band div:nth-child(2) { border-right: 0; }.filter-row { grid-template-columns: 1fr 1fr; }.refresh-panel { grid-template-columns: 1fr; }.page-heading { flex-direction: column; } }
@media (max-width: 520px) { .summary-band, .filter-row, .form-grid { grid-template-columns: 1fr; }.summary-band div { border-right: 0; }.heading-actions { width: 100%; flex-wrap: wrap; } }
</style>
