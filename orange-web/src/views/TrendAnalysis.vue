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
            <el-popover placement="bottom-end" width="320" trigger="click" v-if="hasAppToken">
              <template #reference>
                <el-button size="small" text>{{ history.length ? '记录 ('+history.length+')' : '记录' }}</el-button>
              </template>
              <div class="history-popover">
                <div v-if="!history.length" class="history-empty">暂无记录</div>
                <div v-for="(h,i) in history" :key="i" class="history-item" @click="loadHistory(h)">
                  <div class="history-item-text">
                    <span class="history-topic">{{ (Array.isArray(h.topics) ? h.topics.join('、') : String(h.topics||'全部').replace(/[\[\"]/g,'')) }}</span>
                    <span class="history-time">{{ (h.time||'').substring(0,16) }}</span>
                  </div>
                  <el-button size="small" text type="danger" @click.stop="delHistory(i)">✕</el-button>
                </div>
              </div>
              <div v-if="history.length" class="history-clear-row">
                <el-button size="small" type="danger" text @click="clearHistory">清空全部</el-button>
              </div>
            </el-popover>
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
                  <el-select v-model="profile.purpose" placeholder="选择你的目的" size="large" class="step-select" placement="bottom-start">
                    <el-option v-for="t in purposeOptions" :key="t" :label="t" :value="t" />
                  </el-select>
                  <el-input v-model="profile.customPurpose" placeholder="或输入自定义目的..." class="step-input-mt" maxlength="200" />
                </template>
                <!-- 步骤3: 基础 -->
                <template v-if="currentStep === 2">
                  <div class="step-title">你的编程基础怎么样？</div>
                  <el-select v-model="profile.experience" placeholder="编程经验" size="large" class="step-select" placement="bottom-start">
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

          <!-- RAG 知识库开关 -->
          <div v-if="currentStep === 3" class="rag-switch-row">
            <el-switch v-model="profile.useRag" active-color="#c55312" />
            <span class="rag-label">知识库增强</span>
            <el-tooltip content="开启后会自动检索你上传的方法论文档和开源项目 README，让推荐结果包含更具体的学习路径、案例参考和面试建议" placement="top">
              <el-icon class="help-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>

          <!-- 底部按钮 -->
          <div class="nav-btn-row">
            <button v-if="currentStep > 0" class="nav-btn" @click="goStep(currentStep-1)">← 上一步</button>
            <div v-else />
            <button v-if="currentStep < 3" class="orange-btn" @click="goStep(currentStep+1)">下一步 →</button>
            <button v-else class="orange-btn squeeze-btn" @click="startJuicing" :disabled="analyzing">
              <span v-if="!analyzing">开始榨汁分析</span>
              <span v-else class="squeezing">榨汁中...</span>
            </button>
          </div>
          <div v-if="v2Error" class="error-box">{{ v2Error }}</div>
        </div>
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
      <p v-if="jobDetail" class="job-disclaimer">本页面岗位信息由平台注册用户自主上传分享，本平台仅提供信息存储空间服务，不对内容版权、真实性承担责任。如涉及侵权，请联系 1281748852@qq.com，我们将在 3 个工作日内下架对应内容。</p>
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
        以上分析由 AI 生成，仅供参考。每次提问消耗 50 <PixelOrangeSlice :size="14" style="display:inline-block;vertical-align:middle" />
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { h } from 'vue'
import { useRouter } from 'vue-router'
import PageTour from '../components/PageTour.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { marked } from 'marked'
import PixelOrangeSlice from '../components/PixelOrangeSlice.vue'
import request from '../api/request'
import { getDirections, getJobsByDirection, getJobDetail } from '../api/job'

const chartRef = ref(null)
let chart = null
const graphLoading = ref(false)
const viewMode = ref('week')
const graphData = ref(null)
const insights = ref(null)
const selectedNode = ref(null)
const selectedEdge = ref(null)
const selectedDelta = ref(null)
const detailTab = ref('metrics')
const lastStars = ref({})

const crowdType = (v) => v === '蓝海' ? 'success' : v === '适中' ? 'warning' : 'danger'
const diffType = (v) => v === '入门' ? 'success' : v === '中等' ? 'warning' : 'danger'

const router = useRouter()
const scoreColor = (v) => v >= 70 ? '#67C23A' : v >= 45 ? '#E6A23C' : '#F56C6C'

const insightCards = computed(() => {
  if (!insights.value) return []
  const types = [
    { key: 'hotTop3', title: '🔥 热门方向', tagType: 'danger' },
    { key: 'blueTop3', title: '🌱 新兴方向', tagType: 'success' },
    { key: 'growTop3', title: '📈 增长最快', tagType: 'warning' },
    { key: 'oppTop3', title: '⭐ 推荐', tagType: 'info' }
  ]
  return types.map(t => ({ title: t.title, items: insights.value[t.key] || [], tagType: t.tagType }))
})

// AI
const aiEndpoint = ref('https://api.openai.com/v1')
const aiModel = ref('gpt-4o')
const aiKey = ref('')
const aiQuestion = ref('')
const aiReply = ref('')
const aiReplyHtml = ref('')
const aiLoading = ref(false)
const aiError = ref('')

const tourSteps = [
  { selector: '.graph-card', text: '这是 Topic 全景图！圆圈代表技术方向，越大越热门～ 点击圆圈看详情，连线表示它们共享编程语言。试着拖拽和缩放看看！', expression: 'curious' },
  { selector: '.card-header-row .el-radio-group', text: '点这里可以切换本周和上周的数据，对比两个时段的热度变化，看哪个方向在上升～', expression: 'wink' },
  { selector: '.detail-card', text: '点击全景图里的圆圈后，这里会显示那个 Topic 的详细信息：Top 仓库、语言分布、趋势洞察。', expression: 'smile' },
  { selector: '.insights-result-card', text: 'AI 分析完成后，这里会出现发展趋势、前景预测和热门仓库推荐～ 往下滚动查看完整结果。', expression: 'sparkle' },
  { selector: '.trend-tabs', text: '三个 Tab 切换模式：精准推荐看图谱+填表单，自由提问直接问 AI，工作能力看岗位要求。', expression: 'smile' },
  { selector: '[data-tour="form-panel"]', text: '这是智能分析面板！选好方向、目的、编程基础和时间投入，点「开始分析」AI 会给你一份专属的 Vibe Coding 项目推荐～', expression: 'sparkle' },
]

const trendTab = ref('v2')
const jobDirections = ref([])
const jobDirection = ref('')
const jobList = ref([])
const jobId = ref(null)
const jobDetail = ref('')
const jobDetailHtml = ref('')

// V2 智能分析
const currentStep = ref(0)
const animDirection = ref('')
const juicing = ref(false)

const goStep = (step) => {
  if (step === currentStep.value) return
  animDirection.value = step > currentStep.value ? 'slide-right' : 'slide-left'
  currentStep.value = step
}
const analyzing = ref(false)
const v2Result = ref(null)
const v2Error = ref('')
const squeezeText = ref('🍊 正在收集数据...')
let squeezeTimer = null

const formSteps = [
  { icon: '🎯', label: '方向' }, { icon: '💡', label: '目的' },
  { icon: '📚', label: '基础' }, { icon: '⏰', label: '时间' }
]
const profile = reactive({
  topics: [], purpose: '', experience: '', languages: [], timeCommitment: '',
  customTopics: '', customPurpose: '', customLang: '', customTime: '',
  useRag: true
})

const topicOptions = ['创意编程','游戏开发','WebGL','Canvas','Web音频','AI智能体','RAG检索增强','自托管','家庭助理','ESP32','树莓派','家居自动化','3D打印','其他']
const purposeOptions = ['🎓 学习 AI 编程知识', '💼 应对求职面试', '💰 赚钱/接单/创业', '🔬 探索技术兴趣']
const expOptions = ['新手入门', '1-3年', '3年以上']
const langOptions = ['Python', 'JavaScript', 'TypeScript', 'Go', 'Rust', 'Java', 'C++', '其他']
const timeOptions = ['🕐 碎片时间（每周<5h）', '📅 业余投入（每周5-15h）', '🚀 全力投入（每周>15h）']
const squeezeTexts = ['🍊 正在榨取分析精华...', '🍊 找到了！为你生成推荐...']
const hasAppToken = computed(() => !!sessionStorage.getItem('appToken'))
const history = ref([])

const loadHistoryFromDB = async () => {
  if (!hasAppToken.value) return
  try { const r = await request.get('/app/trend/history'); if (r.code === 1) history.value = r.data.map(h => ({ ...h, time: h.createTime, result: typeof h.result === 'string' ? JSON.parse(h.result) : h.result })) } catch {}
}
const saveToHistory = async () => {
  if (!v2Result.value || !hasAppToken.value) return
  try {
    await request.post('/app/trend/history/save', {
      topics: profile.topics, purpose: profile.purpose, experience: profile.experience,
      languages: profile.languages, timeCommitment: profile.timeCommitment,
      resultString: JSON.stringify(v2Result.value)
    })
    loadHistoryFromDB()
  } catch (e) { console.error('保存历史失败:', e) }
}
const delHistory = async (i) => {
  try { await request.delete('/app/trend/history/' + history.value[i].id); loadHistoryFromDB() } catch {}
}
const clearHistory = async () => {
  try { await request.delete('/app/trend/history/clear'); history.value = [] } catch {}
}
const loadHistory = (h) => {
  v2Result.value = h.result
  profile.topics = h.topics || []
  profile.purpose = h.purpose || ''
  profile.experience = h.experience || ''
  profile.languages = h.languages || []
  profile.timeCommitment = h.timeCommitment || ''
  currentStep.value = 3
}

const startJuicing = async () => {
  if (!profile.topics.length) { v2Error.value = '请至少选择一个技术方向'; currentStep.value = 0; return }
  // 消费确认
  try {
    await ElMessageBox.confirm(
      h('span', { style: 'display:flex;align-items:center;gap:6px' }, [
        '本次分析将消耗 50 ',
        h(PixelOrangeSlice, { size: 16 })
      ]),
      '确认分析', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'info' }
    )
  } catch { return }
  juicing.value = true; analyzing.value = true; v2Error.value = ''; v2Result.value = null
  squeezeText.value = '🍊 正在收集数据...'; let idx = 0
  squeezeTimer = setInterval(() => { idx = (idx+1) % squeezeTexts.length; squeezeText.value = squeezeTexts[idx] }, 3000)
  // 播放榨汁动画 2.5 秒后开始 API 调用
  await new Promise(r => setTimeout(r, 2500))
  juicing.value = false
  try {
    const r = await request.post('/app/trend/analyze-v2', {
      topics: Array.isArray(profile.topics) ? profile.topics : [],
      purpose: profile.purpose || '',
      experience: profile.experience || '',
      languages: Array.isArray(profile.languages) ? profile.languages : [],
      timeCommitment: profile.timeCommitment || '',
      customTopics: profile.customTopics || '',
      customPurpose: profile.customPurpose || '',
      customLang: profile.customLang || '',
      customTime: profile.customTime || '',
      useRag: profile.useRag
    }, { timeout: 0 })
    if (r.code === 1 && r.data) {
      v2Result.value = r.data
      setTimeout(() => saveToHistory(), 500)
    } else {
      v2Error.value = r.msg || '分析失败'
    }
  } catch { v2Error.value = '请求失败，请检查网络连接' }
  finally { analyzing.value = false; juicing.value = false; if (squeezeTimer) clearInterval(squeezeTimer) }
}

const formatNum = (n) => {
  if (!n) return '0'
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return String(n)
}

// 四大领域颜色
const groupColors = {
  '创意技术': '#ffcc80',
  'AI 智能': '#90caf9',
  '自托管': '#a5d6a7',
  '硬件/IoT': '#ce93d8',
  '其他': '#cfd8dc'
}

const loadGraph = async () => {
  graphLoading.value = true
  try {
    const r = await request.get('/app/trend/graph')
    if (r.code === 1) {
      graphData.value = r.data
      insights.value = r.data.insights || null
      // 提取上周 stars 用于计算变化
      if (r.data.lastWeek?.nodes) {
        const map = {}
        r.data.lastWeek.nodes.forEach(n => { map[n.id] = (n.totalStars || 0) })
        lastStars.value = map
      }
      renderChart(viewMode.value)
    }
  } catch {} finally { graphLoading.value = false }
}

const renderChart = (mode) => {
  if (!chartRef.value || !graphData.value) return
  if (!chart) chart = echarts.init(chartRef.value)

  const data = mode === 'week' ? graphData.value.thisWeek : graphData.value.lastWeek
  if (!data || !data.nodes) return

  const nodes = data.nodes.map(n => ({
    id: n.id,
    name: n.name,
    symbolSize: 25 + Math.log10(Math.max(1, n.totalStars || 1)) * 12,
    category: n.group,
    itemStyle: { color: groupColors[n.group] || '#78909c' },
    // 附加数据
    totalStars: n.totalStars,
    repoCount: n.repoCount,
    topRepos: n.topRepos,
    topLanguages: n.topLanguages,
    group: n.group,
    metrics: n.metrics,
    growthPct: n.growthPct
  }))

  const edges = (data.edges || []).map(e => ({
    source: e.source,
    target: e.target,
    lineStyle: { width: Math.min(e.weight || 1, 4), opacity: 0.3, curveness: 0.2 }
  }))

  const categories = Object.keys(groupColors).map(k => ({ name: k, itemStyle: { color: groupColors[k] } }))

  chart.setOption({
    tooltip: {
      formatter: (p) => {
        if (p.dataType === 'node') {
          return `<b>${p.data.name}</b><br/>⭐ ${formatNum(p.data.totalStars)}<br/>📦 ${p.data.repoCount} repos<br/>🏷 ${p.data.group}`
        }
        return `${p.data.source} ↔ ${p.data.target}`
      }
    },
    legend: { data: categories.map(c => c.name), bottom: 0 },
    series: [{
      type: 'graph',
      layout: 'force',
      force: { repulsion: 400, edgeLength: [150, 350], gravity: 0.15, friction: 0.3, layoutAnimation: true },
      roam: true,
      draggable: true,
      categories: categories,
      data: nodes,
      edges: edges,
      label: { show: true, fontSize: 11, color: '#2c2c2c', formatter: '{b}' },
      emphasis: { focus: 'adjacency', label: { fontSize: 14, fontWeight: 'bold' } }
    }]
  })

  // 点击节点/连线
  chart.off('click')
  chart.on('click', (p) => {
    if (p.dataType === 'node') {
      selectedNode.value = p.data
      selectedEdge.value = null
      detailTab.value = 'metrics'
      const last = lastStars.value[p.data.id]
      if (last && last > 0) {
        selectedDelta.value = Math.round((p.data.totalStars - last) * 100 / last)
      } else {
        selectedDelta.value = null
      }
    } else if (p.dataType === 'edge') {
      selectedEdge.value = p.data
      selectedNode.value = null
    }
  })

  // 预计算共享语言（从 raw data 中提取）
  const rawData = mode === 'week' ? graphData.value.thisWeek : graphData.value.lastWeek
  if (rawData?.edges) {
    const nodeMap = {}
    if (rawData.nodes) rawData.nodes.forEach(n => { nodeMap[n.id] = n })
    rawData.edges.forEach(e => {
      const sNode = nodeMap[e.source]; const tNode = nodeMap[e.target]
      e.sourceName = sNode?.name || e.source
      e.targetName = tNode?.name || e.target
      // 计算共享语言
      const sLangs = new Set((sNode?.topLanguages || []).map(l => l.name))
      const tLangs = new Set((tNode?.topLanguages || []).map(l => l.name))
      const shared = [...sLangs].filter(l => tLangs.has(l))
      e.sharedLangs = shared
    })
  }

  window.addEventListener('resize', () => chart?.resize())
}

const onViewChange = () => renderChart(viewMode.value)

// AI
const testLoading = ref(false)

const testConnection = async () => {
  if (!aiEndpoint.value || !aiKey.value) {
    aiError.value = '请填写 Endpoint 和 API Key'
    return
  }
  testLoading.value = true; aiError.value = ''
  try {
    const r = await request.post('/app/trend/analyze', {
      question: '回复OK'
    }, { timeout: 15000 })
    if (r.code === 1) {
      aiError.value = ''
      ElMessage.success('连接成功！模型可用')
    } else {
      aiError.value = r.msg || '连接失败'
    }
  } catch {
    aiError.value = '请求超时或网络不通，请检查 Endpoint'
  } finally { testLoading.value = false }
}

const compareTopics = (edge) => {
  aiQuestion.value = `请对比分析 ${edge.sourceName} 和 ${edge.targetName} 两个方向的趋势和机会`
  // 滚动到 AI 面板
  document.querySelector('.el-card:last-child')?.scrollIntoView({ behavior: 'smooth' })
}

const importToPlan = (r) => {
  const text = `项目名称：${r.name}\n项目介绍：${r.description}\n难度：${r.difficulty}\n前置知识：${r.prerequisites}\nVibeCoding适合度：${r.vibeCodingReason || ''}\n推荐理由：${r.recommendReason}`
  sessionStorage.setItem('plan_import', text)
  router.push('/plan')
}

const askAI = async () => {
  if (!aiQuestion.value.trim()) { aiError.value = '请输入分析问题'; return }
  try { await ElMessageBox.confirm(
    h('span', { style: 'display:flex;align-items:center;gap:6px' }, [
      '本次提问将消耗 50 ',
      h(PixelOrangeSlice, { size: 16 })
    ]),
    '确认发送', { confirmButtonText: '确认', cancelButtonText: '取消', type: 'info' }
  ) } catch { return }
  aiLoading.value = true; aiError.value = ''; aiReply.value = ''; aiReplyHtml.value = ''
  try {
    const r = await request.post('/app/trend/analyze', {
      question: aiQuestion.value.trim()
    }, { timeout: 90000 })
    if (r.code === 1 && r.data) {
      aiReply.value = r.data.content || ''
      aiReplyHtml.value = marked.parse(aiReply.value)
    } else {
      aiError.value = r.msg || '分析失败'
    }
  } catch {
    aiError.value = '请求失败，请检查网络连接'
  } finally { aiLoading.value = false }
}


const loadDirections = async () => {
  try { const r = await getDirections(); jobDirections.value = r.data || [] } catch {}
}
const loadJobList = async () => {
  jobId.value = null; jobDetail.value = ''
  try { const r = await getJobsByDirection(jobDirection.value); jobList.value = r.data || [] } catch {}
}
const loadJobDetail = async () => {
  if (!jobId.value) return
  try {
    const r = await getJobDetail(jobId.value)
    jobDetail.value = r.data?.displayContent || ''
    jobDetailHtml.value = marked.parse(jobDetail.value)
  } catch {}
}

onMounted(() => { loadGraph(); loadHistoryFromDB(); loadDirections() })
onUnmounted(() => { chart?.dispose(); chart = null })
</script>

<style scoped>
/* ============================================================
   PIXEL-MINIMALIST DESIGN SYSTEM — TrendAnalysis
   ============================================================ */

/* --- BASE & TYPOGRAPHY --- */
/* Compact Head */
.compact-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 20px;
  padding: 22px;
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
  line-height: 1.08;
  letter-spacing: -.05em;
}
.compact-head p {
  max-width: 680px;
  margin: 6px 0 0;
  color: var(--text-secondary);
  line-height: 1.6;
  font-size: 13px;
}
.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 28px;
  padding: 0 12px;
  border: 1px solid var(--border-orange);
  border-radius: 999px;
  color: var(--brand-strong);
  background: #fffaf5;
  font-size: 12px;
  font-weight: 900;
  font-family: var(--font-mono);
}
.section-subtitle {
  font-family: var(--font-body);
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0 0 8px;
}
.section-label {
  font-family: "Courier New", "SimHei", monospace;
  font-size: 11px;
  font-weight: 700;
  color: #3a302a;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* --- SPACING UTILITIES --- */
.mt-8  { margin-top: 8px; }
.mt-12 { margin-top: 12px; }
.mt-16 { margin-top: 16px; }
.mr-6  { margin-right: 6px; }
.ml-6  { margin-left: 6px; }
.mb-8  { margin-bottom: 8px; }

/* --- TEXT HELPERS --- */
.text-muted {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  color: #9f9890;
}
.card-title-text {
  font-family: "Courier New", "SimHei", monospace;
  font-weight: 600;
  color: #3a302a;
}
.help-icon {
  margin-left: 6px;
  color: #9f9890;
  cursor: help;
}

/* --- TABS --- */
.trend-tabs {
  margin-bottom: 12px;
}
.trend-tabs :deep(.el-tabs__header) {
  margin: 0;
}
.trend-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: var(--border-orange);
}
.trend-tabs :deep(.el-tabs__item) {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
  color: var(--text-secondary);
  height: 36px;
  line-height: 36px;
}
.trend-tabs :deep(.el-tabs__item.is-active) {
  color: var(--brand-strong);
}
.trend-tabs :deep(.el-tabs__active-bar) {
  height: 2px;
  background-color: var(--brand);
}

/* --- CARDS --- */
.graph-card {
  border: 1px solid var(--border-orange);
  border-radius: 20px;
  box-shadow: var(--shadow-card);
}
.detail-card {
  border: 1px solid var(--border-orange);
  border-radius: 20px;
  height: 556px;
  overflow: auto;
  box-shadow: var(--shadow-card);
}

/* --- CARD INTERNALS --- */
.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.chart-container {
  width: 100%;
  height: 500px;
}
.detail-section-title {
  margin: 0 0 4px;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 14px;
  font-weight: 700;
  color: #3a302a;
}
.detail-text {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  color: #3a302a;
}
.compact-divider {
  margin: 12px 0;
}
.compact-divider-sm {
  margin: 8px 0;
}
.tag-item {
  font-family: "Courier New", "SimHei", monospace;
  font-size: 10px;
  font-weight: 700;
  border-radius: 0;
  margin: 2px 4px 2px 0;
}
.summary-box {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 12px;
  color: #3a302a;
  line-height: 1.6;
  margin-bottom: 12px;
  padding: 8px;
  background: #faf9f7;
  border: 2px solid #f0ece6;
  border-radius: 2px;
}
.insight-box {
  margin-top: 8px;
  padding: 8px;
  background: #faf9f7;
  border: 2px solid #f0ece6;
  border-radius: 2px;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 12px;
  color: #c55312;
  line-height: 1.6;
}
.delta-row {
  margin-top: 8px;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
}
.delta-up {
  color: #67C23A;
  font-weight: 700;
}
.delta-down {
  color: #F56C6C;
  font-weight: 700;
}

/* --- REPO ITEMS --- */
.repo-item {
  padding: 6px 0;
  border-bottom: 1px solid #f0ece6;
  font-size: 12px;
}
.repo-link {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  color: #f37021;
  font-weight: 500;
  text-decoration: none;
}
.repo-link:hover {
  text-decoration: underline;
}
.repo-stars {
  font-family: "Courier New", "SimHei", monospace;
  color: #c55312;
  float: right;
  font-size: 12px;
}
.repo-desc {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 11px;
  color: #9f9890;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* --- INSIGHT CARDS --- */
.insight-card {
  margin-bottom: 10px;
  padding: 10px;
  background: #faf9f7;
  border: 2px solid #f0ece6;
  border-radius: 2px;
}
.insight-card-title {
  font-family: "Courier New", "SimHei", monospace;
  font-size: 11px;
  text-transform: uppercase;
  color: #9f9890;
  margin-bottom: 6px;
  letter-spacing: 0.5px;
}

/* --- FORM SECTION --- */
.orange-form-card {
  background: #ffffff;
  border: 2px solid #f0ece6;
  border-radius: 2px;
  padding: 24px;
}
.form-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.form-title {
  font-family: "Courier New", "SimHei", monospace;
  font-weight: 700;
  font-size: 16px;
  color: #3a302a;
  letter-spacing: 1px;
}
.form-title-sub {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 12px;
  color: #bfb8b0;
  margin-left: 8px;
}

/* --- HISTORY POPOVER --- */
.history-popover {
  max-height: 300px;
  overflow: auto;
}
.history-empty {
  text-align: center;
  color: #bfb8b0;
  padding: 20px;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}
.history-item {
  padding: 6px 0;
  border-bottom: 1px solid #f0ece6;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.history-item-text {
  flex: 1;
  min-width: 0;
  font-size: 12px;
}
.history-topic {
  font-family: "Courier New", "SimHei", monospace;
  color: #3a302a;
  font-weight: 500;
}
.history-time {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  color: #bfb8b0;
  margin-left: 6px;
  font-size: 11px;
}
.history-clear-row {
  margin-top: 8px;
  text-align: right;
}

/* --- STEPS --- */
.steps-bar {
  margin-bottom: 20px;
}
.steps-track {
  display: flex;
  justify-content: center;
  gap: 0;
}
.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  flex: 1;
  position: relative;
  cursor: default;
}
.step-item + .step-item::before {
  content: '';
  position: absolute;
  top: 16px;
  left: -50%;
  width: 100%;
  height: 2px;
  background: #f0ece6;
  z-index: 0;
}
.step-item.active + .step-item::before,
.step-item.done + .step-item::before {
  background: #f37021;
}
.step-circle {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 14px;
  background: #faf9f7;
  color: #f37021;
  border: 2px solid #e5d9c3;
  position: relative;
  z-index: 1;
  transition: all .3s;
}
.step-item.active .step-circle {
  background: #f37021;
  color: #fff;
  border-color: #f37021;
}
.step-item.done .step-circle {
  background: #f37021;
  color: #fff;
  border-color: #f37021;
}
.step-label {
  font-family: "Courier New", "SimHei", monospace;
  font-size: 12px;
  font-weight: 600;
  color: #bfb8b0;
  transition: color .3s;
}
.step-item.active .step-label,
.step-item.done .step-label {
  color: #3a302a;
  font-weight: 700;
}

.step-card-wrapper {
  overflow: hidden;
}
.step-card {
  background: #faf9f7;
  border: 2px solid #f0ece6;
  border-radius: 2px;
  padding: 28px 24px;
  min-height: 180px;
  position: relative;
  overflow: hidden;
}
.card-bg-orange {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%,-50%);
  width: 120px;
  height: 120px;
  background: radial-gradient(circle at 35% 35%, #faf9f7, #e5d9c3 70%);
  border-radius: 45% 55% 50% 50%;
  opacity: .4;
  pointer-events: none;
}
.card-bg-orange::after {
  content: '';
  position: absolute;
  top: -8px;
  left: 40%;
  width: 14px;
  height: 8px;
  background: #a5d6a7;
  border-radius: 50% 50% 0 0;
}
.step-title {
  font-family: "Courier New", "SimHei", monospace;
  font-size: 16px;
  font-weight: 700;
  color: #3a302a;
  margin-bottom: 16px;
  letter-spacing: 0.5px;
}
.step-select {
  width: 100%;
}
.step-input-mt {
  margin-top: 12px;
}

/* --- TRANSITIONS --- */
.step-card-wrapper {
  position: relative;
}
.step-card {
  transition: none;
}

/* 下一步: 旧卡向左滚走 */
.slide-right-leave-active {
  animation: slideOutL .35s ease-in forwards;
  position: absolute;
  width: 100%;
  top: 0;
  left: 0;
}
@keyframes slideOutL {
  from { opacity: 1; transform: translateX(0); }
  to { opacity: 0; transform: translateX(-80px); }
}
.slide-right-leave-active .card-bg-orange {
  animation: rollOutL .35s ease-in forwards;
}
@keyframes rollOutL {
  from { opacity: .4; transform: translate(-50%,-50%) rotate(0); }
  to { opacity: 0; transform: translate(calc(-50% - 200px),-50%) rotate(-200deg); }
}

/* 下一步: 新卡从右边滚入 */
.slide-right-enter-active {
  animation: slideInR .35s ease-out forwards;
}
@keyframes slideInR {
  from { opacity: 0; transform: translateX(80px); }
  to { opacity: 1; transform: translateX(0); }
}
.slide-right-enter-active .card-bg-orange {
  animation: rollInR .35s ease-out forwards;
}
@keyframes rollInR {
  from { opacity: 0; transform: translate(calc(-50% + 200px),-50%) rotate(200deg); }
  to { opacity: .4; transform: translate(-50%,-50%) rotate(0); }
}

/* 上一步: 旧卡向右滚走 */
.slide-left-leave-active {
  animation: slideOutR .35s ease-in forwards;
  position: absolute;
  width: 100%;
  top: 0;
  left: 0;
}
@keyframes slideOutR {
  from { opacity: 1; transform: translateX(0); }
  to { opacity: 0; transform: translateX(80px); }
}
.slide-left-leave-active .card-bg-orange {
  animation: rollOutR .35s ease-in forwards;
}
@keyframes rollOutR {
  from { opacity: .4; transform: translate(-50%,-50%) rotate(0); }
  to { opacity: 0; transform: translate(calc(-50% + 200px),-50%) rotate(200deg); }
}

/* 上一步: 新卡从左边滚入 */
.slide-left-enter-active {
  animation: slideInL .35s ease-out forwards;
}
@keyframes slideInL {
  from { opacity: 0; transform: translateX(-80px); }
  to { opacity: 1; transform: translateX(0); }
}
.slide-left-enter-active .card-bg-orange {
  animation: rollInL .35s ease-out forwards;
}
@keyframes rollInL {
  from { opacity: 0; transform: translate(calc(-50% - 200px),-50%) rotate(-200deg); }
  to { opacity: .4; transform: translate(-50%,-50%) rotate(0); }
}

/* --- RAG SWITCH --- */
.rag-switch-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding: 12px 16px;
  background: #faf9f7;
  border: 2px solid #f0ece6;
  border-radius: 2px;
}
.rag-label {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  color: #3a302a;
}

/* --- BUTTONS --- */
.nav-btn-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}
.orange-btn {
  background: #f37021;
  color: #fff;
  border: none;
  border-radius: 0;
  padding: 10px 28px;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: all .15s;
  box-shadow: 0 3px 0 rgba(197,83,18,0.15);
  letter-spacing: 0.5px;
}
.orange-btn:hover {
  background: #c55312;
}
.orange-btn:disabled {
  opacity: .6;
  cursor: not-allowed;
}
.squeeze-btn {
  padding: 14px 36px;
  font-size: 16px;
}
.nav-btn {
  background: #ffffff;
  color: #f37021;
  border: 2px solid #e5d9c3;
  border-radius: 0;
  padding: 10px 20px;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all .15s;
}
.nav-btn:hover {
  background: #faf9f7;
  border-color: #f37021;
}
.squeezing {
  animation: squeeze 1s ease-in-out infinite;
  display: inline-block;
}
@keyframes squeeze {
  0%,100% { transform: scale(1); }
  50% { transform: scale(1.15); }
}

/* --- ERROR --- */
.error-box {
  color: #c55312;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  margin-top: 10px;
  padding: 8px;
  background: #faf9f7;
  border: 2px solid #e5d9c3;
  border-radius: 2px;
}

/* --- JOBS SECTION --- */
.jobs-section {
  min-height: 300px;
}
.jobs-filter-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  align-items: center;
}
.filter-label {
  font-family: "Courier New", "SimHei", monospace;
  font-size: 13px;
  color: #9f9890;
  margin-right: 4px;
}
.job-select-dir {
  width: 200px;
}
.job-select-pos {
  width: 300px;
}
.jobs-submit-btn {
  margin-left: auto;
  background: #f37021;
  border-color: #f37021;
  font-family: "Courier New", "SimHei", monospace;
  border-radius: 0;
}
.jobs-submit-btn:hover {
  background: #c55312;
  border-color: #c55312;
}
.job-detail-card {
  background: #faf9f7;
  border: 2px solid #f0ece6;
  border-radius: 2px;
  padding: 16px;
  line-height: 1.8;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 14px;
  max-height: 500px;
  overflow: auto;
}
.job-disclaimer {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  color: #9f9890;
  font-size: 11px;
  margin-top: 8px;
  border-top: 1px solid #f0ece6;
  padding-top: 8px;
}

