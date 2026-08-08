<template>
  <div class="home-page">
    <a class="skip-link" href="#home-main">跳到主要内容</a>

    <header class="home-nav">
      <div class="home-shell nav-inner">
        <router-link class="brand" to="/" aria-label="OrangeVC 首页">
          <PixelOrangeLogo class="brand-mark" />
          <span class="brand-copy">
            <strong>OrangeVC</strong>
            <small>vibe coding market</small>
          </span>
        </router-link>

        <nav class="nav-links" aria-label="首页导航">
          <a href="#featured">发现作品</a>
          <a href="#paths">如何开始</a>
          <router-link to="/ai">AI 工具</router-link>
        </nav>

        <div class="nav-actions">
          <router-link v-if="!hasSession" class="text-link" to="/login">登录</router-link>
          <router-link class="primary-link nav-primary" to="/market">
            进入市场 <el-icon aria-hidden="true"><Right /></el-icon>
          </router-link>
        </div>
      </div>
    </header>

    <main id="home-main" tabindex="-1">
      <section class="hero home-shell">
        <div class="hero-copy">
          <p class="hero-kicker">OrangeVC 创意交易市场</p>
          <h1>把灵感做成产品，<br />让好作品找到买家。</h1>
          <p class="hero-summary">连接独立开发者与真实需求方，让作品、合作与交付在一个地方发生。</p>

          <form class="hero-search" @submit.prevent="submitSearch">
            <label for="home-search">搜索作品、需求或技术栈</label>
            <div class="search-control">
              <el-icon class="search-icon" aria-hidden="true"><Search /></el-icon>
              <input
                id="home-search"
                v-model.trim="searchTerm"
                type="search"
                autocomplete="off"
                placeholder="例如：AI 客服、Vue、网站重构"
              />
              <button type="submit">搜索</button>
            </div>
          </form>
        </div>

        <div class="hero-visual">
          <img
            :src="heroVisual"
            width="1122"
            height="1402"
            fetchpriority="high"
            alt="独立创作者制作并验证数字产品，再交给真实需求方"
          />
          <div class="visual-caption">
            <PixelOrangeMascot expression="wink" class="caption-mascot" />
            <p><strong>从创作到成交</strong><span>作品可展示，过程可验证，需求能对接</span></p>
          </div>
        </div>
      </section>

      <section class="trust-rail" aria-label="平台特点">
        <div class="home-shell trust-grid">
          <p><strong>作品可验证</strong><span>演示、截图和说明清楚呈现</span></p>
          <p><strong>状态可追踪</strong><span>审核、沟通和交付节点有迹可循</span></p>
          <p><strong>创作者可发现</strong><span>用真实作品建立长期个人主页</span></p>
        </div>
      </section>

      <section id="featured" class="featured-section home-shell">
        <div class="section-heading">
          <p class="section-kicker">最新上架</p>
          <h2>看看正在被做出来的东西</h2>
          <p>从能演示的产品出发，认识作品背后的创作者。</p>
        </div>

        <div v-if="worksState === 'loading'" class="works-layout" aria-live="polite" aria-label="作品加载中">
          <div class="work-tile work-featured skeleton-card"><span></span><i></i><i></i></div>
          <div class="works-stack">
            <div v-for="index in 3" :key="index" class="work-tile work-compact skeleton-card"><span></span><i></i></div>
          </div>
        </div>

        <div v-else-if="worksState === 'error'" class="state-panel" role="alert">
          <el-icon aria-hidden="true"><Warning /></el-icon>
          <div><strong>作品暂时没有加载出来</strong><span>网络恢复后可以重新获取。</span></div>
          <button type="button" @click="loadWorks">重新加载</button>
        </div>

        <div v-else-if="works.length === 0" class="state-panel empty-panel">
          <PixelOrangeMascot expression="curious" class="empty-mascot" />
          <div><strong>这里正等第一批作品</strong><span>把能运行、能演示的项目带到 OrangeVC。</span></div>
          <router-link class="secondary-link" to="/publish">发布作品</router-link>
        </div>

        <div v-else class="works-layout">
          <article class="work-tile work-featured" @click="openWork(works[0].id)">
            <img :src="workCover(works[0])" :alt="works[0].title + ' 作品封面'" loading="lazy" />
            <div class="work-info">
              <span v-if="works[0].categoryName">{{ works[0].categoryName }}</span>
              <h3>{{ works[0].title }}</h3>
              <p>{{ workSummary(works[0]) }}</p>
              <button type="button" @click.stop="openWork(works[0].id)">
                查看作品 <el-icon aria-hidden="true"><Right /></el-icon>
              </button>
            </div>
          </article>

          <div v-if="works.length > 1" class="works-stack">
            <article
              v-for="(work, index) in works.slice(1, 4)"
              :key="work.id"
              class="work-tile work-compact"
              @click="openWork(work.id)"
            >
              <img :src="workCover(work)" :alt="work.title + ' 作品封面'" loading="lazy" :style="{ objectPosition: compactImagePosition(index) }" />
              <div class="work-info">
                <span v-if="work.categoryName">{{ work.categoryName }}</span>
                <h3>{{ work.title }}</h3>
                <p>{{ workSummary(work) }}</p>
              </div>
              <el-icon class="work-arrow" aria-hidden="true"><Right /></el-icon>
            </article>
          </div>
        </div>

        <router-link class="section-link" to="/market">
          查看全部作品 <el-icon aria-hidden="true"><Right /></el-icon>
        </router-link>
      </section>

      <section id="paths" class="paths-section home-shell">
        <div class="section-heading compact-heading">
          <h2>从你现在的位置开始</h2>
          <p>带着作品来，或者带着一个值得实现的问题来。</p>
        </div>

        <div class="path-grid">
          <article class="path-panel path-maker">
            <div class="path-icon"><el-icon aria-hidden="true"><Monitor /></el-icon></div>
            <div>
              <h3>让作品进入市场</h3>
              <p>补齐封面、演示和说明，让需求方先看懂价值，再开始沟通。</p>
            </div>
            <img :src="makerDesk" width="1536" height="1024" loading="lazy" alt="橙色模块、键盘与纸面原型组成的创作者工作台" />
            <router-link to="/publish">发布作品 <el-icon aria-hidden="true"><Right /></el-icon></router-link>
          </article>

          <article class="path-panel path-client">
            <div class="path-icon"><el-icon aria-hidden="true"><Briefcase /></el-icon></div>
            <div>
              <h3>找到合适的创作者</h3>
              <p>说清目标与预算，从真实作品中判断能力。</p>
            </div>
            <router-link to="/publish">发布需求 <el-icon aria-hidden="true"><Right /></el-icon></router-link>
          </article>

          <article class="path-panel path-ai">
            <div class="path-icon"><el-icon aria-hidden="true"><MagicStick /></el-icon></div>
            <div>
              <h3>还只有一个想法？</h3>
              <p>用趋势分析、方案设计和上线体检，把模糊方向整理成可执行项目。</p>
            </div>
            <router-link to="/ai">打开 AI 工具 <el-icon aria-hidden="true"><Right /></el-icon></router-link>
          </article>
        </div>
      </section>

      <section class="flow-section">
        <div class="home-shell flow-inner">
          <div class="section-heading flow-heading">
            <h2>从展示到交付，不丢掉上下文</h2>
          </div>
          <div class="flow-grid">
            <article>
              <el-icon aria-hidden="true"><UploadFilled /></el-icon>
              <h3>发布可验证内容</h3>
              <p>作品与需求分别表达，关键信息一眼能读懂。</p>
            </article>
            <article>
              <el-icon aria-hidden="true"><ChatDotRound /></el-icon>
              <h3>围绕项目沟通</h3>
              <p>从详情进入对话，减少反复解释和信息丢失。</p>
            </article>
            <article>
              <el-icon aria-hidden="true"><CircleCheckFilled /></el-icon>
              <h3>按约定完成交付</h3>
              <p>报价、订单和验收在交易模式开启时自然衔接。</p>
            </article>
          </div>
        </div>
      </section>

      <section class="closing home-shell">
        <div class="closing-copy">
          <PixelOrangeMascot expression="sparkle" class="closing-mascot" />
          <h2>准备好把项目交给真实世界了吗？</h2>
          <p>让作品被看见，让需求找到能把事情做成的人。</p>
        </div>
        <div class="closing-actions">
          <router-link class="primary-link" to="/market">进入市场 <el-icon aria-hidden="true"><Right /></el-icon></router-link>
          <router-link class="secondary-link" to="/publish">发布作品</router-link>
        </div>
      </section>
    </main>

    <footer class="home-footer">
      <div class="home-shell footer-inner">
        <router-link class="brand footer-brand" to="/">
          <PixelOrangeLogo class="brand-mark" />
          <span class="brand-copy"><strong>OrangeVC</strong><small>让好作品找到真实需求</small></span>
        </router-link>
        <nav aria-label="页脚导航">
          <router-link to="/legal/terms">用户协议</router-link>
          <router-link to="/legal/privacy">隐私政策</router-link>
          <router-link to="/legal/third-parties">第三方服务</router-link>
          <router-link to="/support">举报与申诉</router-link>
        </nav>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Briefcase,
  ChatDotRound,
  CircleCheckFilled,
  MagicStick,
  Monitor,
  Right,
  Search,
  UploadFilled,
  Warning,
} from '@element-plus/icons-vue'
import request from '../api/request'
import PixelOrangeLogo from '../components/PixelOrangeLogo.vue'
import PixelOrangeMascot from '../components/PixelOrangeMascot.vue'
import heroVisual from '../assets/home/orangevc-maker-marketplace.jpg'
import makerDesk from '../assets/home/orangevc-maker-desk.jpg'

