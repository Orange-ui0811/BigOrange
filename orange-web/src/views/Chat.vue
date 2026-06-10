<template>
  <div style="display:flex;height:calc(100vh - 136px);background:#fff;border-radius:12px;overflow:hidden;border:1px solid #f0ece6">
    <!-- 会话列表 -->
    <div style="width:280px;border-right:1px solid #f0ece6;overflow-y:auto;flex-shrink:0">
      <div style="padding:16px;font-weight:600;font-size:14px;border-bottom:1px solid #f0ece6">会话</div>
      <div v-for="c in convs" :key="c.id" :style="{padding:'14px 16px',borderBottom:'1px solid #f5f2ee',cursor:'pointer',background:activeConv===c.id?'#fff7f0':''}"
        @click="openConv(c)">
        <div style="display:flex;align-items:center;justify-content:space-between">
          <span style="font-weight:500;font-size:13px">{{ c.receiverName || c.initiatorName || '未知' }}</span>
          <span style="display:flex;align-items:center;gap:6px">
            <span v-if="c.unreadCount" style="background:#e65100;color:#fff;font-size:10px;padding:1px 6px;border-radius:8px">{{ c.unreadCount }}</span>
            <span @click.stop="deleteConv(c.id)" style="color:#c0c4cc;font-size:16px;line-height:1;cursor:pointer" title="删除会话">&times;</span>
          </span>
        </div>
        <div style="font-size:11px;color:#909399;margin-top:4px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ c.lastMessage || c.workTitle || c.requirementTitle || '新会话' }}</div>
      </div>
      <el-empty v-if="!convs.length" description="暂无会话" :image-size="60" />
    </div>
    <!-- 消息区 -->
    <div style="flex:1;display:flex;flex-direction:column">
      <template v-if="activeConv">
        <div style="padding:12px 16px;font-weight:600;font-size:14px;border-bottom:1px solid #f0ece6;background:#faf9f7">
          {{ activeConv.receiverName || activeConv.initiatorName }}
          <span style="font-weight:400;font-size:12px;color:#909399;margin-left:8px" v-if="activeConv.workTitle">关联：{{ activeConv.workTitle }}</span>
        </div>
        <!-- 报价卡片置顶 -->
        <div v-if="quotes.length" style="padding:8px 16px;border-bottom:1px solid #f0ece6;background:#fff7f0">
          <div v-for="q in quotes" :key="q.id" style="display:flex;align-items:center;justify-content:space-between;padding:6px 0;font-size:13px">
            <span><b>报价 ¥{{ q.price }}</b> <span style="color:#909399;font-size:11px;margin-left:8px">{{ q.status }}</span></span>
            <span style="display:flex;gap:6px">
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
        <div ref="msgList" style="flex:1;overflow-y:auto;padding:16px;display:flex;flex-direction:column;gap:12px">
          <div v-for="m in messages" :key="m.id" :style="{alignSelf:m.senderId===myId?'flex-end':'flex-start',maxWidth:'65%'}">
            <div v-if="m.senderId!==myId" style="font-size:10px;color:#909399;margin-bottom:2px;padding-left:6px">{{ m.senderName||'用户#'+m.senderId }}</div>
            <div :style="{background:m.senderId===myId?'#e65100':'#f5f2ee',color:m.senderId===myId?'#fff':'#2c2c2c',padding:'8px 14px',borderRadius:m.senderId===myId?'16px 16px 4px 16px':'16px 16px 16px 4px',fontSize:'13px',lineHeight:'1.5'}">{{ m.content }}</div>
          </div>
        </div>
        <span ref="bottomRef"></span>
        <div style="padding:12px 16px;border-top:1px solid #f0ece6;display:flex;gap:8px;align-items:center;background:#faf9f7">
          <el-input v-model="msgText" placeholder="输入消息..." size="default" @keyup.enter="sendMsg" />
          <el-button size="default" type="primary" @click="sendMsg" style="background:#e65100;border-color:#e65100">发送</el-button>
          <el-button size="default" @click="showQuoteDialog=true">发报价</el-button>
          <!-- <el-button size="default" type="danger" plain @click="clearChat">清除聊天</el-button> -->
        </div>
      </template>
      <el-empty v-else description="选择一个会话开始聊天" :image-size="100" style="margin:auto" />
    </div>
    <!-- 发报价弹窗 -->
    <el-dialog v-model="showQuoteDialog" title="发起报价" width="360px">
      <el-form label-position="top"><el-form-item label="金额"><el-input v-model="quotePrice" type="number" placeholder="¥" /></el-form-item></el-form>
      <template #footer><el-button @click="showQuoteDialog=false">取消</el-button><el-button type="primary" @click="sendQuote">发送报价</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, nextTick, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'

const convs = ref([])
const activeConv = ref(null)
const messages = ref([])
const quotes = ref([])
const msgText = ref('')
const route = useRoute()
const myId = ref(localStorage.getItem('myId') || null)

onMounted(async () => {
  try { const res = await request.get('/app/conversation/list?page=1&size=30'); if (res.code === 1) convs.value = res.data?.records || [] } catch {}
  // 如果路由带 id，自动打开对应会话
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
  try { const res = await request.get('/app/conversation/list?page=1&size=30'); if (res.code === 1) convs.value = res.data?.records || [] } catch {}
}

const clearChat = async () => {
  if (!activeConv.value) return
  try { await ElMessageBox.confirm('确认清除聊天记录？仅清除你自己的记录，对方不受影响', '清除聊天', { type: 'warning' }) } catch { return }
  await request.put(`/app/conversation/${activeConv.value.id}/hide`)
  activeConv.value = null; messages.value = []
  try { const res = await request.get('/app/conversation/list?page=1&size=30'); if (res.code === 1) convs.value = res.data?.records || [] } catch {}
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
