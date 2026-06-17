<template>
  <div v-loading="loading">
    <el-page-header @back="$router.back()" style="margin-bottom:16px">
      <template #content><span style="font-weight:600">{{ detail.title || '作品详情' }}</span></template>
    </el-page-header>
    <el-card v-if="detail.id" shadow="never" style="border-radius:12px">
      <img v-if="detail.coverUrl" :src="API + detail.coverUrl" style="width:100%;max-height:360px;object-fit:cover;border-radius:8px;margin-bottom:16px" />
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="作者">{{ detail.ownerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审核状态">
          <el-tag :type="detail.auditStatus==='APPROVED'?'success':detail.auditStatus==='REJECTED'?'danger':'warning'" size="small">{{ detail.auditStatus }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="可见性">{{ detail.visibilityStatus }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="简介" :span="2">{{ detail.summary || '无' }}</el-descriptions-item>
        <el-descriptions-item label="详情" :span="2">
          <div style="white-space:pre-wrap;line-height:1.8;max-height:400px;overflow:auto">{{ detail.detail || '无' }}</div>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.demoUrl" label="演示链接" :span="2">
          <a :href="detail.demoUrl" target="_blank" style="color:#409EFF">{{ detail.demoUrl }}</a>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.tags" label="标签">{{ detail.tags }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="detail.saleOptions?.length" style="margin-top:16px">
        <el-tag v-for="so in detail.saleOptions" :key="so.id" type="warning" style="margin-right:8px">¥{{ so.price }} {{ so.saleType }}</el-tag>
      </div>
    </el-card>
    <el-empty v-else-if="!loading" description="未找到该作品" />
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import request from '../api/request'
const API = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
const route = useRoute()
const detail = ref({})
const loading = ref(false)
onMounted(async () => {
  loading.value = true
  try {
    const type = route.params.type || 'work'
    const res = type === 'work'
      ? await request.get('/admin/work/' + route.params.id)
      : await request.get('/admin/requirement/' + route.params.id)
    if (res.code === 1) detail.value = res.data || {}
  } catch {} finally { loading.value = false }
})
</script>
