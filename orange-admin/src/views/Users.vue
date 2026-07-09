<template>
  <div>
    <el-table :data="list" size="small" stripe v-loading="loading" empty-text="暂无用户">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" min-width="100" />
      <el-table-column prop="email" label="邮箱" min-width="160" />
      <el-table-column prop="role" label="角色" width="80">
        <template #default="{row}">
          <el-tag :type="row.role==='ADMIN'?'danger':''" size="small">{{ row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{row}">
          <el-tag :type="row.status==='ACTIVE'?'success':'info'" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" width="160" />
      <el-table-column label="操作" width="100">
        <template #default="{row}">
          <el-button size="small" :type="row.status==='ACTIVE'?'danger':'success'" plain @click="toggle(row.id)">
            {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
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

const load = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/users', { params: { page: page.value, size: pageSize.value } })
    if (res.code === 1) { list.value = res.data?.records || []; total.value = res.data?.total || 0 }
  } finally { loading.value = false }
}

const toggle = async (id) => {
  await request.put(`/admin/users/${id}/status`)
  load()
}

onMounted(load)
</script>
