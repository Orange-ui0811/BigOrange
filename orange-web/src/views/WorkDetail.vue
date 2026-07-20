<template>
  <div v-loading="loading">
    <template v-if="work">
      <!-- 返回 -->
      <button class="detail-back" @click="$router.push('/market')">返回发现</button>

      <div class="detail-layout">
        <PageTour storageKey="workdetail" :steps="workdetailTour" />
        <!-- ═══ 主体内容 ═══ -->
        <article class="detail-main">
          <!-- 封面区 -->
          <div class="detail-cover">
            <span class="eyebrow">作品详情 · {{ work.auditStatus || '已审核' }}<template v-if="work.demoUrl"> · 可演示</template><template v-if="work.videoUrl"> · 有视频</template></span>
            <div style="height:16px"></div>
            <img v-if="work.coverUrl" :src="work.coverUrl" class="cover-img" />
            <div v-else class="mock-browser">
              <div class="browser-bar"><span class="dot"></span><span class="dot"></span><span class="dot"></span></div>
              <div class="browser-body">
                <div class="mock-hero"></div>
                <div class="mock-grid"><div class="mock-block"></div><div class="mock-block"></div></div>
                <div class="mock-block" style="min-height:76px"></div>
              </div>
            </div>
          </div>

          <!-- 内容区 -->
          <div class="detail-content">
            <div class="tag-row">
              <span class="tag orange" v-if="work.categoryName">{{ work.categoryName }}</span>
              <span class="tag yellow" v-if="work.demoUrl">可演示</span>
              <span class="tag yellow" v-if="work.videoUrl">视频展示</span>
              <span class="tag blue" v-if="work.language">{{ work.language }}</span>
              <span class="tag green">{{ work.auditStatus || '已审核' }}</span>
            </div>
            <h2>{{ work.title }}</h2>
            <p v-if="work.summary || work.detail" class="work-desc">{{ work.summary || stripHtml(work.detail) }}</p>

            <!-- 信息网格 -->
            <div class="info-grid">
              <div class="info-box">
                <span>技术栈</span>
                <strong>{{ work.language || work.categoryName || '未指定' }}</strong>
              </div>
              <div class="info-box">
                <span>展示内容</span>
                <strong>{{ work.videoUrl ? '含视频展示' : work.demoUrl ? '可在线体验' : '图文介绍' }}</strong>
              </div>
              <div class="info-box">
                <span>作者</span>
                <strong>{{ work.ownerName || '#'+work.ownerId }}</strong>
              </div>
            </div>

            <!-- 作品亮点 -->
            <template v-if="work.detail">
              <h3>作品亮点</h3>
              <div class="work-detail" v-safe-html="work.detail"></div>
            </template>

            <VideoShowcase v-if="work.videoUrl" :url="work.videoUrl" :poster="work.coverUrl" :title="work.title" />

            <!-- 售卖项 -->
            <div v-if="work.saleOptions?.length" class="sale-options">
              <h3>参考价格</h3>
              <div class="sale-option-list">
                <el-tag v-for="(so,i) in work.saleOptions" :key="i" type="warning" size="large">¥{{ so.price }}</el-tag>
              </div>
              <p class="payment-coming-soon">价格仅供展示，在线支付功能即将开放。</p>
            </div>

            <!-- 演示链接 -->
            <div v-if="work.demoUrl" class="demo-box">
              <span class="demo-label">🔗 在线演示：</span>
              <a :href="work.demoUrl" target="_blank" rel="noopener noreferrer" class="demo-link">点击体验 →</a>
            </div>

            <!-- 评论区 -->
            <div class="comment-section">
              <strong>评论与问答 ({{ comments.length }})</strong>
              <div v-if="comments.length" class="comment-list">
                <div v-for="c in comments" :key="c.id" class="comment-item">
                  <div class="comment-row">
                    <div class="comment-body">
                      <b>{{ c.accountName || '#'+c.accountId }}：</b>
                      <span>{{ c.content }}</span>
                      <span class="comment-time">{{ c.createTime }}</span>
                    </div>
                    <el-button v-if="canDelete(c)" size="small" type="danger" text @click="delCmt(c.id)">删除</el-button>
                  </div>
                </div>
              </div>
              <div class="comment-input-row">
                <el-input v-model="cmtText" placeholder="写评论..." size="small" @keyup.enter="sendCmt"/>
                <el-button size="small" type="primary" @click="sendCmt">发表</el-button>
              </div>
            </div>
          </div>
        </article>

        <!-- ═══ 侧栏 ═══ -->
        <aside class="deal-panel">
          <div class="author-card">
            <el-avatar :size="44" class="author-avatar" :src="work.ownerAvatar || undefined">
              {{ (work.ownerName || '?')[0] }}
            </el-avatar>
            <div>
              <strong>{{ work.ownerName || '#'+work.ownerId }}</strong>
              <span>AI 应用开发者</span>
            </div>
          </div>

          <div class="sale-card active" v-if="work.demoUrl">
            <div><strong>查看演示</strong><span>先了解作品功能和完成度</span></div>
            <strong class="price">Demo</strong>
          </div>
          <div class="sale-card" @click="contactAuthor" style="cursor:pointer">
            <div><strong>咨询作者</strong><span>询问适用场景、技术栈和后续计划</span></div>
            <strong class="price">Chat</strong>
          </div>
          <div class="sale-card" @click="fav" style="cursor:pointer">
            <div><strong>{{ favd ? '已收藏' : '收藏作品' }}</strong><span>加入收藏夹，后续继续查看</span></div>
            <strong class="price">Save</strong>
          </div>

          <el-button type="primary" style="width:100%;margin-top:10px" :loading="contacting" @click="contactAuthor">进入聊天咨询</el-button>
          <el-button style="width:100%;margin-top:10px" @click="fav">{{ favd ? '已收藏' : '收藏作品' }}</el-button>
          <el-button style="width:100%;margin-top:10px" @click="$router.push('/profile/'+work.ownerId)">查看作者主页</el-button>

          <div class="trust-box">提示：当前只展示作品信息、作者信息、评论问答和聊天入口。</div>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageTour from '../components/PageTour.vue'
