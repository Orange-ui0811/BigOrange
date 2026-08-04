<template>
  <div class="recommendations-page">
    <header class="workspace-heading">
      <div><span>PROJECT INTELLIGENCE</span><h2>项目推荐</h2><p>分别管理“容易开始”和“有职业证明力”两套榜单，共享仓库事实与抓取任务。</p></div>
    </header>
    <el-tabs v-model="activeTab" class="workspace-tabs">
      <el-tab-pane label="新手友好榜单" name="beginner"><BeginnerProjects /></el-tab-pane>
      <el-tab-pane label="职业成长榜单" name="career"><CareerProjects /></el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import BeginnerProjects from './BeginnerProjects.vue'
import CareerProjects from './CareerProjects.vue'
const activeTab = ref('beginner')
</script>

<style scoped>
.recommendations-page{display:grid;gap:16px}.workspace-heading{padding:18px 20px;border-left:4px solid #e65100;background:#fff}.workspace-heading span{color:#e65100;font:700 11px/1 monospace}.workspace-heading h2{margin:7px 0 5px;color:#303133;font-size:22px}.workspace-heading p{margin:0;color:#747c88;font-size:13px;line-height:1.6}.workspace-tabs{padding:0 16px 16px;border:1px solid #e4e7ed;border-radius:7px;background:#fff}.workspace-tabs :deep(.el-tabs__content){overflow:visible;padding-top:8px}
</style>
