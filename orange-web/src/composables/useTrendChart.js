import { computed, onBeforeUnmount, ref } from 'vue'
import * as echarts from 'echarts'
import request from '../api/request'

const colors = { '创意技术': '#ffcc80', 'AI 智能': '#90caf9', '自托管': '#a5d6a7',
  '硬件/IoT': '#ce93d8', '其他': '#cfd8dc' }

export const useTrendChart = () => {
  const chartRef = ref(null)
  const graphLoading = ref(false)
  const viewMode = ref('week')
  const graphData = ref(null)
  const insights = ref(null)
  const selectedNode = ref(null)
  const selectedEdge = ref(null)
  const selectedDelta = ref(null)
  const detailTab = ref('metrics')
  const lastStars = ref({})
  let chart

  const insightCards = computed(() => {
    if (!insights.value) return []
    return [
      ['hotTop3', '🔥 热门方向', 'danger'], ['blueTop3', '🌱 新兴方向', 'success'],
      ['growTop3', '📈 增长最快', 'warning'], ['oppTop3', '⭐ 推荐', 'info'],
    ].map(([key, title, tagType]) => ({ title, items: insights.value[key] || [], tagType }))
  })
  const formatNum = value => {
    if (!value) return '0'
    if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
    if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`
    return String(value)
  }
  const resize = () => chart?.resize()

  const renderChart = mode => {
    if (!chartRef.value || !graphData.value) return
    if (!chart) chart = echarts.init(chartRef.value)
    const data = mode === 'week' ? graphData.value.thisWeek : graphData.value.lastWeek
    if (!data?.nodes) return
    const nodes = data.nodes.map(node => ({
      id: node.id, name: node.name,
      symbolSize: 25 + Math.log10(Math.max(1, node.totalStars || 1)) * 12,
      category: node.group, itemStyle: { color: colors[node.group] || '#78909c' },
      totalStars: node.totalStars, repoCount: node.repoCount, topRepos: node.topRepos,
      topLanguages: node.topLanguages, group: node.group, metrics: node.metrics, growthPct: node.growthPct,
    }))
    const edges = (data.edges || []).map(edge => ({ source: edge.source, target: edge.target,
      lineStyle: { width: Math.min(edge.weight || 1, 4), opacity: .3, curveness: .2 } }))
    const categories = Object.keys(colors).map(name => ({ name, itemStyle: { color: colors[name] } }))
    chart.setOption({
      tooltip: { formatter: point => point.dataType === 'node'
        ? `<b>${point.data.name}</b><br/>⭐ ${formatNum(point.data.totalStars)}<br/>📦 ${point.data.repoCount} repos<br/>🏷 ${point.data.group}`
        : `${point.data.source} ↔ ${point.data.target}` },
      legend: { data: categories.map(category => category.name), bottom: 0 },
      series: [{ type: 'graph', layout: 'force',
        force: { repulsion: 400, edgeLength: [150, 350], gravity: .15, friction: .3, layoutAnimation: true },
        roam: true, draggable: true, categories, data: nodes, edges,
        label: { show: true, fontSize: 11, color: '#2c2c2c', formatter: '{b}' },
        emphasis: { focus: 'adjacency', label: { fontSize: 14, fontWeight: 'bold' } } }],
    })
    chart.off('click')
    chart.on('click', point => {
      if (point.dataType === 'node') {
        selectedNode.value = point.data
        selectedEdge.value = null
        detailTab.value = 'metrics'
        const last = lastStars.value[point.data.id]
        selectedDelta.value = last > 0 ? Math.round((point.data.totalStars - last) * 100 / last) : null
      } else if (point.dataType === 'edge') {
        selectedEdge.value = point.data
        selectedNode.value = null
      }
    })
    const nodeMap = Object.fromEntries((data.nodes || []).map(node => [node.id, node]))
    ;(data.edges || []).forEach(edge => {
      const source = nodeMap[edge.source]
      const target = nodeMap[edge.target]
      edge.sourceName = source?.name || edge.source
      edge.targetName = target?.name || edge.target
      const targetLanguages = new Set((target?.topLanguages || []).map(language => language.name))
      edge.sharedLangs = (source?.topLanguages || []).map(language => language.name)
        .filter(language => targetLanguages.has(language))
    })
  }

  const loadGraph = async () => {
    graphLoading.value = true
    try {
      const response = await request.get('/app/trend/graph')
      if (response.code === 1) {
        graphData.value = response.data
        insights.value = response.data.insights || null
        lastStars.value = Object.fromEntries((response.data.lastWeek?.nodes || [])
          .map(node => [node.id, node.totalStars || 0]))
        renderChart(viewMode.value)
      }
    } finally { graphLoading.value = false }
  }
  const onViewChange = () => renderChart(viewMode.value)
  window.addEventListener('resize', resize)
  onBeforeUnmount(() => { window.removeEventListener('resize', resize); chart?.dispose(); chart = null })
  return { chartRef, graphLoading, viewMode, graphData, insights, selectedNode, selectedEdge,
    selectedDelta, detailTab, insightCards, loadGraph, renderChart, onViewChange, formatNum }
}
