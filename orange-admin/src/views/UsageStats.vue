<template>
  <div>
    <h3 style="margin:0 0 16px;font-weight:700">用量统计</h3>

    <!-- 概览卡片 -->
    <el-row :gutter="12" style="margin-bottom:16px" v-if="summary">
      <el-col :span="6"><el-card shadow="never" style="text-align:center"><div style="font-size:28px;font-weight:700;color:#e65100">{{ summary.totalConsumed }}</div><div style="font-size:12px;color:#909399">总消耗 segments</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" style="text-align:center"><div style="font-size:28px;font-weight:700;color:#67c23a">{{ summary.totalRecharged }}</div><div style="font-size:12px;color:#909399">总充值 segments</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" style="text-align:center"><div style="font-size:28px;font-weight:700;color:#909399">{{ summary.consumeCount }}</div><div style="font-size:12px;color:#909399">调用次数</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" style="text-align:center"><div style="font-size:28px;font-weight:700;color:#e6a23c">{{ summary.refundCount }}</div><div style="font-size:12px;color:#909399">退款次数</div></el-card></el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 按天统计 -->
      <el-col :span="14">
        <el-card shadow="never" v-loading="loading">
          <template #header><span style="font-weight:600">每日消耗（近 {{ days }} 天）</span></template>
          <el-table :data="daily" empty-text="暂无数据" size="small" max-height="400">
            <el-table-column prop="day" label="日期" width="110" />
            <el-table-column label="消耗(segments)" width="110">
              <template #default="{row}"><span style="color:#e65100">{{ row.consumed || 0 }}</span></template>
            </el-table-column>
            <el-table-column prop="consumes" label="调用次数" width="80" />
            <el-table-column label="退款" width="80">
              <template #default="{row}"><span style="color:#e6a23c">{{ row.refunds || 0 }}</span></template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 按模块统计 -->
      <el-col :span="10">
        <el-card shadow="never" v-loading="loading">
          <template #header><span style="font-weight:600">按模块消耗</span></template>
          <el-table :data="byModule" empty-text="暂无数据" size="small">
            <el-table-column prop="module" label="模块" show-overflow-tooltip />
            <el-table-column label="消耗" width="90">
              <template #default="{row}"><span style="color:#e65100">{{ row.total || 0 }}</span></template>
            </el-table-column>
            <el-table-column prop="count" label="次数" width="60" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '../api/request'

const loading = ref(false)
const days = ref(7)
const daily = ref([])
const byModule = ref([])

const summary = computed(() => {
  if (!daily.value.length) return null
  let totalConsumed = 0, totalRecharged = 0, consumeCount = 0, refundCount = 0
  for (const d of daily.value) {
    totalConsumed += d.consumed || 0
    totalRecharged += (d.recharged || 0)
    consumeCount += d.consumes || 0
    refundCount += d.refunds || 0
  }
  return { totalConsumed, totalRecharged, consumeCount, refundCount }
})

const load = async () => {
  loading.value = true
  try {
    const r = await request.get('/admin/stats/quota-usage', { params: { days: days.value } })
    daily.value = r.data?.daily || []
    byModule.value = r.data?.byModule || []
  } finally { loading.value = false }
}

onMounted(() => load())
</script>
