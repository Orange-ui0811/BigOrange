<template>
  <div class="career-page">
    <PageTour storageKey="career-center-v1" :steps="tourSteps" />

    <button class="back-link" type="button" @click="$router.push('/ai')">
      <ArrowLeft />
      返回 AI 工具
    </button>

    <header class="page-heading">
      <div>
        <span class="eyebrow">AI 大赛与能力参考</span>
        <h1>用真实任务，校准你的成长方向</h1>
        <p>参加 AI 大赛积累可展示的项目，再对照岗位要求补齐工程能力。</p>
      </div>
      <div class="section-switch" role="tablist" aria-label="功能分区">
        <button
          type="button"
          role="tab"
          :aria-selected="section === 'competitions'"
          :class="{ active: section === 'competitions' }"
          @click="section = 'competitions'"
        >
          <Trophy />
          大赛列表
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="section === 'career'"
          :class="{ active: section === 'career' }"
          @click="section = 'career'"
        >
          <Briefcase />
          工作能力参考
        </button>
      </div>
    </header>

    <main v-if="section === 'competitions'" class="competition-section">
      <div class="section-toolbar">
        <div>
          <h2>{{ competitionScope === 'ACTIVE' ? '正在进行与即将开始' : '历史比赛' }}</h2>
          <p>{{ competitionScope === 'ACTIVE' ? '选择一个与你当前能力匹配的真实赛题。' : '回看往期主题，寻找项目灵感。' }}</p>
        </div>
        <div class="toolbar-actions">
          <div class="scope-switch" aria-label="比赛范围">
            <button type="button" :class="{ active: competitionScope === 'ACTIVE' }" @click="changeScope('ACTIVE')">可参加</button>
            <button type="button" :class="{ active: competitionScope === 'HISTORY' }" @click="changeScope('HISTORY')">历史</button>
          </div>
          <el-button type="primary" @click="competitionDialog = true">
            <Plus />
            投稿比赛
          </el-button>
        </div>
      </div>

      <div class="reward-banner competition-reward">
        <Present />
        <div><strong>投稿通过可获得 20 配额奖励</strong><span>请提交真实、可公开访问的比赛信息。</span></div>
      </div>

      <div v-if="competitionLoading" class="competition-grid" aria-label="正在加载比赛">
        <div v-for="n in 6" :key="n" class="competition-card skeleton-card"><el-skeleton :rows="4" animated /></div>
      </div>

      <div v-else-if="competitionError" class="state-panel" role="alert">
        <Warning />
        <h3>比赛列表暂时无法加载</h3>
        <p>{{ competitionError }}</p>
        <el-button @click="loadCompetitions">重新加载</el-button>
      </div>

      <div v-else-if="!competitions.length" class="state-panel">
        <Trophy />
        <h3>{{ competitionScope === 'ACTIVE' ? '暂时没有可参加的比赛' : '暂时没有历史比赛' }}</h3>
        <p>你可以投稿自己发现的比赛，审核通过后会展示在这里。</p>
        <el-button type="primary" @click="competitionDialog = true">投稿比赛</el-button>
      </div>

      <div v-else class="competition-grid">
        <article v-for="item in competitions" :key="item.id" class="competition-card">
          <div class="card-topline">
            <span class="phase-tag" :class="item.phase.toLowerCase()">{{ phaseText(item.phase) }}</span>
            <span class="organizer"><OfficeBuilding />{{ item.organizer }}</span>
          </div>
          <h3>{{ item.name }}</h3>
          <p class="summary">{{ item.summary }}</p>
          <dl class="time-grid">
            <div><dt>开始</dt><dd>{{ formatTime(item.startTime) }}</dd></div>
            <div><dt>结束</dt><dd>{{ formatTime(item.endTime) }}</dd></div>
          </dl>
          <button type="button" class="detail-link" @click="openCompetition(item.sourceUrl)">
            查看详情
            <TopRight />
          </button>
        </article>
      </div>

      <el-pagination
        v-if="competitionTotal > competitionPageSize"
        class="pagination"
        layout="prev, pager, next"
        :total="competitionTotal"
        :page-size="competitionPageSize"
        v-model:current-page="competitionPage"
        @current-change="loadCompetitions"
      />
    </main>

    <main v-else class="career-section">
      <div class="section-toolbar">
        <div>
          <h2>从岗位要求反推学习重点</h2>
          <p>选择技术方向和岗位，查看真实 JD 中反复出现的能力要求。</p>
        </div>
        <el-button type="primary" @click="jobDialog = true">
          <Plus />
          投稿岗位
        </el-button>
      </div>

      <div class="reward-banner job-reward">
        <Present />
        <div><strong>投稿通过可获得 50 配额奖励</strong><span>请对招聘信息进行原创整理，避免直接搬运完整文案。</span></div>
      </div>

      <div class="career-layout">
        <section class="job-browser" aria-labelledby="job-browser-title">
          <h3 id="job-browser-title">岗位能力库</h3>
          <div class="field-block">
            <span class="field-label">技术方向</span>
            <div v-if="jobDirections.length" class="choice-list">
              <button
                v-for="direction in jobDirections"
                :key="direction"
                type="button"
                :class="{ active: jobDirection === direction }"
                @click="selectDirection(direction)"
              >{{ direction }}</button>
            </div>
            <el-empty v-else description="暂无已审核岗位" :image-size="64" />
          </div>
          <div v-if="jobDirection" class="field-block">
            <span class="field-label">岗位</span>
            <div v-if="jobList.length" class="choice-list">
              <button
                v-for="job in jobList"
                :key="job.id"
                type="button"
                :class="{ active: jobId === job.id }"
                @click="selectJob(job.id)"
              >{{ job.position }}</button>
            </div>
            <el-empty v-else description="该方向暂无岗位" :image-size="64" />
          </div>
          <div v-if="jobLoading" class="job-content"><el-skeleton :rows="8" animated /></div>
          <div v-else-if="jobDetail" class="job-content">
            <div v-safe-html="jobDetailHtml"></div>
            <p class="disclaimer">岗位信息由用户投稿，仅供学习参考。如涉及侵权，请联系 {{ LEGAL_CONTACT }}。</p>
          </div>
          <div v-else class="job-placeholder">
            <Search />
            <p>选择方向和岗位后，在这里查看详细要求。</p>
          </div>
        </section>

        <aside class="ability-panel" aria-labelledby="ability-title">
          <span class="panel-kicker">能力摘要</span>
          <h3 id="ability-title">{{ selectedJobName || '选择岗位后生成参考' }}</h3>
          <dl>
            <div><dt>技术栈</dt><dd>{{ jobTechStack || '等待选择' }}</dd></div>
            <div><dt>作品证据</dt><dd>Demo / 项目文档</dd></div>
            <div><dt>学习重点</dt><dd>工程化与最佳实践</dd></div>
            <div><dt>相关方向</dt><dd>{{ jobDirection || '等待选择' }}</dd></div>
          </dl>
        </aside>
      </div>
    </main>

    <el-dialog v-model="competitionDialog" title="投稿 AI 大赛" width="min(640px, calc(100vw - 32px))" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="比赛名称" required>
          <el-input v-model="competitionForm.name" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="官方网站" required>
          <el-input v-model="competitionForm.sourceUrl" placeholder="https://..." maxlength="768" />
          <span class="helper-text">必须是可公开访问的 HTTPS 官方页面。</span>
        </el-form-item>
        <el-form-item label="比赛简介" required>
          <el-input v-model="competitionForm.summary" type="textarea" :rows="4" maxlength="1200" show-word-limit />
        </el-form-item>
        <el-form-item label="举办方" required>
          <el-input v-model="competitionForm.organizer" maxlength="200" />
        </el-form-item>
        <div class="date-fields">
          <el-form-item label="开始时间（选填）">
            <el-date-picker v-model="competitionForm.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="尚未公布可留空" />
          </el-form-item>
          <el-form-item label="结束时间（选填）">
            <el-date-picker v-model="competitionForm.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="尚未公布可留空" />
          </el-form-item>
        </div>
        <div class="dialog-reward"><Present />审核通过后，奖励 20 配额</div>
      </el-form>
      <template #footer>
        <el-button @click="competitionDialog = false">取消</el-button>
        <el-button type="primary" :loading="competitionSubmitting" @click="submitCompetitionForm">提交审核</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="jobDialog" title="投稿岗位信息" width="min(680px, calc(100vw - 32px))" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="岗位名称" required><el-input v-model="jobForm.position" maxlength="200" /></el-form-item>
        <el-form-item label="技术方向" required>
          <el-select v-model="jobForm.direction" placeholder="选择方向" style="width:100%">
            <el-option v-for="direction in submitDirections" :key="direction" :label="direction" :value="direction" />
          </el-select>
        </el-form-item>
        <el-form-item label="JD 内容（Markdown）" required>
          <el-input v-model="jobForm.content" type="textarea" :rows="10" placeholder="请原创整理岗位职责与要求" />
        </el-form-item>
        <div class="notice-box">
          <strong>投稿须知</strong>
          <p>禁止直接复制企业官网完整招聘文案或搬运他人原创内容。平台会保留投稿账号、IP 和时间用于审核与侵权追溯。</p>
          <el-checkbox v-model="jobAgreed">我已阅读并同意投稿规则</el-checkbox>
        </div>
        <div class="dialog-reward"><Present />审核通过后，奖励 50 配额</div>
      </el-form>
      <template #footer>
        <el-button @click="jobDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!jobAgreed" :loading="jobSubmitting" @click="submitJobForm">提交审核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, Briefcase, OfficeBuilding, Present, TopRight,
  Plus, Search, Trophy, Warning,
} from '@element-plus/icons-vue'
import { marked } from 'marked'
import PageTour from '../components/PageTour.vue'
import request from '../api/request'
import { getCompetitions, submitCompetition } from '../api/competition'
import { getDirections, getJobDetail, getJobsByDirection } from '../api/job'
import { LEGAL_CONTACT } from '../content/legal'

