<template>
  <div class="leaderboard-page">
    <PageTour storageKey="leaderboard" :steps="tourSteps" />
    <!-- Compact Head -->
    <div class="compact-head">
      <div>
        <div class="head-toolbar">
          <span class="eyebrow">榜单中心</span>
          <label class="persona-model-control">
            <span><strong>3D橙子</strong><small>{{ personaModelVisible ? '正在显示' : '已隐藏' }}</small></span>
            <el-switch
              :model-value="personaModelVisible"
              aria-label="在榜单页显示3D橙子"
              @change="setPersonaModelVisible"
            />
          </label>
        </div>
        <h2>从平台热度到 GitHub 趋势，找到真正适合你的项目。</h2>
        <p>浏览站内作品、GitHub 热门仓库、新手可落地项目，或按真实岗位能力筛选职业成长项目。</p>
      </div>
      <LeaderboardPersonaCard :source="source" :model-visible="personaModelVisible" @recommend="recommendWithPersona" />
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
import { useLeaderboard } from '../composables/useLeaderboard'
import LeaderboardPersonaCard from '../components/LeaderboardPersonaCard.vue'
import { useLeaderboardPersonaVisibility } from '../composables/useLeaderboardPersonaVisibility'

const { PageTour, BeginnerProjects, CareerProjects, tourSteps, source, period, category, type,
  list, loading, page, pageSize, total, categories, warning, emptyText, load, onSourceChange,
  onClick, recommendWithPersona } = useLeaderboard()
const { personaModelVisible, setPersonaModelVisible } = useLeaderboardPersonaVisibility()
</script>

<style scoped src="../styles/views/leaderboard.css"></style>
