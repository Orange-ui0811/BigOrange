<template>
  <div class="ai-hub">
    <PageTour storageKey="aihub" :steps="tourSteps" />
    <!-- ═══ Hero ═══ -->
    <div class="hub-hero">
      <div>
        <span class="eyebrow">AI 工具 · 从榜单看方向</span>
        <h2>先看热度，再决定下一步做什么。</h2>
        <p>从方向洞察、方案规划到上线验收，在同一条项目路径中持续推进。</p>
        <div class="leaderboard-alert">滚动预览：本周本网站热门项目 · GitHub AI 热门仓库 · 创作者热度变化</div>
      </div>
      <div class="hub-card-actions">
        <el-button type="primary" @click="$router.push('/leaderboard')">查看完整榜单</el-button>
        <el-button class="btn-ghost" @click="$router.push('/trend')">进入趋势分析</el-button>
      </div>
    </div>

    <!-- ═══ 本周 Ticker ═══ -->
    <div v-if="tickerItems.length" class="ticker-row" @click="$router.push('/leaderboard')" style="cursor:pointer">
      <div class="ticker-label">
        <PixelOrangeLogo bounce style="width:28px;height:28px;flex-shrink:0" />
        <div>
          <strong>本周热门</strong>
          <span>GitHub 涨星项目</span>
        </div>
      </div>
      <div class="ticker-window">
        <div class="ticker-track">
          <div v-for="(item, i) in tickerItems" :key="i" class="ticker-item" @click.stop="openGithub(item.htmlUrl)">
            <strong>{{ item.fullName || item.name }}</strong>
            <span>★ {{ item.stars }} · {{ item.language || 'N/A' }}</span>
          </div>
          <div v-for="(item, i) in tickerItems" :key="'dup'+i" class="ticker-item" @click.stop="openGithub(item.htmlUrl)">
            <strong>{{ item.fullName || item.name }}</strong>
            <span>★ {{ item.stars }} · {{ item.language || 'N/A' }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ═══ 全时段 Ticker ═══ -->
    <div v-if="tickerAllItems.length" class="ticker-row" @click="$router.push('/leaderboard')" style="cursor:pointer;margin-top:12px;">
      <div class="ticker-label all-time">
        <PixelOrangeLogo bounce style="width:28px;height:28px;flex-shrink:0" />
        <div>
          <strong>全时段热门</strong>
          <span>GitHub 历史星标</span>
        </div>
      </div>
      <div class="ticker-window">
        <div class="ticker-track" style="animation-duration:35s;">
          <div v-for="(item, i) in tickerAllItems" :key="'a'+i" class="ticker-item" @click.stop="openGithub(item.htmlUrl)">
            <strong>{{ item.fullName || item.name }}</strong>
            <span>★ {{ item.stars }} · {{ item.language || 'N/A' }}</span>
          </div>
          <div v-for="(item, i) in tickerAllItems" :key="'adup'+i" class="ticker-item" @click.stop="openGithub(item.htmlUrl)">
            <strong>{{ item.fullName || item.name }}</strong>
            <span>★ {{ item.stars }} · {{ item.language || 'N/A' }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ═══ 工具卡片 ═══ -->
    <div class="hub-grid">
      <article class="hub-card tool-trend" @click="$router.push('/trend')">
        <div>
          <div class="tag-row"><span class="hub-tag">趋势分析</span></div>
          <h3>趋势分析</h3>
          <p>精准推荐、自由提问、Topic 全景图、智能分析记录。</p>
        </div>
        <el-button class="card-btn" size="small">进入趋势分析</el-button>
      </article>
      <article class="hub-card tool-plan" @click="$router.push('/plan')">
        <div>
          <div class="tag-row"><span class="hub-tag yellow">方案设计</span></div>
          <h3>方案设计</h3>
          <p>六阶段项目规划：想法、需求、成功标准、风险、任务、岗位匹配。</p>
        </div>
        <el-button class="card-btn" size="small">进入方案设计</el-button>
      </article>
      <article class="hub-card tool-jobs" @click="$router.push('/submit-job')">
        <div>
          <div class="tag-row"><span class="hub-tag green">工作能力参考</span></div>
          <h3>工作能力参考</h3>
          <p>按方向浏览岗位、查看 JD 详情，并提供岗位投稿入口。</p>
        </div>
        <el-button class="card-btn" size="small">进入工作能力参考</el-button>
      </article>
      <article class="hub-card tool-launch" @click="$router.push('/launch-check')">
        <div>
          <div class="tag-row"><span class="hub-tag blue">上线体检</span></div>
          <h3>上线体检</h3>
          <p>八阶段评估部署形态、资源选型、安全门禁、生产验收和持续运维。</p>
        </div>
        <el-button class="card-btn" size="small">进入上线体检</el-button>
      </article>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PixelOrangeLogo from '../components/PixelOrangeLogo.vue'
import PageTour from '../components/PageTour.vue'
import request from '../api/request'

const tourSteps = [
  { selector: '.hub-hero, .ticker-row:first-child', text: '这是 AI 工具入口页！上面滚动条展示 GitHub 最火的项目，下面三个卡片是趋势分析、方案设计和工作能力参考。', expression: 'wink' },
  { selector: '.ticker-row', text: '这两行滚动条分别是本周最火和全时段最火的项目，点一下进入榜单看完整排名。', expression: 'smile' },
  { selector: '.hub-grid', text: '这里集中提供趋势分析、方案设计、工作能力参考和上线体检。', expression: 'sparkle' },
]

const tickerItems = ref([])
const tickerAllItems = ref([])

const openGithub = (url) => {
  if (url) window.open(url, '_blank')
}

onMounted(async () => {
  try {
    const r = await request.get('/app/leaderboard/github', { params: { period: 'week', size: 8 } })
    if (r.code === 1 && r.data?.records) {
      tickerItems.value = r.data.records.map(item => ({
        name: item.name || '',
        fullName: item.fullName || item.name || '',
        stars: item.stars || 0,
        language: item.language || '',
        htmlUrl: item.htmlUrl || ''
      }))
    }
  } catch {}
  try {
    const r2 = await request.get('/app/leaderboard/github', { params: { period: 'all', size: 8 } })
    if (r2.code === 1 && r2.data?.records) {
      tickerAllItems.value = r2.data.records.map(item => ({
        name: item.name || '',
        fullName: item.fullName || item.name || '',
        stars: item.stars || 0,
        language: item.language || '',
        htmlUrl: item.htmlUrl || ''
      }))
    }
  } catch {}
})
</script>

<style scoped>
.ai-hub {
  /* full width — no max-width, matches prototype */
}

/* ═══ Hero ═══ */
.hub-hero {
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 28px;
  border: 1px solid rgba(254, 215, 170, .76);
  border-radius: 28px;
  background:
    linear-gradient(120deg, rgba(255,247,237,.94), rgba(255,255,255,.78)),
    radial-gradient(circle at 92% 10%, rgba(243,112,33,.16), transparent 30%);
  box-shadow: var(--shadow-card-lg);
  margin-bottom: 28px;
  overflow: hidden;
  flex-wrap: wrap;
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
.hub-hero h2 {
  max-width: 760px;
  margin: 14px 0 10px;
  font-family: var(--font-mono);
  font-size: clamp(26px, 4vw, 40px);
  line-height: 1.08;
  letter-spacing: -.06em;
}
.hub-hero p {
  max-width: 760px;
  color: var(--text-secondary);
  line-height: 1.75;
  margin: 0;
  font-size: 14px;
}
.leaderboard-alert {
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px solid #fde68a;
  border-radius: 16px;
  color: #92400e;
  background: #fffbeb;
  font-size: 13px;
  line-height: 1.6;
  font-weight: 700;
}
.hub-card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex-shrink: 0;
}
.btn-ghost {
  border: 1px solid var(--border-orange) !important;
  color: var(--brand-strong) !important;
  background: #fffaf5 !important;
}

/* ═══ Grid ═══ */
.hub-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  margin-top: 28px;
}
.hub-card {
  min-height: 190px;
  padding: 20px;
  border: 1px solid var(--border-weak);
  border-left: 3px solid var(--brand);
  border-radius: 24px;
  background: #fff;
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  cursor: pointer;
  transition: transform .18s, box-shadow .18s, border-color .18s;
}
.hub-card:hover {
  transform: translateY(-3px);
  border-color: var(--border-orange);
  box-shadow: var(--shadow-lift);
}
.hub-card.featured-card {
  border-left: 4px solid var(--brand);
  background: var(--card-featured);
}
.hub-card h3 {
  margin: 0 0 8px;
  font-family: var(--font-mono);
  letter-spacing: -.04em;
  font-size: 20px;
}
.hub-card p {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.7;
  font-size: 13px;
}
.card-btn {
  margin-top: 18px;
  align-self: flex-start;
}

/* Card variants — exact prototype backgrounds */
.tool-trend {
  border-color: #fed7aa;
  border-left: 4px solid var(--brand);
  background:
    linear-gradient(135deg, rgba(255,237,213,.9), rgba(255,255,255,.96)),
    repeating-linear-gradient(0deg, rgba(243,112,33,.035) 0, rgba(243,112,33,.035) 1px, transparent 1px, transparent 14px);
}
.tool-plan {
  border-color: #fde68a;
  border-left: 3px solid #f59e0b;
  background:
    linear-gradient(135deg, rgba(255,251,235,.94), rgba(255,255,255,.96)),
    repeating-linear-gradient(90deg, rgba(245,158,11,.035) 0, rgba(245,158,11,.035) 1px, transparent 1px, transparent 14px);
}
.tool-jobs {
  border-color: #f2c49b;
  border-left: 3px solid #9a5a24;
  background:
    linear-gradient(135deg, rgba(255,250,244,.96), rgba(255,247,237,.82)),
    radial-gradient(circle at 92% 12%, rgba(154,90,36,.10), transparent 32%);
}
.tool-launch {
  border-color: #bfdbfe;
  border-left: 3px solid #2563eb;
  background: #f8fbff;
}

.tag-row { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
.hub-tag {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--brand-strong);
  background: var(--brand-soft);
  font-size: 11px;
  font-weight: 800;
  font-family: var(--font-mono);
}
.hub-tag.yellow { color: #92400e; background: #fffbeb; border-color: #fde68a; }
.hub-tag.green  { color: #9a5a24; background: #fff7ed; border-color: #fed7aa; }
.hub-tag.blue { color: #1d4ed8; background: #eff6ff; border-color: #bfdbfe; }

/* ═══ Ticker ═══ */
.ticker-row {
  display: flex;
  align-items: stretch;
  gap: 0;
}
.ticker-label {
  flex-shrink: 0;
  width: 150px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid var(--border-orange);
  border-right: none;
  border-radius: 18px 0 0 18px;
  background:
    linear-gradient(135deg, rgba(255,237,213,.7), rgba(255,255,255,.88)),
    repeating-linear-gradient(0deg, rgba(243,112,33,.03) 0, rgba(243,112,33,.03) 1px, transparent 1px, transparent 14px);
  box-shadow: var(--shadow-card);
  z-index: 1;
}
.ticker-label strong {
  display: block;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--brand-strong);
  letter-spacing: -.02em;
}
.ticker-label span {
  font-size: 10px;
  color: var(--text-muted);
  font-family: var(--font-mono);
}
.ticker-label.all-time {
  background:
    linear-gradient(135deg, rgba(255,250,244,.8), rgba(255,255,255,.92)),
    repeating-linear-gradient(90deg, rgba(154,90,36,.03) 0, rgba(154,90,36,.03) 1px, transparent 1px, transparent 14px);
}
.ticker-label.all-time strong {
  color: #9a5a24;
}
.ticker-window {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--border-orange);
  border-left: none;
  border-radius: 0 18px 18px 0;
  background: rgba(255,255,255,.76);
  box-shadow: var(--shadow-card);
  padding: 4px 0;
}
.ticker-track {
  width: max-content;
  display: flex;
  gap: 12px;
  padding: 8px 12px;
  animation: ticker-scroll 30s linear infinite;
}
.ticker-track:hover { animation-play-state: paused; }
.ticker-item {
  min-width: 260px;
  padding: 10px 14px;
  border: 2px solid #f2c49b;
  border-radius: 14px;
  background: #fffdf9;
  box-shadow: 3px 3px 0 #ffedd5;
  cursor: pointer;
  transition: transform .15s, box-shadow .15s;
  flex-shrink: 0;
}
.ticker-item:hover {
  transform: translateY(-2px);
  box-shadow: 5px 5px 0 #ffedd5;
}
.ticker-item strong {
  display: block;
  margin-bottom: 4px;
  font-family: var(--font-mono);
  font-size: 13px;
  letter-spacing: -.03em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ticker-item span {
  color: var(--text-secondary);
  font-size: 11px;
}
@keyframes ticker-scroll {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}

@media (max-width: 780px) {
  .hub-grid { grid-template-columns: 1fr; }
  .hub-hero { padding: 22px; }
}
</style>