const tourSteps = [
  { selector: '.section-switch', text: '这里可以切换大赛列表和工作能力参考，两类信息彼此独立。', expression: 'smile' },
  { selector: '.competition-reward', text: '发现真实比赛后可以投稿，审核通过会获得 20 配额奖励。', expression: 'wink' },
  { selector: '.competition-grid', text: '卡片会告诉你比赛内容、举办方和时间，点击查看详情会打开官方页面。', expression: 'sparkle' },
]

const section = ref('competitions')
const competitionScope = ref('ACTIVE')
const competitions = ref([])
const competitionLoading = ref(false)
const competitionError = ref('')
const competitionPage = ref(1)
const competitionPageSize = 12
const competitionTotal = ref(0)
const competitionDialog = ref(false)
const competitionSubmitting = ref(false)
const blankCompetition = () => ({ name: '', sourceUrl: '', summary: '', organizer: '', startTime: '', endTime: '' })
const competitionForm = ref(blankCompetition())

const loadCompetitions = async () => {
  competitionLoading.value = true
  competitionError.value = ''
  try {
    const response = await getCompetitions(competitionScope.value, competitionPage.value, competitionPageSize)
    if (response.code !== 1) throw new Error(response.msg || '请求失败')
    competitions.value = response.data?.records || []
    competitionTotal.value = response.data?.total || 0
  } catch (error) {
    competitions.value = []
    competitionError.value = error?.response?.data?.msg || error?.message || '请稍后重试'
  } finally {
    competitionLoading.value = false
  }
}

