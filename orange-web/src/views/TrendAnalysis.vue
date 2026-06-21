<template>
  <div>
    <h3 style="margin:0 0 4px;font-weight:700">趋势分析</h3>
    <p style="color:#909399;font-size:13px;margin:0 0 8px">Vibe Coding 开源项目热度 · 每周更新 · AI 辅助编程趋势洞察</p>

    <el-row :gutter="16">
      <!-- 左：图谱 -->
      <el-col :span="18">
        <el-card shadow="never" style="border-radius:12px">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px">
            <div>
              <span style="font-weight:600">Topic 全景图</span>
              <el-tooltip content="节点=技术方向（大小=热度），连线=共享编程语言≥2种（越粗关联越强）" placement="top">
                <el-icon style="margin-left:6px;color:#909399;cursor:help"><QuestionFilled /></el-icon>
              </el-tooltip>
            </div>
            <el-radio-group v-model="viewMode" size="small" @change="onViewChange">
              <el-radio-button value="week">本周</el-radio-button>
              <el-radio-button value="last">上周</el-radio-button>
            </el-radio-group>
          </div>
          <div ref="chartRef" style="width:100%;height:500px" v-loading="graphLoading"></div>
        </el-card>
      </el-col>

      <!-- 右：节点详情 -->
      <el-col :span="6">
        <el-card shadow="never" style="border-radius:12px;height:556px;overflow:auto">
          <template v-if="selectedEdge">
            <h4 style="margin:0 0 4px">Topic 关联</h4>
            <el-tag size="small" type="info" style="margin-right:6px">{{ selectedEdge.sourceName }}</el-tag>
            <span style="color:#909399">↔</span>
            <el-tag size="small" type="info" style="margin-left:6px">{{ selectedEdge.targetName }}</el-tag>
            <el-divider style="margin:12px 0" />
            <p style="font-size:13px;color:#606266">这两个方向共享 <b>{{ selectedEdge.weight }}</b> 种编程语言，技能可迁移。</p>
            <div v-if="selectedEdge.sharedLangs?.length" style="margin-top:8px">
              <div style="font-weight:600;font-size:13px;margin-bottom:4px">共享语言</div>
              <el-tag v-for="l in selectedEdge.sharedLangs" :key="l" size="small" type="success" style="margin:2px 4px 2px 0">{{ l }}</el-tag>
            </div>
            <el-button size="small" type="primary" style="margin-top:12px" @click="compareTopics(selectedEdge)">AI 对比分析</el-button>
          </template>
          <template v-if="selectedNode">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px">
              <div>
                <span style="font-weight:600">{{ selectedNode.name }}</span>
                <el-tag size="small" type="info" style="margin-left:6px">{{ selectedNode.group }}</el-tag>
              </div>
              <el-radio-group v-model="detailTab" size="small">
                <el-radio-button value="insights">洞察</el-radio-button>
                <el-radio-button value="metrics">分析</el-radio-button>
                <el-radio-button value="repos">仓库</el-radio-button>
              </el-radio-group>
            </div>
            <el-divider style="margin:8px 0" />
            <!-- 分析视图 -->
            <template v-if="detailTab==='metrics'">
              <div v-if="selectedNode.metrics?.summary" style="font-size:12px;color:#606266;line-height:1.6;margin-bottom:12px;padding:8px;background:#faf9f7;border-radius:6px">
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
              <div v-if="selectedDelta !== null" style="margin-top:8px;font-size:13px">
                较上周: <span :style="{color:selectedDelta>=0?'#67C23A':'#F56C6C',fontWeight:700}">{{ selectedDelta >= 0 ? '↑' : '↓' }}{{ Math.abs(selectedDelta) }}%</span>
              </div>
              <div v-if="selectedNode.metrics?.insight" style="margin-top:8px;padding:8px;background:#f5f9ff;border-radius:6px;font-size:12px;color:#1565c0;line-height:1.6">
                💡 {{ selectedNode.metrics.insight }}
              </div>
            </template>
            <!-- 仓库视图 -->
            <template v-if="detailTab==='repos'">
              <div v-if="selectedNode.topRepos?.length">
                <div style="font-weight:600;font-size:12px;margin-bottom:6px">🏆 Top 5 仓库</div>
                <div v-for="r in selectedNode.topRepos" :key="r.name" style="padding:6px 0;border-bottom:1px solid #f5f2ee;font-size:12px">
                  <a :href="r.htmlUrl" target="_blank" style="color:#409EFF;font-weight:500">{{ r.name }}</a>
                  <span style="color:#e65100;float:right">⭐{{ formatNum(r.stars) }}</span>
                  <div style="font-size:11px;color:#909399;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ r.description || '' }}</div>
                </div>
              </div>
              <div v-if="selectedNode.topLanguages?.length" style="margin-top:12px">
                <div style="font-weight:600;font-size:12px;margin-bottom:4px">主要语言</div>
                <el-tag v-for="l in selectedNode.topLanguages" :key="l.name" size="small" style="margin:2px 4px 2px 0">{{ l.name }} ({{ l.count }})</el-tag>
              </div>
            </template>
          </template>
          <template v-if="!selectedNode && !selectedEdge || (selectedNode && detailTab==='insights')">
            <div style="font-weight:600;font-size:13px;margin-bottom:8px">📊 趋势洞察</div>
            <div v-for="card in insightCards" :key="card.title" style="margin-bottom:10px;padding:10px;background:#faf9f7;border-radius:8px">
              <div style="font-size:12px;color:#909399;margin-bottom:6px">{{ card.title }}</div>
              <el-tag v-for="n in card.items" :key="n" :type="card.tagType" size="small" style="margin:2px 4px">{{ n }}</el-tag>
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>

    <!-- 🍊 橙子智能分析面板 -->
    <div style="margin-top:16px">
      <!-- 步骤式表单卡片 -->
      <div class="orange-form-card">
        <!-- 标题栏 + 历史记录 -->
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px">
          <div>
            <span style="font-weight:700;font-size:16px;color:#5d4037">🍊 智能分析</span>
            <span style="font-size:12px;color:#bcaaa4;margin-left:8px">告诉我你的想法</span>
          </div>
          <el-popover placement="bottom-end" width="320" trigger="click" v-if="hasAppToken">
            <template #reference>
              <el-button size="small" text>{{ history.length ? '📋 记录 ('+history.length+')' : '📋 记录' }}</el-button>
            </template>
            <div style="max-height:300px;overflow:auto">
              <div v-if="!history.length" style="text-align:center;color:#bcaaa4;padding:20px">暂无记录</div>
              <div v-for="(h,i) in history" :key="i" style="padding:6px 0;border-bottom:1px solid #f5f2ee;cursor:pointer;display:flex;justify-content:space-between;align-items:center" @click="loadHistory(h)">
                <div style="flex:1;min-width:0;font-size:12px">
                  <span style="color:#5d4037;font-weight:500">{{ (Array.isArray(h.topics) ? h.topics.join('、') : String(h.topics||'全部').replace(/[\[\"]/g,'')) }}</span>
                  <span style="color:#bcaaa4;margin-left:6px;font-size:11px">{{ (h.time||'').substring(0,16) }}</span>
                </div>
                <el-button size="small" text type="danger" @click.stop="delHistory(i)">✕</el-button>
              </div>
            </div>
            <div v-if="history.length" style="margin-top:8px;text-align:right">
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
              <div class="step-title">🎯 你对哪个技术方向感兴趣？</div>
              <el-select v-model="profile.topics" multiple placeholder="选择方向（可多选）" size="large" style="width:100%" placement="bottom-start">
                <el-option v-for="t in topicOptions" :key="t" :label="t" :value="t" />
              </el-select>
              <el-input v-model="profile.customTopics" placeholder="或输入自定义方向..." style="margin-top:12px" maxlength="200" />
            </template>
            <!-- 步骤2: 目的 -->
            <template v-if="currentStep === 1">
              <div class="step-title">💡 为什么想做这个方向的项目？</div>
              <el-select v-model="profile.purpose" placeholder="选择你的目的" size="large" style="width:100%" placement="bottom-start">
                <el-option v-for="t in purposeOptions" :key="t" :label="t" :value="t" />
              </el-select>
              <el-input v-model="profile.customPurpose" placeholder="或输入自定义目的..." style="margin-top:12px" maxlength="200" />
            </template>
            <!-- 步骤3: 基础 -->
            <template v-if="currentStep === 2">
              <div class="step-title">📚 你的编程基础怎么样？</div>
              <el-select v-model="profile.experience" placeholder="编程经验" size="large" style="width:100%" placement="bottom-start">
                <el-option v-for="t in expOptions" :key="t" :label="t" :value="t" />
              </el-select>
              <el-select v-model="profile.languages" multiple placeholder="掌握的语言（可多选）" size="large" style="width:100%;margin-top:12px">
                <el-option v-for="t in langOptions" :key="t" :label="t" :value="t" />
              </el-select>
              <el-input v-model="profile.customLang" placeholder="或输入自定义..." style="margin-top:12px" maxlength="200" />
            </template>
            <!-- 步骤4: 时间 -->
            <template v-if="currentStep === 3">
              <div class="step-title">⏰ 你每周能投入多少时间？</div>
              <el-select v-model="profile.timeCommitment" placeholder="选择时间投入" size="large" style="width:100%" placement="bottom-start">
                <el-option v-for="t in timeOptions" :key="t" :label="t" :value="t" />
              </el-select>
              <el-input v-model="profile.customTime" placeholder="或输入自定义..." style="margin-top:12px" maxlength="200" />
            </template>
          </div>
          </Transition>
        </div>

        <!-- 底部按钮 -->
        <div style="display:flex;justify-content:space-between;align-items:center;margin-top:16px">
          <button v-if="currentStep > 0" class="nav-btn prev" @click="goStep(currentStep-1)">← 上一步</button>
          <div v-else />
          <button v-if="currentStep < 3" class="orange-btn" @click="goStep(currentStep+1)">下一步 →</button>
          <button v-else class="orange-btn squeeze-btn" @click="startJuicing" :disabled="analyzing">
            <span v-if="!analyzing">🍊 开始榨汁分析</span>
            <span v-else class="squeezing">🍊🍊🍊🍊 榨汁中...</span>
          </button>
        </div>
        <div v-if="v2Error" style="color:#e65100;font-size:13px;margin-top:10px;padding:8px;background:#fff3e0;border-radius:8px">{{ v2Error }}</div>
      </div>

      <!-- 榨汁动画 -->
      <div v-if="juicing" class="juice-animation">
        <div class="juice-scene">
          <span class="j-orange" style="animation-delay:.0s">🍊</span>
          <span class="j-orange" style="animation-delay:.3s">🍊</span>
          <span class="j-orange" style="animation-delay:.6s">🍊</span>
          <span class="j-orange" style="animation-delay:.9s">🍊</span>
          <span class="j-cup">🥤</span>
        </div>
        <div style="margin-top:16px;font-size:14px;color:#e65100;font-weight:600">{{ squeezeText }}</div>
      </div>

      <!-- 结果区 -->
      <div v-if="v2Result" style="margin-top:16px">
        <!-- 领域洞察 -->
        <div style="background:#fff;border-radius:16px;padding:20px;box-shadow:0 2px 12px rgba(0,0,0,.04);border-left:4px solid #ff9800">
          <div style="font-weight:700;font-size:15px;color:#5d4037;margin-bottom:12px">📊 领域洞察</div>
          <el-row :gutter="12">
            <el-col :span="8">
              <div class="leaf-card">
                <span class="leaf-dot">🍃</span>
                <div class="leaf-label">发展趋势</div>
                <div class="leaf-text">{{ v2Result.analysis?.trend || '-' }}</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="leaf-card">
                <span class="leaf-dot">🔮</span>
                <div class="leaf-label">发展前景</div>
                <div class="leaf-text">{{ v2Result.analysis?.prospect || '-' }}</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="leaf-card">
                <span class="leaf-dot">📈</span>
                <div class="leaf-label">市场需求</div>
                <div class="leaf-text">{{ v2Result.analysis?.demand || '-' }}</div>
              </div>
            </el-col>
          </el-row>
          <div v-if="v2Result.analysis?.summary" style="margin-top:12px;padding-top:12px;border-top:1px solid #ffe0b2;font-size:13px;color:#5d4037;line-height:1.6">
            💬 {{ v2Result.analysis.summary }}
          </div>
        </div>

        <!-- 推荐项目 -->
        <div style="font-weight:700;font-size:15px;color:#5d4037;margin:20px 0 12px">🍊 为你推荐</div>
        <el-row :gutter="12">
          <el-col :span="8" v-for="(r, i) in v2Result.recommendations" :key="i">
            <div class="rec-card" style="margin-bottom:14px;height:100%">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
            <div style="display:flex;align-items:center;gap:8px">
              <span class="rec-num">🍊 #{{ i+1 }}</span>
              <span style="font-weight:700;font-size:15px;color:#5d4037">{{ r.name }}</span>
            </div>
            <div style="display:flex;align-items:center;gap:8px">
              <el-tag :type="(r.difficulty==='入门'?'success':r.difficulty==='中等'?'warning':'danger')||'info'" size="small" effect="dark">{{ r.difficulty || '未知' }}</el-tag>
              <span style="font-size:12px;color:#e65100;font-weight:700">{{ r.score }}分</span>
            </div>
          </div>
          <p style="font-size:13px;color:#6b6b6b;margin:0 0 10px;line-height:1.6">{{ r.description }}</p>
          <div style="margin-top:10px;font-size:13px;line-height:1.8">
            <p v-if="r.vibeCodingReason" style="background:#fff8e1;padding:8px 10px;border-radius:8px;font-size:12px">🤖 <b>Vibe Coding：</b>{{ r.vibeCodingReason }}</p>
            <p style="margin-top:6px"><span style="color:#e65100">●</span> <b>推荐：</b>{{ r.recommendReason }}</p>
            <p><span style="color:#ff9800">●</span> <b>前置：</b>{{ r.prerequisites }}</p>
            <p v-if="r.references?.length">
              <span style="color:#81c784">●</span> <b>参考：</b>
              <span v-for="(ref,j) in r.references" :key="j">
                <template v-if="ref.url"><a :href="ref.url" target="_blank" style="color:#e65100">{{ ref.name }}</a></template>
                <template v-else>{{ ref }}</template><span v-if="j < r.references.length-1">、</span>
              </span>
            </p>
          </div>
          <button class="import-plan-btn" @click="importToPlan(r)">📋 一键导入方案设计</button>
        </div>
          </el-col>
        </el-row>

        <!-- 学习路径 -->
        <div v-if="v2Result.learning" style="background:#fff;border-radius:16px;padding:20px;box-shadow:0 2px 12px rgba(0,0,0,.04);border-left:4px solid #81c784;margin-top:12px">
          <div style="font-weight:700;font-size:15px;color:#5d4037;margin-bottom:12px">📖 学习路径</div>
          <div v-if="v2Result.learning.steps?.length" style="display:flex;align-items:center;gap:0;margin-bottom:12px;flex-wrap:wrap">
            <template v-for="(s,j) in v2Result.learning.steps" :key="j">
              <div class="step-dot">{{ s }}</div>
              <span v-if="j < v2Result.learning.steps.length-1" style="color:#e65100;font-size:18px;margin:0 2px">→</span>
            </template>
          </div>
          <div v-if="v2Result.learning.timeline" style="font-size:13px;color:#5d4037;margin-bottom:4px"><b>⏱ 预计：</b>{{ v2Result.learning.timeline }}</div>
          <div v-if="v2Result.learning.tips" style="font-size:13px;color:#6b6b6b;line-height:1.6">{{ v2Result.learning.tips }}</div>
        </div>
      </div>

      <!-- 旧版折叠 -->
      <el-collapse style="margin-top:16px">
        <el-collapse-item title="💬 自由提问（旧版）">
          <el-row :gutter="8">
            <el-col :span="6"><el-input v-model="aiEndpoint" placeholder="https://api.deepseek.com/v1" size="small" /></el-col>
            <el-col :span="3"><el-input v-model="aiModel" placeholder="deepseek-chat" size="small" /></el-col>
            <el-col :span="5"><el-input v-model="aiKey" placeholder="sk-xxx" type="password" show-password size="small" /></el-col>
            <el-col :span="2"><el-button size="small" @click="testConnection" :loading="testLoading">测试</el-button></el-col>
            <el-col :span="8">
              <el-input v-model="aiQuestion" placeholder="自由提问" size="small" @keyup.enter="askAI">
                <template #append><el-button :loading="aiLoading" @click="askAI">发送</el-button></template>
              </el-input>
            </el-col>
          </el-row>
          <div v-if="aiReply" style="background:#faf9f7;border-radius:8px;padding:12px;max-height:300px;overflow:auto;line-height:1.8;font-size:13px;margin-top:8px" v-html="aiReplyHtml"></div>
          <div v-if="aiError" style="color:#F56C6C;font-size:13px;margin-top:4px">{{ aiError }}</div>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { marked } from 'marked'
import request from '../api/request'

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
  customTopics: '', customPurpose: '', customLang: '', customTime: ''
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
  juicing.value = true; analyzing.value = true; v2Error.value = ''; v2Result.value = null
  squeezeText.value = '🍊 正在收集数据...'; let idx = 0
  squeezeTimer = setInterval(() => { idx = (idx+1) % squeezeTexts.length; squeezeText.value = squeezeTexts[idx] }, 3000)
  // 播放榨汁动画 2.5 秒后开始 API 调用
  await new Promise(r => setTimeout(r, 2500))
  juicing.value = false
  try {
    const r = await request.post('/app/trend/analyze-v2', {
      topics: profile.topics || [],
      purpose: profile.purpose || '',
      experience: profile.experience || '',
      languages: profile.languages || [],
      timeCommitment: profile.timeCommitment || '',
      customTopics: profile.customTopics || '',
      customPurpose: profile.customPurpose || '',
      customLang: profile.customLang || '',
      customTime: profile.customTime || ''
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
      endpoint: aiEndpoint.value,
      model: aiModel.value || 'gpt-4o',
      apiKey: aiKey.value,
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
  if (!aiKey.value || !aiEndpoint.value || !aiQuestion.value.trim()) {
    aiError.value = '请填写 API Endpoint、API Key 和分析问题'
    return
  }
  aiLoading.value = true; aiError.value = ''; aiReply.value = ''; aiReplyHtml.value = ''
  try {
    const r = await request.post('/app/trend/analyze', {
      endpoint: aiEndpoint.value,
      model: aiModel.value,
      apiKey: aiKey.value,
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

onMounted(() => { loadGraph(); loadHistoryFromDB() })
onUnmounted(() => { chart?.dispose(); chart = null })
</script>

<style scoped>
/* 🍊 步骤表单 */
.orange-form-card { background: #fff; border-radius: 20px; padding: 24px; box-shadow: 0 2px 16px rgba(230,81,0,.08); }
.steps-bar { margin-bottom: 20px; }
.steps-track { display: flex; justify-content: center; gap: 0; }
.step-item { display: flex; flex-direction: column; align-items: center; gap: 6px; flex: 1; position: relative; cursor: default; }
.step-item + .step-item::before { content: ''; position: absolute; top: 16px; left: -50%; width: 100%; height: 2px; background: #ffe0b2; z-index: 0; }
.step-item.active + .step-item::before, .step-item.done + .step-item::before { background: #e65100; }
.step-circle { width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 14px; background: #fff3e0; color: #e65100; border: 2px solid #ffe0b2; position: relative; z-index: 1; transition: all .3s; }
.step-item.active .step-circle { background: #e65100; color: #fff; border-color: #e65100; box-shadow: 0 0 8px rgba(230,81,0,.4); }
.step-item.done .step-circle { background: #e65100; color: #fff; border-color: #e65100; }
.step-label { font-size: 12px; color: #bcaaa4; transition: color .3s; }
.step-item.active .step-label, .step-item.done .step-label { color: #5d4037; font-weight: 600; }

.step-card-wrapper { overflow: hidden; }
.step-card { background: #fffdf5; border-radius: 16px; padding: 28px 24px; min-height: 180px; position: relative; overflow: hidden; border: 1px solid #ffe0b2; }
.card-bg-orange { position: absolute; left: 50%; top: 50%; transform: translate(-50%,-50%); width: 120px; height: 120px; background: radial-gradient(circle at 35% 35%, #fff3e0, #ffe0b2 70%); border-radius: 45% 55% 50% 50%; opacity: .4; pointer-events: none; }
.card-bg-orange::after { content: ''; position: absolute; top: -8px; left: 40%; width: 14px; height: 8px; background: #a5d6a7; border-radius: 50% 50% 0 0; }
.step-title { font-size: 16px; font-weight: 700; color: #5d4037; margin-bottom: 16px; }
.anim-in { animation: slideIn .35s ease-out; }
@keyframes slideIn { from { opacity: 0; transform: translateX(30px); } to { opacity: 1; transform: translateX(0); } }

/* 🍊 橙子滚动 - Vue Transition */
.step-card-wrapper { position: relative; }
.step-card { transition: none; }

/* 下一步: 旧卡向左滚走 */
.slide-right-leave-active { animation: slideOutL .35s ease-in forwards; position: absolute; width: 100%; top: 0; left: 0; }
@keyframes slideOutL {
  from { opacity: 1; transform: translateX(0); }
  to { opacity: 0; transform: translateX(-80px); }
}
.slide-right-leave-active .card-bg-orange { animation: rollOutL .35s ease-in forwards; }
@keyframes rollOutL {
  from { opacity: .4; transform: translate(-50%,-50%) rotate(0); }
  to { opacity: 0; transform: translate(calc(-50% - 200px),-50%) rotate(-200deg); }
}

/* 下一步: 新卡从右边滚入 */
.slide-right-enter-active { animation: slideInR .35s ease-out forwards; }
@keyframes slideInR {
  from { opacity: 0; transform: translateX(80px); }
  to { opacity: 1; transform: translateX(0); }
}
.slide-right-enter-active .card-bg-orange { animation: rollInR .35s ease-out forwards; }
@keyframes rollInR {
  from { opacity: 0; transform: translate(calc(-50% + 200px),-50%) rotate(200deg); }
  to { opacity: .4; transform: translate(-50%,-50%) rotate(0); }
}

/* 上一步: 旧卡向右滚走 */
.slide-left-leave-active { animation: slideOutR .35s ease-in forwards; position: absolute; width: 100%; top: 0; left: 0; }
@keyframes slideOutR {
  from { opacity: 1; transform: translateX(0); }
  to { opacity: 0; transform: translateX(80px); }
}
.slide-left-leave-active .card-bg-orange { animation: rollOutR .35s ease-in forwards; }
@keyframes rollOutR {
  from { opacity: .4; transform: translate(-50%,-50%) rotate(0); }
  to { opacity: 0; transform: translate(calc(-50% + 200px),-50%) rotate(200deg); }
}

/* 上一步: 新卡从左边滚入 */
.slide-left-enter-active { animation: slideInL .35s ease-out forwards; }
@keyframes slideInL {
  from { opacity: 0; transform: translateX(-80px); }
  to { opacity: 1; transform: translateX(0); }
}
.slide-left-enter-active .card-bg-orange { animation: rollInL .35s ease-out forwards; }
@keyframes rollInL {
  from { opacity: 0; transform: translate(calc(-50% - 200px),-50%) rotate(-200deg); }
  to { opacity: .4; transform: translate(-50%,-50%) rotate(0); }
}

/* 按钮 */
.orange-btn { background: linear-gradient(135deg, #e65100, #ff9800); color: #fff; border: none; border-radius: 24px; padding: 10px 28px; font-size: 15px; font-weight: 700; cursor: pointer; transition: all .2s; box-shadow: 0 3px 12px rgba(230,81,0,.25); }
.orange-btn:hover { transform: translateY(-2px) scale(1.03); box-shadow: 0 6px 20px rgba(230,81,0,.35); }
.orange-btn:disabled { opacity: .6; cursor: not-allowed; }
.squeeze-btn { padding: 14px 36px; font-size: 16px; }
.nav-btn { background: #fff; color: #e65100; border: 1px solid #ffe0b2; border-radius: 24px; padding: 10px 20px; font-size: 14px; cursor: pointer; transition: all .2s; }
.nav-btn:hover { background: #fff3e0; }
.squeezing { animation: squeeze 1s ease-in-out infinite; display: inline-block; }
@keyframes squeeze { 0%,100% { transform: scale(1); } 50% { transform: scale(1.15); } }

/* 结果卡片 */
.leaf-card { background: #fffdf7; border-radius: 12px; padding: 14px; border: 1px solid #ffe0b2; height: 100%; }
.leaf-dot { font-size: 18px; display: block; margin-bottom: 6px; }
.leaf-label { font-size: 11px; color: #bcaaa4; margin-bottom: 2px; }
.leaf-text { font-size: 13px; color: #5d4037; font-weight: 500; line-height: 1.5; }

.rec-card { background: #fff; border-radius: 16px; padding: 18px; box-shadow: 0 2px 12px rgba(0,0,0,.04); border-left: 4px solid #e65100; transition: transform .15s; }
.rec-card:hover { transform: translateY(-2px); }
.rec-num { background: linear-gradient(135deg, #e65100, #ff9800); color: #fff; font-size: 11px; font-weight: 700; padding: 3px 10px; border-radius: 12px; }

.juice-glass { position: relative; width: 36px; height: 60px; background: #fff8f0; border: 2px solid #ffe0b2; border-radius: 8px 8px 18px 18px; overflow: hidden; }
.juice-liquid { position: absolute; bottom: 0; left: 0; right: 0; background: linear-gradient(0deg, #e65100, #ff9800 40%, #ffcc80 100%); border-radius: 0 0 16px 16px; transition: height .8s ease-out; display: flex; align-items: center; justify-content: center; min-height: 0; }
.juice-liquid::after { content: ''; position: absolute; top: 4px; left: 0; right: 0; height: 3px; background: rgba(255,255,255,.25); border-radius: 50%; }
.juice-straw { position: absolute; top: -18px; left: 50%; transform: translateX(-50%) rotate(-15deg); width: 4px; height: 26px; background: linear-gradient(180deg, #ff9800, #e65100); border-radius: 2px; }
.juice-score { color: #fff; font-size: 14px; font-weight: 700; text-shadow: 0 1px 3px rgba(0,0,0,.4); position: relative; z-index: 1; }
.juice-score-outside { font-size: 12px; color: #e65100; font-weight: 700; }

.step-dot { background: linear-gradient(135deg, #fff, #fff3e0); border: 1px solid #ffe0b2; border-radius: 20px; padding: 6px 14px; font-size: 12px; color: #5d4037; white-space: nowrap; }
.import-plan-btn { width: 100%; margin-top: 10px; padding: 8px 12px; font-size: 12px; background: linear-gradient(135deg, #fff, #fff3e0); color: #e65100; border: 1px solid #ffe0b2; border-radius: 20px; cursor: pointer; font-weight: 600; transition: all .15s; }
.import-plan-btn:hover { background: #fff3e0; border-color: #e65100; }
</style>
