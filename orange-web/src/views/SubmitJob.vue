<template>
  <div class="jobs-page">
    <!-- Back -->
    <button class="detail-back" @click="$router.push('/ai')">返回 AI 工具</button>

    <!-- ═══ Compact Head ═══ -->
    <div class="compact-head">
      <div>
        <span class="eyebrow">工作能力参考</span>
        <h2 v-if="!showForm">按方向查看岗位，反推作品能力要求。</h2>
        <h2 v-else>投稿岗位信息</h2>
        <p v-if="!showForm">选择技术方向和岗位，查看 JD 详情。所有岗位由社区用户投稿，仅供参考。</p>
        <p v-else>填写真实岗位信息，提交后等待审核。禁止搬运他人原创 JD。</p>
      </div>
      <el-button v-if="!showForm" type="primary" @click="showForm = true">投稿岗位</el-button>
      <el-button v-else @click="showForm = false">返回浏览</el-button>
    </div>

    <!-- ═══ 浏览模式：岗位浏览 + 能力参考 ═══ -->
    <div v-if="!showForm" class="ai-layout">
      <!-- 左：岗位选择 + 详情 -->
      <div class="panel panel-left">
        <div class="filter-row">
          <span class="filter-label">方向</span>
          <div class="segmented">
            <span
              v-for="d in jobDirections" :key="d"
              :class="{ active: jobDirection === d }"
              @click="selectDirection(d)"
            >{{ d }}</span>
          </div>
        </div>
        <div class="filter-row" v-if="jobList.length">
          <span class="filter-label">岗位</span>
          <div class="segmented">
            <span
              v-for="j in jobList" :key="j.id"
              :class="{ active: jobId === j.id }"
              @click="selectJob(j.id)"
            >{{ j.position }}</span>
          </div>
        </div>

        <!-- 岗位详情 -->
        <div v-if="jobDetail" class="job-detail-card">
          <strong class="detail-label">岗位详情</strong>
          <div class="job-detail-content" v-html="jobDetailHtml"></div>
          <p class="job-disclaimer">本页面岗位信息由平台注册用户自主上传分享，本平台仅提供信息存储空间服务，不对内容版权、真实性承担责任。如涉及侵权，请联系 1281748852@qq.com，我们将在 3 个工作日内下架对应内容。</p>
        </div>
        <el-empty v-if="jobDirection && !jobId && !jobDetail" description="请选择岗位查看详情" />
        <el-empty v-if="!jobDirection" description="请选择技术方向" />
      </div>

      <!-- 右：能力参考 -->
      <div class="panel panel-right">
        <h3>能力参考</h3>
        <div class="mini-list">
          <div class="mini-list-item">
            <span>技术栈</span>
            <strong class="price">{{ jobTechStack || '选择岗位后显示' }}</strong>
          </div>
          <div class="mini-list-item">
            <span>作品证据</span>
            <strong class="price">Demo / 项目文档</strong>
          </div>
          <div class="mini-list-item">
            <span>学习重点</span>
            <strong class="price">工程化与最佳实践</strong>
          </div>
          <div class="mini-list-item">
            <span>相关方向</span>
            <strong class="price">{{ jobDirection || '—' }}</strong>
          </div>
        </div>
        <div class="trust-box" style="margin-top:14px;">
          提示：岗位描述、职责、要求以 Markdown 渲染。平台展示用户投稿内容，并保留侵权联系和下架说明。
        </div>
      </div>
    </div>

    <!-- ═══ 投稿模式：表单 ═══ -->
    <div v-if="showForm" class="submit-section">
      <div class="panel submit-panel">
        <el-form :model="form" label-position="top">
          <el-form-item label="岗位名称">
            <el-input v-model="form.position" placeholder="如：AI Agent 算法工程师" maxlength="200" />
          </el-form-item>
          <el-form-item label="技术方向">
            <el-select v-model="form.direction" placeholder="选择方向" style="width:100%">
              <el-option v-for="d in directions" :key="d" :label="d" :value="d" />
            </el-select>
          </el-form-item>
          <el-form-item label="JD 内容（Markdown）">
            <el-input v-model="form.content" type="textarea" :rows="12" placeholder="请粘贴岗位描述、职责和要求（支持 Markdown）" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="submit" :loading="submitting" :disabled="!agreed" style="width:100%">提交审核</el-button>
          </el-form-item>
        </el-form>

        <div class="notice-box">
          <p class="notice-title">投稿须知：</p>
          <p>1. 禁止直接复制企业官网完整招聘文案、搬运他人原创 JD</p>
          <p>2. 上传内容需自己原创改写，或持有企业官方转载授权</p>
          <p>3. 因侵权引发的法律责任全部由上传用户自行承担</p>
          <p>4. 平台有权随时删除涉嫌侵权内容，多次侵权可封禁账号</p>
          <p>5. 平台会留存你的投稿账号、IP、时间用于侵权追溯</p>
          <el-checkbox v-model="agreed">我已阅读并同意以上条款</el-checkbox>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { marked } from 'marked'
