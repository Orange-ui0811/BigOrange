<template>
  <div class="ai-hub">
    <PageTour ref="tourRef" storageKey="aihub-v2" :steps="tourSteps" />

    <section class="hub-hero">
      <div class="hero-copy">
        <span class="eyebrow">AI 工具 · 从方向到行动</span>
        <h2>找到方向，然后把下一步做清楚。</h2>
        <p>观察技术趋势、规划自己的项目，或从一个真实 Issue 开始参与开源。</p>
        <div class="leaderboard-alert">本周 GitHub 热门 · 新手可落地项目 · 职业成长项目 · 近期可参与的开源 Issue</div>
      </div>
      <div class="hero-actions" aria-label="快捷入口">
        <el-button type="primary" @click="router.push('/open-source?tab=discover')"><Search />开源项目榜单</el-button>
        <el-button class="btn-ghost" @click="router.push('/leaderboard')"><TrendCharts />完整榜单</el-button>
        <el-button class="btn-ghost" @click="router.push('/ai/career')"><Trophy />AI 比赛列表</el-button>
      </div>
    </section>

    <section class="ranking-streams" aria-label="项目与开源贡献榜单">
      <div v-if="tickerItems.length" class="ticker-row" @click="router.push('/leaderboard')">
        <div class="ticker-label">
          <PixelOrangeLogo bounce class="ticker-logo" />
          <div><strong>本周热门</strong><span>GitHub 涨星项目</span></div>
        </div>
        <div class="ticker-window">
          <div class="ticker-track">
            <button v-for="item in tickerLoop" :key="item.loopKey" type="button" class="ticker-item" @click.stop="openGithub(item.htmlUrl)">
              <strong>{{ item.fullName || item.name }}</strong><span>★ {{ item.stars }} · {{ item.language || 'N/A' }}</span>
            </button>
          </div>
        </div>
      </div>

      <div class="ticker-row beginner-ticker" @click="openLeaderboardSource('beginner')">
        <div class="ticker-label beginner-label">
          <PixelOrangeLogo bounce class="ticker-logo" />
          <div><strong>新手推荐</strong><span>已核验可落地</span></div>
        </div>
        <div class="ticker-window">
          <div v-if="beginnerTickerItems.length" class="ticker-track beginner-track">
            <button v-for="item in beginnerTickerLoop" :key="item.loopKey" type="button" class="ticker-item beginner-item" @click.stop="openLeaderboardSource('beginner')">
              <strong>{{ item.title }}</strong><span>{{ difficultyText(item.difficulty) }} · {{ item.score }}/100 新手分 · {{ item.language || '多技术栈' }}</span>
            </button>
          </div>
          <button v-else type="button" class="ticker-empty beginner-empty" @click.stop="openLeaderboardSource('beginner')">
            新手精选生成中，进入榜单查看项目库
          </button>
        </div>
      </div>

      <div class="ticker-row career-ticker" @click="openLeaderboardSource('career')">
        <div class="ticker-label career-label">
          <PixelOrangeLogo bounce class="ticker-logo" />
          <div><strong>职业成长</strong><span>真实岗位能力</span></div>
        </div>
        <div class="ticker-window">
          <div v-if="careerTickerItems.length" class="ticker-track career-track">
            <button v-for="item in careerTickerLoop" :key="item.loopKey" type="button" class="ticker-item career-item" @click.stop="openLeaderboardSource('career')">
              <strong>{{ item.title }}</strong><span>{{ item.role || '职业方向待评估' }} · {{ item.score }}/100 职业分</span>
            </button>
          </div>
          <button v-else type="button" class="ticker-empty career-empty" @click.stop="openLeaderboardSource('career')">
            职业精选生成中，进入榜单查看项目库
          </button>
        </div>
      </div>

      <div v-if="issueTickerItems.length" class="ticker-row issue-ticker" @click="router.push('/open-source?tab=discover')">
        <div class="ticker-label issue-label">
          <PixelOrangeLogo class="ticker-logo" />
          <div><strong>开源贡献</strong><span>可参与 Issue</span></div>
        </div>
        <div class="ticker-window">
          <div class="ticker-track issue-track">
            <button v-for="item in issueTickerLoop" :key="item.loopKey" type="button" class="ticker-item issue-item" @click.stop="router.push(`/open-source?tab=discover&issue=${item.id}`)">
              <strong>{{ item.title }}</strong><span>{{ statusText(item.participationStatus) }} · {{ difficultyText(item.difficulty) }}</span>
            </button>
          </div>
        </div>
      </div>

      <div class="ticker-row competition-ticker" @click="router.push('/ai/career')">
        <div class="ticker-label competition-label">
          <PixelOrangeLogo class="ticker-logo" />
          <div><strong>AI 大赛</strong><span>正在进行与即将开始</span></div>
        </div>
        <div class="ticker-window">
          <div v-if="competitionTickerItems.length" class="ticker-track competition-track">
            <button v-for="item in competitionTickerLoop" :key="item.loopKey" type="button" class="ticker-item competition-item" @click.stop="router.push('/ai/career')">
              <strong>{{ item.name }}</strong><span>{{ competitionPhaseText(item.phase) }} · {{ item.organizer || '举办方待确认' }}</span>
            </button>
          </div>
          <button v-else type="button" class="ticker-empty" @click.stop="router.push('/ai/career')">
            暂无已发布比赛，进入大赛列表查看或投稿
          </button>
        </div>
      </div>
    </section>

    <section class="path-section">
      <header class="section-heading">
        <div><span class="section-kicker">CHOOSE YOUR PATH</span><h2>你现在想推进哪件事？</h2></div>
        <button type="button" class="tour-trigger" @click="tourRef?.restart()"><Guide />使用引导</button>
      </header>

      <div class="path-grid">
        <button v-for="path in paths" :key="path.id" type="button" :class="['path-card', `path-${path.id}`]" @click="router.push(path.route)">
          <span class="path-topline"><span class="path-index">{{ path.index }}</span><span class="path-count">{{ path.meta }}</span></span>
          <span class="path-icon"><component :is="path.icon" /></span>
          <strong>{{ path.title }}</strong>
          <span class="path-description">{{ path.description }}</span>
          <span class="path-action">进入专属页面 <ArrowRight /></span>
        </button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PixelOrangeLogo from '../components/PixelOrangeLogo.vue'
