<template>
  <div>
    <el-tabs v-model="tab" style="margin-bottom:12px">
      <el-tab-pane label="作品管理" name="works" />
      <el-tab-pane label="需求管理" name="reqs" />
    </el-tabs>
    <el-table :data="list" size="small" stripe v-loading="loading" empty-text="暂无内容">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column label="标题" min-width="140" show-overflow-tooltip>
        <template #default="{row}"><a href="#" @click.prevent="openWork(row)" style="color:#409EFF;text-decoration:none">{{ row.title }}</a></template>
      </el-table-column>
      <el-table-column :prop="tab==='works'?'ownerName':'publisherName'" label="作者" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{row}">
          <el-tag :type="statusType(row.auditStatus)" size="small">{{ row.auditStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="可见" width="80">
        <template #default="{row}">{{ row.visibilityStatus }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{row}">
          <el-button size="small" @click="showDetail(row.id)">详情</el-button>
          <el-button size="small" type="warning" plain @click="offline(row.id)">下架</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-if="total>pageSize" style="margin-top:16px;justify-content:flex-end" background layout="total, prev, pager, next" :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="load" />
    <el-dialog v-model="detailVisible" title="内容详情" width="600px" top="5vh">
      <div style="max-height:60vh;overflow:auto;line-height:1.8">
        <p><b>标题：</b>{{ detail.title }}</p><p><b>状态：</b>{{ detail.auditStatus }}</p>
        <p><b>简介：</b>{{ detail.summary || '无' }}</p><div v-html="'<b>详情：</b>' + (detail.detail || '无')"></div>
        <p><b>时间：</b>{{ detail.createTime }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import request from '../api/request'

const router = useRouter()
const tab = ref('works')
const list = ref([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const pageSize = ref(20)
const detailVisible = ref(false)
const detail = ref({})

const load = async () => {
  loading.value = true
  try {
    const api = tab.value === 'works' ? '/admin/work' : '/admin/requirement'
    const res = await request.get(api, { params: { page: page.value, size: pageSize.value } })
    if (res.code === 1) { list.value = res.data?.records || []; total.value = res.data?.total || 0 }
  } finally { loading.value = false }
}

const openWork = (row) => {
  const path = (tab.value === 'works' ? '/work/' : '/requirement/') + row.id
  router.push(path)
}

const showDetail = async (id) => {
  try {
    const api = tab.value === 'works' ? `/admin/work/${id}` : `/admin/requirement/${id}`
    const res = await request.get(api)
    if (res.code === 1) { detail.value = res.data; detailVisible.value = true }
  } catch (e) {}
}

const statusType = (s) => {
  if (s === 'APPROVED') return 'success'
  if (s === 'REJECTED') return 'danger'
  if (s === 'PENDING_REVIEW') return 'warning'
  return 'info'
}

const offline = async (id) => {
  try { await ElMessageBox.confirm('确认下架？', '下架', { type: 'warning' }) } catch { return }
  const api = tab.value === 'works' ? `/admin/work/${id}/offline` : `/admin/requirement/${id}/offline`
  await request.put(api, { reason: '管理员下架' })
  load()
}

watch(tab, () => { page.value = 1; load() })
onMounted(load)
</script>
