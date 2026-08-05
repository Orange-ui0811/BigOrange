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
import { useMarket } from '../composables/useMarket'

const { PageTour, marketTour, activeTab, works, reqs, worksTotal, reqsTotal, categories,
  categoryId, keyword, worksPage, reqsPage, pageSize, loading, coverVariant, load,
  switchTab, doSearch, scrollToSection } = useMarket()
</script>

<style scoped src="../styles/views/market.css"></style>
