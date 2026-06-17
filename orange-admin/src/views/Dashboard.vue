<template>
  <div>
    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px">
      <span style="font-size:13px;color:#909399">快捷设置</span>
      <div style="display:flex;align-items:center;gap:16px">
        <span style="font-size:13px;color:#606266">交易模式</span>
        <el-switch v-model="tradeEnabled" @change="toggleTrade" active-text="开" inactive-text="关" />
      </div>
    </div>
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="4" v-for="c in visibleCards" :key="c.label">
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
import { ref, computed, onMounted } from 'vue'
import request from '../api/request'
import { tradeEnabled } from '../stores/tradeMode'

const cards = ref([
  { label: '用户总数', value: 0, trade: false }, { label: '作品总数', value: 0, trade: false },
  { label: '需求总数', value: 0, trade: false }, { label: '待审核', value: 0, trade: false },
  { label: '订单总数', value: 0, trade: true }, { label: '交易额', value: 0, trade: true }
])
const visibleCards = computed(() => tradeEnabled.value ? cards.value : cards.value.filter(c => !c.trade))
const topWorks = ref([])
const topCreators = ref([])

const toggleTrade = async (val) => {
  try { await request.put('/admin/config/trade-mode', { enabled: val }) } catch { tradeEnabled.value = !val }
}

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
    // 读取交易模式
    const tm = await request.get('/admin/config/trade-mode')
    if (tm.code === 1) tradeEnabled.value = tm.data.enabled
  } catch (e) {}
})
</script>
