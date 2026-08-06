<template>
  <div>
    <PageTour storageKey="trend" :steps="tourSteps" />

    <!-- Compact Head -->
    <div class="compact-head">
      <div>
        <span class="eyebrow">趋势分析</span>
        <h2>精准推荐、自由提问、工作能力参考。</h2>
        <p>Vibe Coding 开源项目热度 · 每周更新 · AI 辅助编程趋势洞察</p>
      </div>
    </div>

    <el-tabs v-model="trendTab" class="trend-tabs">
      <el-tab-pane label="精准推荐" name="v2" />
      <el-tab-pane label="自由提问" name="v1" />
      <el-tab-pane label="工作能力参考" name="jobs" />
    </el-tabs>

    <div v-if="trendTab==='v2'">
      <p class="section-subtitle">Vibe Coding 开源项目热度 · 每周更新 · AI 辅助编程趋势洞察</p>

      <el-row :gutter="16">
        <!-- 左：图谱 -->
        <el-col :span="18">
          <el-card shadow="never" class="graph-card">
            <div class="card-header-row">
              <div>
                <span class="card-title-text">Topic 全景图</span>
                <el-tooltip content="节点=技术方向（大小=热度），连线=共享编程语言≥2种（越粗关联越强）" placement="top">
                  <el-icon class="help-icon"><QuestionFilled /></el-icon>
                </el-tooltip>
              </div>
              <el-radio-group v-model="viewMode" size="small" @change="onViewChange">
                <el-radio-button value="week">本周</el-radio-button>
                <el-radio-button value="last">上周</el-radio-button>
              </el-radio-group>
            </div>
            <div ref="chartRef" class="chart-container" v-loading="graphLoading"></div>
          </el-card>
        </el-col>

        <!-- 右：节点详情 -->
        <el-col :span="6">
          <el-card shadow="never" class="detail-card">
            <template v-if="selectedEdge">
              <h4 class="detail-section-title">Topic 关联</h4>
              <el-tag size="small" type="info" class="mr-6">{{ selectedEdge.sourceName }}</el-tag>
              <span class="text-muted">↔</span>
              <el-tag size="small" type="info" class="ml-6">{{ selectedEdge.targetName }}</el-tag>
              <el-divider class="compact-divider" />
              <p class="detail-text">这两个方向共享 <b>{{ selectedEdge.weight }}</b> 种编程语言，技能可迁移。</p>
              <div v-if="selectedEdge.sharedLangs?.length" class="mt-8">
                <div class="section-label">共享语言</div>
                <el-tag v-for="l in selectedEdge.sharedLangs" :key="l" size="small" type="success" class="tag-item">{{ l }}</el-tag>
              </div>
              <el-button size="small" type="primary" class="mt-12" @click="compareTopics(selectedEdge)">AI 对比分析</el-button>
            </template>
            <template v-if="selectedNode">
              <div class="card-header-row">
                <div>
                  <span class="card-title-text">{{ selectedNode.name }}</span>
                  <el-tag size="small" type="info" class="ml-6">{{ selectedNode.group }}</el-tag>
                </div>
                <el-radio-group v-model="detailTab" size="small">
                  <el-radio-button value="insights">洞察</el-radio-button>
                  <el-radio-button value="metrics">分析</el-radio-button>
                  <el-radio-button value="repos">仓库</el-radio-button>
                </el-radio-group>
              </div>
              <el-divider class="compact-divider-sm" />
              <!-- 分析视图 -->
              <template v-if="detailTab==='metrics'">
                <div v-if="selectedNode.metrics?.summary" class="summary-box">
                  {{ selectedNode.metrics.summary }}
                </div>
                <el-descriptions :column="1" size="small" border>
                  <el-descriptions-item label="总 Stars">{{ formatNum(selectedNode.totalStars) }}</el-descriptions-item>
                  <el-descriptions-item label="仓库数(全时段)">{{ selectedNode.repoCount }}</el-descriptions-item>
                  <el-descriptions-item v-if="selectedNode.metrics?.vcCount > 0" label="Vibe Coding">
                    <el-tag type="warning" size="small">{{ selectedNode.metrics.vcCount }} 个项目 · {{ selectedNode.metrics.vcRatio }}%</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item v-if="selectedNode.metrics?.weeklyStars" label="本周新增⭐">{{ formatNum(selectedNode.metrics.weeklyStars) }}</el-descriptions-item>
                  <el-descriptions-item v-if="selectedNode.metrics" label="拥挤度">
                    <el-tag v-if="selectedNode.metrics?.crowdedness" :type="crowdType(selectedNode.metrics.crowdedness)" size="small">{{ selectedNode.metrics.crowdedness }}</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item v-if="selectedNode.metrics" label="难度">
                    <el-tag v-if="selectedNode.metrics?.difficulty" :type="diffType(selectedNode.metrics.difficulty)" size="small">{{ selectedNode.metrics.difficulty }}</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item v-if="selectedNode.metrics" label="活跃度">{{ selectedNode.metrics.activity }}</el-descriptions-item>
                  <el-descriptions-item v-if="selectedNode.metrics?.opportunityScore" label="综合评分">
                    <el-progress :percentage="selectedNode.metrics.opportunityScore" :color="scoreColor(selectedNode.metrics.opportunityScore)" :stroke-width="6" />
                  </el-descriptions-item>
                </el-descriptions>
                <div v-if="selectedDelta !== null" class="delta-row">
                  较上周: <span :class="selectedDelta>=0 ? 'delta-up' : 'delta-down'">{{ selectedDelta >= 0 ? '↑' : '↓' }}{{ Math.abs(selectedDelta) }}%</span>
                </div>
                <div v-if="selectedNode.metrics?.insight" class="insight-box">
                  {{ selectedNode.metrics.insight }}
                </div>
              </template>
              <!-- 仓库视图 -->
              <template v-if="detailTab==='repos'">
                <div v-if="selectedNode.topRepos?.length">
                  <div class="section-label">Top 5 仓库</div>
                  <div v-for="r in selectedNode.topRepos" :key="r.name" class="repo-item">
                    <a :href="r.htmlUrl" target="_blank" class="repo-link">{{ r.name }}</a>
                    <span class="repo-stars">⭐{{ formatNum(r.stars) }}</span>
                    <div class="repo-desc">{{ r.description || '' }}</div>
                  </div>
                </div>
                <div v-if="selectedNode.topLanguages?.length" class="mt-12">
                  <div class="section-label">主要语言</div>
                  <el-tag v-for="l in selectedNode.topLanguages" :key="l.name" size="small" class="tag-item">{{ l.name }} ({{ l.count }})</el-tag>
                </div>
              </template>
            </template>
            <template v-if="!selectedNode && !selectedEdge || (selectedNode && detailTab==='insights')">
              <div class="section-label mb-8">趋势洞察</div>
              <div v-for="card in insightCards" :key="card.title" class="insight-card">
                <div class="insight-card-title">{{ card.title }}</div>
                <el-tag v-for="n in card.items" :key="n" :type="card.tagType" size="small" class="tag-item">{{ n }}</el-tag>
              </div>
            </template>
          </el-card>
        </el-col>
      </el-row>

      <!-- 智能分析面板 -->
      <div class="mt-16">
        <!-- 步骤式表单卡片 -->
        <div class="orange-form-card" data-tour="form-panel">
          <!-- 标题栏 + 历史记录 -->
          <div class="form-header-row">
            <div>
              <span class="form-title">智能分析</span>
              <span class="form-title-sub">告诉我你的想法</span>
            </div>
            <TrendHistoryPopover v-if="hasAppToken" :history="history" @select="loadHistory" @delete="delHistory" @clear="clearHistory" />
          </div>
          <!-- 步骤指示器 -->
          <div class="steps-bar">
            <div class="steps-track">
              <div v-for="(s,i) in formSteps" :key="i" class="step-item" :class="{ active: i === currentStep, done: i < currentStep }" @click="i < currentStep && goStep(i)">
                <div class="step-circle">{{ i < currentStep ? '✓' : s.icon }}</div>
                <div class="step-label">{{ s.label }}</div>
              </div>
            </div>
          </div>

          <!-- 单张卡片 - 带滚动橙子动画 -->
          <div class="step-card-wrapper">
            <Transition :name="animDirection">
              <div class="step-card" :key="currentStep">
                <div class="card-bg-orange"></div>
                <!-- 步骤1: 方向 -->
                <template v-if="currentStep === 0">
                  <div class="step-title">你对哪个技术方向感兴趣？</div>
                  <el-select v-model="profile.topics" multiple placeholder="选择方向（可多选）" size="large" class="step-select" placement="bottom-start">
                    <el-option v-for="t in topicOptions" :key="t" :label="t" :value="t" />
                  </el-select>
                  <el-input v-model="profile.customTopics" placeholder="或输入自定义方向..." class="step-input-mt" maxlength="200" />
                </template>
                <!-- 步骤2: 目的 -->
                <template v-if="currentStep === 1">
                  <div class="step-title">为什么想做这个方向的项目？</div>
                  <el-select v-model="profile.purpose" placeholder="选择你的目的" size="large" class="step-select" placement="bottom-start" @change="handlePurposeChange">
                    <el-option v-for="t in purposeOptions" :key="t" :label="t" :value="t" />
                  </el-select>
                  <el-input v-model="profile.customPurpose" placeholder="或输入自定义目的..." class="step-input-mt" maxlength="200" />
                </template>
                <!-- 步骤3: 基础 -->
                <template v-if="currentStep === 2">
                  <div class="step-title">你的编程基础怎么样？</div>
                  <el-select v-model="profile.experience" placeholder="编程经验" size="large" class="step-select" placement="bottom-start" @change="handleExperienceChange">
                    <el-option v-for="t in expOptions" :key="t" :label="t" :value="t" />
                  </el-select>
                  <el-select v-model="profile.languages" multiple placeholder="掌握的语言（可多选）" size="large" class="step-input-mt" placement="bottom-start">
                    <el-option v-for="t in langOptions" :key="t" :label="t" :value="t" />
                  </el-select>
                  <el-input v-model="profile.customLang" placeholder="或输入自定义..." class="step-input-mt" maxlength="200" />
                </template>
                <!-- 步骤4: 时间 -->
                <template v-if="currentStep === 3">
                  <div class="step-title">你每周能投入多少时间？</div>
                  <el-select v-model="profile.timeCommitment" placeholder="选择时间投入" size="large" class="step-select" placement="bottom-start">
                    <el-option v-for="t in timeOptions" :key="t" :label="t" :value="t" />
                  </el-select>
                  <el-input v-model="profile.customTime" placeholder="或输入自定义..." class="step-input-mt" maxlength="200" />
                </template>
              </div>
            </Transition>
          </div>

          <!-- 底部按钮 -->
          <div class="nav-btn-row">
            <button v-if="currentStep > 0" class="nav-btn" @click="goStep(currentStep-1)">← 上一步</button>
            <div v-else />
            <button v-if="currentStep < 3" class="orange-btn" @click="goStep(currentStep+1)">下一步 →</button>
            <div v-else />
          </div>
          <div v-if="v2Error" class="error-box">{{ v2Error }}</div>
        </div>
        <TrendPersonaAnalysisCard
          v-model:use-persona="profile.usePersona"
          v-model:use-rag="profile.useRag"
          v-model:prefer-beginner-projects="profile.preferBeginnerProjects"
          v-model:prefer-career-projects="profile.preferCareerProjects"
          :analyzing="analyzing"
          @analyze="startJuicing"
        >
          <template #actions>
            <TrendHistoryPopover v-if="hasAppToken" :history="history" @select="loadHistory" @delete="delHistory" @clear="clearHistory" />
          </template>
        </TrendPersonaAnalysisCard>
      </div>
    </div>

    <!-- 岗位浏览 -->
    <div v-if="trendTab==='jobs'" class="jobs-section">
      <div class="jobs-filter-row">
        <span class="filter-label">筛选：</span>
        <el-select v-model="jobDirection" placeholder="选择方向" size="small" class="job-select-dir" @change="loadJobList">
          <el-option v-for="d in jobDirections" :key="d" :label="d" :value="d" />
        </el-select>
        <el-select v-model="jobId" placeholder="选择岗位" size="small" class="job-select-pos" @change="loadJobDetail">
          <el-option v-for="j in jobList" :key="j.id" :label="j.position" :value="j.id" />
        </el-select>
        <el-button size="small" type="primary" class="jobs-submit-btn" @click="$router.push('/submit-job')">投稿岗位</el-button>
      </div>
      <div v-if="jobDetail" class="job-detail-card" v-safe-html="jobDetailHtml"></div>
      <p v-if="jobDetail" class="job-disclaimer">本页面岗位信息由平台注册用户自主上传分享，仅供参考。如涉及侵权，请联系 {{ LEGAL_CONTACT }}，平台将按举报、下架与申诉流程及时核验处理。</p>
      <el-empty v-if="jobDirection && !jobId && !jobDetail" description="请选择岗位" />
      <el-empty v-if="!jobDirection" description="请选择方向查看岗位" />
    </div>

    <!-- 榨汁动画 -->
    <div v-if="juicing" class="juice-wrapper">
      <div class="juice-scene">
        <span class="j-orange" style="animation-delay:.0s">🍊</span>
        <span class="j-orange" style="animation-delay:.3s">🍊</span>
        <span class="j-orange" style="animation-delay:.6s">🍊</span>
        <span class="j-orange" style="animation-delay:.9s">🍊</span>
        <span class="j-cup">🥤</span>
      </div>
      <div class="juice-text-status">{{ squeezeText }}</div>
    </div>

    <!-- 结果区 -->
    <div v-if="v2Result" class="result-section">
      <!-- 领域洞察 -->
      <div class="insights-result-card">
        <div class="insights-result-title">领域洞察</div>
        <el-row :gutter="12">
          <el-col :span="8">
            <div class="leaf-card">
              <span class="leaf-icon">🍃</span>
              <div class="leaf-label">发展趋势</div>
              <div class="leaf-text">{{ v2Result.analysis?.trend || '-' }}</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="leaf-card">
              <span class="leaf-icon">🔮</span>
              <div class="leaf-label">发展前景</div>
              <div class="leaf-text">{{ v2Result.analysis?.prospect || '-' }}</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="leaf-card">
              <span class="leaf-icon">📈</span>
              <div class="leaf-label">市场需求</div>
              <div class="leaf-text">{{ v2Result.analysis?.demand || '-' }}</div>
            </div>
          </el-col>
        </el-row>
        <div v-if="v2Result.analysis?.summary" class="insights-summary">
          {{ v2Result.analysis.summary }}
        </div>
      </div>

      <section v-if="v2Result.beginnerCases?.length" class="beginner-cases">
        <div class="rec-section-title">已核验的新手项目案例</div>
        <p class="beginner-cases-note">这些仓库用于把趋势结论落到具体实践，不作为趋势热度的统计证据。</p>
        <div class="beginner-case-grid">
          <article v-for="project in v2Result.beginnerCases" :key="project.id" class="beginner-case-card">
            <div><span>{{ project.primaryLanguage || 'GitHub' }}</span><b>{{ project.beginnerScore }}/100</b></div>
            <h3>{{ project.descriptionZh || project.repositoryName }}</h3>
            <p><b>匹配说明：</b>{{ project.matchExplanation || project.beginnerReason }}</p>
            <footer><a :href="project.htmlUrl" target="_blank" rel="noopener noreferrer">查看仓库</a><button type="button" @click="importBeginnerCase(project)">用于方案设计</button></footer>
          </article>
        </div>
      </section>

      <section v-if="v2Result.careerCases?.length" class="beginner-cases career-cases">
        <div class="rec-section-title">已核验的职业成长项目案例</div>
        <p class="beginner-cases-note">这些仓库用于说明岗位能力、作品证明和面试讨论点，不作为趋势热度的统计证据。</p>
        <div class="beginner-case-grid">
          <article v-for="project in v2Result.careerCases" :key="project.id" class="beginner-case-card career-case-card">
            <div><span>{{ project.bestRoleName || '职业成长' }}</span><b>{{ project.overallScore }}/100</b></div>
            <h3>{{ project.summaryZh || project.repositoryName }}</h3>
            <p><b>匹配说明：</b>{{ project.matchExplanation || project.careerValue || project.descriptionOriginal }}</p>
            <footer><a :href="project.htmlUrl" target="_blank" rel="noopener noreferrer">查看仓库</a><button type="button" @click="importCareerCase(project)">用于方案设计</button></footer>
          </article>
        </div>
      </section>

      <!-- 推荐项目 -->
      <div class="rec-section-title">为你推荐</div>
      <el-row :gutter="12">
        <el-col :span="8" v-for="(r, i) in v2Result.recommendations" :key="i">
          <div class="rec-card">
            <div class="rec-header">
              <div class="rec-name-area">
                <span class="rec-num">#{{ i+1 }}</span>
                <span class="rec-name">{{ r.name }}</span>
              </div>
              <div class="rec-meta-area">
                <el-tag :type="(r.difficulty==='入门'?'success':r.difficulty==='中等'?'warning':'danger')||'info'" size="small" effect="dark">{{ r.difficulty || '未知' }}</el-tag>
                <span class="rec-score">{{ r.score }}分</span>
              </div>
            </div>
            <p class="rec-desc">{{ r.description }}</p>
            <div class="rec-details">
              <p v-if="r.vibeCodingReason" class="rec-vibe-box"><b>Vibe Coding：</b>{{ r.vibeCodingReason }}</p>
              <p class="rec-bullet"><span class="bullet-orange">●</span> <b>推荐：</b>{{ r.recommendReason }}</p>
              <p class="rec-bullet"><span class="bullet-amber">●</span> <b>前置：</b>{{ r.prerequisites }}</p>
              <p v-if="r.references?.length" class="rec-bullet">
                <span class="bullet-green">●</span> <b>参考：</b>
                <span v-for="(ref,j) in r.references" :key="j">
                  <template v-if="ref.url"><a :href="ref.url" target="_blank" class="ref-link">{{ ref.name }}</a></template>
                  <template v-else>{{ ref }}</template><span v-if="j < r.references.length-1">、</span>
                </span>
              </p>
            </div>
            <button class="import-plan-btn" @click="importToPlan(r)">一键导入方案设计</button>
          </div>
        </el-col>
      </el-row>

      <!-- 学习路径 -->
      <div v-if="v2Result.learning" class="learning-card">
        <div class="insights-result-title">学习路径</div>
        <div v-if="v2Result.learning.steps?.length" class="learning-steps">
          <template v-for="(s,j) in v2Result.learning.steps" :key="j">
            <div class="step-dot">{{ s }}</div>
            <span v-if="j < v2Result.learning.steps.length-1" class="step-arrow">→</span>
          </template>
        </div>
        <div v-if="v2Result.learning.timeline" class="learning-timeline"><b>预计：</b>{{ v2Result.learning.timeline }}</div>
        <div v-if="v2Result.learning.tips" class="learning-tips">{{ v2Result.learning.tips }}</div>
      </div>
    </div>

    <!-- V1 自由提问 -->
    <div v-if="trendTab==='v1'" class="v1-section">
      <el-input v-model="aiQuestion" placeholder="自由提问，基于知识库和GitHub数据回答..." size="small" @keyup.enter="askAI">
        <template #append><el-button :loading="aiLoading" @click="askAI" class="send-btn">发送</el-button></template>
      </el-input>
      <div v-if="aiReply" class="v1-reply" v-safe-html="aiReplyHtml"></div>
      <div v-if="aiError" class="v1-error">{{ aiError }}</div>
      <p v-if="aiReply" class="v1-disclaimer">
        以上分析由 AI 生成，仅供参考。每次提问消耗 {{ trendV1Cost }} <PixelOrangeSlice :size="14" style="display:inline-block;vertical-align:middle" />
      </p>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import PageTour from '../components/PageTour.vue'