import request from '../api/request'
import { getDirections, getJobsByDirection, getJobDetail } from '../api/job'

const router = useRouter()

/* ---- 浏览模式 ---- */
const showForm = ref(false)
const jobDirections = ref([])
const jobDirection = ref('')
const jobList = ref([])
const jobId = ref(null)
const jobDetail = ref('')
const jobDetailHtml = ref('')

const selectDirection = async (d) => {
  jobDirection.value = d
  jobId.value = null
  jobDetail.value = ''
  try { const r = await getJobsByDirection(d); jobList.value = r.data || [] } catch {}
}
const selectJob = async (id) => {
  jobId.value = id
  try {
    const r = await getJobDetail(id)
    jobDetail.value = r.data?.displayContent || ''
    jobDetailHtml.value = marked.parse(jobDetail.value)
  } catch {}
}

const jobTechStack = computed(() => {
  if (!jobDetail.value) return ''
  // 简单提取技术关键词
  const techs = ['Python', 'Java', 'Go', 'Rust', 'TypeScript', 'JavaScript', 'Vue', 'React', 'Spring', 'AI', 'LLM', 'RAG']
  const found = techs.filter(t => jobDetail.value.toLowerCase().includes(t.toLowerCase()))
  return found.length ? found.join(' / ') : '详见岗位描述'
})

/* ---- 投稿模式 ---- */
const form = ref({ position: '', direction: '', content: '' })
const directions = ref([])
const agreed = ref(false)
const submitting = ref(false)

const submit = async () => {
  if (!form.value.position) { ElMessage.warning('请输入岗位名称'); return }
  if (!form.value.content) { ElMessage.warning('请输入JD内容'); return }
  submitting.value = true
  try {
    await request.post('/app/job/submit', form.value)
    ElMessage.success('投稿成功，等待审核')
    showForm.value = false
    form.value = { position: '', direction: '', content: '' }
    agreed.value = false
    loadDirections()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '投稿失败') }
  finally { submitting.value = false }
}

const loadDirections = async () => {
  try { const r = await getDirections(); jobDirections.value = r.data || []; directions.value = r.data || [] } catch {}
}

onMounted(() => { loadDirections() })
</script>

<style scoped>
.jobs-page { max-width: 1100px; }

/* Back */
.detail-back {
  margin-bottom: 14px;
  display: inline-flex; align-items: center; gap: 6px;
  min-height: 36px;
  border: 1px solid var(--line);
  border-radius: 14px; padding: 0 14px;
  color: var(--text-secondary); background: #fff;
  font-family: var(--font-mono); font-weight: 800; font-size: 12px;
  cursor: pointer; transition: background .15s, color .15s;
}
.detail-back:hover { background: #fff7ed; color: var(--brand-strong); }

/* Compact Head */
.compact-head {
  display: flex; align-items: flex-end; justify-content: space-between; gap: 18px;
  margin-bottom: 20px; padding: 22px;
  border: 1px solid rgba(254, 215, 170, .76);
  border-radius: 28px;
  background: rgba(255,255,255,.86);
  box-shadow: var(--shadow-card-lg);
  flex-wrap: wrap;
}
.compact-head h2 {
  margin: 0;
  font-family: var(--font-mono);
  font-size: clamp(22px, 3.5vw, 32px);
  line-height: 1.08; letter-spacing: -.05em;
}
.compact-head p {
  max-width: 680px; margin: 6px 0 0;
  color: var(--text-secondary); line-height: 1.6; font-size: 13px;
}
.eyebrow {
  display: inline-flex; align-items: center; gap: 8px;
  min-height: 28px; padding: 0 12px;
  border: 1px solid var(--border-orange);
  border-radius: 999px;
  color: var(--brand-strong); background: #fffaf5;
  font-size: 12px; font-weight: 900; font-family: var(--font-mono);
}

/* Layout */
.ai-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(280px, .7fr);
  gap: 16px;
  align-items: start;
}
.panel {
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: 28px;
  background: #fff;
  box-shadow: var(--shadow-card);
}
.panel-left { border-color: rgba(254, 215, 170, .8); }