import PageTour from '../components/PageTour.vue'
import request from '../api/request'

const router = useRouter()
const tourRef = ref(null)
const tourSteps = [
  { selector: '.hub-hero', text: '顶部提供三个等尺寸快捷入口，可以直接查看开源项目、完整榜单或 AI 比赛。', expression: 'wink' },
  { selector: '.ranking-streams', text: '五行滚动信息帮助你观察项目热度、新手项目、职业成长机会、开源 Issue 和近期 AI 大赛。', expression: 'smile' },
  { selector: '.path-grid', text: '选择一条行动路径后会进入独立页面，不会再在首页展开复杂工具。', expression: 'sparkle' },
]

const paths = [
  { id: 'build', index: '01', title: '做自己的项目', icon: 'Aim', meta: '4 个工具', route: '/project-tools', description: '从榜单排行寻找灵感，再完成趋势判断、方案设计和上线体检。' },
  { id: 'contribute', index: '02', title: '参与开源项目', icon: 'Collection', meta: '4 个阶段', route: '/open-source?tab=discover', description: '发现真实可参与的 Issue，让 AI 帮你选择、理解岗位价值并生成贡献指南。' },
  { id: 'career', index: '03', title: 'AI大赛与能力参考', icon: 'Trophy', meta: '大赛与岗位', route: '/ai/career', description: '通过参加AI大赛积累自己的项目，用真实岗位要求校准学习方向' },
]