const router = useRouter()
const searchTerm = ref('')
const works = ref([])
const worksState = ref('loading')
const hasSession = Boolean(sessionStorage.getItem('appToken'))

const submitSearch = () => {
  const query = searchTerm.value ? { keyword: searchTerm.value } : undefined
  router.push({ path: '/market', query })
}

const openWork = id => router.push('/work/' + id)
const workCover = work => work.coverUrl || heroVisual
const workSummary = work => work.summary || work.detail || '打开详情，查看演示、说明和创作者信息。'
const compactImagePosition = index => ['50% 38%', '50% 58%', '50% 72%'][index] || '50% 50%'

const loadWorks = async () => {
  worksState.value = 'loading'
  try {
    const response = await request.get('/app/work/public', { params: { page: 1, size: 4 } })
    if (response.code !== 1) throw new Error(response.msg || '作品加载失败')
    works.value = response.data?.records || []
    worksState.value = 'ready'
  } catch {
    worksState.value = 'error'
  }
}

onMounted(loadWorks)
</script>

<style scoped>
.home-page {
  --home-bg: #f7f7f2;
  --home-surface: #fffefb;
  --home-surface-strong: #f0f1eb;
  --home-text: #20211d;
  --home-muted: #686b63;
  --home-line: #dedfd8;
  --home-line-strong: #bfc1b8;
  --home-accent: #ed762f;
  --home-accent-ink: #21170f;
  --home-accent-soft: #f9d8c2;
  --home-shadow: rgba(38, 35, 29, .1);
  min-height: 100dvh;
  overflow: clip;
  color: var(--home-text);
  background: var(--home-bg);
  font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif;
}

