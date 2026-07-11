<template>
  <div class="message-layout">
    <!-- ═══ 左：会话列表 ═══ -->
    <aside class="panel conv-panel">
      <h3>会话</h3>
      <div v-for="c in convs" :key="c.id"
        class="conversation"
        :class="{ active: activeConv===c.id }"
        @click="openConv(c)"
      >
        <strong>{{ c.initiatorId == myId ? c.receiverName : c.initiatorName }}</strong>
        <p>{{ c.lastMessage || c.workTitle || c.requirementTitle || '新会话' }}</p>
        <span v-if="c.unreadCount" class="conv-badge">{{ c.unreadCount }}</span>
        <span class="conv-del" @click.stop="deleteConv(c.id)" title="删除">×</span>
      </div>
      <el-empty v-if="!convs.length" description="暂无会话" :image-size="60" />
    </aside>

    <!-- ═══ 中：聊天区 ═══ -->
    <section class="panel chat-panel">
      <template v-if="activeConv">
        <div class="chat-topbar">
          <div class="tag-row">
            <span class="chat-tag orange" v-if="activeConv.workTitle">关联作品</span>
            <span class="chat-tag yellow" v-if="activeConv.requirementTitle">需求待确认</span>
          </div>
          <strong class="chat-assoc-title">{{ activeConv.workTitle || activeConv.requirementTitle || '聊天' }}</strong>
        </div>

        <!-- 报价卡片 -->
        <div v-if="tradeEnabled && quotes.length" class="quote-area">
          <div v-for="q in quotes" :key="q.id" class="quote-item">
            <span><b>报价 ¥{{ q.price }}</b> <span class="quote-status">{{ q.status }}</span></span>
            <span class="quote-actions">
              <template v-if="q.status==='PENDING_CONFIRM' && q.receiverId===myId">
                <el-button size="small" type="success" @click="quoteAct(q.id,'accept')">接受</el-button>
                <el-button size="small" type="danger" @click="quoteAct(q.id,'reject')">拒绝</el-button>
              </template>
              <template v-if="q.status==='ACCEPTED' && q.senderId===myId">
                <el-button size="small" type="primary" @click="orderFromQuote(q)">下单</el-button>
              </template>
            </span>
          </div>
        </div>

        <div ref="msgList" class="chat-messages">
          <div v-for="m in messages" :key="m.id" class="msg-row" :class="{ 'msg-row-mine': m.senderId===myId }">
            <el-avatar :size="32" class="msg-avatar" :class="{ 'msg-avatar-mine': m.senderId===myId, 'msg-avatar-other': m.senderId!==myId }" :src="m.senderAvatar || undefined">{{ (m.senderName||'#')[0] }}</el-avatar>
            <div class="msg-content-wrapper">
              <div v-if="m.senderId!==myId" class="msg-sender-name">{{ m.senderName||'用户#'+m.senderId }}</div>
              <div class="msg-bubble" :class="{ 'msg-bubble-mine': m.senderId===myId }">{{ m.content }}</div>
            </div>
          </div>
        </div>
        <span ref="bottomRef"></span>

        <div class="chat-input-row">
          <input v-model="msgText" placeholder="输入消息..." @keyup.enter="sendMsg" class="chat-input" />
          <el-button type="primary" @click="sendMsg">发送</el-button>
          <el-button v-if="tradeEnabled" @click="showQuoteDialog=true">发报价</el-button>
        </div>
      </template>
      <el-empty v-else description="选择一个会话开始聊天" :image-size="80" class="chat-empty" />
    </section>

    <!-- ═══ 右：沟通摘要 ═══ -->
    <aside class="panel summary-panel">
      <h3>沟通摘要</h3>
      <div class="mini-list">
        <div class="mini-list-item">
          <span>关联内容</span>
          <strong class="price">{{ activeConv?.workTitle || activeConv?.requirementTitle || '—' }}</strong>
        </div>
        <div class="mini-list-item">
          <span>沟通状态</span>
          <strong class="price">进行中</strong>
        </div>
        <div class="mini-list-item">
          <span>需求范围</span>
          <strong class="price">待确认</strong>
        </div>
        <div class="mini-list-item">
          <span>下一步</span>
          <strong class="price">补充说明</strong>
        </div>
      </div>
      <div class="trust-box">
        提示：聊天承载作品咨询、需求确认和系统通知。
      </div>
    </aside>

    <!-- 发报价弹窗 -->
    <el-dialog v-if="tradeEnabled" v-model="showQuoteDialog" title="发起报价" width="360px">
      <el-form label-position="top">
        <el-form-item label="金额">
          <el-input v-model="quotePrice" type="number" placeholder="¥" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showQuoteDialog=false">取消</el-button>
        <el-button type="primary" @click="sendQuote">发送报价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'
