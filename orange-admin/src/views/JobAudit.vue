<template>
  <div class="submission-page">
    <header class="page-head">
      <div>
        <span class="eyebrow">社区内容治理</span>
        <h1>投稿审核</h1>
        <p>核验用户投稿，控制公开内容质量，并确认配额奖励状态。</p>
      </div>
      <div class="head-actions">
        <el-button v-if="contentType === 'COMPETITION'" type="primary" @click="openCompetitionCreate">
          <Plus />
          新增比赛
        </el-button>
      </div>
    </header>

    <section class="ai-review-control" aria-label="AI 审核设置">
      <div>
        <strong>岗位 AI 审核</strong>
        <span>{{ contentType === 'JOB' ? '开启后处理待审核岗位投稿' : '当前仅支持岗位投稿，比赛投稿仍由管理员人工核验' }}</span>
      </div>
      <el-switch
        v-model="aiEnabled"
        :disabled="contentType !== 'JOB'"
        active-text="已开启"
        inactive-text="已关闭"
        @change="toggleAi"
      />
    </section>

    <div class="content-switch" role="tablist" aria-label="投稿类型">
      <button type="button" role="tab" :aria-selected="contentType === 'JOB'" :class="{ active: contentType === 'JOB' }" @click="changeContentType('JOB')">
        <Briefcase />
        岗位投稿
      </button>
      <button type="button" role="tab" :aria-selected="contentType === 'COMPETITION'" :class="{ active: contentType === 'COMPETITION' }" @click="changeContentType('COMPETITION')">
        <Trophy />
        比赛投稿
      </button>
    </div>

    <section class="audit-workspace">
      <div class="status-row">
        <div class="status-tabs">
          <button v-for="item in statusOptions" :key="item.value" type="button" :class="{ active: status === item.value }" @click="changeStatus(item.value)">
            {{ item.label }}
          </button>
        </div>
        <span class="record-count">共 {{ total }} 条</span>
      </div>

      <el-table v-if="contentType === 'JOB'" :data="list" v-loading="loading" empty-text="暂无岗位投稿" row-key="id">
        <el-table-column prop="id" label="ID" width="72" />
        <el-table-column prop="position" label="岗位" min-width="200" show-overflow-tooltip />
        <el-table-column prop="direction" label="方向" width="140" show-overflow-tooltip />
        <el-table-column prop="submitterId" label="投稿用户" width="110">
          <template #default="{ row }">{{ row.submitterId ? `#${row.submitterId}` : '系统导入' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="投稿时间" width="180" />
        <el-table-column label="奖励" width="110">
          <template #default>{{ rewardLabel(rewardPricing.job) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="showJobDetail(row)">查看</el-button>
            <template v-if="status === 'PENDING'">
              <el-button size="small" type="success" @click="approveJob(row.id)">通过</el-button>
              <el-button size="small" type="danger" text @click="rejectJob(row.id)">拒绝</el-button>
            </template>
            <template v-else-if="status === 'APPROVED'">
              <el-button size="small" @click="editJob(row)">编辑</el-button>
              <el-button size="small" type="warning" :loading="syncingId === row.id" @click="syncKnowledge(row.id)">更新知识库</el-button>
            </template>
            <el-popconfirm title="确认永久删除这条投稿？" @confirm="deleteJob(row.id)">
              <template #reference><el-button size="small" type="danger" text>删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else :data="list" v-loading="loading" empty-text="暂无比赛投稿" row-key="id">
        <el-table-column prop="id" label="ID" width="72" />
        <el-table-column prop="name" label="比赛名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="organizer" label="举办方" min-width="150" show-overflow-tooltip />
        <el-table-column label="比赛时间" width="190">
          <template #default="{ row }">{{ shortDate(row.startTime) }} 至 {{ shortDate(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="submissionSource" label="来源" width="100">
          <template #default="{ row }">{{ row.submissionSource === 'ADMIN' ? '管理员' : `用户 #${row.submitterId}` }}</template>
        </el-table-column>
        <el-table-column label="奖励" width="120">
          <template #default="{ row }">
            <span v-if="row.rewardGranted" class="reward-done">已发 {{ row.rewardQuota }}</span>
            <span v-else>{{ row.submissionSource === 'USER' ? '通过后 20' : '不发放' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openCompetitionEdit(row)">查看与编辑</el-button>
            <template v-if="status === 'PENDING'">
              <el-button size="small" type="success" @click="approveCompetition(row.id)">通过</el-button>
              <el-button size="small" type="danger" text @click="rejectCompetition(row.id)">拒绝</el-button>
            </template>
            <el-popconfirm title="确认永久删除这条比赛？" @confirm="deleteCompetition(row.id)">
              <template #reference><el-button size="small" type="danger" text>删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > pageSize"
        class="pagination"
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        v-model:current-page="page"
        @current-change="loadList"
      />
    </section>

    <el-dialog v-model="jobDetailVisible" title="岗位投稿详情" width="min(720px, calc(100vw - 32px))">
      <div class="detail-meta">
        <span>投稿用户：{{ jobDetail.submitterId ? `#${jobDetail.submitterId}` : '系统导入' }}</span>
        <span>审核通过奖励：{{ rewardLabel(rewardPricing.job) }}</span>
      </div>
      <div class="job-detail">{{ jobDetail.content || '无内容' }}</div>
      <template v-if="status === 'PENDING'" #footer>
        <el-button @click="jobDetailVisible = false">关闭</el-button>
        <el-button type="danger" @click="rejectJob(jobDetail.id)">拒绝</el-button>
        <el-button type="success" @click="approveJob(jobDetail.id)">通过并奖励</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="jobEditVisible" title="编辑岗位" width="min(620px, calc(100vw - 32px))">
      <el-form label-position="top">
        <el-form-item label="岗位名称"><el-input v-model="jobEditForm.position" /></el-form-item>
        <el-form-item label="技术方向"><el-input v-model="jobEditForm.direction" /></el-form-item>
        <el-form-item label="展示内容"><el-input v-model="jobEditForm.displayContent" type="textarea" :rows="10" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="jobEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveJob">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="competitionEditorVisible"
      :title="competitionMode === 'create' ? '新增比赛' : '查看与编辑比赛'"
      width="min(760px, calc(100vw - 32px))"
      destroy-on-close
    >
      <div v-if="competitionMode === 'edit'" class="detail-meta">
        <span>投稿来源：{{ competitionEditor.submissionSource === 'USER' ? `用户 #${competitionEditor.submitterId}` : '管理员录入' }}</span>
        <span v-if="competitionEditor.rewardGranted" class="reward-done">奖励已发放：{{ competitionEditor.rewardQuota }} 配额</span>
        <span v-else>
          审核通过奖励：{{ competitionEditor.submissionSource === 'USER' ? rewardLabel(rewardPricing.competition) : '不发放' }}
        </span>
      </div>
      <el-form label-position="top">
        <el-form-item label="比赛名称" required><el-input v-model="competitionEditor.name" maxlength="200" /></el-form-item>
        <el-form-item label="官方网站" required>
          <div class="url-input-row">
            <el-input v-model="competitionEditor.sourceUrl" placeholder="https://..." maxlength="768" />
            <el-button :loading="fetchingPreview" @click="fetchPreview"><Download />从网页提取</el-button>
          </div>
          <span class="helper-text">提取结果只作为候选，不会自动覆盖当前内容。</span>
        </el-form-item>
        <el-form-item label="比赛简介" required><el-input v-model="competitionEditor.summary" type="textarea" :rows="4" maxlength="1200" show-word-limit /></el-form-item>
        <el-form-item label="举办方" required><el-input v-model="competitionEditor.organizer" maxlength="200" /></el-form-item>
        <div class="date-fields">
          <el-form-item label="开始时间（选填）">
            <el-date-picker v-model="competitionEditor.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="尚未公布可留空" />
          </el-form-item>
          <el-form-item label="结束时间（选填）">
            <el-date-picker v-model="competitionEditor.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="尚未公布可留空" />
          </el-form-item>
        </div>
        <el-form-item v-if="competitionEditor.rejectReason" label="拒绝原因">
          <el-input :model-value="competitionEditor.rejectReason" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="competitionEditorVisible = false">取消</el-button>
        <el-button :loading="saving" @click="saveCompetition(false)">保存</el-button>
        <el-button
          v-if="competitionMode === 'edit' && competitionEditor.auditStatus === 'PENDING'"
          type="success"
          :loading="saving"
          @click="saveCompetition(true)"
        >保存并通过</el-button>
        <el-button v-if="competitionMode === 'create'" type="primary" :loading="saving" @click="createCompetition">发布比赛</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="网页提取候选信息" width="min(620px, calc(100vw - 32px))">
      <p class="preview-note">请核验以下内容。点击应用后才会写入编辑表单，仍需手动保存或审批。</p>
      <dl class="preview-list">
        <div><dt>比赛名称</dt><dd>{{ preview.name || '未提取到' }}</dd></div>
        <div><dt>举办方</dt><dd>{{ preview.organizer || '未提取到' }}</dd></div>
        <div><dt>比赛简介</dt><dd>{{ preview.summary || '未提取到' }}</dd></div>
        <div><dt>开始时间</dt><dd>{{ preview.startTime || '未提取到' }}</dd></div>
        <div><dt>结束时间</dt><dd>{{ preview.endTime || '未提取到' }}</dd></div>
        <div><dt>最终网址</dt><dd>{{ preview.sourceUrl || '未提取到' }}</dd></div>
      </dl>
      <template #footer>
        <el-button @click="previewVisible = false">取消</el-button>
        <el-button type="primary" @click="applyPreview">应用候选信息</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Briefcase, Download, Plus, Trophy } from '@element-plus/icons-vue'
import request from '../api/request'

const rewardPricing = ref({ work: 50, job: 50, competition: 20 })
const rewardLabel = amount => amount > 0 ? `${amount} 配额` : '不发放'
const rewardActionText = amount => amount > 0 ? `发放 ${amount} 配额` : '不发放配额奖励'

const contentType = ref('JOB')
const status = ref('PENDING')
const statusOptions = [
  { value: 'PENDING', label: '待审核' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已拒绝' },
]
const list = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = 50
const total = ref(0)
const aiEnabled = ref(false)
const saving = ref(false)
const syncingId = ref(null)
const ensureOk = response => {
  if (response?.code !== 1) throw new Error(response?.msg || '操作失败')
  return response
}

const loadList = async () => {
  loading.value = true
  try {
    if (contentType.value === 'JOB') {
      const response = await request.get(status.value === 'PENDING' ? '/admin/job/pending' : '/admin/job/list', {
        params: { status: status.value, page: page.value, size: pageSize },
      })
      ensureOk(response)
      list.value = status.value === 'PENDING' ? (response.data || []) : (response.data?.records || [])
      total.value = status.value === 'PENDING' ? list.value.length : (response.data?.total || 0)
    } else {
      const response = await request.get('/admin/competition/list', {
        params: { status: status.value, page: page.value, size: pageSize },
      })
      ensureOk(response)
      list.value = response.data?.records || []
      total.value = response.data?.total || 0
    }
  } catch (error) {
    list.value = []
    total.value = 0
    ElMessage.error(error?.response?.data?.msg || error?.message || '列表加载失败')
  } finally {
    loading.value = false
  }
}
const changeContentType = value => {
  contentType.value = value
  status.value = 'PENDING'
  page.value = 1
  loadList()
}
const changeStatus = value => {
  status.value = value
  page.value = 1
  loadList()
}

const jobDetailVisible = ref(false)
const jobDetail = ref({})
const showJobDetail = async row => {
  try {
    const response = await request.get(`/admin/job/${row.id}`)
    ensureOk(response)
    jobDetail.value = response.data || {}
    jobDetailVisible.value = true
  } catch {
    ElMessage.error('详情加载失败')
  }
}
const approveJob = async id => {
  try {
    await ElMessageBox.confirm(`审核通过后将${rewardActionText(rewardPricing.value.job)}，确认继续？`, '确认岗位投稿', { type: 'warning' })
    ensureOk(await request.post(`/admin/job/${id}/approve`))
    ElMessage.success(`已通过，${rewardActionText(rewardPricing.value.job)}`)
    jobDetailVisible.value = false
    loadList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.response?.data?.msg || error?.message || '操作失败')
  }
}
const rejectJob = async id => {
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因（可选）', '拒绝岗位投稿', { inputType: 'textarea' })
    ensureOk(await request.post(`/admin/job/${id}/reject`, { reason: value || '' }))
    ElMessage.success('已拒绝')
    jobDetailVisible.value = false
    loadList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.response?.data?.msg || error?.message || '操作失败')
  }
}
const deleteJob = async id => {
  try { ensureOk(await request.delete(`/admin/job/${id}`)); ElMessage.success('已删除'); loadList() }
  catch { ElMessage.error('删除失败') }
}
const jobEditVisible = ref(false)
const jobEditForm = ref({ id: null, position: '', direction: '', displayContent: '' })
const editJob = async row => {
  try {
    const response = await request.get(`/admin/job/${row.id}`)
    ensureOk(response)
    jobEditForm.value = {
      id: row.id,
      position: response.data?.position || '',
      direction: response.data?.direction || '',
      displayContent: response.data?.displayContent || response.data?.content || '',
    }
    jobEditVisible.value = true
  } catch { ElMessage.error('详情加载失败') }
}
const saveJob = async () => {
  saving.value = true
  try {
    ensureOk(await request.put(`/admin/job/${jobEditForm.value.id}`, jobEditForm.value))
    ElMessage.success('已保存')
    jobEditVisible.value = false
    loadList()
  } catch { ElMessage.error('保存失败') }
  finally { saving.value = false }
}
const syncKnowledge = async id => {
  syncingId.value = id
  try { ensureOk(await request.post(`/admin/job/${id}/sync-knowledge`)); ElMessage.success('知识库已更新') }
  catch { ElMessage.error('同步失败') }
  finally { syncingId.value = null }
}
const toggleAi = async value => {
  try {
    ensureOk(await request.put('/admin/job/ai-auto/toggle', { enabled: value }))
    ElMessage.success(value ? '岗位 AI 托管已开启' : '岗位 AI 托管已关闭')
    if (value) loadList()
  } catch {
    aiEnabled.value = !value
    ElMessage.error('切换失败')
  }
}

