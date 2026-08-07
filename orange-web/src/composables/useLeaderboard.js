import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageTour from '../components/PageTour.vue'
import BeginnerProjects from '../views/BeginnerProjects.vue'
import CareerProjects from '../views/CareerProjects.vue'
import request from '../api/request'
import { openExternalSafely } from './useSafeExternalNavigation'

export const useLeaderboard = () => {
const router = useRouter()
const route = useRoute()

const tourSteps = [
  { selector: '.leaderboard-filters', text: '切换本网站、GitHub 和新手推荐三个数据源。新手推荐只展示已经完成环境核验和中文说明的真实仓库。', expression: 'wink' },
  { selector: '.leaderboard-tabs', text: '本网站榜单分作品和创作者两个维度，分别看哪些作品最受欢迎、哪些创作者最高产。', expression: 'smile' },
  { selector: '.rank-grid, .rank-card', text: '点击任意卡片跳转到作品详情或 GitHub 仓库。排名越靠前越热门，看看有没有你感兴趣的方向～', expression: 'sparkle' },
]

const validSources = new Set(['local', 'github', 'beginner', 'career'])
const normalizeSource = value => validSources.has(String(value)) ? String(value) : 'local'
const source = ref(normalizeSource(route.query.source))
const period = ref('all')
const category = ref(null)
const type = ref(source.value === 'github' ? 'repos' : ['beginner', 'career'].includes(source.value) ? source.value : 'works')
const list = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const categories = ref([])

const warning = ref('')

const emptyText = computed(() => {
  if (warning.value) return warning.value
  if (category.value && period.value === 'week') return '该分类本周暂无内容'
  if (category.value) return '该分类暂无内容'
  if (period.value === 'week') return '本周涨星暂无数据'
  if (period.value === 'new') return '本周暂无新增项目'
  if (source.value === 'github') return 'GitHub 数据暂无内容'
  return '暂无内容'
})

const loadCategories = async () => {
  try {
    const r = await request.get('/app/category', { params: { type: 'WORK' } })
    if (r.code === 1) {
      categories.value = r.data || []
    }
  } catch {}
}

const load = async () => {
  if (['beginner', 'career'].includes(source.value)) {
    list.value = []
    total.value = 0
    warning.value = ''
    return
  }
  loading.value = true
  try {
    let endpoint, params = { page: page.value, size: pageSize.value }

    if (source.value === 'local') {
      if (type.value === 'works') {
        endpoint = '/app/leaderboard/works'
        if (category.value) params.categoryId = category.value
        params.period = period.value
      } else {
        endpoint = '/app/leaderboard/creators'
        params.period = period.value
      }
    } else {
      endpoint = '/app/leaderboard/github'
      if (category.value) params.topicName = category.value
      params.period = period.value
    }

    const r = await request.get(endpoint, { params })
    if (r.code === 1 && r.data) {
      list.value = r.data.records || []
      total.value = r.data.total || 0
      warning.value = r.data.warning || ''
    } else {
      list.value = []
      total.value = 0
      warning.value = ''
    }
  } catch {
    list.value = []
    total.value = 0
    warning.value = source.value === 'github' ? 'GitHub API 暂时不可用，请稍后重试' : ''
  } finally {
    loading.value = false
  }
}

const onSourceChange = () => {
  page.value = 1
  category.value = null
  warning.value = ''
  router.replace({ path: '/leaderboard', query: source.value === 'local' ? {} : { source: source.value } })
  if (source.value === 'github') type.value = 'repos'
  else if (source.value === 'beginner') type.value = 'beginner'
  else if (source.value === 'career') type.value = 'career'
  else {
    if (!['works', 'creators'].includes(type.value)) type.value = 'works'
    if (period.value === 'new') period.value = 'all' // "本周新增"仅 GitHub 有
  }
  load()
}

watch(() => route.query.source, value => {
  const next = normalizeSource(value)
  if (next !== source.value) {
    source.value = next
    onSourceChange()
  }
})

const onClick = (item) => {
  if (source.value === 'local' && type.value === 'works') {
    router.push('/work/' + item.id)
  } else if (source.value === 'local' && type.value === 'creators') {
    router.push('/profile/' + item.id)
  } else if (source.value === 'github') {
    openExternalSafely(item.htmlUrl)
  }
}

const recommendWithPersona = () => {
  const preset = source.value === 'beginner' ? 'beginner'
    : source.value === 'career' ? 'career' : 'general'
  return router.push({
    path: '/trend',
    query: { persona: '1', rag: '1', preset, autoAnalyze: '1', leaderboardSource: source.value },
  })
}

onMounted(async () => {
  await loadCategories()
  load()
})
  return { PageTour, BeginnerProjects, CareerProjects, tourSteps, source, period, category, type,
    list, loading, page, pageSize, total, categories, warning, emptyText, load, onSourceChange,
    onClick, recommendWithPersona }}
