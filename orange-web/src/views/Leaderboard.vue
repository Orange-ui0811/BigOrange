<template>
  <div class="leaderboard-page">
    <PageTour storageKey="leaderboard" :steps="tourSteps" />
    <!-- Compact Head -->
    <div class="compact-head">
      <div>
        <span class="eyebrow">榜单中心</span>
        <h2>从平台热度到 GitHub 趋势，找到真正适合你的项目。</h2>
        <p>浏览站内作品、GitHub 热门仓库、新手可落地项目，或按真实岗位能力筛选职业成长项目。</p>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="leaderboard-filters">
      <span class="filter-label">数据源</span>
      <el-radio-group v-model="source" size="default" @change="onSourceChange" class="filter-group">
        <el-radio-button value="local">本网站</el-radio-button>
        <el-radio-button value="github">GitHub</el-radio-button>
        <el-radio-button value="beginner">新手推荐</el-radio-button>
        <el-radio-button value="career">职业成长</el-radio-button>
      </el-radio-group>

      <span v-if="!['beginner','career'].includes(source)" class="filter-label filter-label-spaced">时间</span>
      <el-radio-group v-if="!['beginner','career'].includes(source)" v-model="period" size="default" @change="load" class="filter-group">
        <el-radio-button value="all">全时段</el-radio-button>
        <el-radio-button value="week">本周涨星</el-radio-button>
        <el-radio-button value="new" v-if="source==='github'">本周新增</el-radio-button>
      </el-radio-group>

      <span v-if="!['beginner','career'].includes(source)" class="filter-label filter-label-spaced">分类</span>
      <el-select v-if="!['beginner','career'].includes(source)" v-model="category" placeholder="全部" clearable size="default" class="filter-select" @change="load">
        <el-option label="全部" :value="undefined" />
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="source==='github'?(c.topicName||c.id):c.id" />
      </el-select>
    </div>

    <BeginnerProjects v-if="source === 'beginner'" embedded class="beginner-source" />
    <CareerProjects v-if="source === 'career'" class="beginner-source" />

    <template v-if="!['beginner','career'].includes(source)">
      <!-- GitHub 降级提示 -->
      <el-alert v-if="warning" :title="warning" type="warning" show-icon closable class="leaderboard-alert" />

      <!-- 子 Tab -->
      <el-tabs v-model="type" class="leaderboard-tabs" @tab-change="load">
        <el-tab-pane label="作品" name="works" v-if="source==='local'" />
        <el-tab-pane label="仓库" name="repos" v-if="source==='github'" />
        <el-tab-pane label="创作者" name="creators" v-if="source==='local'" />
      </el-tabs>

    <!-- 加载态 -->
    <el-row :gutter="16" v-if="loading">
      <el-col :span="6" v-for="i in 8" :key="i" class="skeleton-col">
        <el-skeleton :loading="true" animated class="skeleton-card">
          <template #template>
            <el-skeleton-item variant="text" class="skeleton-line-wide" />
            <el-skeleton-item variant="text" class="skeleton-line-mid" />
            <el-skeleton-item variant="text" class="skeleton-line-narrow" />
          </template>
        </el-skeleton>
      </el-col>
    </el-row>

    <!-- 数据列表 -->
    <el-row :gutter="16" v-else>
      <el-col :span="6" v-for="(item, idx) in list" :key="item.id||idx" class="card-col">
        <el-card shadow="never" class="rank-card" @click="onClick(item)">
          <!-- 排名徽章 -->
          <div class="rank-badge">
            {{ (page-1)*pageSize + idx + 1 }}
          </div>

          <!-- 本网站作品 -->
          <template v-if="source==='local' && type==='works'">
            <div class="card-title">{{ item.title || item.name }}</div>
            <div class="card-meta">
              <span class="card-owner">{{ item.ownerName || item.username }}</span>
              <span v-if="item.category_name" class="card-category">{{ item.category_name }}</span>
            </div>
            <div class="card-footer">
              <span class="card-score">&#9733; {{ item.favoriteCount || item.favorite_count || 0 }}</span>
            </div>
          </template>

          <!-- 本网站创作者 -->
          <template v-if="source==='local' && type==='creators'">
            <div class="card-creator-row">
              <el-avatar :size="36" :src="item.avatarUrl||item.avatar_url" class="creator-avatar">{{ (item.username||'?')[0] }}</el-avatar>
              <div class="card-title">{{ item.username }}</div>
            </div>
            <div class="card-bio">{{ item.bio || '' }}</div>
            <div class="card-stats">
              <span>&#9733; {{ item.totalFavorites || item.total_favorites || 0 }}</span>
              <span>{{ item.workCount || item.work_count || 0 }}作品</span>
            </div>
          </template>

          <!-- GitHub 仓库 -->
          <template v-if="source==='github'">
            <div class="card-title">{{ item.name }}</div>
            <div class="card-fullname">{{ item.fullName }}</div>
            <div class="card-description">
              {{ item.descriptionZh || item.description || '暂无描述' }}
            </div>
            <div class="card-footer">
              <span class="card-score">&#9733; {{ item.stars }}</span>
              <span v-if="item.language" class="card-language">{{ item.language }}</span>
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>

      <!-- 空态 -->
      <el-empty v-if="!loading && !list.length" :description="emptyText" />

      <!-- 分页 -->
      <div v-if="total > pageSize" class="leaderboard-pagination">
        <el-pagination background layout="total, prev, pager, next" :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="load" />
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageTour from '../components/PageTour.vue'
import BeginnerProjects from './BeginnerProjects.vue'
import CareerProjects from './CareerProjects.vue'
import request from '../api/request'

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
    window.open(item.htmlUrl, '_blank')
  }
}

