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
              <div class="work-cta">
                <strong class="work-price">{{ formatWorkPrice(works[0].price) }}</strong>
                <button type="button" @click.stop="openWork(works[0].id)">
                  查看作品 <el-icon aria-hidden="true"><Right /></el-icon>
                </button>
              </div>
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
                <strong class="work-price compact-price">{{ formatWorkPrice(work.price) }}</strong>
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
import PixelOrangeLogo from '../components/PixelOrangeLogo.vue'
import PixelOrangeMascot from '../components/PixelOrangeMascot.vue'
import { usePublicHome } from '../composables/usePublicHome'
import { formatWorkPrice } from '../utils/workPrice'
import heroVisual from '../assets/home/orangevc-maker-marketplace.jpg'
import makerDesk from '../assets/home/orangevc-maker-desk.jpg'

const {
  searchTerm, works, worksState, hasSession, submitSearch, openWork,
  workCover, workSummary, compactImagePosition, loadWorks,
} = usePublicHome()
</script>

<style scoped>
@import '../styles/home.css';
</style>
