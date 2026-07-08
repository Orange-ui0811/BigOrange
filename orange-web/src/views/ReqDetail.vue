<template>
  <div v-loading="loading">
    <template v-if="req">
      <button class="detail-back" @click="$router.push('/market')">返回发现</button>

      <div class="detail-layout">
        <!-- ═══ 主体内容 ═══ -->
        <article class="detail-main">
          <div class="detail-content">
            <div class="tag-row">
              <span class="tag blue" v-if="req.categoryName">{{ req.categoryName }}</span>
              <span class="tag yellow">预算待沟通</span>
              <span class="tag green">{{ req.auditStatus || '已审核' }}</span>
            </div>
            <h2>{{ req.title }}</h2>
            <p v-if="req.summary || req.detail" class="req-desc">{{ req.summary || stripHtml(req.detail) }}</p>

            <div class="info-grid">
              <div class="info-box">
                <span>预算范围</span>
                <strong>¥{{ req.budgetMin || '?' }} - ¥{{ req.budgetMax || '?' }}</strong>
              </div>
              <div class="info-box">
                <span>期望周期</span>
                <strong>沟通后确认</strong>
              </div>
              <div class="info-box">
                <span>沟通方式</span>
                <strong>先聊天确认</strong>
              </div>
            </div>

            <template v-if="req.detail">
              <h3>需求重点</h3>
              <div class="req-detail" v-html="req.detail"></div>
            </template>

            <div v-if="req.publisherName" class="supplement-card">
              <strong>补充说明</strong>
              <p>需求方可继续补充参考网站、品牌资料、页面数量和验收标准。</p>
            </div>
          </div>
        </article>

        <!-- ═══ 侧栏 ═══ -->
        <aside class="deal-panel">
          <div class="author-card">
            <el-avatar :size="44" class="author-avatar">{{ (req.publisherName || '?')[0] }}</el-avatar>
            <div>
              <strong>{{ req.publisherName || '#'+req.publisherId }}</strong>
              <span>需求发布者</span>
            </div>
          </div>

          <div class="sale-card active">
            <div><strong>查看需求</strong><span>确认目标、范围和预算区间</span></div>
            <strong class="price">Req</strong>
          </div>
          <div class="sale-card" @click="contactPublisher" style="cursor:pointer">
            <div><strong>联系需求方</strong><span>通过聊天补充细节</span></div>
            <strong class="price">Chat</strong>
          </div>
          <div class="sale-card" style="cursor:pointer">
            <div><strong>收藏需求</strong><span>后续继续跟进</span></div>
            <strong class="price">Save</strong>
          </div>

          <el-button type="primary" style="width:100%;margin-top:10px" :loading="contacting" @click="contactPublisher">进入聊天咨询</el-button>
          <el-button style="width:100%;margin-top:10px" @click="$router.push('/profile/'+req.publisherId)">查看发布者主页</el-button>

          <div class="trust-box">提示：当前只展示需求信息、发布者信息和聊天入口。</div>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../api/request'
const route=useRoute(); const router=useRouter()
const req=ref(null),loading=ref(false),contacting=ref(false)
const contactPublisher = async () => {
  if (!req.value) return; contacting.value = true
  try {
    const res = await request.post('/app/conversation', { requirementId: req.value.id, receiverId: req.value.publisherId })
    if (res.code === 1) router.push('/chat/'+res.data.id)
  } finally { contacting.value = false }
}
const stripHtml = (html) => {
  if (!html) return ''
  const div = document.createElement('div')
  div.innerHTML = html
  return div.textContent || div.innerText || ''
}
onMounted(async()=>{loading.value=true;try{const r=await request.get('/app/requirement/'+route.params.id);if(r.code===1)req.value=r.data}catch{}finally{loading.value=false}})
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
.tag.blue   { color: #9a5a24; background: #fffaf4; border-color: #fcd9bd; }
.tag.yellow { color: #92400e; background: #fffbeb; border-color: #fde68a; }
.tag.green  { color: #9a5a24; background: #fff7ed; border-color: #fed7aa; }

.detail-content h2 {
  margin: 0 0 10px;
  font-family: var(--font-mono);
  font-size: clamp(24px, 4vw, 36px);
  line-height: 1.1;
  letter-spacing: -.05em;
}
.req-desc { color: var(--text-secondary); line-height: 1.7; margin: 0 0 20px; }

.info-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 20px 0; }
.info-box {
  padding: 14px; border: 1px solid var(--line);
  border-radius: 18px; background: #f9fafb;
}
.info-box span { color: var(--text-secondary); font-size: 11px; }
.info-box strong { display: block; margin-top: 4px; font-family: var(--font-mono); color: var(--text-primary); font-size: 13px; }

.detail-content h3 { margin: 22px 0 10px; font-family: var(--font-mono); font-size: 18px; letter-spacing: -.03em; }
.req-detail { color: var(--text-primary); line-height: 1.8; }
.req-detail :deep(h2),
.req-detail :deep(h3) { font-family: var(--font-mono); }

/* 补充说明 */
.supplement-card {
  margin-top: 20px; padding: 16px;
  border: 1px solid var(--line);
  border-radius: 20px; background: #fff;
}
.supplement-card strong { font-family: var(--font-mono); font-size: 14px; letter-spacing: -.03em; }
.supplement-card p { margin: 8px 0 0; color: var(--text-secondary); font-size: 13px; line-height: 1.6; }

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

.trust-box {
  margin-top: 14px; padding: 12px;
  border-radius: 18px; background: #fffbeb;
  border: 1px solid #fde68a; color: #92400e;
  font-size: 12px; line-height: 1.6;
}

@media (max-width: 1120px) { .detail-layout { grid-template-columns: 1fr; } .deal-panel { position: static; } }
@media (max-width: 780px) { .info-grid { grid-template-columns: 1fr; } }
</style>