const blankCompetition = () => ({
  id: null, name: '', sourceUrl: '', summary: '', organizer: '',
  startTime: '', endTime: '', auditStatus: 'APPROVED',
  submissionSource: 'ADMIN', submitterId: null, rewardGranted: false, rewardQuota: 0,
})
const competitionEditorVisible = ref(false)
const competitionMode = ref('create')
const competitionEditor = ref(blankCompetition())
const openCompetitionCreate = () => {
  competitionMode.value = 'create'
  competitionEditor.value = blankCompetition()
  competitionEditorVisible.value = true
}
const openCompetitionEdit = async row => {
  try {
    const response = await request.get(`/admin/competition/${row.id}`)
    ensureOk(response)
    competitionMode.value = 'edit'
    competitionEditor.value = { ...blankCompetition(), ...response.data }
    competitionEditorVisible.value = true
  } catch { ElMessage.error('比赛详情加载失败') }
}
const validateCompetition = () => {
  const form = competitionEditor.value
  if (!form.name?.trim() || !form.sourceUrl?.trim() || !form.summary?.trim() || !form.organizer?.trim()) {
    ElMessage.warning('请完整填写比赛信息')
    return false
  }
  if (!form.sourceUrl.toLowerCase().startsWith('https://')) {
    ElMessage.warning('官方网站必须使用 HTTPS')
    return false
  }
  if (form.startTime && form.endTime && new Date(form.endTime) <= new Date(form.startTime)) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return false
  }
  return true
}
const payload = () => {
  const { name, sourceUrl, summary, organizer, startTime, endTime } = competitionEditor.value
  return {
    name, sourceUrl, summary, organizer,
    startTime: startTime || null,
    endTime: endTime || null,
  }
}
const createCompetition = async () => {
  if (!validateCompetition()) return
  saving.value = true
  try {
    ensureOk(await request.post('/admin/competition', payload()))
    ElMessage.success('比赛已发布')
    competitionEditorVisible.value = false
    status.value = 'APPROVED'
    loadList()
  } catch (error) { ElMessage.error(error?.response?.data?.msg || error?.message || '发布失败') }
  finally { saving.value = false }
}
const saveCompetition = async approve => {
  if (!validateCompetition()) return
  saving.value = true
  try {
    ensureOk(await request.put(`/admin/competition/${competitionEditor.value.id}`, payload()))
    if (approve) {
      ensureOk(await request.post(`/admin/competition/${competitionEditor.value.id}/approve`))
      ElMessage.success('已保存、通过并发放奖励')
    } else {
      ElMessage.success('已保存')
    }
    competitionEditorVisible.value = false
    loadList()
  } catch (error) { ElMessage.error(error?.response?.data?.msg || error?.message || '保存失败') }
  finally { saving.value = false }
}
const approveCompetition = async id => {
  try {
    await ElMessageBox.confirm(
      `用户投稿通过后将${rewardActionText(rewardPricing.value.competition)}，管理员录入内容不发奖励。确认继续？`,
      '确认比赛投稿',
      { type: 'warning' }
    )
    ensureOk(await request.post(`/admin/competition/${id}/approve`))
    ElMessage.success('比赛已通过，用户奖励已处理')
    loadList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.response?.data?.msg || error?.message || '操作失败')
  }
}
const rejectCompetition = async id => {
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因（可选）', '拒绝比赛投稿', { inputType: 'textarea' })
    ensureOk(await request.post(`/admin/competition/${id}/reject`, { reason: value || '' }))
    ElMessage.success('已拒绝')
    loadList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.response?.data?.msg || error?.message || '操作失败')
  }
}
const deleteCompetition = async id => {
  try { ensureOk(await request.delete(`/admin/competition/${id}`)); ElMessage.success('已删除'); loadList() }
  catch { ElMessage.error('删除失败') }
}