import PixelOrangeSlice from '../components/PixelOrangeSlice.vue'
import TrendPersonaAnalysisCard from '../components/TrendPersonaAnalysisCard.vue'
import TrendHistoryPopover from '../components/TrendHistoryPopover.vue'
import request from '../api/request'
import { LEGAL_CONTACT } from '../content/legal'
import { getDirections, getJobsByDirection, getJobDetail } from '../api/job'
import { useTrendAnalysis } from '../composables/useTrendAnalysis'
import { useTrendChart } from '../composables/useTrendChart'
import { useTrendHistory } from '../composables/useTrendHistory'

const router = useRouter()
const route = useRoute()
const trendTab = ref('v2')
const currentStep = ref(0)
const animDirection = ref('')
const profile = reactive({
  topics: [], purpose: '', experience: '', languages: [], timeCommitment: '',
  customTopics: '', customPurpose: '', customExp: '', customLang: '', customTime: '',
  usePersona: false, useRag: true, preferBeginnerProjects: false, preferCareerProjects: false,
})
const leaderboardAutoAnalyze = route.query.autoAnalyze === '1'
if (route.query.persona === '1') profile.usePersona = true
if (route.query.rag === '1') profile.useRag = true
if (route.query.preset === 'beginner') profile.preferBeginnerProjects = true
if (route.query.preset === 'career') profile.preferCareerProjects = true
let refreshHistory
const analysis = useTrendAnalysis(profile, () => refreshHistory?.())
const { analyzing, v2Result, v2Error, juicing, squeezeText, trendV1Cost, trendV2Cost,
  aiQuestion, aiReply, aiReplyHtml, aiLoading, aiError, askAI } = analysis