/* --- JUICE ANIMATION --- */
.juice-wrapper {
  text-align: center;
  padding: 40px;
}
.juice-text-status {
  margin-top: 16px;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 14px;
  color: #f37021;
  font-weight: 600;
}

/* --- RESULTS --- */
.result-section {
  margin-top: 16px;
}
.insights-result-card {
  background: #ffffff;
  border: 2px solid #f0ece6;
  border-radius: 2px;
  padding: 20px;
  border-left: 4px solid #f37021;
}
.insights-result-title {
  font-family: "Courier New", "SimHei", monospace;
  font-weight: 700;
  font-size: 15px;
  color: #3a302a;
  margin-bottom: 12px;
  letter-spacing: 1px;
}
.insights-summary {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0ece6;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  color: #3a302a;
  line-height: 1.6;
}
.rec-section-title {
  font-family: "Courier New", "SimHei", monospace;
  font-weight: 700;
  font-size: 15px;
  color: #3a302a;
  margin: 20px 0 12px;
  letter-spacing: 1px;
}

/* --- LEAF CARDS --- */
.leaf-card {
  background: #faf9f7;
  border: 2px solid #f0ece6;
  border-radius: 2px;
  padding: 14px;
  height: 100%;
}
.leaf-icon {
  font-size: 18px;
  display: block;
  margin-bottom: 6px;
}
.leaf-label {
  font-family: "Courier New", "SimHei", monospace;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  color: #bfb8b0;
  margin-bottom: 2px;
  letter-spacing: 0.5px;
}
.leaf-text {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  color: #3a302a;
  font-weight: 500;
  line-height: 1.5;
}