import VideoShowcase from '../components/VideoShowcase.vue'
import request from '../api/request'

const workdetailTour = [
  { selector: '.detail-cover', text: '顶部是作品封面；作者提供演示地址或视频时，可以继续向下查看和体验。', expression: 'curious' },
  { selector: '.deal-panel', text: '右侧操作面板：联系作者、收藏作品、查看作者主页。点「进入聊天咨询」开始沟通。', expression: 'wink' },
  { selector: '.comment-section', text: '底部评论区：看看别人怎么评价的，也可以留言和作者交流！', expression: 'smile' },
]

const API = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
const route=useRoute(); const router=useRouter()
const work=ref(null),loading=ref(false),favd=ref(false),comments=ref([]),cmtText=ref(''),contacting=ref(false)
const contactAuthor = async () => {
  if (!work.value) return; contacting.value = true
  try {
    const res = await request.post('/app/conversation', { workId: work.value.id, receiverId: work.value.ownerId })
    if (res.code === 1) router.push('/chat/'+res.data.id)
  } finally { contacting.value = false }
}
const stripHtml = (html) => {
  if (!html) return ''
  return new DOMParser().parseFromString(html, 'text/html').body.textContent || ''
}
onMounted(async()=>{
  loading.value=true
  try{const r=await request.get('/app/work/'+route.params.id);if(r.code===1)work.value=r.data}catch{}
  try{request.post('/app/history?targetType=WORK&targetId='+route.params.id)}catch{}
  try{const c=await request.get('/app/comment/WORK/'+route.params.id+'?page=1&size=20');if(c.code===1)comments.value=c.data?.records||[]}catch{}
  loading.value=false
})
const myId = sessionStorage.getItem('myId')
const canDelete = (c) => {
  if (!work.value || !myId) return false
  return String(c.accountId) === myId || String(work.value.ownerId) === myId
}
const delCmt = async (id) => {
  try { await request.delete('/app/comment/' + id); comments.value = comments.value.filter(c => c.id !== id) } catch {}
}
const fav=async()=>{try{await request.post('/app/favorite?targetType=WORK&targetId='+route.params.id);favd.value=true}catch{}}
const sendCmt=async()=>{if(!cmtText.value.trim())return;try{await request.post('/app/comment',{targetType:'WORK',targetId:parseInt(route.params.id),content:cmtText.value.trim()});cmtText.value='';const c=await request.get('/app/comment/WORK/'+route.params.id+'?page=1&size=20');if(c.code===1)comments.value=c.data?.records||[]}catch{}}
</script>

