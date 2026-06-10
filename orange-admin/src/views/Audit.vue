<template>
  <div>
    <el-tabs v-model="tab" style="margin-bottom:12px">
      <el-tab-pane label="待审作品" name="works" />
      <el-tab-pane label="待审需求" name="reqs" />
    </el-tabs>
    <el-table :data="list" size="small" stripe v-loading="loading" empty-text="暂无待审内容">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
      <el-table-column :prop="tab==='works'?'ownerName':'publisherName'" label="发布者" width="120" />
      <el-table-column label="时间" width="160">
        <template #default="{row}">{{ row.createTime }}</template>
      </el-table-column>
      <el-table-column label="操作" width="260">
        <template #default="{row}">
          <el-button size="small" @click="showDetail(row.id)">详情</el-button>
          <el-button size="small" type="success" plain @click="audit(row.id, true)">通过</el-button>
          <el-button size="small" type="danger" plain @click="reject(row.id)">拒绝</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="detailVisible" title="内容详情" width="600px" top="5vh">
      <div style="max-height:60vh;overflow:auto;line-height:1.8">
        <p><b>标题：</b>{{ detail.title }}</p>
        <p><b>发布者：</b>{{ detail.ownerName || detail.publisherName }}</p>
        <p><b>状态：</b>{{ detail.auditStatus }}</p>
        <p><b>简介：</b>{{ detail.summary || '无' }}</p>
        <p><b>详情：</b>{{ detail.detail || '无' }}</p>
        <p v-if="detail.budgetMin"><b>预算：</b>{{ detail.budgetMin }} - {{ detail.budgetMax }}</p>
        <p><b>时间：</b>{{ detail.createTime }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import request from '../api/request'

const tab = ref('works')
const list = ref([])
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref({})

const load = async () => {
  loading.value = true
  try {
    const api = tab.value === 'works' ? '/admin/audit/works' : '/admin/audit/requirements'
    const res = await request.get(api, { params: { page: 1, size: 50 } })
    if (res.code === 1) list.value = res.data?.records || []
  } finally { loading.value = false }
}

const showDetail = async (id) => {
  try {
    const api = tab.value === 'works' ? `/admin/work/${id}` : `/admin/requirement/${id}`
    const res = await request.get(api)
    if (res.code === 1) { detail.value = res.data; detailVisible.value = true }
  } catch (e) {}
}

const audit = async (id, approved) => {
  const type = tab.value === 'works' ? 'work' : 'requirement'
  try {
    await request.post(`/admin/audit/${type}/${id}/${approved ? 'approve' : 'reject'}`, { reason: approved ? '审核通过' : '' })
    load()
  } catch (e) {}
}

const reject = (id) => {
  ElMessageBox.prompt('拒绝原因', '拒绝', { confirmButtonText: '确认拒绝', cancelButtonText: '取消' })
    .then(({ value }) => {
      const type = tab.value === 'works' ? 'work' : 'requirement'
      request.post(`/admin/audit/${type}/${id}/reject`, { reason: value || '未说明原因' }).then(() => load())
    })
    .catch(() => {})
}

watch(tab, load)
onMounted(load)
</script>