.home-page :where(a) { color: inherit; text-decoration: none; }
.home-page :where(button, input) { font: inherit; }
.home-page :where(h1, h2, h3, p) { word-break: break-word; }
.home-shell { width: min(calc(100% - 48px), 1280px); margin-inline: auto; }

.skip-link {
  position: fixed;
  left: 16px;
  top: 12px;
  z-index: 100;
  padding: 10px 14px;
  border-radius: 8px;
  color: var(--home-accent-ink);
  background: var(--home-accent);
  transform: translateY(-160%);
  transition: transform .18s ease;
}
.skip-link:focus { transform: translateY(0); }

.home-nav {
  position: relative;
  z-index: 10;
  border-bottom: 1px solid var(--home-line);
  background: color-mix(in srgb, var(--home-bg) 90%, transparent);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
}
.nav-inner {
  min-height: 70px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 32px;
}
.brand { display: inline-flex; width: max-content; align-items: center; gap: 12px; }
.brand-mark { width: 38px; height: 44px; flex: none; }
.brand-copy { display: grid; gap: 1px; }
.brand-copy strong { font-size: 15px; letter-spacing: -.02em; }
.brand-copy small { color: var(--home-muted); font: 10px/1.2 "Courier New", monospace; letter-spacing: .08em; }
.nav-links { display: flex; align-items: center; gap: 32px; color: var(--home-muted); font-size: 13px; font-weight: 700; }
.nav-links a, .text-link { transition: color .18s ease; }
.nav-links a:hover, .text-link:hover { color: var(--home-accent); }
.nav-actions { justify-self: end; display: flex; align-items: center; gap: 20px; font-size: 13px; font-weight: 750; }

