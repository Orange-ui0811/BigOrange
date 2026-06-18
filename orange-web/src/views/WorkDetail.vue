<template>
  <div v-loading="loading">
    <el-card v-if="work" shadow="never" style="border-radius:12px">
      <img v-if="work.coverUrl" :src="API+work.coverUrl" style="width:100%;max-height:400px;object-fit:cover;border-radius:12px;margin-bottom:16px" />
      <h2 style="margin:0 0 8px">{{work.title}}</h2>
      <p style="color:#909399;margin:0 0 8px">by {{work.ownerName||'#'+work.ownerId}} · {{work.auditStatus}} · {{work.createTime}}</p>
      <div style="color:#6b6b6b;line-height:1.8" v-html="work.detail||work.summary||'暂无详情'"></div>
      <div v-if="work.demoUrl" style="margin-top:12px;padding:12px;background:#f5f9ff;border-radius:8px;border:1px solid #dce8f5">
        <span style="font-size:13px;color:#1565c0">🔗 在线演示：</span>
        <a :href="work.demoUrl" target="_blank" style="font-size:13px;color:#e65100;font-weight:600;margin-left:4px">点击体验 →</a>
        <span style="font-size:11px;color:#909399;margin-left:8px">（源码不可见）</span>
      </div>
      <div v-if="tradeEnabled && work.saleOptions?.length" style="margin-top:16px;padding-top:16px;border-top:1px solid #f0ece6">
        <h4>售卖项</h4>
        <el-tag v-for="(so,i) in work.saleOptions" :key="i" style="margin:4px 8px 4px 0" type="warning">¥{{so.price}} {{so.saleType}}</el-tag>
      </div>
      <div style="margin-top:16px;display:flex;gap:8px">
        <el-button type="primary" :loading="contacting" @click="contactAuthor" style="background:#e65100;border-color:#e65100">联系作者</el-button>
        <el-button @click="fav" :type="favd?'success':''">{{favd?'已收藏':'收藏'}}</el-button>
      </div>
    </el-card>
    <!-- 评论区 -->
    <el-card shadow="never" style="border-radius:12px;margin-top:16px">
      <h4 style="margin:0 0 12px">评论 ({{comments.length}})</h4>
      <div v-for="c in comments" :key="c.id" style="padding:8px 0;border-bottom:1px solid #f5f2ee;font-size:13px">
        <div style="display:flex;justify-content:space-between;align-items:flex-start">
          <div style="flex:1">
            <span style="font-weight:500;color:#2c2c2c">{{c.accountName||'#'+c.accountId}}</span>
            <span style="color:#909399;font-size:11px;margin-left:8px">{{c.createTime}}</span>
            <div style="color:#6b6b6b;margin-top:2px">{{c.content}}</div>
          </div>
          <el-button v-if="canDelete(c)" size="small" type="danger" text @click="delCmt(c.id)" style="flex-shrink:0;margin-left:8px">删除</el-button>
        </div>
      </div>
      <div style="margin-top:12px;display:flex;gap:8px">
        <el-input v-model="cmtText" placeholder="写评论..." size="small" @keyup.enter="sendCmt"/>
        <el-button size="small" type="primary" @click="sendCmt">发表</el-button>
      </div>
    </el-card>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../api/request'
import { tradeEnabled } from '../stores/tradeMode'
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
