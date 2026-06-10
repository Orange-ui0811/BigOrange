<template>
  <el-table :data="list" size="small" v-loading="loading" empty-text="暂无浏览记录">
    <el-table-column prop="targetType" label="类型" width="80"><template #default="{row}"><el-tag size="small">{{row.targetType}}</el-tag></template></el-table-column>
    <el-table-column prop="targetTitle" label="标题" show-overflow-tooltip/>
    <el-table-column prop="createTime" label="时间" width="160"/>
  </el-table>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import request from '../api/request'
const list=ref([]),loading=ref(false)
onMounted(async()=>{loading.value=true;try{const r=await request.get('/app/history?page=1&size=50');if(r.code===1)list.value=r.data?.records||[]}finally{loading.value=false}})
</script>
