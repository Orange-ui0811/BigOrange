<template>
  <div class="market-page">
    <PageTour storageKey="market" :steps="marketTour" />
    <!-- ═══ Hero ═══ -->
    <div class="market-hero">
      <div class="hero-left">
        <span class="eyebrow">发现市场</span>
        <h2>让你的 vibecoding 作品被看到，被发现，被认可。</h2>
        <p>首页只做三件事：搜索作品/需求，按分类筛选，快速进入榜单查看热度。</p>
        <div class="hero-search">
          <input v-model="keyword" placeholder="搜索作品、需求、产品类型或作者，例如：AI 客服 / 网站重构 / Vue 后台" @keyup.enter="doSearch" />
          <el-button type="primary" @click="doSearch">开始搜索</el-button>
        </div>
        <div class="trust-rail">
          <span class="trust-chip">作品与需求先审核</span>
          <span class="trust-chip">演示、截图、说明分开展示</span>
        </div>
      </div>
      <div class="hero-side">
        <div class="signal-card" @click="scrollToSection('works-section')">
          <strong>作品市场</strong>
          <span>封面、简介、作者、热度集中展示，点击进入作品列表。</span>
        </div>
        <div class="signal-card" @click="scrollToSection('reqs-section')">
          <strong>需求大厅</strong>
          <span>预算范围、交付目标和沟通入口优先展示。</span>
        </div>
        <div class="signal-card" @click="$router.push('/leaderboard')">
          <strong>榜单入口</strong>
          <span>本网站作品/创作者榜单，以及 GitHub AI 仓库榜单。</span>
        </div>
      </div>
    </div>

    <!-- ═══ 内容筛选 Pills ═══ -->
    <div class="content-tabs">
      <button :class="['pill', { active: activeTab === 'all' }]" @click="switchTab('all')">全部</button>
      <button :class="['pill', { active: activeTab === 'works' }]" @click="switchTab('works')">作品</button>
      <button :class="['pill', { active: activeTab === 'reqs' }]" @click="switchTab('reqs')">需求</button>
      <el-select v-model="categoryId" placeholder="全部分类" clearable size="small" style="width:140px;margin-left:auto;" @change="load">
        <el-option label="全部分类" :value="null" />
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
    </div>

    <!-- ═══ 作品精选 ═══ -->
    <section v-if="activeTab === 'all' || activeTab === 'works'" id="works-section" class="section">
      <div class="section-head">
        <div>
          <h3>作品精选</h3>
          <p>对齐真实数据：标题、简介、作者、收藏/评论、审核状态和详情入口。</p>
        </div>
        <el-button v-if="activeTab === 'all'" @click="switchTab('works')">查看全部作品</el-button>
      </div>
      <div class="market-grid" v-loading="loading">
        <article
          v-for="item in works"
          :key="'w'+item.id"
          class="work-card"
          @click="$router.push('/work/'+item.id)"
        >
          <!-- 封面 -->
          <div class="work-cover" :class="coverVariant(item)">
            <img v-if="item.coverUrl" :src="item.coverUrl" class="cover-img" />
            <div v-else class="window">
              <i style="width:62%"></i><i style="width:86%"></i><i style="width:48%"></i><i style="width:74%"></i>
            </div>
            <span v-if="item.videoUrl" class="video-chip">▶ 视频</span>
          </div>
          <!-- 信息 -->
          <div class="work-body">
            <div class="tag-row">
              <span class="tag orange" v-if="item.categoryName">{{ item.categoryName }}</span>
              <span class="tag green" v-if="item.statusText">{{ item.statusText }}</span>
              <span class="tag blue" v-if="item.language">{{ item.language }}</span>
            </div>
            <h4>{{ item.title }}</h4>
            <p>{{ item.summary || item.detail || '暂无简介' }}</p>
            <div class="card-foot">
              <span>{{ item.ownerName || '#'+item.ownerId }} · {{ item.favoriteCount || 0 }} 收藏 · {{ item.commentCount || 0 }} 评论</span>
              <strong class="price">查看详情</strong>
            </div>
          </div>
        </article>
      </div>
      <el-empty v-if="!loading && !works.length" description="暂无作品" />
      <el-pagination
        v-if="activeTab === 'works' && worksTotal > pageSize"
        class="market-pager"
        background layout="prev, pager, next"
        :total="worksTotal" :page-size="pageSize"
        v-model:current-page="worksPage" @current-change="loadWorks"
      />
    </section>

    <!-- ═══ 需求大厅 ═══ -->
    <section v-if="activeTab === 'all' || activeTab === 'reqs'" id="reqs-section" class="section">
      <div class="section-head">
        <div>
          <h3>需求大厅</h3>
          <p>需求卡片强调预算范围、交付目标和沟通入口。</p>
        </div>
        <el-button v-if="activeTab === 'all'" @click="switchTab('reqs')">查看全部需求</el-button>
      </div>
      <div class="req-list" v-loading="loading">
        <article
          v-for="item in reqs"
          :key="'r'+item.id"
          class="req-strip"
          @click="$router.push('/requirement/'+item.id)"
        >
          <div>
            <div class="tag-row">
              <span class="tag blue" v-if="item.categoryName">{{ item.categoryName }}</span>
              <span class="tag yellow">预算待沟通</span>
            </div>
            <h4>{{ item.title }}</h4>
            <p>{{ item.summary || item.detail || '暂无描述' }}</p>
          </div>
          <strong class="price">预算 ¥{{ item.budgetMin || '?' }} - ¥{{ item.budgetMax || '?' }}</strong>
        </article>
      </div>
      <el-empty v-if="!loading && !reqs.length" description="暂无需求" />
      <el-pagination
        v-if="activeTab === 'reqs' && reqsTotal > pageSize"
        class="market-pager"
        background layout="prev, pager, next"
        :total="reqsTotal" :page-size="pageSize"
        v-model:current-page="reqsPage" @current-change="loadReqs"
      />
    </section>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PageTour from '../components/PageTour.vue'