/* Filter rows */
.filter-row {
  display: flex; align-items: center; gap: 10px;
  margin-bottom: 14px; flex-wrap: wrap;
}
.filter-label {
  font-size: 12px; color: var(--text-muted);
  font-family: var(--font-mono); font-weight: 800;
  letter-spacing: .03em;
  flex-shrink: 0;
}

/* Segmented */
.segmented {
  display: inline-flex; flex-wrap: wrap; gap: 4px;
  padding: 4px;
  border: 1px solid var(--border-orange);
  border-radius: 14px;
  background: #fff7ed;
}
.segmented span {
  min-height: 28px; display: inline-flex; align-items: center;
  padding: 0 10px;
  border-radius: 10px;
  color: var(--text-secondary);
  font-size: 12px; font-weight: 800; font-family: var(--font-mono);
  cursor: pointer; transition: background .15s, color .15s;
}
.segmented span:hover { color: var(--brand-strong); }
.segmented span.active {
  color: #fff; background: var(--brand);
  box-shadow: 2px 2px 0 rgba(154,83,28,.12);
}

/* Job detail */
.job-detail-card {
  margin-top: 16px;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: #fff;
}
.detail-label {
  font-family: var(--font-mono);
  font-size: 14px;
  letter-spacing: -.03em;
  display: block;
  margin-bottom: 12px;
}
.job-detail-content {
  line-height: 1.7;
  font-size: 13px;
  color: var(--text-primary);
}
.job-detail-content :deep(h2),
.job-detail-content :deep(h3) { font-family: var(--font-mono); }
.job-detail-content :deep(ul),
.job-detail-content :deep(ol) { padding-left: 20px; }
.job-disclaimer {
  margin-top: 14px; padding-top: 12px;
  border-top: 1px solid var(--border-light);
  font-size: 11px; color: var(--text-muted); line-height: 1.5;
}

/* Right panel */
.panel-right h3 {
  margin: 0 0 14px;
  font-family: var(--font-mono);
  letter-spacing: -.03em;
  font-size: 18px;
}
.mini-list { display: grid; gap: 10px; }
.mini-list-item {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
  display: flex; align-items: center; justify-content: space-between;
  gap: 10px;
  font-size: 13px;
}
.mini-list-item span { color: var(--text-secondary); }
.price { color: var(--brand-strong); font-family: var(--font-mono); font-weight: 900; font-size: 12px; }
.trust-box {
  padding: 12px; border-radius: 18px;
  background: #fffbeb; border: 1px solid #fde68a;
  color: #92400e; font-size: 12px; line-height: 1.6;
}

/* Submit section */
.submit-section { max-width: 720px; }
.submit-panel {
  border-color: rgba(254, 215, 170, .8);
  padding: 24px;
}
.notice-box {
  background: #fffbeb;
  padding: 14px;
  border-radius: 18px;
  font-size: 12px; line-height: 1.8;
  color: var(--text-primary);
  border: 1px solid #fde68a;
  margin-top: 16px;
}
.notice-title { font-weight: 700; margin: 0 0 4px; font-family: var(--font-mono); }

@media (max-width: 780px) {
  .ai-layout { grid-template-columns: 1fr; }
}
</style>