const changeScope = scope => {
  competitionScope.value = scope
  competitionPage.value = 1
  loadCompetitions()
}

const phaseText = phase => ({ UPCOMING: '即将开始', ONGOING: '进行中', ENDED: '已结束', UNSCHEDULED: '时间待公布' }[phase] || '待确认')
const formatTime = value => {
  if (!value) return '待确认'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(date)
}
const openCompetition = value => {
  try {
    const url = new URL(value)
    if (url.protocol !== 'https:') throw new Error()
    window.open(url.href, '_blank', 'noopener,noreferrer')
  } catch {
    ElMessage.error('比赛链接无效')
  }
}

const submitCompetitionForm = async () => {
  const form = competitionForm.value
  if (!form.name.trim() || !form.sourceUrl.trim() || !form.summary.trim() || !form.organizer.trim()) {
    ElMessage.warning('请完整填写比赛信息')
    return
  }
  if (!form.sourceUrl.toLowerCase().startsWith('https://')) {
    ElMessage.warning('官方网站必须使用 HTTPS')
    return
  }
  if (form.startTime && form.endTime && new Date(form.endTime) <= new Date(form.startTime)) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }
  competitionSubmitting.value = true
  try {
    const response = await submitCompetition({
      ...form,
      startTime: form.startTime || null,
      endTime: form.endTime || null,
    })
    if (response.code !== 1) throw new Error(response.msg || '投稿失败')
    ElMessage.success('投稿成功，审核通过后将奖励 20 配额')
    competitionDialog.value = false
    competitionForm.value = blankCompetition()
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '投稿失败')
  } finally {
    competitionSubmitting.value = false
  }
}

