<template>
  <div>
    <el-table :data="list" size="small" stripe v-loading="loading" empty-text="暂无订单">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="buyerName" label="买方" width="100" />
      <el-table-column prop="sellerName" label="卖方" width="100" />
      <el-table-column prop="workTitle" label="作品" min-width="120" show-overflow-tooltip />
      <el-table-column label="金额" width="90">
        <template #default="{row}">{{ '¥'+row.totalAmount }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{row}">
          <el-tag :type="orderStatusType(row.orderStatus)" size="small">{{ row.orderStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="160" />
    </el-table>
    <el-pagination v-if="total>pageSize" style="margin-top:16px;justify-content:flex-end" background layout="total, prev, pager, next" :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="load" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../api/request'

const list = ref([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const pageSize = ref(20)

const orderStatusType = (s) => {
  if (s === 'COMPLETED') return 'success'
  if (s === 'CANCELLED') return 'info'
  if (s.indexOf('WAITING') >= 0 || s === 'PENDING') return 'warning'
  return ''
}

const load = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/orders', { params: { page: page.value, size: pageSize.value } })
    if (res.code === 1) { list.value = res.data?.records || []; total.value = res.data?.total || 0 }
  } finally { loading.value = false }
}

onMounted(load)
</script>
