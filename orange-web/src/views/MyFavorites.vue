<template>
  <div class="favorites-page">
    <el-table :data="list" size="small" v-loading="loading" empty-text="暂无收藏" @row-click="goDetail" class="favorites-table clickable-table">
      <el-table-column label="类型" width="80">
        <template #default="{row}">
          <el-tag size="small">{{row.targetType==='WORK'?'作品':'需求'}}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="targetTitle" label="标题" show-overflow-tooltip/>
      <el-table-column label="操作" width="80">
        <template #default="{row}">
          <el-button size="small" type="danger" @click.stop="remove(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/request'
const router = useRouter()
const list=ref([]),loading=ref(false)
onMounted(async()=>{loading.value=true;try{const r=await request.get('/app/favorite/list?page=1&size=50');if(r.code===1)list.value=r.data?.records||[]}finally{loading.value=false}})
const goDetail = (row) => {
  if (row.targetType === 'WORK') router.push('/work/' + row.targetId)
  else if (row.targetType === 'REQUIREMENT') router.push('/requirement/' + row.targetId)
}
const remove=async(row)=>{await request.delete('/app/favorite/'+row.id);list.value=list.value.filter(f=>f.id!==row.id)}
</script>

<style scoped>
.favorites-page {
  background: #ffffff;
  border: 2px solid #f0ece6;
  border-radius: 2px;
  padding: 16px;
}

.favorites-table {
  border: 2px solid #f0ece6;
  border-radius: 2px;
}

.clickable-table :deep(.el-table__row) {
  cursor: pointer;
}

.favorites-table :deep(.el-table__header th) {
  background-color: #faf9f7;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  color: #3a302a;
  letter-spacing: 1px;
  border-bottom: 2px solid #f0ece6;
}

.favorites-table :deep(.el-table__body td) {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  color: #3a302a;
  border-bottom: 1px solid #f0ece6;
}

.favorites-table :deep(.el-table__row:hover > td) {
  background-color: #faf9f7;
}

.favorites-table :deep(.el-button) {
  font-family: "Courier New", "SimHei", monospace;
  border-radius: 0;
}

.favorites-table :deep(.el-button--small) {
  height: 28px;
  font-size: 11px;
}

.favorites-table :deep(.el-button--danger) {
  background: #e53935;
  border-color: #e53935;
  color: #fff;
  box-shadow: 0 3px 0 rgba(198, 40, 40, 0.15);
}

.favorites-table :deep(.el-button--danger:hover) {
  background: #c62828;
  border-color: #c62828;
}

.favorites-table :deep(.el-tag) {
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