<style scoped>
/* 返回 */
.detail-back {
  margin-bottom: 14px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 0 14px;
  color: var(--text-secondary);
  background: #fff;
  font-family: var(--font-mono);
  font-weight: 800;
  font-size: 12px;
  cursor: pointer;
  transition: background .15s, color .15s;
}
.detail-back:hover { background: #fff7ed; color: var(--brand-strong); }

/* 布局 */
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 22px;
  align-items: start;
}
.detail-main {
  border: 1px solid rgba(254, 215, 170, .74);
  border-radius: 28px;
  background: rgba(255,255,255,.86);
  box-shadow: var(--shadow-card-lg);
  overflow: hidden;
}

/* 封面 */
.detail-cover {
  min-height: 280px;
  padding: 24px;
  background:
    linear-gradient(135deg, rgba(255,237,213,.82), rgba(255,255,255,.68)),
    repeating-linear-gradient(0deg, rgba(243,112,33,.05) 0, rgba(243,112,33,.05) 1px, transparent 1px, transparent 14px);
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
.cover-img {
  width: 100%;
  max-height: 320px;
  object-fit: cover;
  border-radius: 14px;
  border: 1px solid var(--border-orange);
}

/* Mock browser */
.mock-browser {
  overflow: hidden;
  min-height: 240px;
  border: 1px solid var(--line);
  border-radius: 22px;
  background: #fff;
  box-shadow: var(--shadow-card);
}
.browser-bar {
  height: 40px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 14px;
  background: #fff7ed;
  border-bottom: 1px solid var(--border-orange);
}
.dot {
  width: 10px; height: 10px;
  border-radius: 50%;
  background: var(--brand);
}
.dot:nth-child(2) { background: var(--gold); }
.dot:nth-child(3) { background: var(--green); }
.browser-body { padding: 20px; display: grid; gap: 12px; }
.mock-hero {
  min-height: 70px;
  border-radius: 18px;
  background: linear-gradient(135deg, #fff7ed, #fed7aa);
  border: 1px solid var(--border-orange);
}
.mock-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.mock-block {
  min-height: 80px;
  border-radius: 18px;
  background: #f9fafb;
  border: 1px solid var(--line);
}

/* 内容 */
.detail-content { padding: 26px; }
.tag-row { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
.tag {
  min-height: 22px;
  display: inline-flex; align-items: center;
  padding: 0 8px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--text-secondary);
  background: #f9fafb;
  font-size: 11px; font-weight: 800;
  font-family: var(--font-mono);
}
.tag.orange { color: var(--brand-strong); background: var(--brand-soft); border-color: var(--border-orange); }
.tag.green  { color: #9a5a24; background: #fff7ed; border-color: #fed7aa; }
.tag.blue   { color: #9a5a24; background: #fffaf4; border-color: #fcd9bd; }
.tag.yellow { color: #92400e; background: #fffbeb; border-color: #fde68a; }

.detail-content h2 {
  margin: 0 0 10px;
  font-family: var(--font-mono);
  font-size: clamp(24px, 4vw, 36px);
  line-height: 1.1;
  letter-spacing: -.05em;
}
.work-desc { color: var(--text-secondary); line-height: 1.7; margin: 0 0 20px; }

.info-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 20px 0; }
.info-box {
  padding: 14px; border: 1px solid var(--line);
  border-radius: 18px; background: #f9fafb;
}
.info-box span { color: var(--text-secondary); font-size: 11px; }
.info-box strong { display: block; margin-top: 4px; font-family: var(--font-mono); color: var(--text-primary); font-size: 13px; }

.detail-content h3 { margin: 22px 0 10px; font-family: var(--font-mono); font-size: 18px; letter-spacing: -.03em; }
.work-detail { color: var(--text-primary); line-height: 1.8; }
.work-detail :deep(h2),
.work-detail :deep(h3) { font-family: var(--font-mono); }

/* 售卖项 */
.sale-options { margin-top: 20px; }
.sale-option-list { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }

/* 演示 */
.demo-box {
  margin-top: 18px; padding: 14px;
  border: 1px solid var(--border-orange);
  border-radius: 12px;
  background: #fffaf5;
  display: flex; align-items: center; gap: 8px;
}
.demo-label { font-size: 13px; color: var(--text-primary); }
.demo-link { font-size: 13px; color: var(--brand-strong); font-weight: 800; font-family: var(--font-mono); }

/* 评论 */
.comment-section {
  margin-top: 22px; padding: 18px;
  border: 1px solid var(--line);
  border-radius: 20px; background: #fff;
}
.comment-section strong { font-family: var(--font-mono); font-size: 14px; letter-spacing: -.03em; }
.comment-list { margin-top: 12px; display: grid; gap: 8px; }
.comment-item { padding: 8px 0; border-bottom: 1px solid var(--border-light); font-size: 13px; }
.comment-row { display: flex; justify-content: space-between; align-items: flex-start; }
.comment-body { flex: 1; line-height: 1.6; }
.comment-body b { color: var(--brand-strong); }
.comment-time { color: var(--text-muted); font-size: 11px; margin-left: 8px; }
.comment-input-row { margin-top: 12px; display: flex; gap: 8px; }

/* 侧栏 */
.deal-panel {
  position: sticky; top: 68px;
  padding: 22px;
  border: 1px solid rgba(254, 215, 170, .74);
  border-radius: 28px;
  background: rgba(255,255,255,.86);
  box-shadow: var(--shadow-card-lg);
}
.author-card {
  display: flex; gap: 12px; align-items: center;
  padding-bottom: 16px; border-bottom: 1px solid var(--line);
  margin-bottom: 16px;
}
.author-avatar {
  background: linear-gradient(135deg, var(--brand), #fbbf24) !important;
  border: 2px solid #fff; border-radius: 16px !important;
}
.author-card strong { display: block; font-size: 14px; }
.author-card span { color: var(--text-secondary); font-size: 12px; }

.sale-card {
  padding: 14px; border: 1px solid var(--line);
  border-radius: 18px; margin-bottom: 8px;
  display: flex; justify-content: space-between; gap: 12px;
  transition: border-color .15s, background .15s;
}
.sale-card.active { background: var(--cream); border-color: var(--border-orange); }
.sale-card:hover { border-color: var(--border-orange); background: #fffaf5; }
.sale-card strong { font-size: 14px; }
.sale-card span { display: block; margin-top: 3px; color: var(--text-secondary); font-size: 11px; line-height: 1.4; }
.price { color: var(--brand-strong); font-family: var(--font-mono); font-weight: 900; font-size: 13px; }
.payment-coming-soon { margin: 10px 0 0; color: var(--text-muted); font-size: 12px; }

.trust-box {
  margin-top: 14px; padding: 12px;
  border-radius: 18px; background: #fffbeb;
  border: 1px solid #fde68a; color: #92400e;
  font-size: 12px; line-height: 1.6;
}

@media (max-width: 1120px) { .detail-layout { grid-template-columns: 1fr; } .deal-panel { position: static; } }
@media (max-width: 780px) { .info-grid, .mock-grid { grid-template-columns: 1fr; } }
</style>
