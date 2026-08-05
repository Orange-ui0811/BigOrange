<template>
  <el-container class="app-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <!-- ═══ 侧边栏 ═══ -->
    <el-aside width="252px" class="app-sidebar">

      <!-- 品牌 + 像素橙子 -->
      <div class="sidebar-brand">
        <svg class="sidebar-logo" viewBox="0 0 40 40" aria-hidden="true">
          <rect x="14" y="1" width="3" height="8" fill="#4d230d" />
          <rect x="17" y="2" width="1" height="7" fill="#4d230d" />
          <rect x="15" y="2" width="2" height="6" fill="#79bd28" />
          <rect x="19" y="2" width="9" height="2" fill="#4d230d" />
          <rect x="18" y="4" width="12" height="2" fill="#4d230d" />
          <rect x="18" y="6" width="11" height="2" fill="#4d230d" />
          <rect x="20" y="4" width="8" height="2" fill="#8bd33a" />
          <rect x="19" y="6" width="8" height="2" fill="#78c42f" />
          <rect x="12" y="6" width="10" height="2" fill="#4d230d" />
          <rect x="9" y="8" width="4" height="2" fill="#4d230d" />
          <rect x="7" y="10" width="3" height="2" fill="#4d230d" />
          <rect x="5" y="12" width="3" height="4" fill="#4d230d" />
          <rect x="4" y="16" width="2" height="5" fill="#4d230d" />
          <rect x="3" y="21" width="2" height="7" fill="#4d230d" />
          <rect x="4" y="28" width="2" height="4" fill="#4d230d" />
          <rect x="6" y="32" width="3" height="3" fill="#4d230d" />
          <rect x="9" y="35" width="5" height="2" fill="#4d230d" />
          <rect x="14" y="37" width="13" height="1" fill="#4d230d" />
          <rect x="27" y="35" width="5" height="2" fill="#4d230d" />
          <rect x="32" y="32" width="3" height="3" fill="#4d230d" />
          <rect x="35" y="27" width="2" height="5" fill="#4d230d" />
          <rect x="36" y="18" width="1" height="9" fill="#4d230d" />
          <rect x="35" y="14" width="2" height="4" fill="#4d230d" />
          <rect x="33" y="11" width="3" height="3" fill="#4d230d" />
          <rect x="30" y="9" width="3" height="2" fill="#4d230d" />
          <rect x="27" y="7" width="3" height="2" fill="#4d230d" />
          <rect x="13" y="8" width="14" height="2" fill="#ffad3f" />
          <rect x="27" y="9" width="3" height="1" fill="#ffad3f" />
          <rect x="10" y="10" width="23" height="2" fill="#ffa739" />
          <rect x="8" y="12" width="27" height="4" fill="#ffa033" />
          <rect x="6" y="16" width="29" height="5" fill="#ff9a2e" />
          <rect x="5" y="21" width="31" height="7" fill="#ff9329" />
          <rect x="6" y="28" width="29" height="4" fill="#ff8823" />
          <rect x="9" y="32" width="23" height="3" fill="#ff7e1f" />
          <rect x="14" y="35" width="13" height="2" fill="#ef6f18" />
          <rect x="33" y="18" width="3" height="8" fill="#ff7623" />
          <rect x="14" y="21" width="2" height="3" fill="#5c260f" />
          <rect x="24" y="21" width="2" height="3" fill="#5c260f" />
          <rect x="18" y="28" width="1" height="1" fill="#5c260f" />
          <rect x="22" y="28" width="1" height="1" fill="#5c260f" />
          <rect x="19" y="29" width="1" height="1" fill="#5c260f" />
          <rect x="21" y="29" width="1" height="1" fill="#5c260f" />
          <rect x="20" y="30" width="1" height="1" fill="#5c260f" />
        </svg>
        <div class="sidebar-brand-text">
          <span class="brand-name">OrangeVC</span>
          <span class="brand-sub">vibe coding market</span>
        </div>
      </div>

      <!-- 用户面板 -->
      <div class="sidebar-user">
        <PersonaOrangeBadge v-if="personaAvatar.enabled" compact :equipment="personaAvatar.equipment" :appearance="personaAvatar.appearance" label="我的橙子画像头像" />
        <el-avatar v-else :size="44" class="sidebar-avatar" :src="avatarUrl || undefined">
          {{ (nickname || me.nickname || me.username || '?')[0] }}
        </el-avatar>
        <div class="sidebar-user-info">
          <strong>{{ nickname || me.nickname || me.username }}</strong>
          <span>创作者</span>
        </div>
      </div>

      <div class="sidebar-divider"></div>

      <!-- ═══ 5 项主导航 ═══ -->
      <div class="nav-section-label">主导航</div>
      <el-menu :default-active="activeMenu" class="sidebar-menu" router>
        <el-menu-item index="/market">
          <span class="nav-item-content">
            <el-icon><Shop /></el-icon>
            <span>发现</span>
          </span>
        </el-menu-item>
        <el-menu-item index="/ai">
          <span class="nav-item-content">
            <el-icon><Cpu /></el-icon>
            <span>AI 工具</span>
          </span>
        </el-menu-item>
        <el-menu-item index="/ai-records">
          <span class="nav-item-content">
            <el-icon><Document /></el-icon>
            <span>AI 记录</span>
          </span>
        </el-menu-item>
        <el-menu-item index="/persona-workshop">
          <span class="nav-item-content">
            <el-icon><MagicStick /></el-icon>
            <span>橙子画像工坊</span>
          </span>
        </el-menu-item>
        <el-menu-item index="/publish">
          <span class="nav-item-content">
            <el-icon><Edit /></el-icon>
            <span>发布</span>
          </span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <span class="nav-item-content">
            <el-icon><ChatDotRound /></el-icon>
            <span>消息</span>
          </span>
          <span v-if="chatUnread" class="menu-badge">{{ chatUnread }}</span>
        </el-menu-item>
        <el-menu-item index="/me">
          <span class="nav-item-content">
            <el-icon><User /></el-icon>
            <span>我的</span>
          </span>
        </el-menu-item>
      </el-menu>

      <!-- 橙子使用指南 -->
      <GuideBubble />

      <!-- 功能归属提示 -->
      <div class="nav-hint">
        <strong>功能归属</strong>
        榜单、趋势分析、方案设计 → AI 工具<br/>
        画像、素材箱、橙子换装 → 橙子画像工坊<br/>
        通知、收藏、历史、配额、设置 → 我的
      </div>

      <!-- ═══ 底部工具 ═══ -->
      <div class="sidebar-tools">
        <div class="sidebar-divider"></div>
        <button class="tool-btn" type="button" @click="$router.push('/me/notifications')">
          <span>系统通知</span>
          <span v-if="unreadCount" class="menu-badge">{{ unreadCount }}</span>
        </button>
        <button class="tool-btn" type="button" @click="$router.push('/me/settings')">
          <span>设置</span>
        </button>
        <div class="legal-links" aria-label="法律与安全">
          <router-link to="/legal/terms">用户协议</router-link>
          <router-link to="/legal/privacy">隐私政策</router-link>
          <router-link to="/support">举报申诉</router-link>
        </div>
        <button class="tool-btn logout-btn" type="button" @click="logout">
          <span>退出</span>
        </button>
      </div>
    </el-aside>

    <!-- ═══ 主区域 ═══ -->
    <el-container>
      <el-header class="app-header">
        <div class="topbar-title">
          <h1>{{ route.meta.title || 'OrangeVC' }}</h1>
        </div>
        <div class="top-actions"></div>
      </el-header>
      <el-main id="main-content" class="app-main" tabindex="-1">
        <router-view />
      </el-main>
    </el-container>

    <nav class="mobile-nav" aria-label="主导航">
      <button v-for="item in mobileNavItems" :key="item.path" type="button"
              :class="{ active: activeMenu === item.path }" :aria-current="activeMenu === item.path ? 'page' : undefined"
              @click="router.push(item.path)">
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
        <span v-if="item.path === '/chat' && chatUnread" class="mobile-badge">{{ chatUnread }}</span>
      </button>
    </nav>
    <ReleaseAnnouncementDialog :account-id="accountId" />
  </el-container>
</template>

<script setup>
import { useMainLayout } from '../composables/useMainLayout'

const { GuideBubble, PersonaOrangeBadge, ReleaseAnnouncementDialog, Shop, Cpu, Edit, ChatDotRound, User, Document, route,
  MagicStick, unreadCount, avatarUrl, nickname, me, chatUnread, personaAvatar, mobileNavItems,
  accountId, logout, activeMenu } = useMainLayout()
</script>

<style scoped src="../styles/layouts/main-layout.css"></style>
