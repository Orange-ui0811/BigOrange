<template>
  <div><el-tabs v-model="tab"><el-tab-pane label="我的作品" name="works"/><el-tab-pane label="我的需求" name="reqs"/></el-tabs>
    <el-table :data="list" size="small" v-loading="loading" empty-text="暂无内容">
      <el-table-column prop="id" label="ID" width="50"/>
      <el-table-column prop="title" label="标题" show-overflow-tooltip/>
      <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="st(row.auditStatus)" size="small">{{row.auditStatus}}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="260"><template #default="{row}">
        <el-button size="small" @click="$router.push('/edit/'+(tab==='works'?'work':'requirement')+'/'+row.id)">编辑</el-button>
        <el-button size="small" @click="act(row.id,'submit')" v-if="row.auditStatus==='DRAFT'||row.auditStatus==='REJECTED'">提交审核</el-button>
        <el-button size="small" @click="act(row.id,'visibility')">{{row.visibilityStatus}}</el-button>
        <el-button size="small" type="danger" @click="act(row.id,'delete')">删除</el-button>
      </template></el-table-column>
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
