<template>
  <el-table :data="list" size="small" v-loading="loading" empty-text="暂无收藏">
    <el-table-column label="类型" width="80"><template #default="{row}"><el-tag size="small">{{row.targetType}}</el-tag></template></el-table-column>
    <el-table-column prop="targetTitle" label="标题" show-overflow-tooltip/>
    <el-table-column label="操作" width="80"><template #default="{row}"><el-button size="small" type="danger" @click="remove(row)">取消</el-button></template></el-table-column>
  </el-table>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import request from '../api/request'
const list=ref([]),loading=ref(false)
onMounted(async()=>{loading.value=true;try{const r=await request.get('/app/favorite/list?page=1&size=50');if(r.code===1)list.value=r.data?.records||[]}finally{loading.value=false}})
const remove=async(row)=>{await request.delete('/app/favorite/'+row.id);list.value=list.value.filter(f=>f.id!==row.id)}
</script>