const tickerItems = ref([])
const beginnerTickerItems = ref([])
const careerTickerItems = ref([])
const issueTickerItems = ref([])
const competitionTickerItems = ref([])
const makeLoop = items => [...items, ...items].map((item, index) => ({ ...item, loopKey: `${item.id || item.fullName || item.name}-${index}` }))
const tickerLoop = computed(() => makeLoop(tickerItems.value))
const beginnerTickerLoop = computed(() => makeLoop(beginnerTickerItems.value))
const careerTickerLoop = computed(() => makeLoop(careerTickerItems.value))
const issueTickerLoop = computed(() => makeLoop(issueTickerItems.value))
const competitionTickerLoop = computed(() => makeLoop(competitionTickerItems.value))
const statusText = value => ({ TRY: '可尝试', POSSIBLY_CLAIMED: '可能被认领', NOT_RECOMMENDED: '不建议' }[value] || '待核验')
const difficultyText = value => ({ BEGINNER: '入门', EASY: '简单', MEDIUM: '中等' }[value] || '待评估')
const competitionPhaseText = value => ({ UPCOMING: '即将开始', ONGOING: '进行中', UNSCHEDULED: '时间待公布' }[value] || '可查看')
const openGithub = url => { if (url) window.open(url, '_blank', 'noopener') }
const openLeaderboardSource = source => router.push({ path: '/leaderboard', query: { source } })
const normalizeRepository = item => ({ name: item.name || '', fullName: item.fullName || item.name || '', stars: item.stars || 0, language: item.language || '', htmlUrl: item.htmlUrl || '' })
const normalizeBeginnerProject = item => ({ id: item.id, title: item.descriptionZh || item.repositoryName || item.fullName || '待命名项目', score: item.beginnerScore || 0, difficulty: item.difficulty, language: item.primaryLanguage || '' })
const normalizeCareerProject = item => ({ id: item.id, title: item.summaryZh || item.repositoryName || item.fullName || '待命名项目', score: item.overallScore || 0, role: item.bestRoleName || '' })

onMounted(async () => {
  const [weekly, beginner, career, issues, competitions] = await Promise.allSettled([
    request.get('/app/leaderboard/github', { params: { period: 'week', size: 8 } }),
    request.get('/app/beginner-projects/weekly'),
    request.get('/app/career-projects/weekly'),
    request.get('/app/open-source/ticker'),
    request.get('/app/competition/list', { params: { scope: 'ACTIVE', page: 1, size: 8 } }),
  ])
  if (weekly.status === 'fulfilled' && weekly.value.code === 1) tickerItems.value = (weekly.value.data?.records || []).map(normalizeRepository)
  if (beginner.status === 'fulfilled' && beginner.value.code === 1) beginnerTickerItems.value = (beginner.value.data || []).map(normalizeBeginnerProject)
  if (career.status === 'fulfilled' && career.value.code === 1) careerTickerItems.value = (career.value.data || []).map(normalizeCareerProject)
  if (issues.status === 'fulfilled' && issues.value.code === 1) issueTickerItems.value = issues.value.data || []
  if (competitions.status === 'fulfilled' && competitions.value.code === 1) competitionTickerItems.value = competitions.value.data?.records || []
})
</script>