.primary-link,
.secondary-link {
  min-height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding-inline: 22px;
  border: 1px solid var(--home-accent);
  border-radius: 14px;
  color: var(--home-accent-ink);
  background: var(--home-accent);
  font-weight: 850;
  box-shadow: 0 10px 24px color-mix(in srgb, var(--home-accent) 20%, transparent);
  transition: transform .18s ease, filter .18s ease, border-color .18s ease;
}
.primary-link:hover { filter: brightness(1.04); transform: translateY(-2px); }
.nav-primary { min-height: 40px; padding-inline: 16px; border-radius: 12px; box-shadow: none; }
.secondary-link { border-color: var(--home-line-strong); background: var(--home-surface); box-shadow: none; }
.secondary-link:hover, .section-link:hover { border-color: var(--home-accent); color: var(--home-accent); transform: translateY(-2px); }

.hero {
  min-height: calc(100dvh - 70px);
  display: grid;
  grid-template-columns: minmax(0, .93fr) minmax(360px, .77fr);
  align-items: center;
  gap: clamp(40px, 7vw, 108px);
  padding-block: clamp(48px, 7vh, 84px);
}
.hero-copy { position: relative; z-index: 1; animation: reveal-copy .7s cubic-bezier(.16, 1, .3, 1) both; }
.hero-kicker, .section-kicker { margin: 0 0 20px; color: var(--home-accent); font: 800 12px/1.4 "Courier New", monospace; letter-spacing: .1em; }
.hero h1 { max-width: 760px; margin: 0; font-size: clamp(46px, 5.6vw, 76px); line-height: 1.08; letter-spacing: -.065em; text-wrap: balance; }
.hero-summary { max-width: 570px; margin: 26px 0 0; color: var(--home-muted); font-size: clamp(16px, 1.6vw, 19px); line-height: 1.75; }
.hero-search { max-width: 630px; margin-top: 34px; }
.hero-search label { display: block; margin-bottom: 10px; color: var(--home-text); font-size: 13px; font-weight: 750; }
.search-control {
  min-height: 58px;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 12px;
  padding: 6px 6px 6px 18px;
  border: 1px solid var(--home-line-strong);
  border-radius: 16px;
  background: var(--home-surface);
  box-shadow: 0 16px 36px var(--home-shadow);
  transition: border-color .2s ease, box-shadow .2s ease;
}
.search-control:focus-within { border-color: var(--home-accent); box-shadow: 0 0 0 4px color-mix(in srgb, var(--home-accent) 14%, transparent), 0 16px 36px var(--home-shadow); }
.search-icon { color: var(--home-accent); font-size: 20px; }
.search-control input { width: 100%; min-width: 0; border: 0; outline: 0; color: var(--home-text); background: transparent; font-size: 15px; }
.search-control input::placeholder { color: color-mix(in srgb, var(--home-muted) 80%, transparent); }
.search-control button, .state-panel button {
  min-height: 44px;
  padding: 0 20px;
  border: 1px solid var(--home-accent);
  border-radius: 12px;
  color: var(--home-accent-ink);
  background: var(--home-accent);
  font-weight: 850;
  cursor: pointer;
  transition: transform .18s ease, filter .18s ease;
}
.search-control button:hover, .state-panel button:hover { filter: brightness(1.04); transform: translateY(-1px); }
.search-control button:active, .state-panel button:active { transform: translateY(1px) scale(.99); }