const historyFeature = useTrendHistory({ profile, result: v2Result, currentStep })
const { history, hasAppToken, loadHistoryFromDB, delHistory, clearHistory, loadHistory } = historyFeature
refreshHistory = loadHistoryFromDB
const chartFeature = useTrendChart()
const { chartRef, graphLoading, viewMode, graphData, insights, selectedNode, selectedEdge,
  selectedDelta, detailTab, insightCards, loadGraph, onViewChange, formatNum } = chartFeature

const aiEndpoint = ref('https://api.openai.com/v1')
const aiModel = ref('gpt-4o')
const aiKey = ref('')
const testLoading = ref(false)
const jobDirections = ref([])
const jobDirection = ref('')
const jobList = ref([])
const jobId = ref(null)
const jobDetail = ref('')
const jobDetailHtml = ref('')

const crowdType = value => value === '蓝海' ? 'success' : value === '适中' ? 'warning' : 'danger'
const diffType = value => value === '入门' ? 'success' : value === '中等' ? 'warning' : 'danger'
const scoreColor = value => value >= 70 ? '#67C23A' : value >= 45 ? '#E6A23C' : '#F56C6C'
const goStep = step => {
  if (step === currentStep.value) return
  animDirection.value = step > currentStep.value ? 'slide-right' : 'slide-left'
  currentStep.value = step
}
const startJuicing = () => analysis.startJuicing(currentStep)
const handleExperienceChange = value => { profile.preferBeginnerProjects = String(value || '').includes('新手') }
const handlePurposeChange = value => { if (String(value || '').includes('求职')) profile.preferCareerProjects = true }