<style scoped>
.ai-hub { width: 100%; }
.hub-hero { display: grid; grid-template-columns: minmax(0, 1fr) 224px; gap: 32px; padding: 28px; border: 1px solid rgba(254,215,170,.8); border-radius: 20px; background: linear-gradient(120deg, rgba(255,247,237,.96), rgba(255,255,255,.82)), radial-gradient(circle at 92% 10%, rgba(243,112,33,.14), transparent 30%); box-shadow: var(--shadow-card-lg); }
.hero-copy { min-width: 0; }
.eyebrow { display: inline-flex; min-height: 28px; align-items: center; padding: 0 12px; border: 1px solid var(--border-orange); border-radius: 999px; color: var(--brand-strong); background: #fffaf5; font: 900 12px/1 var(--font-mono); }
.hub-hero h2 { max-width: 760px; margin: 14px 0 10px; font: 900 clamp(27px,4vw,40px)/1.12 var(--font-mono); letter-spacing: 0; }
.hub-hero p { max-width: 720px; margin: 0; color: var(--text-secondary); font-size: 14px; line-height: 1.75; }
.leaderboard-alert { margin-top: 16px; padding: 12px 14px; border: 1px solid #fde68a; border-radius: 8px; color: #92400e; background: #fffbeb; font-size: 13px; font-weight: 700; line-height: 1.6; }
.hero-actions { display: grid; grid-template-rows: repeat(3, 48px); gap: 10px; align-self: center; }
.hero-actions :deep(.el-button) { width: 100%; height: 48px; justify-content: flex-start; margin: 0; padding: 0 16px; }
.hero-actions :deep(svg) { width: 17px; height: 17px; margin-right: 8px; }
.btn-ghost { border-color: var(--border-orange) !important; color: var(--brand-strong) !important; background: rgba(255,250,245,.92) !important; }
.ranking-streams { display: grid; gap: 12px; margin-top: 22px; }
.ticker-row { display: flex; min-height: 72px; align-items: stretch; cursor: pointer; }
.ticker-label { position: relative; z-index: 1; display: flex; width: 156px; flex-shrink: 0; align-items: center; gap: 10px; padding: 10px 14px; border: 1px solid var(--border-orange); border-right: 0; border-radius: 8px 0 0 8px; background: #fff7ed; box-shadow: var(--shadow-card); }
.ticker-logo { width: 28px; height: 28px; flex-shrink: 0; }
.ticker-label strong { display: block; color: var(--brand-strong); font: 800 12px/1.3 var(--font-mono); }
.ticker-label span { color: var(--text-muted); font: 10px/1.4 var(--font-mono); }
.ticker-label.beginner-label { border-color: #f6c66f; background: #fffbeb; }.ticker-label.beginner-label strong { color: #a16207; }
.ticker-label.career-label { border-color: #99d8cf; background: #f0fdfa; }.ticker-label.career-label strong { color: #0f766e; }
.ticker-label.issue-label { border-color: #bfdbfe; background: #eff6ff; }.ticker-label.issue-label strong { color: #1d4ed8; }
.ticker-label.competition-label { border-color: #bbf7d0; background: #f0fdf4; }.ticker-label.competition-label strong { color: #166534; }
.ticker-window { min-width: 0; flex: 1; overflow: hidden; padding: 4px 0; border: 1px solid var(--border-orange); border-left: 0; border-radius: 0 8px 8px 0; background: rgba(255,255,255,.78); box-shadow: var(--shadow-card); }
.beginner-ticker .ticker-window { border-color: #f6c66f; }
.career-ticker .ticker-window { border-color: #99d8cf; }
.issue-ticker .ticker-window { border-color: #bfdbfe; }
.competition-ticker .ticker-window { border-color: #bbf7d0; }
.ticker-track { display: flex; width: max-content; gap: 12px; padding: 8px 12px; animation: ticker-scroll 30s linear infinite; }.beginner-track { animation-duration: 35s; }.career-track { animation-duration: 39s; }.issue-track { animation-duration: 38s; }
.competition-track { animation-duration: 42s; }
.ticker-track:hover, .ticker-track:focus-within { animation-play-state: paused; }
.ticker-item { display: block; min-width: 260px; max-width: 320px; min-height: 48px; flex-shrink: 0; padding: 8px 12px; border: 1px solid #f2c49b; border-radius: 8px; background: #fffdf9; color: var(--text-primary); text-align: left; cursor: pointer; transition: border-color .18s, transform .18s; }
.ticker-item:hover, .ticker-item:focus-visible { border-color: var(--brand); outline: none; transform: translateY(-2px); }
.ticker-item strong { display: block; overflow: hidden; margin-bottom: 4px; font: 800 13px/1.3 var(--font-mono); text-overflow: ellipsis; white-space: nowrap; }.ticker-item span { color: var(--text-secondary); font-size: 11px; }
.beginner-item { border-color: #f6c66f; background: #fffdf5; }
.career-item { border-color: #99d8cf; background: #f8fffd; }
.issue-item { min-width: 310px; border-color: #bfdbfe; background: #fff; }
.competition-item { min-width: 300px; border-color: #bbf7d0; background: #fbfffc; }
.ticker-empty { min-height: 48px; margin: 8px 12px; padding: 0 14px; border: 1px dashed #86efac; border-radius: 8px; background: #fbfffc; color: #166534; font-size: 12px; font-weight: 700; cursor: pointer; }
.beginner-empty { border-color: #f6c66f; background: #fffdf5; color: #a16207; }
.career-empty { border-color: #99d8cf; background: #f8fffd; color: #0f766e; }
.ticker-empty:hover,.ticker-empty:focus-visible { border-style: solid; outline: 2px solid rgba(22,101,52,.18); }
.path-section { margin-top: 42px; }
.section-heading { display: flex; min-height: 64px; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 18px; padding-bottom: 16px; border-bottom: 1px solid var(--border-weak); }
.section-kicker { color: var(--brand-strong); font: 800 11px/1.2 var(--font-mono); }.section-heading h2 { margin: 7px 0 0; font: 900 25px/1.25 var(--font-mono); letter-spacing: 0; }
.tour-trigger { display: inline-flex; min-height: 44px; align-items: center; gap: 7px; padding: 0 13px; border: 1px solid var(--border-weak); border-radius: 8px; background: #fff; color: var(--text-secondary); font-weight: 700; cursor: pointer; }.tour-trigger svg { width: 16px; }.tour-trigger:hover, .tour-trigger:focus-visible { border-color: var(--brand); color: var(--brand-strong); outline: 2px solid rgba(243,112,33,.2); }
.path-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 14px; }
.path-card { --path-color: var(--brand); --path-soft: var(--brand-soft); display: grid; min-height: 250px; align-content: start; padding: 20px; border: 1px solid var(--border-weak); border-radius: 8px; background: #fff; color: var(--text-primary); text-align: left; cursor: pointer; transition: border-color .18s, box-shadow .18s, transform .18s; }
.path-card:hover, .path-card:focus-visible { border-color: var(--path-color); box-shadow: var(--shadow-lift); outline: none; transform: translateY(-3px); }.path-card:active { transform: translateY(-1px); }
.path-contribute { --path-color: #2563eb; --path-soft: #eff6ff; }.path-career { --path-color: #166534; --path-soft: #f0fdf4; }
.path-topline { display: flex; align-items: center; justify-content: space-between; }.path-index { color: var(--text-muted); font: 800 11px/1 var(--font-mono); }.path-count { color: var(--path-color); font-size: 11px; font-weight: 800; }
.path-icon { display: grid; width: 42px; height: 42px; place-items: center; margin: 22px 0 15px; border-radius: 8px; color: var(--path-color); background: var(--path-soft); }.path-icon :deep(svg) { width: 21px; height: 21px; }
.path-card > strong { font: 900 19px/1.3 var(--font-mono); }.path-description { min-height: 66px; margin-top: 9px; color: var(--text-secondary); font-size: 13px; line-height: 1.65; }
.path-action { display: flex; align-items: center; justify-content: space-between; margin-top: 20px; padding-top: 14px; border-top: 1px solid var(--border-weak); color: var(--path-color); font-size: 12px; font-weight: 800; }.path-action :deep(svg) { width: 16px; }
@keyframes ticker-scroll { from { transform: translateX(0); } to { transform: translateX(-50%); } }
@media (max-width: 980px) { .hub-hero { grid-template-columns: 1fr; }.hero-actions { width: 100%; grid-template-columns: repeat(3,minmax(0,1fr)); grid-template-rows: 48px; }.path-grid { grid-template-columns: 1fr; }.path-card { min-height: 190px; }.path-description { min-height: auto; } }
@media (max-width: 680px) { .hub-hero { padding: 20px; border-radius: 12px; }.hero-actions { grid-template-columns: 1fr; grid-template-rows: repeat(3,48px); }.ticker-row { display: block; }.ticker-label { width: 100%; min-height: 56px; border-right: 1px solid var(--border-orange); border-bottom: 0; border-radius: 8px 8px 0 0; }.ticker-label.beginner-label { border-right-color: #f6c66f; }.ticker-label.career-label { border-right-color: #99d8cf; }.ticker-label.issue-label { border-right-color: #bfdbfe; }.ticker-window { border-top: 0; border-left: 1px solid var(--border-orange); border-radius: 0 0 8px 8px; }.beginner-ticker .ticker-window { border-left-color: #f6c66f; }.career-ticker .ticker-window { border-left-color: #99d8cf; }.issue-ticker .ticker-window { border-left-color: #bfdbfe; }.ticker-item,.issue-item { min-width: 240px; max-width: 280px; }.section-heading { align-items: flex-start; flex-direction: column; }.section-heading h2 { font-size: 22px; } }
@media (max-width: 680px) { .ticker-label.competition-label { border-right-color: #bbf7d0; }.competition-ticker .ticker-window { border-left-color: #bbf7d0; }.competition-item { min-width: 240px; max-width: 280px; } }
@media (prefers-reduced-motion: reduce) { .ticker-track { width: 100%; overflow-x: auto; animation: none; transform: none; }.path-card,.ticker-item { transition: none; } }
</style>
