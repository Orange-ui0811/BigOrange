<template>
  <el-card shadow="never" style="border-radius:12px">
    <div v-loading="loading">
      <div v-for="n in list" :key="n.id" style="padding:12px 0;border-bottom:1px solid #f5f2ee;display:flex;align-items:center;gap:12px"
        :style="{opacity:n.isRead?0.55:1}">
        <el-tag :type="tagType(n.type)" size="small">{{ n.type }}</el-tag>
        <span style="flex:1;font-size:13px;color:#2c2c2c">{{ n.message }}</span>
        <span style="font-size:11px;color:#909399">{{ n.createTime }}</span>
        <el-button v-if="!n.isRead" size="small" text @click="read(n.id)">标为已读</el-button>
      </div>
      <el-empty v-if="!loading && !list.length" description="暂无通知" :image-size="60" />
    </div>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../api/request'
const list=ref([]),loading=ref(false)
const tagType=t=>({AUDIT:'warning',MESSAGE:'primary',QUOTE:'success',ORDER:'',COMMENT:'info',FAVORITE:'danger'}[t]||'')
const read=async(id)=>{await request.put(`/app/notification/${id}/read`);const i=list.value.find(n=>n.id===id);if(i)i.isRead=1}
onMounted(async()=>{loading.value=true;try{const r=await request.get('/app/notification/list?page=1&size=50');if(r.code===1)list.value=r.data||[]}finally{loading.value=false}})
</script>
