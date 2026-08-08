import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import PageTour from '../components/PageTour.vue'
import request from '../api/request'

export const useMarket = () => {
const route = useRoute()
const marketTour = [
  { selector: '.hero-search', text: '在这里搜索作品和需求！输入关键词找你想看的 AI 产品或创意需求。', expression: 'smile' },
  { selector: '.content-tabs', text: '用这些小药丸快速筛选：全部看、只看作品、只看需求。右边还有分类下拉帮你精确筛选～', expression: 'wink' },
  { selector: '.market-grid, .req-list', text: '点击任意卡片进入详情页。作品有封面和演示链接，需求有预算范围。看到喜欢的记得收藏！', expression: 'sparkle' },
]
const activeTab = ref('all')
const works = ref([])
const reqs = ref([])
const worksTotal = ref(0)
const reqsTotal = ref(0)
const categories = ref([])
const categoryId = ref('')
const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const worksPage = ref(1)
const reqsPage = ref(1)
const pageSize = ref(12)
const loading = ref(false)

const coverVariant = (item) => {
  if (!item.coverUrl) {
    const variants = ['', 'dark', 'blue']
    return variants[item.id % 3]
  }
  return ''
}

const loadWorks = async () => {
  loading.value = true
  try {
    const params = { page: worksPage.value, size: pageSize.value }
    if (categoryId.value) params.categoryId = categoryId.value
    if (keyword.value) params.keyword = keyword.value
    const res = await request.get('/app/work/public', { params })
    if (res.code === 1) { works.value = res.data?.records || []; worksTotal.value = res.data?.total || 0 }
  } finally { loading.value = false }
}

const loadReqs = async () => {
  loading.value = true
  try {
    const params = { page: reqsPage.value, size: pageSize.value }
    if (categoryId.value) params.categoryId = categoryId.value
    if (keyword.value) params.keyword = keyword.value
    const res = await request.get('/app/requirement/public', { params })
    if (res.code === 1) { reqs.value = res.data?.records || []; reqsTotal.value = res.data?.total || 0 }
  } finally { loading.value = false }
}

const load = async () => {
  if (activeTab.value === 'works') {
    await loadWorks()
  } else if (activeTab.value === 'reqs') {
    await loadReqs()
  } else {
    // 'all' — 加载少量预览
    loading.value = true
    try {
      const [wRes, rRes] = await Promise.all([
        request.get('/app/work/public', { params: { page: 1, size: 6, categoryId: categoryId.value || undefined, keyword: keyword.value || undefined } }),
        request.get('/app/requirement/public', { params: { page: 1, size: 3, categoryId: categoryId.value || undefined, keyword: keyword.value || undefined } })
      ])
      if (wRes.code === 1) { works.value = wRes.data?.records || []; worksTotal.value = wRes.data?.total || 0 }
      if (rRes.code === 1) { reqs.value = rRes.data?.records || []; reqsTotal.value = rRes.data?.total || 0 }
    } finally { loading.value = false }
  }
}

const switchTab = (tab) => {
  activeTab.value = tab
  if (tab === 'works') worksPage.value = 1
  if (tab === 'reqs') reqsPage.value = 1
  load()
}

const doSearch = () => { worksPage.value = 1; reqsPage.value = 1; load() }

const scrollToSection = (id) => {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(async () => {
  try { const catRes = await request.get('/app/category?type=WORK'); if (catRes.code === 1) categories.value = catRes.data } catch {}
  load()
})

watch([categoryId], () => { worksPage.value = 1; reqsPage.value = 1; load() })
  return { PageTour, marketTour, activeTab, works, reqs, worksTotal, reqsTotal, categories,
    categoryId, keyword, worksPage, reqsPage, pageSize, loading, coverVariant, loadWorks,
    loadReqs, load, switchTab, doSearch, scrollToSection }}