.hero-visual { position: relative; min-height: 630px; display: grid; align-items: center; animation: reveal-visual .85s .08s cubic-bezier(.16, 1, .3, 1) both; }
.hero-visual::before { content: ""; position: absolute; width: 74%; aspect-ratio: 1; right: -5%; bottom: 2%; border-radius: 50%; background: var(--home-accent-soft); filter: blur(1px); }
.hero-visual img { position: relative; width: 100%; max-height: min(70dvh, 700px); aspect-ratio: 4 / 5; object-fit: cover; border: 1px solid var(--home-line); border-radius: 16px; box-shadow: 18px 22px 0 var(--home-accent-soft), 0 30px 70px var(--home-shadow); }
.visual-caption { position: absolute; left: -34px; bottom: 38px; z-index: 2; min-width: 280px; display: grid; grid-template-columns: 52px 1fr; align-items: center; gap: 12px; padding: 13px 16px 13px 12px; border: 1px solid var(--home-line-strong); border-radius: 14px; background: color-mix(in srgb, var(--home-surface) 92%, transparent); box-shadow: 0 16px 40px var(--home-shadow); backdrop-filter: blur(14px); -webkit-backdrop-filter: blur(14px); }
.caption-mascot { width: 52px; height: 52px; }
.visual-caption p { display: grid; gap: 4px; margin: 0; }
.visual-caption strong { font-size: 13px; }
.visual-caption span { color: var(--home-muted); font-size: 11px; }

.trust-rail { border-block: 1px solid var(--home-line); background: var(--home-surface-strong); }
.trust-grid { display: grid; grid-template-columns: repeat(3, 1fr); }
.trust-grid p { display: grid; gap: 6px; margin: 0; padding: 28px 34px; }
.trust-grid p:first-child { padding-left: 0; }
.trust-grid p + p { border-left: 1px solid var(--home-line); }
.trust-grid strong { font-size: 14px; }
.trust-grid span { color: var(--home-muted); font-size: 12px; line-height: 1.6; }

