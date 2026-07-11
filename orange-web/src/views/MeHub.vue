<template>
  <div class="me-hub">
    <PageTour storageKey="mehub" :steps="mehubTour" />
    <!-- 摘要 -->
    <div class="profile-summary">
      <div>
        <div class="eyebrow">我的</div>
        <div class="author-row">
          <el-avatar :size="44" class="me-avatar">{{ (me.username || '?')[0] }}</el-avatar>
          <div>
            <strong>{{ me.nickname || me.username }}</strong>
            <span>{{ me.bio || 'AI 应用开发者' }}</span>
          </div>
        </div>
      </div>
      <div class="summary-actions">
        <el-button type="primary" @click="$router.push('/profile/me')">查看个人主页</el-button>
        <el-button @click="$router.push('/me/edit')">编辑资料</el-button>
      </div>
    </div>

    <!-- 功能卡片网格 -->
    <div class="hub-grid">
      <div class="hub-card featured" @click="$router.push('/me/works')">
        <div class="tag-row"><span class="hub-tag">内容管理</span></div>
        <h3>我的作品</h3>
        <p>管理已发布作品、草稿、审核状态和上架/下架。</p>
        <el-button class="mt-auto" type="primary" size="small">进入作品管理</el-button>
      </div>
      <div class="hub-card" @click="$router.push('/me/orders')">
        <h3>我的订单</h3>
        <p>查看购买和售出的订单记录。</p>
        <el-button class="mt-auto" size="small">查看订单</el-button>
      </div>
      <div class="hub-card" @click="$router.push('/me/favorites')">
        <h3>收藏夹</h3>
        <p>收藏的作品和需求，方便后续跟进。</p>
        <el-button class="mt-auto" size="small">查看收藏</el-button>
      </div>
      <div class="hub-card" @click="$router.push('/me/history')">
        <h3>浏览历史</h3>
        <p>最近浏览过的作品和需求记录。</p>
        <el-button class="mt-auto" size="small">查看历史</el-button>
      </div>
      <div class="hub-card" @click="$router.push('/me/quota')">
        <div class="quota-card-title">
          <PixelOrangeSlice :size="22" />
          <h3>配额</h3>
        </div>
        <p>AI 工具消耗、充值码兑换和配额记录。</p>
        <el-button class="mt-auto" size="small">查看配额</el-button>
      </div>
      <div class="hub-card" @click="$router.push('/me/settings')">
        <h3>设置</h3>
        <p>修改密码、账号安全和偏好设置。</p>
        <el-button class="mt-auto" size="small">进入设置</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PixelOrangeSlice from '../components/PixelOrangeSlice.vue'
import PageTour from '../components/PageTour.vue'
import request from '../api/request'

const mehubTour = [
  { selector: '.profile-summary', text: '顶部是你的个人信息和快捷操作：查看公开主页、编辑个人资料。', expression: 'smile' },
  { selector: '.hub-card.featured', text: '「我的作品」是最常用的入口。在这里管理已发布、草稿、上下架状态。', expression: 'love' },
  { selector: '.hub-grid', text: '六个大卡片覆盖所有个人管理功能：作品、订单、收藏、历史、配额、设置。点什么进什么～', expression: 'smile' },
]

const me = ref({ username: '', nickname: '' })

onMounted(async () => {
  try { const res = await request.get('/app/profile/me'); if (res.code === 1) me.value = res.data } catch {}
})
</script>

<style scoped>
.me-hub {
  /* full width — matches prototype */
}

/* 摘要 */
.profile-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 20px;
  padding: 22px;
  border: 1px solid var(--border-orange);
  border-radius: 28px;
  background: rgba(255,255,255,0.9);
  box-shadow: var(--shadow-card-lg);
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
.author-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
}
.me-avatar {
  background: linear-gradient(135deg, var(--brand), #fbbf24) !important;
  border: 2px solid #fff;
  border-radius: 16px !important;
}
.author-row strong { display: block; font-size: 14px; }
.author-row span { color: var(--text-secondary); font-size: 12px; }
.summary-actions { display: flex; gap: 8px; flex-wrap: wrap; }

/* Grid */
.hub-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}
.hub-card {
  min-height: 190px;
  padding: 20px;
  border: 1px solid var(--border-weak);
  border-left: 3px solid var(--brand-soft);
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
.hub-card.featured {
  grid-column: span 2;
  border-color: var(--border-orange);
  border-left: 4px solid var(--brand);
  background:
    linear-gradient(135deg, rgba(255,237,213,.88), rgba(255,255,255,.92)),
    repeating-linear-gradient(0deg, rgba(243,112,33,.045) 0, rgba(243,112,33,.045) 1px, transparent 1px, transparent 14px);
}
.hub-card h3 {
  margin: 0 0 8px;
  font-family: var(--font-mono);
  letter-spacing: -.04em;
  font-size: 20px;
}
.quota-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.quota-card-title h3 {
  margin: 0;
}
.hub-card p {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.7;
  font-size: 13px;
}
.mt-auto { margin-top: 18px; align-self: flex-start; }

.tag-row { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
.hub-tag {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--brand-strong);
  background: var(--brand-soft);
  font-size: 11px;
  font-weight: 800;
  font-family: var(--font-mono);
}

@media (max-width: 780px) {
  .hub-grid { grid-template-columns: 1fr; }
  .hub-card.featured { grid-column: auto; }
  .profile-summary { flex-direction: column; align-items: stretch; }
}
</style>
