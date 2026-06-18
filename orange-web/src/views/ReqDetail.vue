<template>
  <div v-loading="loading">
    <el-card v-if="req" shadow="never" style="border-radius:12px">
      <h2 style="margin:0 0 8px">{{req.title}}</h2>
      <p style="color:#909399;margin:0 0 8px">by {{req.publisherName||'#'+req.publisherId}} · {{req.auditStatus}} · {{req.createTime}}</p>
      <div style="color:#6b6b6b;line-height:1.8" v-html="req.detail||'暂无详情'"></div>
      <div v-if="req.budgetMin||req.budgetMax" style="margin-top:8px"><el-tag type="warning">预算 ¥{{req.budgetMin}}-{{req.budgetMax}}</el-tag></div>
      <div style="margin-top:16px"><el-button type="primary" :loading="contacting" @click="contactPublisher" style="background:#e65100;border-color:#e65100">联系发布者</el-button></div>
    </el-card>
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
onMounted(async()=>{loading.value=true;try{const r=await request.get('/app/requirement/'+route.params.id);if(r.code===1)req.value=r.data}catch{}finally{loading.value=false}})
</script>
