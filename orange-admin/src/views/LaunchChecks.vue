<template>
  <div class="launch-admin">
    <div class="page-heading">
      <div>
        <h3>上线体检</h3>
        <p>查看用户项目进度、阶段报告和累计配额消耗。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadData">刷新</el-button>
    </div>

    <div class="summary-band">
      <div><span>项目总数</span><strong>{{ total }}</strong></div>
      <div><span>已完成</span><strong>{{ completed }}</strong></div>
      <div><span>进行中</span><strong>{{ Math.max(0, total - completed) }}</strong></div>
      <div><span>累计消耗</span><strong>{{ totalCost }}</strong><small> 配额</small></div>
    </div>

    <el-table :data="records" v-loading="loading" empty-text="暂无上线体检项目" class="project-table">
      <el-table-column prop="projectName" label="项目" min-width="180" show-overflow-tooltip />
      <el-table-column label="用户" min-width="150">
        <template #default="{ row }">
          <div class="user-cell"><strong>{{ row.accountName }}</strong><span>#{{ row.accountId }} · {{ row.username }}</span></div>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="150">
        <template #default="{ row }">
          <div class="progress-cell"><span>{{ row.currentStage || 0 }}/8</span><el-progress :percentage="Math.round((row.currentStage || 0) / 8 * 100)" :show-text="false" :stroke-width="7" /></div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><el-tag size="small" :type="row.status === 'COMPLETED' ? 'success' : 'warning'">{{ row.status === 'COMPLETED' ? '已完成' : '进行中' }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="totalCost" label="累计消耗" width="110" align="right" />
      <el-table-column prop="updateTime" label="更新时间" width="170"><template #default="{ row }">{{ formatTime(row.updateTime) }}</template></el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }"><el-button size="small" text type="primary" @click="openDetail(row.id)">查看</el-button></template>
      </el-table-column>
    </el-table>

    <div class="pagination-row">
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="loadData" />
    </div>

    <el-drawer v-model="drawerOpen" size="min(760px, 92vw)" title="上线体检详情" destroy-on-close>
      <el-skeleton v-if="detailLoading" :rows="8" animated />
      <div v-else-if="detail" class="detail-view">
        <div class="detail-meta">
          <div><span>项目</span><strong>{{ detail.projectName }}</strong></div>
          <div><span>用户</span><strong>{{ detail.accountName }} · #{{ detail.accountId }}</strong></div>
          <div><span>地域</span><strong>{{ detail.region || '未提供' }}</strong></div>
          <div><span>预算</span><strong>{{ detail.budget || '未提供' }}</strong></div>
        </div>
        <p class="project-summary">{{ detail.projectSummary }}</p>
        <el-collapse accordion>
          <el-collapse-item v-for="stage in detail.stages" :key="stage.stageNo" :name="stage.stageNo">
            <template #title><span class="stage-title">{{ stage.stageNo }}. {{ stage.stageTitle }}</span><small>{{ stage.segmentCost }} 配额</small></template>
            <div class="markdown-body" v-html="renderMarkdown(stage.content)"></div>
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import request from '../api/request'

const loading = ref(false)
const detailLoading = ref(false)
const drawerOpen = ref(false)
const records = ref([])
const detail = ref(null)
const page = ref(1)
const pageSize = 20
const total = ref(0)
const completed = ref(0)
const totalCost = ref(0)
const formatTime = (value) => value ? String(value).replace('T', ' ').substring(0, 19) : ''
const renderMarkdown = (content) => DOMPurify.sanitize(marked.parse(content || ''))

const loadData = async () => {
  loading.value = true
  try {
    const response = await request.get('/admin/launch-check', { params: { page: page.value, size: pageSize } })
    if (response.code === 1) {
      records.value = response.data.records || []
      total.value = Number(response.data.summary?.total || 0)
      completed.value = Number(response.data.summary?.completed || 0)
      totalCost.value = Number(response.data.summary?.totalCost || 0)
    }
  } finally { loading.value = false }
}

const openDetail = async (id) => {
  drawerOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const response = await request.get(`/admin/launch-check/${id}`)
    if (response.code === 1) detail.value = response.data
  } finally { detailLoading.value = false }
}

onMounted(loadData)
</script>

<style scoped>
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.page-heading h3 { margin: 0; color: #303133; font-size: 18px; }
.page-heading p { margin: 5px 0 0; color: #909399; font-size: 13px; }
.summary-band { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border: 1px solid #e4e7ed; border-radius: 6px; background: #fff; margin-bottom: 16px; }
.summary-band > div { min-height: 88px; padding: 16px 18px; border-right: 1px solid #ebeef5; }
.summary-band > div:last-child { border-right: 0; }
.summary-band span { display: block; color: #909399; font-size: 12px; }
.summary-band strong { display: inline-block; margin-top: 8px; color: #303133; font-size: 24px; font-variant-numeric: tabular-nums; }
.summary-band small { color: #909399; }
.project-table { width: 100%; border: 1px solid #e4e7ed; border-radius: 6px; }
.user-cell strong, .user-cell span { display: block; }
.user-cell span { margin-top: 3px; color: #909399; font-size: 11px; }
.progress-cell { display: grid; grid-template-columns: 28px minmax(0, 1fr); align-items: center; gap: 8px; }
.progress-cell span { color: #e65100; font-size: 12px; font-weight: 700; }
.pagination-row { display: flex; justify-content: flex-end; margin-top: 16px; }
.detail-meta { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1px; border: 1px solid #ebeef5; background: #ebeef5; margin-bottom: 16px; }
.detail-meta > div { min-height: 70px; padding: 12px; background: #fff; }
.detail-meta span, .detail-meta strong { display: block; }
.detail-meta span { color: #909399; font-size: 11px; }
.detail-meta strong { margin-top: 6px; color: #303133; font-size: 13px; }
.project-summary { padding: 14px; border-left: 3px solid #e65100; background: #fff7ed; color: #606266; font-size: 13px; line-height: 1.7; }
.stage-title { margin-right: 10px; color: #303133; font-weight: 700; }
.stage-title + small { color: #909399; }
.markdown-body { color: #303133; font-size: 13px; line-height: 1.75; }
.markdown-body :deep(table) { width: 100%; border-collapse: collapse; display: block; overflow-x: auto; }
.markdown-body :deep(th), .markdown-body :deep(td) { padding: 8px; border: 1px solid #dcdfe6; text-align: left; vertical-align: top; }
.markdown-body :deep(pre) { overflow-x: auto; padding: 12px; border-radius: 4px; background: #25272a; color: #f5f7fa; }
@media (max-width: 760px) { .summary-band { grid-template-columns: repeat(2, minmax(0, 1fr)); }.summary-band > div:nth-child(2) { border-right: 0; }.detail-meta { grid-template-columns: 1fr; } }
</style>
