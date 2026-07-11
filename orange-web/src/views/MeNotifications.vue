<template>
  <el-card shadow="never" class="notif-card">
    <div v-loading="loading" class="notif-body">
      <div v-if="list.length" class="notif-toolbar">
        <el-checkbox v-model="selectAll" @change="toggleAll" class="notif-select-all">全选</el-checkbox>
        <el-button size="small" type="danger" plain @click="batchDelete" :disabled="!selected.length">删除选中 ({{selected.length}})</el-button>
      </div>
      <div v-for="n in list" :key="n.id" class="notif-item" :class="{ 'notif-read': n.isRead }">
        <el-checkbox :model-value="selected.includes(n.id)" @change="toggle(n.id)" />
        <el-tag :type="tagType(n.type)" size="small">{{ n.type }}</el-tag>
        <span class="notif-message">{{ n.message }}</span>
        <span class="notif-time">{{ n.createTime }}</span>
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

<style scoped>
.notif-card {
  border: 2px solid #f0ece6;
  border-radius: 2px;
  box-shadow: none;
}

.notif-card :deep(.el-card__body) {
  padding: 16px;
}

.notif-body {
  min-height: 100px;
}

.notif-toolbar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
}

.notif-select-all {
  margin-right: 12px;
}

.notif-item {
  padding: 12px 0;
  border-bottom: 1px solid #f0ece6;
  display: flex;
  align-items: center;
  gap: 12px;
  transition: opacity 0.2s;
}

.notif-item:last-child {
  border-bottom: none;
}

.notif-read {
  opacity: 0.55;
}

.notif-message {
  flex: 1;
  font-size: 13px;
  color: #3a302a;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

.notif-time {
  font-size: 11px;
  color: #9f9890;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  white-space: nowrap;
}

.notif-toolbar :deep(.el-button) {
  font-family: "Courier New", "SimHei", monospace;
  border-radius: 0;
}

.notif-toolbar :deep(.el-button--danger.is-plain) {
  color: #e53935;
  border-color: #e53935;
  background: transparent;
}

.notif-toolbar :deep(.el-button--danger.is-plain:hover) {
  background: #e53935;
  color: #fff;
}

.notif-item :deep(.el-button) {
  font-family: "Courier New", "SimHei", monospace;
  border-radius: 0;
}

.notif-item :deep(.el-button--small) {
  height: 28px;
  font-size: 11px;
}

.notif-item :deep(.el-tag) {
  border-radius: 0;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 10px;
  font-weight: 700;
  height: 20px;
  line-height: 20px;
  padding: 0 6px;
  border: none;
  flex-shrink: 0;
}

.notif-item :deep(.el-tag--warning) {
  background: #f37021;
  color: #fff;
}

.notif-item :deep(.el-tag--primary) {
  background: #1565c0;
  color: #fff;
}

.notif-item :deep(.el-tag--success) {
  background: #7fb63c;
  color: #fff;
}

.notif-item :deep(.el-tag--info) {
  background: #e5d9c3;
  color: #3a302a;
}

.notif-item :deep(.el-tag--danger) {
  background: #e53935;
  color: #fff;
}

.notif-item :deep(.el-checkbox) {
  flex-shrink: 0;
}
</style>