import { unreadCount } from '../stores/unread'
import { tradeEnabled } from '../stores/tradeMode'

const convs = ref([])
const activeConv = ref(null)
const messages = ref([])
const quotes = ref([])
const msgText = ref('')
const API = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
const route = useRoute()
const myId = ref(sessionStorage.getItem('myId') || null)

let ws = null
const connectWs = () => {
  if (ws) try { ws.close() } catch {}
  const uid = myId.value; if (!uid) return
  const token = sessionStorage.getItem('appToken')
  const wsBase = import.meta.env.VITE_WS_URL || 'ws://127.0.0.1:8080'
  ws = new WebSocket(wsBase + '/ws/'+uid+'?token='+token)
  ws.onmessage = (e) => {
    try {
      const data = JSON.parse(e.data)
      if (data.type === 'message' && activeConv.value) openConv(activeConv.value)
      if (data.type === 'notification') { convList(); unreadCount.value++ }
    } catch {}
  }
  ws.onclose = () => { setTimeout(connectWs, 5000) }
}

const convList = async () => {
  try { const res = await request.get('/app/conversation/list?page=1&size=30'); if (res.code === 1) convs.value = res.data?.records || [] } catch {}
}

onMounted(async () => {
  connectWs()
  await convList()
  if (route.params.id) {
    const c = convs.value.find(c => String(c.id) === route.params.id)
    if (c) openConv(c)
  }
})
const showQuoteDialog = ref(false)
const quotePrice = ref('')
const msgList = ref(null)
const bottomRef = ref(null)

const scrollBottom = () => { nextTick(() => { if (bottomRef.value) bottomRef.value.scrollIntoView({ block: 'end' }) }) }
watch(messages, scrollBottom)

const openConv = async (c) => {
  activeConv.value = c
  try {
    const [msgRes, quoteRes] = await Promise.all([
      request.get(`/app/message/${c.id}?page=1&size=100`),
      request.get(`/app/quote/by-conversation/${c.id}`).catch(() => ({ data: [] }))
    ])
    if (msgRes.code === 1) messages.value = [...(msgRes.data || [])].reverse()
    if (quoteRes.code === 1) quotes.value = quoteRes.data || []
    await request.put(`/app/message/${c.id}/read`)
    scrollBottom()
  } catch {}
}

const sendMsg = async () => {
  if (!msgText.value.trim() || !activeConv.value) return
  try { await request.post('/app/message', { conversationId: activeConv.value.id, content: msgText.value.trim() }); msgText.value = ''; openConv(activeConv.value); scrollBottom() } catch {}
}

const sendQuote = async () => {
  if (!quotePrice.value || !activeConv.value) return
  try { await request.post('/app/quote', { conversationId: activeConv.value.id, price: parseFloat(quotePrice.value) }); showQuoteDialog.value = false; openConv(activeConv.value) } catch {}
}

const quoteAct = async (id, action) => {
  try { await request.put(`/app/quote/${id}/${action}`); openConv(activeConv.value) } catch {}
}

const deleteConv = async (id) => {
  try { await ElMessageBox.confirm('确认删除会话及全部消息？', '删除会话', { type: 'warning' }) } catch { return }
  await request.delete(`/app/conversation/${id}`)
  if (activeConv.value?.id === id) { activeConv.value = null; messages.value = [] }
  await convList()
}

const orderFromQuote = async (q) => {
  const deposit = prompt('定金金额（总价¥'+q.price+'）:', String(Math.round(q.price*0.3)))
  if (!deposit) return
  const finalAmount = q.price - parseFloat(deposit)
  try {
    const res = await request.post('/app/order', { sourceType: 'QUOTE', sourceId: q.id, deposit: parseFloat(deposit), finalAmount, totalAmount: q.price })
    if (res.code === 1) ElMessage.success('订单创建成功！到我的订单查看')
  } catch {}
}
</script>