const jobDirections = ref([])
const submitDirections = ref([])
const jobDirection = ref('')
const jobList = ref([])
const jobId = ref(null)
const jobDetail = ref('')
const jobDetailHtml = ref('')
const jobLoading = ref(false)
const jobDialog = ref(false)
const jobAgreed = ref(false)
const jobSubmitting = ref(false)
const jobForm = ref({ position: '', direction: '', content: '' })

const loadDirections = async () => {
  try {
    const response = await getDirections()
    jobDirections.value = response.data || []
    submitDirections.value = response.data?.length ? response.data : ['AI Agent', 'RAG', '前端开发', '后端开发', '产品设计']
  } catch {
    submitDirections.value = ['AI Agent', 'RAG', '前端开发', '后端开发', '产品设计']
  }
}
const selectDirection = async direction => {
  jobDirection.value = direction
  jobId.value = null
  jobDetail.value = ''
  jobDetailHtml.value = ''
  try {
    const response = await getJobsByDirection(direction)
    jobList.value = response.data || []
  } catch {
    jobList.value = []
    ElMessage.error('岗位列表加载失败')
  }
}
const selectJob = async id => {
  jobId.value = id
  jobLoading.value = true
  try {
    const response = await getJobDetail(id)
    jobDetail.value = response.data?.displayContent || ''
    jobDetailHtml.value = marked.parse(jobDetail.value)
  } catch {
    ElMessage.error('岗位详情加载失败')
  } finally {
    jobLoading.value = false
  }
}
const selectedJobName = computed(() => jobList.value.find(item => item.id === jobId.value)?.position || '')
const jobTechStack = computed(() => {
  if (!jobDetail.value) return ''
  const keywords = ['Python', 'Java', 'Go', 'Rust', 'TypeScript', 'JavaScript', 'Vue', 'React', 'Spring', 'AI', 'LLM', 'RAG']
  const found = keywords.filter(item => jobDetail.value.toLowerCase().includes(item.toLowerCase()))
  return found.length ? found.join(' / ') : '详见岗位描述'
})
const submitJobForm = async () => {
  if (!jobForm.value.position.trim() || !jobForm.value.direction || !jobForm.value.content.trim()) {
    ElMessage.warning('请完整填写岗位信息')
    return
  }
  jobSubmitting.value = true
  try {
    const response = await request.post('/app/job/submit', jobForm.value)
    if (response.code !== 1) throw new Error(response.msg || '投稿失败')
    ElMessage.success('投稿成功，审核通过后将奖励 50 配额')
    jobDialog.value = false
    jobAgreed.value = false
    jobForm.value = { position: '', direction: '', content: '' }
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '投稿失败')
  } finally {
    jobSubmitting.value = false
  }
}

onMounted(() => {
  loadCompetitions()
  loadDirections()
})
</script>

