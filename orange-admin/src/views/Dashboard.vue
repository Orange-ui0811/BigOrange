<template>
  <div>
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="4" v-for="c in cards" :key="c.label">
        <el-card shadow="never" style="border-radius:10px;text-align:center;padding:8px 0">
          <div style="font-size:28px;font-weight:700;color:#303133">{{ c.value }}</div>
          <div style="font-size:12px;color:#909399;margin-top:4px">{{ c.label }}</div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" style="border-radius:10px" header="热门作品 Top5">
          <el-table :data="topWorks" size="small" stripe>
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="title" label="作品" />
            <el-table-column prop="favoriteCount" label="收藏数" width="80" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" style="border-radius:10px" header="活跃创作者 Top5">
          <el-table :data="topCreators" size="small" stripe>
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="username" label="用户" />
            <el-table-column prop="workCount" label="作品数" width="80" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../api/request'

const cards = ref([
  { label: '用户总数', value: 0 }, { label: '作品总数', value: 0 },
  { label: '需求总数', value: 0 }, { label: '待审核', value: 0 },
  { label: '订单总数', value: 0 }, { label: '交易额', value: 0 }
])
const topWorks = ref([])
const topCreators = ref([])

onMounted(async () => {
  try {
    const dash = await request.get('/admin/statistics/dashboard')
    if (dash.code === 1) {
      cards.value[0].value = dash.data.totalUsers || 0
      cards.value[1].value = dash.data.totalWorks || 0
      cards.value[2].value = dash.data.totalRequirements || 0
      cards.value[3].value = dash.data.pendingAudits || 0
      cards.value[4].value = dash.data.totalOrders || 0
      cards.value[5].value = dash.data.todayRevenue || 0
    }
    const tw = await request.get('/admin/statistics/top-works?limit=5')
    if (tw.code === 1) topWorks.value = tw.data
    const tc = await request.get('/admin/statistics/top-creators?limit=5')
    if (tc.code === 1) topCreators.value = tc.data
  } catch (e) {}
})
</script>