import request from '../api/request'

const marketTour = [
  { selector: '.hero-search', text: '在这里搜索作品和需求！输入关键词找你想看的 AI 产品或创意需求。', expression: 'smile' },
  { selector: '.content-tabs', text: '用这些小药丸快速筛选：全部看、只看作品、只看需求。右边还有分类下拉帮你精确筛选～', expression: 'wink' },
  { selector: '.market-grid, .req-list', text: '点击任意卡片进入详情页。作品有封面和演示链接，需求有预算范围。看到喜欢的记得收藏！', expression: 'sparkle' },
]
import { tradeEnabled } from '../stores/tradeMode'

const API = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
const router = useRouter()
const activeTab = ref('all')
const works = ref([])
const reqs = ref([])
const worksTotal = ref(0)
const reqsTotal = ref(0)
const categories = ref([])
const categoryId = ref(null)
const keyword = ref('')
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
</script>

<style scoped>
.market-page {
  /* full width — prototype has no max-width */
}

/* ===== Hero ===== */
.market-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(280px, .95fr);
  gap: 18px;
  padding: 24px;
  border: 1px solid rgba(254, 215, 170, .88);
  border-radius: 28px;
  background:
    linear-gradient(110deg, rgba(255, 247, 237, .96), rgba(255, 255, 255, .74)),
    repeating-linear-gradient(0deg, rgba(243, 112, 33, .045) 0, rgba(243, 112, 33, .045) 1px, transparent 1px, transparent 14px),
    repeating-linear-gradient(90deg, rgba(243, 112, 33, .035) 0, rgba(243, 112, 33, .035) 1px, transparent 1px, transparent 14px);
  box-shadow: var(--shadow-card-lg);
  margin-bottom: 28px;
  overflow: hidden;
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

.market-hero h2 {
  max-width: 620px;
  margin: 14px 0 10px;
  font-family: var(--font-mono);
  font-size: clamp(26px, 4vw, 38px);
  line-height: 1.08;
  letter-spacing: -.06em;
}

.market-hero p {
  max-width: 680px;
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

/* 搜索条 */
.hero-search {
  margin-top: 18px;
  max-width: 740px;
  display: flex;
  gap: 10px;
  padding: 8px;
  border: 1px solid var(--border-orange);
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 14px 30px rgba(243, 112, 33, .10);
}
.hero-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  padding: 0 12px;
  font-size: 14px;
  font-family: var(--font-body);
}

/* Trust chips */
.trust-rail {
  margin-top: 18px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.trust-chip {
  min-height: 32px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border: 1px solid rgba(254, 215, 170, .9);
  border-radius: 999px;
  background: rgba(255, 255, 255, .72);
  color: #9a5a24;
  font-size: 12px;
  font-weight: 800;
  box-shadow: 0 8px 16px rgba(154, 83, 28, .05);
}

/* Hero side — signal cards */
.hero-side {
  display: grid;
  gap: 12px;
}
.signal-card {
  min-height: 100px;
  padding: 16px;
  border: 1px solid var(--border-weak);
  border-left: 3px solid var(--brand-soft);
  border-radius: 18px;
  background: var(--surface-warm);
  box-shadow: var(--shadow-card);
  transition: transform 0.15s, box-shadow 0.15s, border-color 0.15s;
  cursor: pointer;
}
.signal-card:hover {
  transform: translateY(-3px);
  border-color: var(--border-orange);
  box-shadow: var(--shadow-lift);
}
.signal-card strong {
  display: block;
  margin-bottom: 6px;
  font-family: var(--font-mono);
  letter-spacing: -.03em;
  font-size: 14px;
}
.signal-card span {
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

/* ===== Content Pills ===== */
.content-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 0 0 18px;
  align-items: center;
}
.pill {
  min-height: 36px;
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 0 14px;
  color: var(--text-secondary);
  background: #fff;
  font-weight: 800;
  font-size: 13px;
  cursor: pointer;
  font-family: var(--font-mono);
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}
.pill:hover {
  border-color: var(--border-orange);
  color: var(--brand-strong);
}
.pill.active {
  color: var(--brand-strong);
  border-color: var(--border-orange);
  background: var(--brand-soft);
}

/* ===== Section ===== */
.section {
  margin-top: 28px;
  padding: 24px;
  border: 1px solid rgba(254, 215, 170, .72);
  border-radius: 28px;
  background: rgba(255, 255, 255, .82);
  box-shadow: var(--shadow-card-lg);
  position: relative;
}
.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}
.section-head h3 {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 22px;
  letter-spacing: -.04em;
}
.section-head p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
}

