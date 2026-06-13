<template>
  <el-card shadow="never" style="border-radius:12px">
    <div v-loading="loading">
      <div v-if="list.length" style="margin-bottom:12px">
        <el-checkbox v-model="selectAll" @change="toggleAll" style="margin-right:12px">全选</el-checkbox>
        <el-button size="small" type="danger" plain @click="batchDelete" :disabled="!selected.length">删除选中 ({{selected.length}})</el-button>
      </div>
      <div v-for="n in list" :key="n.id" style="padding:12px 0;border-bottom:1px solid #f5f2ee;display:flex;align-items:center;gap:12px"
        :style="{opacity:n.isRead?0.55:1}">
        <el-checkbox :model-value="selected.includes(n.id)" @change="toggle(n.id)" />
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
import { unreadCount } from '../stores/unread'
const list=ref([]),loading=ref(false),selected=ref([]),selectAll=ref(false)
const tagType=t=>({AUDIT:'warning',MESSAGE:'primary',QUOTE:'success',ORDER:'',COMMENT:'info',FAVORITE:'danger'}[t]||'')
const read=async(id)=>{await request.put(`/app/notification/${id}/read`);const i=list.value.find(n=>n.id===id);if(i){i.isRead=1;unreadCount.value=Math.max(0,unreadCount.value-1)}}
const toggle=(id)=>{
  const idx=selected.value.indexOf(id)
  if(idx>=0) selected.value.splice(idx,1); else selected.value.push(id)
  selectAll.value = selected.value.length === list.value.length
}
const toggleAll=(val)=>{
  selected.value = val ? list.value.map(n=>n.id) : []
}
const batchDelete=async()=>{
  if(!selected.value.length) return
  await request.delete('/app/notification/batch', { data: selected.value })
  selected.value=[]; selectAll.value=false
  try{const r=await request.get('/app/notification/list?page=1&size=50');if(r.code===1)list.value=r.data||[]}catch{}
}
onMounted(async()=>{loading.value=true;try{const r=await request.get('/app/notification/list?page=1&size=50');if(r.code===1)list.value=r.data||[]}finally{loading.value=false}})
</script>
