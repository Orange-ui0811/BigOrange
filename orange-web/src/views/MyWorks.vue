<template>
  <div class="my-works-page">
    <div class="page-heading">
      <div>
        <span>创作管理</span>
        <h2>已上传内容</h2>
        <p>作品可在列表中直接修改所有发布字段，也可撤回审核或已上架的作品。</p>
      </div>
      <el-button type="primary" @click="$router.push('/publish?type=work')">上传新作品</el-button>
    </div>
    <el-tabs v-model="tab" class="works-tabs">
      <el-tab-pane label="我的作品" name="works"/>
      <el-tab-pane label="我的需求" name="reqs"/>
    </el-tabs>
    <el-table :data="list" size="small" v-loading="loading" empty-text="暂无内容" class="works-table">
      <el-table-column prop="id" label="ID" width="50"/>
      <el-table-column prop="title" label="标题" show-overflow-tooltip/>
      <el-table-column label="状态" width="100">
        <template #default="{row}">
          <el-tag :type="st(row.auditStatus)" size="small">{{ statusText(row.auditStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="tab === 'works'" label="参考价" width="120">
        <template #default="{row}">
          <strong class="price-cell">{{ formatWorkPrice(row.price) }}</strong>
        </template>
      </el-table-column>
      <el-table-column label="可见性" width="96">
        <template #default="{row}">
          <span class="visibility-dot" :class="row.visibilityStatus?.toLowerCase()"></span>
          {{ row.visibilityStatus === 'PUBLIC' ? '公开' : '私密' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="500" fixed="right">
        <template #default="{row}">
          <div class="action-row">
            <el-button v-if="tab === 'works'" size="small" type="primary" @click="editInline(row)">直接修改</el-button>
            <el-button size="small" plain @click="$router.push('/edit/'+(tab==='works'?'work':'requirement')+'/'+row.id)">完整页编辑</el-button>
            <el-button
              v-if="row.auditStatus==='DRAFT'||row.auditStatus==='REJECTED'"
              size="small"
              :loading="actionLoading === `${row.id}:submit`"
              @click="act(row, 'submit')"
            >提交审核</el-button>
            <el-button
              v-if="tab === 'works' && (row.auditStatus === 'PENDING_REVIEW' || row.auditStatus === 'APPROVED')"
              size="small"
              type="warning"
              plain
              :loading="actionLoading === `${row.id}:withdraw`"
              @click="act(row, 'withdraw')"
            >撤回作品</el-button>
            <el-button size="small" plain :loading="actionLoading === `${row.id}:visibility`" @click="act(row, 'visibility')">
              {{ row.visibilityStatus === 'PUBLIC' ? '设为私密' : '设为公开' }}
            </el-button>
            <el-button size="small" type="danger" plain :loading="actionLoading === `${row.id}:delete`" @click="act(row, 'delete')">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <WorkInlineEditor v-model="editorOpen" :work-id="editingWorkId" @saved="load" />
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'
import WorkInlineEditor from '../components/WorkInlineEditor.vue'
import { formatWorkPrice } from '../utils/workPrice'

const tab = ref('works')
const list = ref([])
const loading = ref(false)
const actionLoading = ref('')
const editorOpen = ref(false)
const editingWorkId = ref(null)
const st = status => ({ APPROVED: 'success', REJECTED: 'danger', PENDING_REVIEW: 'warning' }[status] || 'info')
const statusText = status => ({ DRAFT: '草稿', PENDING_REVIEW: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[status] || status)

const load = async () => {
  loading.value = true
  const api = tab.value === 'works' ? '/app/work/my' : '/app/requirement/my'
  try {
    const response = await request.get(api, { params: { page: 1, size: 50 } })
    if (response.code === 1) list.value = response.data?.records || []
  } finally {
    loading.value = false
  }
}

const editInline = row => {
  editingWorkId.value = row.id
  editorOpen.value = true
}

const act = async (row, action) => {
  if (action === 'delete') {
    try {
      await ElMessageBox.confirm('删除后无法恢复，确认删除该内容？', '确认删除', { type: 'warning' })
    } catch { return }
  }
  if (action === 'withdraw') {
    try {
      await ElMessageBox.confirm('撤回后作品将回到草稿，若已上架将立即从市场移除。作品内容会保留，可修改后重新提交。', '撤回作品', {
        confirmButtonText: '确认撤回', cancelButtonText: '取消', type: 'warning',
      })
    } catch { return }
  }

  const key = `${row.id}:${action}`
  actionLoading.value = key
  const api = tab.value === 'works' ? `/app/work/${row.id}` : `/app/requirement/${row.id}`
  try {
    if (action === 'submit') await request.post(`${api}/submit`)
    else if (action === 'withdraw') await request.post(`${api}/withdraw`)
    else if (action === 'visibility') await request.put(`${api}/visibility`)
    else if (action === 'delete') await request.delete(api)
    const successText = { submit: '已提交审核', withdraw: '作品已撤回至草稿', visibility: '可见性已更新', delete: '已删除' }[action]
    if (successText) ElMessage.success(successText)
    await load()
  } finally {
    actionLoading.value = ''
  }
}

watch(tab, load)
onMounted(load)
</script>

<style scoped>
.my-works-page {
  background: #ffffff;
  border: 2px solid #f0ece6;
  border-radius: 2px;
  padding: 16px;
}

.page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 4px 2px 18px;
  border-bottom: 1px solid #f0ece6;
}

.page-heading span {
  color: #f37021;
  font: 700 11px/1.4 "Courier New", monospace;
  letter-spacing: 1.6px;
}

.page-heading h2 { margin: 4px 0 5px; color: #3a302a; font-size: 24px; }
.page-heading p { margin: 0; color: #847a71; font-size: 13px; line-height: 1.6; }

.works-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.works-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 2px;
  background-color: #f0ece6;
}

.works-tabs :deep(.el-tabs__item) {
  font-family: "Courier New", "SimHei", monospace;
  font-size: 13px;
  font-weight: 600;
  color: #9f9890;
  letter-spacing: 0.5px;
  height: 36px;
  line-height: 36px;
}

.works-tabs :deep(.el-tabs__item.is-active) {
  color: #f37021;
}

.works-tabs :deep(.el-tabs__active-bar) {
  background-color: #f37021;
  height: 2px;
}

.works-table {
  border: 2px solid #f0ece6;
  border-radius: 2px;
}

.works-table :deep(.el-table__header th) {
  background-color: #faf9f7;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  color: #3a302a;
  letter-spacing: 1px;
  border-bottom: 2px solid #f0ece6;
}

.works-table :deep(.el-table__body td) {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  color: #3a302a;
  border-bottom: 1px solid #f0ece6;
}

.works-table :deep(.el-table__row:hover > td) {
  background-color: #faf9f7;
}

.works-table :deep(.el-button) {
  font-family: "Courier New", "SimHei", monospace;
  border-radius: 0;
}

.works-table :deep(.el-button--small) {
  height: 28px;
  font-size: 11px;
}

.works-table :deep(.el-button--default) {
  background: #fff;
  border-color: #dfd7ce;
  color: #51473f;
  box-shadow: none;
}

.works-table :deep(.el-button--default:hover) {
  background: #fff8f2;
  border-color: #f37021;
  color: #c55312;
}

.works-table :deep(.el-button--primary) { background: #f37021; border-color: #f37021; color: #fff; }
.works-table :deep(.el-button--primary:hover) { background: #c55312; border-color: #c55312; }

.works-table :deep(.el-button--danger) {
  background: #e53935;
  border-color: #e53935;
  color: #fff;
  box-shadow: 0 3px 0 rgba(198, 40, 40, 0.15);
}

.works-table :deep(.el-button--danger:hover) {
  background: #c62828;
  border-color: #c62828;
}

.works-table :deep(.el-tag) {
  border-radius: 0;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 10px;
  font-weight: 700;
  height: 20px;
  line-height: 20px;
  padding: 0 6px;
  border: none;
}

.works-table :deep(.el-tag--success) {
  background: #7fb63c;
  color: #fff;
}

.works-table :deep(.el-tag--danger) {
  background: #e53935;
  color: #fff;
}

.works-table :deep(.el-tag--warning) {
  background: #f37021;
  color: #fff;
}

.works-table :deep(.el-tag--info) {
  background: #e5d9c3;
  color: #3a302a;
}

.works-table :deep(.el-loading-mask) {
  border-radius: 2px;
}

.action-row { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.action-row :deep(.el-button) { margin-left: 0; }
.price-cell { color: #c55312; font: 700 13px/1.4 "Courier New", monospace; }
.visibility-dot { display: inline-block; width: 7px; height: 7px; margin-right: 4px; border-radius: 50%; background: #b9b2aa; }
.visibility-dot.public { background: #7fb63c; }

@media (max-width: 720px) {
  .my-works-page { padding: 12px; }
  .page-heading { align-items: flex-start; flex-direction: column; gap: 12px; }
}
</style>