<style scoped>
.career-page { width: 100%; max-width: 1180px; margin: 0 auto; }
.back-link { min-height: 44px; display: inline-flex; align-items: center; gap: 8px; padding: 0; border: 0; color: var(--text-secondary); background: transparent; font: 700 14px/1 var(--font-mono); cursor: pointer; }
.back-link svg { width: 18px; }
.back-link:hover, .back-link:focus-visible { color: var(--brand-strong); }
.page-heading { display: flex; align-items: flex-end; justify-content: space-between; flex-wrap: wrap; gap: 24px 32px; padding: 24px 0 28px; border-bottom: 1px solid var(--line); }
.page-heading > div:first-child { min-width: 0; flex: 1 1 520px; }
.eyebrow { display: inline-flex; min-height: 28px; align-items: center; padding: 0 10px; border: 1px solid var(--border-orange); border-radius: 8px; color: var(--brand-strong); background: #fffaf5; font: 800 12px/1 var(--font-mono); }
.page-heading h1 { margin: 12px 0 8px; font: 900 32px/1.2 var(--font-mono); letter-spacing: 0; }
.page-heading p, .section-toolbar p { margin: 0; color: var(--text-secondary); font-size: 15px; line-height: 1.7; }
.section-switch, .scope-switch { display: inline-grid; grid-auto-flow: column; gap: 4px; padding: 4px; border: 1px solid var(--line); border-radius: 8px; background: #f8fafc; }
.section-switch { flex: 0 0 auto; }
.section-switch button, .scope-switch button { min-height: 44px; display: inline-flex; align-items: center; justify-content: center; gap: 8px; padding: 0 16px; border: 0; border-radius: 6px; color: var(--text-secondary); background: transparent; font-weight: 800; cursor: pointer; white-space: nowrap; }
.section-switch button.active, .scope-switch button.active { color: #fff; background: var(--brand); }
.section-switch svg { width: 18px; }
.competition-section, .career-section { padding: 28px 0 48px; }
.section-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 16px; }
.section-toolbar h2 { margin: 0 0 6px; font: 900 24px/1.25 var(--font-mono); letter-spacing: 0; }
.toolbar-actions { display: flex; align-items: center; gap: 12px; }
.toolbar-actions :deep(.el-button), .section-toolbar > :deep(.el-button) { min-height: 44px; margin: 0; }
.toolbar-actions svg, .section-toolbar :deep(svg) { width: 17px; margin-right: 6px; }
.reward-banner { min-height: 64px; display: flex; align-items: center; gap: 14px; margin-bottom: 20px; padding: 12px 16px; border: 1px solid #99d5c8; border-radius: 8px; color: #164e43; background: #edfdf8; }
.reward-banner > svg { width: 24px; flex: 0 0 24px; }
.reward-banner div { display: grid; gap: 3px; }
.reward-banner strong { font-size: 15px; }
.reward-banner span { color: #356b62; font-size: 13px; }
.job-reward { border-color: #f6cf8d; color: #7a4210; background: #fff9ed; }
.job-reward span { color: #805a32; }
.competition-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.competition-card { min-height: 292px; display: flex; flex-direction: column; padding: 20px; border: 1px solid var(--line); border-radius: 8px; background: #fff; box-shadow: 0 8px 24px rgba(34, 48, 58, .06); transition: transform .2s ease, border-color .2s ease; }
.competition-card:hover { transform: translateY(-2px); border-color: #e4a873; }
.card-topline { min-height: 28px; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.phase-tag { display: inline-flex; align-items: center; min-height: 26px; padding: 0 9px; border-radius: 6px; color: #166534; background: #dcfce7; font-size: 12px; font-weight: 800; }
.phase-tag.upcoming { color: #1d4ed8; background: #dbeafe; }
.phase-tag.ended { color: #475569; background: #e2e8f0; }
.organizer { min-width: 0; display: inline-flex; align-items: center; justify-content: flex-end; gap: 5px; color: var(--text-muted); font-size: 12px; overflow-wrap: anywhere; }
.organizer svg { width: 14px; flex: 0 0 14px; }
.competition-card h3 { margin: 18px 0 10px; font: 900 19px/1.4 var(--font-mono); letter-spacing: 0; overflow-wrap: anywhere; }
.summary { display: -webkit-box; min-height: 67px; margin: 0; overflow: hidden; color: var(--text-secondary); font-size: 14px; line-height: 1.6; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.time-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin: 18px 0; }
.time-grid div { min-width: 0; padding-left: 10px; border-left: 2px solid #cbd5e1; }
.time-grid dt { color: var(--text-muted); font-size: 12px; }
.time-grid dd { margin: 4px 0 0; color: var(--text-primary); font-size: 12px; font-weight: 700; line-height: 1.45; }
.detail-link { min-height: 44px; display: inline-flex; align-items: center; justify-content: space-between; margin-top: auto; padding: 0 12px; border: 1px solid var(--line); border-radius: 6px; color: var(--brand-strong); background: #fff; font-weight: 800; cursor: pointer; }
.detail-link:hover, .detail-link:focus-visible { border-color: var(--brand); background: #fff7ed; }
.detail-link svg { width: 17px; }
.skeleton-card { justify-content: center; }
.state-panel { min-height: 280px; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 32px; border: 1px dashed #cbd5e1; border-radius: 8px; text-align: center; }
.state-panel > svg { width: 38px; color: var(--text-muted); }
.state-panel h3 { margin: 14px 0 6px; }
.state-panel p { margin: 0 0 18px; color: var(--text-secondary); }
.pagination { justify-content: center; margin-top: 24px; }
.career-layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 20px; align-items: start; }
.job-browser, .ability-panel { padding: 22px; border: 1px solid var(--line); border-radius: 8px; background: #fff; }
.job-browser > h3, .ability-panel h3 { margin: 0; font: 900 19px/1.4 var(--font-mono); letter-spacing: 0; }
.field-block { padding: 18px 0; border-bottom: 1px solid var(--line); }
.field-label, .panel-kicker { display: block; margin-bottom: 10px; color: var(--text-muted); font-size: 12px; font-weight: 800; text-transform: uppercase; }
.choice-list { display: flex; flex-wrap: wrap; gap: 8px; }
.choice-list button { min-height: 40px; padding: 0 13px; border: 1px solid var(--line); border-radius: 6px; color: var(--text-secondary); background: #fff; font-weight: 700; cursor: pointer; }
.choice-list button.active { border-color: var(--brand); color: #fff; background: var(--brand); }
.job-content { min-height: 220px; padding-top: 20px; color: var(--text-primary); font-size: 14px; line-height: 1.75; }
.job-content :deep(h1), .job-content :deep(h2), .job-content :deep(h3) { font-family: var(--font-mono); letter-spacing: 0; }
.job-content :deep(ul), .job-content :deep(ol) { padding-left: 22px; }
.disclaimer { margin-top: 20px; padding-top: 14px; border-top: 1px solid var(--line); color: var(--text-muted); font-size: 12px; }
.job-placeholder { min-height: 220px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--text-muted); text-align: center; }
.job-placeholder svg { width: 32px; }
.ability-panel { border-top: 4px solid #0f766e; }
.ability-panel dl { display: grid; gap: 0; margin: 18px 0 0; }
.ability-panel dl div { padding: 14px 0; border-top: 1px solid var(--line); }
.ability-panel dt { color: var(--text-muted); font-size: 12px; }
.ability-panel dd { margin: 5px 0 0; color: #115e59; font-size: 14px; font-weight: 800; overflow-wrap: anywhere; }
.date-fields { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.date-fields :deep(.el-date-editor) { width: 100%; }
.helper-text { display: block; margin-top: 6px; color: var(--text-muted); font-size: 12px; }
.dialog-reward { min-height: 44px; display: flex; align-items: center; gap: 9px; margin-top: 12px; padding: 0 12px; border-radius: 6px; color: #166534; background: #ecfdf5; font-weight: 800; }
.dialog-reward svg { width: 18px; }
.notice-box { padding: 14px; border: 1px solid #f6cf8d; border-radius: 8px; color: #74430f; background: #fff9ed; }
.notice-box p { margin: 6px 0 10px; font-size: 13px; line-height: 1.65; }
button:focus-visible, :deep(.el-button:focus-visible) { outline: 3px solid rgba(15,118,110,.32); outline-offset: 2px; }
@media (prefers-reduced-motion: reduce) { .competition-card { transition: none; } }
@media (max-width: 960px) {
  .competition-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .career-layout { grid-template-columns: 1fr; }
  .ability-panel { position: static; }
}
@media (max-width: 720px) {
  .page-heading, .section-toolbar { align-items: stretch; flex-direction: column; }
  .page-heading > div:first-child { width: 100%; flex: none; }
  .section-switch { width: 100%; grid-template-columns: 1fr 1fr; grid-auto-flow: row; }
  .toolbar-actions { align-items: stretch; flex-direction: column; }
  .scope-switch { width: 100%; grid-template-columns: 1fr 1fr; grid-auto-flow: row; }
  .competition-grid { grid-template-columns: 1fr; }
  .competition-card { min-height: 270px; }
  .date-fields { grid-template-columns: 1fr; gap: 0; }
  .page-heading h1 { font-size: 27px; }
}
</style>
