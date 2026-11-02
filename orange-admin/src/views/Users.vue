<template>
  <section class="users-page">
    <header class="users-heading">
      <div>
        <h3>用户列表</h3>
        <p>共 {{ total }} 个账号，包含普通用户与管理员账号</p>
      </div>
      <div class="pagination-actions" aria-label="用户列表翻页">
        <el-button size="small" plain :disabled="page <= 1" aria-label="进入上一页" @click="goPrevious">
          上一页
        </el-button>
        <el-tag type="info" effect="plain">第 {{ page }} / {{ totalPages }} 页</el-tag>
        <el-button size="small" type="primary" plain :disabled="page >= totalPages" aria-label="进入下一页" @click="goNext">
          下一页
        </el-button>
      </div>
    </header>

    <el-table :data="list" size="small" stripe v-loading="loading" empty-text="暂无用户" class="users-table">
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
  </section>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import request from '../api/request'

const list = ref([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const pageSize = ref(50)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

const load = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/users', { params: { page: page.value, size: pageSize.value } })
    if (res.code === 1) { list.value = res.data?.records || []; total.value = res.data?.total || 0 }
  } finally { loading.value = false }
}

const goPrevious = () => {
  if (page.value <= 1) return
  page.value -= 1
  load()
}

const goNext = () => {
  if (page.value >= totalPages.value) return
  page.value += 1
  load()
}

const toggle = async (id) => {
  await request.put(`/admin/users/${id}/status`)
  load()
}

onMounted(load)
</script>

<style scoped>
.users-page {
  min-width: 0;
  overflow: hidden;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: #fff;
}

.users-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid #ebeef5;
}

.users-heading h3 {
  margin: 0;
  color: #303133;
  font-size: 16px;
}

.users-heading p {
  margin: 5px 0 0;
  color: #909399;
  font-size: 12px;
}

.users-table {
  width: 100%;
}

.pagination-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}
.pagination-actions :deep(.el-button + .el-button) { margin-left: 0; }

@media (max-width: 760px) {
  .users-heading { padding: 14px; flex-wrap: wrap; }
  .pagination-actions { width: 100%; justify-content: flex-end; }
}
</style>
