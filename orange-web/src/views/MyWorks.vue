<template>
  <div class="my-works-page">
    <el-tabs v-model="tab" class="works-tabs">
      <el-tab-pane label="我的作品" name="works"/>
      <el-tab-pane label="我的需求" name="reqs"/>
    </el-tabs>
    <el-table :data="list" size="small" v-loading="loading" empty-text="暂无内容" class="works-table">
      <el-table-column prop="id" label="ID" width="50"/>
      <el-table-column prop="title" label="标题" show-overflow-tooltip/>
      <el-table-column label="状态" width="100">
        <template #default="{row}">
          <el-tag :type="st(row.auditStatus)" size="small">{{row.auditStatus}}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{row}">
          <el-button size="small" @click="$router.push('/edit/'+(tab==='works'?'work':'requirement')+'/'+row.id)">编辑</el-button>
          <el-button size="small" @click="act(row.id,'submit')" v-if="row.auditStatus==='DRAFT'||row.auditStatus==='REJECTED'">提交审核</el-button>
          <el-button size="small" @click="act(row.id,'visibility')">{{row.visibilityStatus}}</el-button>
          <el-button size="small" type="danger" @click="act(row.id,'delete')">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import request from '../api/request'
const tab=ref('works'),list=ref([]),loading=ref(false)
const st=s=>({APPROVED:'success',REJECTED:'danger',PENDING_REVIEW:'warning'}[s]||'info')
const load=async()=>{loading.value=true;const api=tab.value==='works'?'/app/work/my':'/app/requirement/my';try{const r=await request.get(api,{params:{page:1,size:50}});if(r.code===1)list.value=r.data?.records||[]}finally{loading.value=false}}
const act=async(id,action)=>{if(action==='delete'){try{await ElMessageBox.confirm('确认删除？')}catch{return}};const api=tab.value==='works'?`/app/work/${id}`:`/app/requirement/${id}`;if(action==='submit')await request.post(`${api}/submit`);else if(action==='visibility')await request.put(`${api}/visibility`);else if(action==='delete')await request.delete(api);load()}
watch(tab,load);onMounted(load)
</script>

<style scoped>
.my-works-page {
  background: #ffffff;
  border: 2px solid #f0ece6;
  border-radius: 2px;
  padding: 16px;
}

.works-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.works-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 2px;
  background-color: #f0ece6;
}

.works-tabs :deep(.el-tabs__item) {
  font-family: "Courier New", "SimHei", monospace;
  font-size: 13px;
  font-weight: 600;
  color: #9f9890;
  letter-spacing: 0.5px;
  height: 36px;
  line-height: 36px;
}

.works-tabs :deep(.el-tabs__item.is-active) {
  color: #f37021;
}

.works-tabs :deep(.el-tabs__active-bar) {
  background-color: #f37021;
  height: 2px;
}

.works-table {
  border: 2px solid #f0ece6;
  border-radius: 2px;
}

.works-table :deep(.el-table__header th) {
  background-color: #faf9f7;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  color: #3a302a;
  letter-spacing: 1px;
  border-bottom: 2px solid #f0ece6;
}

.works-table :deep(.el-table__body td) {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  color: #3a302a;
  border-bottom: 1px solid #f0ece6;
}

.works-table :deep(.el-table__row:hover > td) {
  background-color: #faf9f7;
}

.works-table :deep(.el-button) {
  font-family: "Courier New", "SimHei", monospace;
  border-radius: 0;
}

.works-table :deep(.el-button--small) {
  height: 28px;
  font-size: 11px;
}

.works-table :deep(.el-button--default) {
  background: #f37021;
  border-color: #f37021;
  color: #fff;
  box-shadow: 0 3px 0 rgba(197, 83, 18, 0.15);
}

.works-table :deep(.el-button--default:hover) {
  background: #c55312;
  border-color: #c55312;
}

.works-table :deep(.el-button--danger) {
  background: #e53935;
  border-color: #e53935;
  color: #fff;
  box-shadow: 0 3px 0 rgba(198, 40, 40, 0.15);
}

.works-table :deep(.el-button--danger:hover) {
  background: #c62828;
  border-color: #c62828;
}

.works-table :deep(.el-tag) {
  border-radius: 0;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 10px;
  font-weight: 700;
  height: 20px;
  line-height: 20px;
  padding: 0 6px;
  border: none;
}

.works-table :deep(.el-tag--success) {
  background: #7fb63c;
  color: #fff;
}

.works-table :deep(.el-tag--danger) {
  background: #e53935;
  color: #fff;
}

.works-table :deep(.el-tag--warning) {
  background: #f37021;
  color: #fff;
}

.works-table :deep(.el-tag--info) {
  background: #e5d9c3;
  color: #3a302a;
}

.works-table :deep(.el-loading-mask) {
  border-radius: 2px;
}
</style>
