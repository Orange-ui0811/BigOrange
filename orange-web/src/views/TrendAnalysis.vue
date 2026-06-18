<template>
  <div>
    <h3 style="margin:0 0 4px;font-weight:700">趋势分析</h3>
    <p style="color:#909399;font-size:13px;margin:0 0 8px">Vibe Coding 开源项目热度 · 每周更新 · AI 辅助编程趋势洞察</p>

    <!-- 趋势洞察 Tab -->
    <el-tabs v-model="mainTab" style="margin-bottom:12px">
      <el-tab-pane label="📊 趋势洞察" name="insights">
        <el-row :gutter="16" v-if="insights">
          <el-col :span="6" v-for="card in insightCards" :key="card.title">
            <el-card shadow="never" style="border-radius:10px;text-align:center">
              <div style="font-size:13px;color:#909399;margin-bottom:8px">{{ card.title }}</div>
              <el-tag v-for="n in card.items" :key="n" :type="card.tagType" size="small" style="margin:2px 4px">{{ n }}</el-tag>
            </el-card>
          </el-col>
        </el-row>
        <el-empty v-else description="数据加载中..." :image-size="60" />
      </el-tab-pane>
      <el-tab-pane label="🔗 知识图谱" name="graph" />
    </el-tabs>

    <el-row :gutter="16" v-if="mainTab==='graph' && graphLoaded">
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
                <el-radio-button value="metrics">分析</el-radio-button>
                <el-radio-button value="repos">仓库</el-radio-button>
              </el-radio-group>
            </div>
            <el-divider style="margin:8px 0" />
            <!-- 分析视图 -->
            <template v-if="detailTab==='metrics'">
              <el-descriptions :column="1" size="small" border>
                <el-descriptions-item label="总 Stars">{{ formatNum(selectedNode.totalStars) }}</el-descriptions-item>
                <el-descriptions-item label="仓库数">{{ selectedNode.repoCount }}</el-descriptions-item>
                <el-descriptions-item v-if="selectedNode.metrics" label="拥挤度">
                  <el-tag :type="crowdType(selectedNode.metrics.crowdedness)" size="small">{{ selectedNode.metrics.crowdedness }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item v-if="selectedNode.metrics" label="难度">
                  <el-tag :type="diffType(selectedNode.metrics.difficulty)" size="small">{{ selectedNode.metrics.difficulty }}</el-tag>
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
          <el-empty v-else description="点击图谱节点或连线查看详情" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <!-- AI 分析面板 -->
    <el-card shadow="never" style="border-radius:12px;margin-top:16px">
      <h4 style="margin:0 0 12px">🤖 AI 趋势分析</h4>
      <el-row :gutter="8" style="margin-bottom:8px">
        <el-col :span="6"><el-input v-model="aiEndpoint" placeholder="https://api.deepseek.com/v1" size="small" /></el-col>
        <el-col :span="3"><el-input v-model="aiModel" placeholder="deepseek-chat" size="small" /></el-col>
        <el-col :span="5"><el-input v-model="aiKey" placeholder="sk-xxx" type="password" show-password size="small" /></el-col>
        <el-col :span="2"><el-button size="small" @click="testConnection" :loading="testLoading">测试</el-button></el-col>
        <el-col :span="8">
          <el-input v-model="aiQuestion" placeholder="如：哪些方向是蓝海？" size="small" @keyup.enter="askAI">
            <template #append><el-button :loading="aiLoading" @click="askAI">分析</el-button></template>
          </el-input>
        </el-col>
      </el-row>
      <div v-if="aiReply" style="background:#faf9f7;border-radius:8px;padding:16px;max-height:400px;overflow:auto;line-height:1.8;font-size:13px" v-html="aiReplyHtml"></div>
      <div v-if="aiError" style="color:#F56C6C;font-size:13px;margin-top:8px">{{ aiError }}</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { marked } from 'marked'
import request from '../api/request'

const chartRef = ref(null)
let chart = null
const graphLoading = ref(false)
const graphLoaded = ref(false)
const mainTab = ref('insights')
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

const scoreColor = (v) => v >= 70 ? '#67C23A' : v >= 45 ? '#E6A23C' : '#F56C6C'

const insightCards = computed(() => {
  if (!insights.value) return []
  const types = [
    { key: 'hotTop3', title: '🔥 热门方向', tagType: 'danger' },
    { key: 'blueTop3', title: '🌊 蓝海机会', tagType: 'success' },
    { key: 'growTop3', title: '📈 增长最快', tagType: 'warning' },
    { key: 'oppTop3', title: '⭐ 最佳机会', tagType: '' }
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
      // 初始不渲染，等切换到图谱 Tab
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
    group: n.group
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

onMounted(() => loadGraph())
onUnmounted(() => { chart?.dispose(); chart = null })

watch(mainTab, async (tab) => {
  if (tab === 'graph') {
    graphLoaded.value = true
    await nextTick()
    setTimeout(() => {
      if (graphData.value && chartRef.value) {
        if (chart) chart.dispose(); chart = null
        renderChart(viewMode.value)
      }
    }, 100)
  }
})
</script>