/* --- RECOMMENDATION CARDS --- */
.rec-card {
  background: #ffffff;
  border: 2px solid #f0ece6;
  border-radius: 2px;
  padding: 18px;
  border-left: 4px solid #f37021;
  margin-bottom: 14px;
  height: 100%;
}
.rec-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.rec-name-area {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rec-num {
  background: #f37021;
  color: #fff;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 11px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 0;
}
.rec-name {
  font-family: "Courier New", "SimHei", monospace;
  font-weight: 700;
  font-size: 15px;
  color: #3a302a;
}
.rec-meta-area {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rec-score {
  font-family: "Courier New", "SimHei", monospace;
  font-size: 12px;
  color: #f37021;
  font-weight: 700;
}
.rec-desc {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  color: #3a302a;
  margin: 0 0 10px;
  line-height: 1.6;
}
.rec-details {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.8;
}
.rec-vibe-box {
  background: #faf9f7;
  padding: 8px 10px;
  border: 2px solid #f0ece6;
  border-radius: 2px;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 12px;
}
.rec-bullet {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  margin-top: 6px;
}
.bullet-orange {
  color: #f37021;
}
.bullet-amber {
  color: #f37021;
}
.bullet-green {
  color: #81c784;
}
.ref-link {
  color: #f37021;
  text-decoration: none;
}
.ref-link:hover {
  text-decoration: underline;
}

/* --- LEARNING PATH --- */
.learning-card {
  background: #ffffff;
  border: 2px solid #f0ece6;
  border-radius: 2px;
  padding: 20px;
  border-left: 4px solid #81c784;
  margin-top: 12px;
}
.learning-steps {
  display: flex;
  align-items: center;
  gap: 0;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.step-dot {
  background: #ffffff;
  border: 2px solid #e5d9c3;
  border-radius: 0;
  padding: 6px 14px;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 12px;
  color: #3a302a;
  white-space: nowrap;
}
.step-arrow {
  color: #f37021;
  font-size: 18px;
  margin: 0 2px;
}
.learning-timeline {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  color: #3a302a;
  margin-bottom: 4px;
}
.learning-tips {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  color: #3a302a;
  line-height: 1.6;
}

/* --- V1 FREE QUESTION --- */
.v1-section {
  margin-top: 16px;
}
.send-btn {
  background: #f37021;
  border-color: #f37021;
  color: #fff;
  font-family: "Courier New", "SimHei", monospace;
  border-radius: 0;
}
.send-btn:hover {
  background: #c55312;
  border-color: #c55312;
}
.v1-reply {
  background: #faf9f7;
  border: 2px solid #f0ece6;
  border-radius: 2px;
  padding: 12px;
  max-height: 400px;
  overflow: auto;
  line-height: 1.8;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  margin-top: 8px;
}
.v1-error {
  color: #F56C6C;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  margin-top: 4px;
}
.v1-disclaimer {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  color: #9f9890;
  font-size: 11px;
  margin-top: 8px;
  border-top: 1px solid #f0ece6;
  padding-top: 8px;
}

.import-plan-btn {
  width: 100%;
  margin-top: 10px;
  padding: 8px 12px;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 12px;
  font-weight: 600;
  background: #ffffff;
  color: #f37021;
  border: 2px solid #e5d9c3;
  border-radius: 0;
  cursor: pointer;
  transition: all .15s;
  letter-spacing: 0.5px;
}
.import-plan-btn:hover {
  background: #faf9f7;
  border-color: #f37021;
}
</style>
