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
    <el-dialog v-model="detailVisible" title="内容详情" width="700px" top="3vh">
      <div style="max-height:65vh;overflow:auto;line-height:1.8">
        <el-image v-if="detail.coverUrl" :src="'/api'+detail.coverUrl" style="width:100%;max-height:300px;object-fit:cover;border-radius:12px;margin-bottom:12px" fit="cover" />
        <h3 style="margin:0 0 4px">{{ detail.title }}</h3>
        <p style="color:#909399;font-size:13px;margin:0 0 8px">by {{ detail.ownerName || detail.publisherName || '#'+(detail.ownerId||detail.publisherId) }} · {{ detail.auditStatus }} · {{ detail.createTime }}</p>
        <el-alert v-if="detail.demoUrl" title="在线演示" type="info" :closable="false" style="margin-bottom:12px">
          <a :href="detail.demoUrl" target="_blank" style="color:#e65100;font-weight:600">{{ detail.demoUrl }}</a>
        </el-alert>
        <p v-if="detail.summary" style="color:#6b6b6b;white-space:pre-wrap">{{ detail.summary }}</p>
        <p v-if="detail.detail" style="color:#2c2c2c;white-space:pre-wrap">{{ detail.detail }}</p>
        <div v-if="tradeEnabled && detail.saleOptions?.length" style="margin-top:12px;padding-top:12px;border-top:1px solid #eee">
          <b>售卖项：</b>
          <el-tag v-for="so in detail.saleOptions" :key="so.id" type="warning" style="margin:4px 8px 4px 0">¥{{ so.price }} {{ so.saleType }}</el-tag>
        </div>
        <div v-if="detail.budgetMin" style="margin-top:8px">
          <el-tag type="warning">预算 ¥{{ detail.budgetMin }} - ¥{{ detail.budgetMax }}</el-tag>
        </div>
        <p v-if="detail.tags" style="margin-top:8px"><b>标签：</b>{{ detail.tags }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import request from '../api/request'
import { tradeEnabled } from '../stores/tradeMode'

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