const fetchingPreview = ref(false)
const previewVisible = ref(false)
const preview = ref({})
const fetchPreview = async () => {
  if (!competitionEditor.value.sourceUrl?.trim()) {
    ElMessage.warning('请先填写官方网站')
    return
  }
  fetchingPreview.value = true
  try {
    const response = await request.post('/admin/competition/fetch-preview', { sourceUrl: competitionEditor.value.sourceUrl })
    ensureOk(response)
    preview.value = response.data || {}
    previewVisible.value = true
  } catch (error) { ElMessage.error(error?.response?.data?.msg || error?.message || '网页提取失败') }
  finally { fetchingPreview.value = false }
}
const applyPreview = () => {
  for (const key of ['name', 'sourceUrl', 'summary', 'organizer', 'startTime', 'endTime']) {
    if (preview.value[key]) competitionEditor.value[key] = preview.value[key]
  }
  previewVisible.value = false
  ElMessage.success('候选信息已应用，请核验后保存')
}
const shortDate = value => value ? value.replace('T', ' ').slice(0, 16) : '待确认'

onMounted(async () => {
  loadList()
  try {
    const rewardResponse = await request.get('/app/config/rewards')
    ensureOk(rewardResponse)
    rewardPricing.value = { ...rewardPricing.value, ...(rewardResponse.data || {}) }
  } catch {}
  try {
    const response = await request.get('/admin/job/ai-auto/status')
    ensureOk(response)
    aiEnabled.value = response.data?.enabled || false
  } catch {}
})
</script>