.featured-section, .paths-section { padding-block: clamp(88px, 10vw, 150px); }
.section-heading { max-width: 690px; }
.section-heading h2 { margin: 0; font-size: clamp(34px, 4.3vw, 58px); line-height: 1.08; letter-spacing: -.055em; }
.section-heading > p:last-child { margin: 20px 0 0; color: var(--home-muted); font-size: 17px; line-height: 1.7; }
.works-layout { min-height: 610px; display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(320px, .75fr); gap: 18px; margin-top: 46px; }
.works-stack { display: grid; grid-template-rows: repeat(3, 1fr); gap: 18px; min-width: 0; }
.work-tile { position: relative; overflow: hidden; border: 1px solid var(--home-line); border-radius: 18px; background: var(--home-surface); cursor: pointer; transition: transform .25s ease, box-shadow .25s ease; }
.work-tile:hover { transform: translateY(-4px); box-shadow: 0 24px 52px var(--home-shadow); }
.work-tile img { width: 100%; height: 100%; object-fit: cover; }
.work-featured .work-info { position: absolute; inset: auto 0 0; padding: 88px 30px 28px; color: #fff; background: linear-gradient(transparent, rgba(18, 19, 17, .88)); }
.work-info > span { color: var(--home-accent); font: 800 11px/1.4 "Courier New", monospace; letter-spacing: .08em; }
.work-info h3 { margin: 8px 0 8px; font-size: 24px; line-height: 1.25; }
.work-info p { margin: 0; color: color-mix(in srgb, currentColor 78%, transparent); font-size: 13px; line-height: 1.65; }
.work-featured .work-info button { display: inline-flex; align-items: center; gap: 6px; margin-top: 18px; padding: 0; border: 0; color: #fff; background: transparent; font-weight: 800; cursor: pointer; }
.work-compact { display: grid; grid-template-columns: 36% 1fr auto; align-items: stretch; min-height: 0; }
.work-compact img { min-width: 0; }
.work-compact .work-info { align-self: center; min-width: 0; padding: 18px 14px 18px 18px; }
.work-compact .work-info h3 { overflow: hidden; margin: 5px 0 6px; font-size: 17px; text-overflow: ellipsis; white-space: nowrap; }
.work-compact .work-info p { display: -webkit-box; overflow: hidden; color: var(--home-muted); -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.work-arrow { align-self: center; margin-right: 16px; color: var(--home-accent); }
.section-link { display: inline-flex; align-items: center; gap: 8px; margin-top: 32px; border-bottom: 1px solid var(--home-line-strong); padding-bottom: 5px; font-weight: 800; transition: color .18s ease, transform .18s ease; }

.state-panel { min-height: 250px; display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 22px; margin-top: 46px; padding: clamp(28px, 5vw, 60px); border: 1px solid var(--home-line); border-radius: 18px; background: var(--home-surface); }
.state-panel > .el-icon { color: var(--home-accent); font-size: 32px; }
.state-panel > div { display: grid; gap: 7px; }
.state-panel strong { font-size: 18px; }
.state-panel span { color: var(--home-muted); font-size: 13px; }
.empty-mascot { width: 64px; height: 64px; }

.skeleton-card { padding: 28px; cursor: default; background: linear-gradient(100deg, var(--home-surface) 30%, var(--home-surface-strong) 48%, var(--home-surface) 66%); background-size: 220% 100%; animation: skeleton-shimmer 1.5s linear infinite; }
.skeleton-card span, .skeleton-card i { display: block; border-radius: 8px; background: color-mix(in srgb, var(--home-line) 76%, transparent); }
.skeleton-card span { width: 52%; height: 20px; }
.skeleton-card i { width: 78%; height: 12px; margin-top: 14px; }
.skeleton-card i:last-child { width: 38%; }

.compact-heading { max-width: 770px; }
.path-grid { display: grid; grid-template-columns: 1.12fr .88fr; grid-template-areas: "maker client" "maker ai"; gap: 18px; margin-top: 46px; }
.path-panel { position: relative; min-height: 300px; display: flex; flex-direction: column; align-items: flex-start; gap: 22px; overflow: hidden; padding: 32px; border: 1px solid var(--home-line); border-radius: 18px; background: var(--home-surface); }
.path-panel > div:not(.path-icon), .path-panel > a, .path-icon { position: relative; z-index: 2; }
.path-panel h3 { margin: 0; font-size: clamp(24px, 2.5vw, 34px); letter-spacing: -.04em; }
.path-panel p { max-width: 470px; margin: 12px 0 0; color: var(--home-muted); line-height: 1.7; }
.path-panel > a { display: inline-flex; align-items: center; gap: 7px; margin-top: auto; font-weight: 850; }
.path-panel > a:hover { color: var(--home-accent); }
.path-icon { width: 46px; height: 46px; display: grid; place-items: center; border-radius: 13px; color: var(--home-accent); background: var(--home-accent-soft); font-size: 23px; }
.path-maker { grid-area: maker; min-height: 620px; color: #fff; }
.path-maker::after { content: ""; position: absolute; inset: 0; background: linear-gradient(180deg, rgba(17, 18, 16, .03) 25%, rgba(17, 18, 16, .84) 100%); }
.path-maker img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.path-maker .path-icon { color: #fff; background: rgba(237, 118, 47, .88); }
.path-maker p { color: rgba(255, 255, 255, .78); }
.path-client { grid-area: client; background: var(--home-accent-soft); }
.path-client::before { content: ""; position: absolute; width: 210px; height: 210px; right: -60px; bottom: -75px; border: 2px solid color-mix(in srgb, var(--home-accent) 34%, transparent); border-radius: 50%; }
.path-ai { grid-area: ai; background: var(--home-surface-strong); }

.flow-section { border-block: 1px solid var(--home-line); background: var(--home-surface-strong); }
.flow-inner { padding-block: clamp(82px, 9vw, 128px); }
.flow-heading { max-width: 800px; }
.flow-grid { display: grid; grid-template-columns: repeat(3, 1fr); margin-top: 50px; counter-reset: flow; }
.flow-grid article { position: relative; min-height: 240px; padding: 30px 34px; border-top: 1px solid var(--home-line-strong); counter-increment: flow; }
.flow-grid article + article { border-left: 1px solid var(--home-line); }
.flow-grid article::before { content: "0" counter(flow); position: absolute; right: 22px; top: 24px; color: var(--home-line-strong); font: 800 13px/1 "Courier New", monospace; }
.flow-grid .el-icon { color: var(--home-accent); font-size: 28px; }
.flow-grid h3 { margin: 62px 0 12px; font-size: 20px; }
.flow-grid p { margin: 0; color: var(--home-muted); font-size: 13px; line-height: 1.75; }

.closing { display: grid; grid-template-columns: 1fr auto; align-items: end; gap: 60px; padding-block: clamp(86px, 10vw, 150px); }
.closing-copy { display: grid; grid-template-columns: auto 1fr; column-gap: 22px; align-items: center; }
.closing-mascot { grid-row: 1 / 3; width: 70px; height: 70px; }
.closing h2 { max-width: 820px; margin: 0; font-size: clamp(34px, 4.4vw, 60px); line-height: 1.08; letter-spacing: -.055em; }
.closing p { margin: 14px 0 0; color: var(--home-muted); font-size: 17px; }
.closing-actions { display: flex; gap: 12px; }

.home-footer { border-top: 1px solid var(--home-line); }
.footer-inner { min-height: 140px; display: flex; align-items: center; justify-content: space-between; gap: 32px; }
.footer-brand .brand-mark { width: 42px; }
.footer-inner nav { display: flex; flex-wrap: wrap; gap: 22px; color: var(--home-muted); font-size: 12px; }
.footer-inner nav a:hover { color: var(--home-accent); }

@keyframes reveal-copy { from { opacity: 0; transform: translateY(22px); } to { opacity: 1; transform: translateY(0); } }
@keyframes reveal-visual { from { opacity: 0; transform: translateY(30px) scale(.98); } to { opacity: 1; transform: translateY(0) scale(1); } }
@keyframes skeleton-shimmer { from { background-position: 110% 0; } to { background-position: -110% 0; } }

@media (prefers-color-scheme: dark) {
  .home-page {
    --home-bg: #181917;
    --home-surface: #22231f;
    --home-surface-strong: #2a2b26;
    --home-text: #f3f1ea;
    --home-muted: #b9b9b0;
    --home-line: #3d3f38;
    --home-line-strong: #5d5f55;
    --home-accent-soft: #58301c;
    --home-shadow: rgba(9, 9, 8, .34);
  }
}

@media (max-width: 1040px) {
  .nav-inner { gap: 22px; }
  .nav-links { gap: 18px; }
  .hero { grid-template-columns: minmax(0, 1fr) minmax(320px, .75fr); gap: 42px; }
  .hero-visual { min-height: 540px; }
  .works-layout { grid-template-columns: 1fr; min-height: 0; }
  .work-featured { min-height: 560px; }
  .works-stack { grid-template-columns: repeat(3, 1fr); grid-template-rows: none; }
  .work-compact { min-height: 260px; grid-template-columns: 1fr; grid-template-rows: 145px 1fr; }
  .work-compact .work-info p { display: none; }
  .work-arrow { position: absolute; right: 0; bottom: 20px; }
  .closing { grid-template-columns: 1fr; }
}

@media (max-width: 780px) {
  .home-shell { width: min(100% - 32px, 680px); }
  .home-nav { backdrop-filter: none; -webkit-backdrop-filter: none; background: var(--home-bg); }
  .nav-inner { min-height: 64px; grid-template-columns: 1fr auto; gap: 12px; }
  .nav-links, .nav-actions .text-link { display: none; }
  .brand-copy small { display: none; }
  .nav-primary { min-height: 38px; padding-inline: 13px; }
  .hero { min-height: auto; grid-template-columns: 1fr; gap: 48px; padding-block: 52px 78px; }
  .hero h1 { font-size: clamp(42px, 12.5vw, 62px); }
  .hero-summary { margin-top: 20px; }
  .hero-search { margin-top: 28px; }
  .search-control { grid-template-columns: auto 1fr; padding: 9px 10px 9px 15px; }
  .search-control button { grid-column: 1 / -1; width: 100%; }
  .hero-visual { min-height: auto; padding-left: 10px; }
  .hero-visual img { max-height: none; box-shadow: 10px 12px 0 var(--home-accent-soft), 0 24px 48px var(--home-shadow); }
  .visual-caption { left: -4px; bottom: 20px; min-width: 248px; }
  .trust-grid { grid-template-columns: 1fr; }
  .trust-grid p { padding: 22px 2px; }
  .trust-grid p + p { border-left: 0; border-top: 1px solid var(--home-line); }
  .featured-section, .paths-section { padding-block: 78px; }
  .works-stack { grid-template-columns: 1fr; }
  .work-featured { min-height: 480px; }
  .work-compact { min-height: 160px; grid-template-columns: 120px 1fr auto; grid-template-rows: none; }
  .path-grid { grid-template-columns: 1fr; grid-template-areas: "maker" "client" "ai"; }
  .path-panel { min-height: 280px; }
  .path-maker { min-height: 540px; }
  .flow-grid { grid-template-columns: 1fr; }
  .flow-grid article { min-height: 190px; padding-inline: 4px; }
  .flow-grid article + article { border-left: 0; }
  .closing-copy { grid-template-columns: 1fr; }
  .closing-mascot { grid-row: auto; margin-bottom: 18px; }
  .closing-actions { flex-wrap: wrap; }
  .footer-inner { min-height: 190px; flex-direction: column; align-items: flex-start; justify-content: center; }
}

@media (max-width: 480px) {
  .hero { padding-top: 42px; }
  .hero h1 br { display: none; }
  .hero h1 { font-size: clamp(39px, 12vw, 52px); }
  .visual-caption { width: calc(100% - 24px); min-width: 0; grid-template-columns: 44px 1fr; }
  .caption-mascot { width: 44px; height: 44px; }
  .state-panel { grid-template-columns: auto 1fr; }
  .state-panel button, .state-panel .secondary-link { grid-column: 1 / -1; width: 100%; }
  .work-featured { min-height: 420px; }
  .path-panel { padding: 26px; }
  .closing-actions > a { width: 100%; }
}

@media (prefers-reduced-motion: reduce) {
  .home-page *, .home-page *::before, .home-page *::after { scroll-behavior: auto !important; animation-duration: .01ms !important; animation-iteration-count: 1 !important; transition-duration: .01ms !important; }
}
</style>
