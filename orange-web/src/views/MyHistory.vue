<template>
  <div class="history-page">
    <el-table :data="list" size="small" v-loading="loading" empty-text="暂无浏览记录" class="history-table">
      <el-table-column prop="targetType" label="类型" width="80">
        <template #default="{row}">
          <el-tag size="small">{{row.targetType}}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="targetTitle" label="标题" show-overflow-tooltip/>
      <el-table-column prop="createTime" label="时间" width="160"/>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../api/request'
const list=ref([]),loading=ref(false)
onMounted(async()=>{loading.value=true;try{const r=await request.get('/app/history?page=1&size=50');if(r.code===1)list.value=r.data?.records||[]}finally{loading.value=false}})
</script>

<style scoped>
.history-page {
  background: #ffffff;
  border: 2px solid #f0ece6;
  border-radius: 2px;
  padding: 16px;
}

.history-table {
  border: 2px solid #f0ece6;
  border-radius: 2px;
}

.history-table :deep(.el-table__header th) {
  background-color: #faf9f7;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  color: #3a302a;
  letter-spacing: 1px;
  border-bottom: 2px solid #f0ece6;
}

.history-table :deep(.el-table__body td) {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  color: #3a302a;
  border-bottom: 1px solid #f0ece6;
}

.history-table :deep(.el-table__row:hover > td) {
  background-color: #faf9f7;
}

.history-table :deep(.el-tag) {
  border-radius: 0;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 10px;
  font-weight: 700;
  height: 20px;
  line-height: 20px;
  padding: 0 6px;
  border: none;
  background: #e5d9c3;
  color: #3a302a;
}
</style>