watch(() => profile.preferBeginnerProjects, enabled => { if (enabled) profile.useRag = true })
watch(() => profile.preferCareerProjects, enabled => { if (enabled) profile.useRag = true })
watch(() => profile.useRag, enabled => {
  if (!enabled) {
    profile.preferBeginnerProjects = false
    profile.preferCareerProjects = false
  }
})

const formSteps = [
  { icon: '🎯', label: '方向' }, { icon: '💡', label: '目的' },
  { icon: '📚', label: '基础' }, { icon: '⏰', label: '时间' },
]
const topicOptions = ['创意编程','游戏开发','WebGL','Canvas','Web音频','AI智能体','RAG检索增强','自托管','家庭助理','ESP32','树莓派','家居自动化','3D打印','其他']
const purposeOptions = ['🎓 学习 AI 编程知识', '💼 应对求职面试', '💰 赚钱/接单/创业', '🔬 探索技术兴趣']
const expOptions = ['新手入门', '1-3年', '3年以上']
const langOptions = ['Python', 'JavaScript', 'TypeScript', 'Go', 'Rust', 'Java', 'C++', '其他']
const timeOptions = ['🕐 碎片时间（每周<5h）', '📅 业余投入（每周5-15h）', '🚀 全力投入（每周>15h）']
const tourSteps = [
  { selector: '.graph-card', text: '这是 Topic 全景图！圆圈代表技术方向，越大越热门～ 点击圆圈看详情，连线表示它们共享编程语言。试着拖拽和缩放看看！', expression: 'curious' },
  { selector: '.card-header-row .el-radio-group', text: '点这里可以切换本周和上周的数据，对比两个时段的热度变化，看哪个方向在上升～', expression: 'wink' },
  { selector: '.detail-card', text: '点击全景图里的圆圈后，这里会显示那个 Topic 的详细信息：Top 仓库、语言分布、趋势洞察。', expression: 'smile' },
  { selector: '.insights-result-card', text: 'AI 分析完成后，这里会出现发展趋势、前景预测和热门仓库推荐～ 往下滚动查看完整结果。', expression: 'sparkle' },
  { selector: '.trend-tabs', text: '三个 Tab 切换模式：精准推荐看图谱+填表单，自由提问直接问 AI，工作能力看岗位要求。', expression: 'smile' },
  { selector: '[data-tour="form-panel"]', text: '这是智能分析面板！选好方向、目的、编程基础和时间投入，点「开始分析」AI 会给你一份专属的 Vibe Coding 项目推荐～', expression: 'sparkle' },
]