<style scoped>
.submission-page { width: 100%; }
.page-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding-bottom: 22px; border-bottom: 1px solid #e5e7eb; }
.eyebrow { color: #b45309; font-size: 12px; font-weight: 800; text-transform: uppercase; }
.page-head h1 { margin: 8px 0 6px; color: #111827; font-size: 28px; line-height: 1.2; letter-spacing: 0; }
.page-head p { margin: 0; color: #64748b; font-size: 14px; }
.head-actions { min-height: 44px; display: flex; align-items: center; gap: 12px; }
.head-actions :deep(.el-button) { min-height: 44px; }
.head-actions svg { width: 17px; margin-right: 6px; }
.ai-review-control { display: flex; min-height: 64px; align-items: center; justify-content: space-between; gap: 20px; margin: 16px 0 0; padding: 12px 16px; border: 1px solid #fed7aa; border-left: 4px solid #f97316; border-radius: 8px; background: #fffaf5; }
.ai-review-control strong,.ai-review-control span { display: block; }
.ai-review-control strong { color: #9a3412; font-size: 14px; }
.ai-review-control span { margin-top: 4px; color: #64748b; font-size: 12px; line-height: 1.5; }
.content-switch { display: inline-grid; grid-template-columns: 1fr 1fr; gap: 4px; margin: 22px 0 16px; padding: 4px; border: 1px solid #dbe1e8; border-radius: 8px; background: #f8fafc; }
.content-switch button { min-width: 150px; min-height: 44px; display: inline-flex; align-items: center; justify-content: center; gap: 8px; border: 0; border-radius: 6px; color: #475569; background: transparent; font-weight: 800; cursor: pointer; }
.content-switch button.active { color: #fff; background: #d96523; }
.content-switch svg { width: 18px; }
.audit-workspace { padding: 18px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
.status-row { min-height: 44px; display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.status-tabs { display: flex; gap: 8px; }
.status-tabs button { min-height: 38px; padding: 0 14px; border: 1px solid #dbe1e8; border-radius: 6px; color: #475569; background: #fff; font-weight: 700; cursor: pointer; }
.status-tabs button.active { border-color: #0f766e; color: #0f766e; background: #ecfdf5; }
.record-count { color: #64748b; font-size: 13px; }
.reward-done { color: #047857; font-weight: 800; }
.pagination { justify-content: center; margin-top: 18px; }
.detail-meta { display: flex; flex-wrap: wrap; gap: 10px 24px; margin-bottom: 16px; padding: 12px 14px; border-radius: 6px; color: #0f5d53; background: #ecfdf5; font-size: 13px; font-weight: 700; }
.job-detail { max-height: 58vh; overflow: auto; padding: 16px; border: 1px solid #e5e7eb; border-radius: 6px; color: #334155; font-size: 14px; line-height: 1.75; white-space: pre-wrap; }
.url-input-row { width: 100%; display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; }
.url-input-row :deep(.el-button) { min-height: 40px; }
.url-input-row svg { width: 16px; margin-right: 5px; }
.helper-text { display: block; margin-top: 6px; color: #64748b; font-size: 12px; }
.date-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.date-fields :deep(.el-date-editor) { width: 100%; }
.preview-note { margin: 0 0 14px; color: #475569; line-height: 1.6; }
.preview-list { margin: 0; border-top: 1px solid #e5e7eb; }
.preview-list div { display: grid; grid-template-columns: 100px minmax(0, 1fr); gap: 16px; padding: 12px 0; border-bottom: 1px solid #e5e7eb; }
.preview-list dt { color: #64748b; font-size: 13px; }
.preview-list dd { margin: 0; color: #1e293b; line-height: 1.55; overflow-wrap: anywhere; }
button:focus-visible, :deep(.el-button:focus-visible) { outline: 3px solid rgba(15,118,110,.28); outline-offset: 2px; }
@media (max-width: 720px) {
  .page-head { align-items: stretch; flex-direction: column; }
  .head-actions { justify-content: space-between; }
  .ai-review-control { align-items: flex-start; flex-direction: column; }
  .content-switch { width: 100%; }
  .content-switch button { min-width: 0; }
  .status-row { align-items: flex-start; flex-direction: column; }
  .status-tabs { width: 100%; }
  .status-tabs button { flex: 1; }
  .audit-workspace { padding: 12px; overflow-x: auto; }
  .url-input-row, .date-fields { grid-template-columns: 1fr; }
}
</style>