onMounted(async () => {
  await loadCategories()
  load()
})
</script>

<style scoped>
.leaderboard-page {
  /* outer wrapper — no extra card, compact-head handles the top */
}
.beginner-source { margin-top: 4px; }

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

/* Filters */
.leaderboard-filters {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.filter-label {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 800;
  font-family: var(--font-mono);
  text-transform: uppercase;
  letter-spacing: .05em;
}

.filter-label-spaced {
  margin-left: 8px;
}

.filter-group :deep(.el-radio-button__inner) {
  font-family: var(--font-mono);
  font-size: 12px;
  border-radius: 14px;
  border: 1px solid var(--border-orange);
  background: #ffffff;
  color: var(--text-secondary);
  box-shadow: none;
  padding: 6px 14px;
}

.filter-group :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--brand);
  border-color: var(--brand);
  color: #fff;
  box-shadow: 2px 2px 0 rgba(154,83,28,.12);
}

.filter-select {
  width: 160px;
}

.filter-select :deep(.el-input__wrapper) {
  border-radius: 14px;
  border: 1px solid var(--border-orange);
  box-shadow: none;
  height: 36px;
}

.filter-select :deep(.el-input__wrapper:focus),
.filter-select :deep(.el-input__wrapper.is-focus) {
  border-color: #f37021;
  box-shadow: none;
}

/* Alert */
.leaderboard-alert {
  margin-bottom: 16px;
  border-radius: 16px;
  border: 1px solid #fde68a;
  background: #fffbeb;
}

.leaderboard-alert :deep(.el-alert__title) {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  color: #c55312;
}

/* Tabs */
.leaderboard-tabs {
  margin-bottom: 16px;
}

.leaderboard-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.leaderboard-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: var(--border-orange);
}

.leaderboard-tabs :deep(.el-tabs__item) {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 700;
  color: var(--text-secondary);
  letter-spacing: 0;
  height: 36px;
  line-height: 36px;
}

.leaderboard-tabs :deep(.el-tabs__item.is-active) {
  color: var(--brand-strong);
}

.leaderboard-tabs :deep(.el-tabs__active-bar) {
  background-color: var(--brand);
  height: 2px;
}

/* Skeleton */
.skeleton-col {
  margin-bottom: 16px;
}

.skeleton-card {
  padding: 16px;
  border: 1px solid var(--border-orange);
  border-radius: 14px;
}

.skeleton-line-wide {
  width: 80%;
}

.skeleton-line-mid {
  width: 50%;
}

.skeleton-line-narrow {
  width: 30%;
}

/* Cards */
.card-col {
  margin-bottom: 16px;
}

.rank-card {
  border: 1px solid var(--border-weak);
  border-left: 3px solid var(--brand-soft);
  border-radius: 14px;
  cursor: pointer;
  height: 100%;
  box-shadow: var(--shadow-card);
  position: relative;
  transition: transform .15s, box-shadow .15s, border-color .15s;
}

.rank-card:hover {
  border-color: var(--brand-strong);
  transform: translate(-2px, -2px);
  box-shadow: var(--shadow-pixel);
}

.rank-card :deep(.el-card__body) {
  padding: 18px;
}

.rank-badge {
  position: absolute;
  top: -6px;
  left: -6px;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  background: var(--brand);
  color: #fff;
  font-size: 11px;
  font-weight: 900;
  font-family: var(--font-mono);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
  box-shadow: 2px 2px 0 rgba(154,83,28,.12);
}

.card-title {
  font-weight: 600;
  font-size: 15px;
  color: #3a302a;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

.card-meta {
  font-size: 12px;
  color: #9f9890;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

.card-owner {
  color: #9f9890;
}

.card-category {
  color: #f37021;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-score {
  font-size: 13px;
  color: #f37021;
  font-weight: 700;
  font-family: "Courier New", "SimHei", monospace;
}

.card-creator-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.creator-avatar {
  background: linear-gradient(135deg, var(--brand), #fbbf24);
  font-size: 14px;
  font-family: var(--font-mono);
  border-radius: 16px;
  flex-shrink: 0;
}

.card-bio {
  font-size: 12px;
  color: #9f9890;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

.card-stats {
  display: flex;
  gap: 12px;
  font-size: 13px;
  color: #3a302a;
  font-family: "Courier New", "SimHei", monospace;
}

.card-fullname {
  font-size: 11px;
  color: #9f9890;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: "Courier New", "SimHei", monospace;
}

.card-description {
  font-size: 12px;
  color: #9f9890;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
  min-height: 34px;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

.card-language {
  font-size: 11px;
  color: var(--text-primary);
  background: var(--brand-soft);
  padding: 2px 8px;
  border-radius: 999px;
  font-family: var(--font-mono);
  font-weight: 700;
}

/* Pagination */
.leaderboard-pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.leaderboard-pagination :deep(.el-pagination.is-background .el-pager li) {
  border-radius: 2px;
  font-family: "Courier New", "SimHei", monospace;
}

.leaderboard-pagination :deep(.el-pagination.is-background .el-pager li.is-active) {
  background: #f37021;
}

.leaderboard-pagination :deep(.btn-prev),
.leaderboard-pagination :deep(.btn-next) {
  border-radius: 2px;
}
</style>