/* ===== Work Cards ===== */
.market-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}
.work-card {
  border: 2px solid #f2c49b;
  border-left: 4px solid var(--brand);
  border-radius: 14px;
  background: var(--surface-warm);
  box-shadow: var(--shadow-pixel-sm);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s, border-color 0.15s;
  overflow: hidden;
}
.work-card:hover {
  transform: translate(-2px, -2px);
  border-color: var(--brand-strong);
  box-shadow: var(--shadow-pixel), 8px 8px 0 rgba(154, 83, 28, .05);
}

/* 封面 */
.work-cover {
  position: relative;
  height: 140px;
  padding: 12px;
  border-bottom: 2px solid #f2c49b;
  background:
    radial-gradient(circle at 20% 20%, rgba(255,255,255,.62), transparent 24%),
    linear-gradient(135deg, #fffaf4, #fdba74);
  overflow: hidden;
}
.video-chip {
  position: absolute;
  right: 10px;
  bottom: 10px;
  display: inline-flex;
  min-height: 25px;
  align-items: center;
  padding: 0 9px;
  border: 1px solid rgba(255,255,255,.48);
  border-radius: 9px;
  color: #fff;
  background: rgba(33, 23, 17, .82);
  box-shadow: 2px 2px 0 rgba(92,38,15,.18);
  backdrop-filter: blur(8px);
  font: 800 10px/1 var(--font-body);
}
.work-cover.dark {
  background:
    radial-gradient(circle at 24% 22%, rgba(255,255,255,.42), transparent 26%),
    linear-gradient(135deg, #fff7ed, #fb923c);
}
.work-cover.blue {
  background:
    radial-gradient(circle at 24% 22%, rgba(255,255,255,.46), transparent 26%),
    linear-gradient(135deg, #fffaf4, #fbbf24);
}
.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 2px;
}
.window {
  height: 100%;
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 2px solid rgba(180, 83, 9, .36);
  border-radius: 8px;
  background:
    linear-gradient(#fff7ed 0 18px, transparent 18px),
    rgba(255,255,255,.78);
  box-shadow: 3px 3px 0 rgba(180, 83, 9, .08);
}
.window i {
  display: block;
  height: 8px;
  border-radius: 1px;
  background: rgba(180, 83, 9, .12);
}

/* 卡片内容 */
.work-body {
  padding: 18px;
}
.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}
.tag {
  min-height: 22px;
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--text-secondary);
  background: #f9fafb;
  font-size: 11px;
  font-weight: 800;
  font-family: var(--font-mono);
}
.tag.orange { color: var(--brand-strong); background: var(--brand-soft); border-color: var(--border-orange); }
.tag.green  { color: #9a5a24; background: #fff7ed; border-color: #fed7aa; }
.tag.blue   { color: #9a5a24; background: #fffaf4; border-color: #fcd9bd; }
.tag.yellow { color: #92400e; background: #fffbeb; border-color: #fde68a; }

.work-body h4 {
  margin: 0 0 6px;
  font-size: 15px;
  letter-spacing: -.01em;
}
.work-body p {
  min-height: 36px;
  margin: 0 0 12px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  color: var(--text-muted);
  font-size: 11px;
  padding-top: 10px;
  border-top: 1px dashed rgba(254, 215, 170, .9);
}
.price {
  color: var(--brand-strong);
  font-family: var(--font-mono);
  font-weight: 900;
  font-size: 13px;
}

/* ===== Requirements Strips ===== */
.req-list {
  display: grid;
  gap: 12px;
}
.req-strip {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 16px;
  align-items: center;
  padding: 16px;
  border: 1px solid var(--border-weak);
  border-left: 3px solid var(--brand-soft);
  border-radius: 22px;
  background: var(--surface-warm);
  box-shadow: var(--shadow-card);
  cursor: pointer;
  transition: transform 0.15s, border-color 0.15s, box-shadow 0.15s;
}
.req-strip:hover {
  transform: translateY(-2px);
  border-color: var(--border-orange);
  box-shadow: var(--shadow-card-lg);
}
.req-strip h4 {
  margin: 0 0 6px;
  font-size: 15px;
}
.req-strip p {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.55;
  font-size: 13px;
}

/* 分页 */
.market-pager {
  margin-top: 20px;
  justify-content: center;
}

/* 空状态 */
.section .el-empty {
  padding: 24px 0;
}

@media (max-width: 1120px) {
  .market-hero { grid-template-columns: 1fr; }
}
@media (max-width: 780px) {
  .market-grid { grid-template-columns: 1fr; }
  .market-hero { padding: 18px; }
  .hero-search { flex-direction: column; }
  .req-strip { grid-template-columns: 1fr; }
}
</style>