const testConnection = async () => {
  if (!aiEndpoint.value || !aiKey.value) { aiError.value = '请填写 Endpoint 和 API Key'; return }
  testLoading.value = true
  aiError.value = ''
  try {
    const response = await request.post('/app/trend/analyze', { question: '回复OK' }, { timeout: 15000 })
    if (response.code === 1) ElMessage.success('连接成功！模型可用')
    else aiError.value = response.msg || '连接失败'
  } catch { aiError.value = '请求超时或网络不通，请检查 Endpoint' }
  finally { testLoading.value = false }
}
const compareTopics = edge => {
  aiQuestion.value = `请对比分析 ${edge.sourceName} 和 ${edge.targetName} 两个方向的趋势和机会`
  document.querySelector('.el-card:last-child')?.scrollIntoView({ behavior: 'smooth' })
}
const importToPlan = recommendation => {
  sessionStorage.setItem('plan_import', `项目名称：${recommendation.name}\n项目介绍：${recommendation.description}\n难度：${recommendation.difficulty}\n前置知识：${recommendation.prerequisites}\nVibeCoding适合度：${recommendation.vibeCodingReason || ''}\n推荐理由：${recommendation.recommendReason}`)
  router.push('/plan')
}
const importBeginnerCase = project => {
  sessionStorage.setItem('plan_import', `项目名称：${project.descriptionZh || project.repositoryName}\n参考仓库：${project.htmlUrl}\n适合原因：${project.beginnerReason}\n可改造方向：${project.modificationIdeas || ''}\n难度：${project.difficulty}\n预计时间：${project.estimatedDuration || '待评估'}`)
  router.push('/plan')
}
const importCareerCase = project => {
  sessionStorage.setItem('plan_import', `项目名称：${project.summaryZh || project.repositoryName}\n参考仓库：${project.htmlUrl}\n目标岗位：${project.bestRoleName || '待选择'}\n职业价值：${project.careerValue || ''}\n可证明能力：${project.proofPoints || ''}\n差异化方向：${project.modificationPlan || ''}\n预计时间：${project.estimatedDuration || '待评估'}`)
  router.push('/plan')
}
const loadDirections = async () => {
  try { const response = await getDirections(); jobDirections.value = response.data || [] } catch {}
}
const loadJobList = async () => {
  jobId.value = null
  jobDetail.value = ''
  try { const response = await getJobsByDirection(jobDirection.value); jobList.value = response.data || [] } catch {}
}
const loadJobDetail = async () => {
  if (!jobId.value) return
  try {
    const response = await getJobDetail(jobId.value)
    jobDetail.value = response.data?.displayContent || ''
    jobDetailHtml.value = marked.parse(jobDetail.value)
  } catch {}
}

onMounted(async () => {
  loadGraph(); loadHistoryFromDB(); loadDirections()
  await analysis.loadPricing()
  if (leaderboardAutoAnalyze) {
    const query = { ...route.query }
    delete query.autoAnalyze
    await router.replace({ path: route.path, query })
    await nextTick()
    await startJuicing()
  }
})
onUnmounted(analysis.dispose)
</script>

<style scoped src="../styles/trend-analysis.css"></style>