<style scoped>
/* ═══ Layout: 3-column ═══ */
.message-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr) 280px;
  gap: 16px;
  min-height: calc(100vh - 156px);
}
.panel {
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 24px;
  background: #fff;
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.panel h3 {
  margin: 0 0 14px;
  font-family: var(--font-mono);
  letter-spacing: -.03em;
  font-size: 16px;
}

/* ═══ Left: Conversations ═══ */
.conv-panel { overflow-y: auto; }
.conversation {
  position: relative;
  padding: 12px;
  border-radius: 16px;
  border: 1px solid var(--line);
  margin-bottom: 8px;
  background: #fff;
  cursor: pointer;
  transition: border-color .15s, background .15s;
}
.conversation:hover { border-color: var(--border-orange); background: #fffaf5; }
.conversation.active {
  border-color: var(--border-orange);
  background: var(--cream);
}
.conversation strong { display: block; font-size: 13px; }
.conversation p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: calc(100% - 40px);
}
.conv-badge {
  position: absolute;
  top: 10px; right: 10px;
  min-width: 20px; height: 20px;
  padding: 0 6px;
  display: inline-flex; align-items: center; justify-content: center;
  border-radius: 999px;
  color: #fff; background: var(--brand);
  font-size: 10px; font-family: var(--font-mono); font-weight: 900;
}
.conv-del {
  position: absolute;
  bottom: 8px; right: 10px;
  color: var(--text-muted);
  font-size: 15px; cursor: pointer;
}
.conv-del:hover { color: #e53935; }

/* ═══ Center: Chat ═══ */
.chat-panel { padding: 0; }
.chat-topbar {
  padding: 14px 18px;
  border-bottom: 1px solid var(--border-light);
}
.chat-assoc-title {
  font-family: var(--font-mono);
  font-size: 15px;
  letter-spacing: -.03em;
}
.tag-row { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }
.chat-tag {
  display: inline-flex; align-items: center;
  min-height: 22px; padding: 0 8px;
  border: 1px solid var(--line);
  border-radius: 999px;
  font-size: 11px; font-weight: 800; font-family: var(--font-mono);
}
.chat-tag.orange { color: var(--brand-strong); background: var(--brand-soft); border-color: var(--border-orange); }
.chat-tag.yellow { color: #92400e; background: #fffbeb; border-color: #fde68a; }

.quote-area {
  padding: 8px 18px;
  border-bottom: 1px solid var(--border-light);
  background: #fffbeb;
}
.quote-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 4px 0; font-size: 13px; color: var(--text-primary);
}
.quote-status { color: var(--text-secondary); font-size: 11px; margin-left: 8px; font-family: var(--font-mono); }
.quote-actions { display: flex; gap: 4px; }

/* Messages */
.chat-messages {
  flex: 1; overflow-y: auto;
  padding: 16px;
  display: flex; flex-direction: column; gap: 14px;
  background: #fffaf7;
}
.msg-row { display: flex; align-items: flex-end; gap: 8px; max-width: 80%; align-self: flex-start; }
.msg-row-mine { flex-direction: row-reverse; align-self: flex-end; }
.msg-avatar { flex-shrink: 0; font-size: 12px; border-radius: 16px; background: #bfb8b0; }
.msg-avatar-mine { background: var(--brand); }
.msg-avatar-other { background: var(--text-muted); }
.msg-content-wrapper { min-width: 0; }
.msg-sender-name { font-size: 11px; color: var(--text-muted); margin-bottom: 2px; font-family: var(--font-mono); }
.msg-bubble {
  padding: 10px 14px; border-radius: 18px;
  font-size: 13px; line-height: 1.5;
  background: #f3f4f6; color: var(--text-primary);
  word-break: break-word;
}
.msg-bubble-mine { background: var(--brand); color: #fff; }

/* Chat input */
.chat-input-row {
  padding: 12px 16px;
  border-top: 1px solid var(--border-light);
  display: flex; gap: 8px; align-items: center;
  background: #fff;
}
.chat-input {
  flex: 1; min-width: 0;
  height: 38px;
  border: 1px solid var(--border-orange);
  border-radius: 999px;
  padding: 0 16px;
  outline: 0;
  font-family: var(--font-body); font-size: 13px;
}
.chat-input:focus { border-color: var(--brand); }
.chat-empty { margin: auto; }

/* ═══ Right: Summary ═══ */
.summary-panel { overflow-y: auto; }
.mini-list { display: grid; gap: 10px; }
.mini-list-item {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: #fff;
  display: flex; align-items: center; justify-content: space-between;
  gap: 10px; font-size: 12px;
}
.mini-list-item span { color: var(--text-secondary); }
.price { color: var(--brand-strong); font-family: var(--font-mono); font-weight: 900; font-size: 12px; }
.trust-box {
  margin-top: 14px; padding: 12px;
  border-radius: 18px; background: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e; font-size: 11px; line-height: 1.6;
}

@media (max-width: 1120px) { .message-layout { grid-template-columns: 1fr; } .conv-panel, .summary-panel { max-height: 300px; } }
@media (max-width: 780px) { .message-layout { grid-template-columns: 1fr; } }
</style>
