<template>
  <div>
    <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px">
      <h3 style="margin:0;font-weight:700">岗位审核</h3>
      <span style="font-size:13px;font-weight:600">🤖 AI 托管</span>
      <el-switch v-model="aiEnabled" @change="toggleAi" size="small" />
    </div>

    <el-tabs v-model="tab" @tab-change="loadList">
      <el-tab-pane label="待审核" name="PENDING" />
      <el-tab-pane label="已通过" name="APPROVED" />
      <el-tab-pane label="已拒绝" name="REJECTED" />
    </el-tabs>

    <el-table :data="list" v-loading="loading" empty-text="暂无数据" size="small">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="position" label="岗位" min-width="180" show-overflow-tooltip />
      <el-table-column prop="direction" label="方向" width="100" />
      <el-table-column prop="createTime" label="时间" width="140" />
      <el-table-column label="操作" width="200" v-if="tab==='PENDING'">
        <template #default="{row}">
          <el-button size="small" type="primary" @click="showDetail(row)">查看</el-button>
          <el-button size="small" type="success" @click="approve(row.id)">通过</el-button>
          <el-popconfirm title="确认删除？" @confirm="doDelete(row.id)"><template #reference><el-button size="small" type="danger" text>删除</el-button></template></el-popconfirm>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" v-if="tab==='APPROVED'">
        <template #default="{row}">
          <el-button size="small" type="primary" @click="showDetail(row)">查看</el-button>
          <el-button size="small" @click="editRow(row)">编辑</el-button>
          <el-button size="small" type="warning" @click="syncKnowledge(row.id)" :loading="syncingId===row.id">更新知识库</el-button>
          <el-popconfirm title="确认删除？" @confirm="doDelete(row.id)"><template #reference><el-button size="small" type="danger" text>删除</el-button></template></el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination v-if="total>size" style="margin-top:12px;justify-content:center"
      layout="prev,next" :total="total" :page-size="size" v-model:current-page="page" @current-change="loadList" small />

    <el-dialog v-model="dialogVisible" title="审核详情" width="700px">
      <div style="max-height:60vh;overflow:auto;line-height:1.8;white-space:pre-wrap;font-size:14px">{{ detailContent }}</div>
      <template v-if="tab==='PENDING'" #footer>
        <el-input v-model="rejectReason" placeholder="拒绝原因（可选）" size="small" style="margin-bottom:8px" />
        <el-button @click="dialogVisible=false">关闭</el-button>
        <el-button type="danger" @click="reject(detailId)">拒绝</el-button>
        <el-button type="success" @click="approve(detailId); dialogVisible=false">通过</el-button>
      </template>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" title="编辑岗位" width="600px">
      <el-form label-position="top">
        <el-form-item label="岗位名称"><el-input v-model="editForm.position" /></el-form-item>
        <el-form-item label="技术方向"><el-input v-model="editForm.direction" /></el-form-item>
        <el-form-item label="展示内容"><el-input v-model="editForm.displayContent" type="textarea" :rows="10" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible=false">取消</el-button>
        <el-button type="primary" @click="editSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'

const tab = ref('PENDING')
const list = ref([]); const loading = ref(false)
const page = ref(1); const size = 50; const total = ref(0)
const dialogVisible = ref(false); const detailContent = ref('')
const detailId = ref(null); const rejectReason = ref('')
const aiEnabled = ref(false)

const loadList = async () => {
  loading.value = true
  try {
    const r = await request.get(tab.value==='PENDING'?'/admin/job/pending':'/admin/job/list', {
      params: { status: tab.value, page: page.value, size }
    })
    list.value = tab.value==='PENDING'? r.data : (r.data?.records || [])
    total.value = tab.value==='PENDING'? list.value.length : (r.data?.total || 0)
  } finally { loading.value = false }
}

const showDetail = async (row) => {
  detailId.value = row.id
  try {
    const r = await request.get('/admin/job/' + row.id)
    detailContent.value = r.data?.content || '(无内容)'
  } catch { detailContent.value = '(加载失败)' }
  dialogVisible.value = true
}

const approve = async (id) => {
  try { await request.post('/admin/job/' + id + '/approve'); ElMessage.success('已通过'); loadList() }
  catch (e) { ElMessage.error(e?.response?.data?.msg || '操作失败') }
}

const reject = async (id) => {
  try {
    await request.post('/admin/job/' + id + '/reject', { reason: rejectReason.value })
    ElMessage.success('已拒绝'); rejectReason.value = ''; dialogVisible.value = false; loadList()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '操作失败') }
}

const editVisible = ref(false)
const syncingId = ref(null)
const editForm = ref({ id: null, position: '', direction: '', displayContent: '' })
const editRow = async (row) => {
  try {
    const r = await request.get('/admin/job/' + row.id)
    editForm.value = { id: row.id, position: r.data?.position || '', direction: r.data?.direction || '', displayContent: r.data?.displayContent || r.data?.content || '' }
  } catch { editForm.value = { id: row.id, position: row.position, direction: row.direction, displayContent: '' } }
  editVisible.value = true
}
const editSave = async () => {
  try {
    await request.put('/admin/job/' + editForm.value.id, editForm.value)
    ElMessage.success('已保存'); editVisible.value = false; loadList()
  } catch (e) { ElMessage.error('保存失败') }
}
const doDelete = async (id) => {
  try { await request.delete('/admin/job/' + id); ElMessage.success('已删除'); loadList() }
  catch (e) { ElMessage.error('删除失败') }
}

const syncKnowledge = async (id) => {
  syncingId.value = id
  try {
    await request.post('/admin/job/' + id + '/sync-knowledge')
    ElMessage.success('知识库已更新')
  } catch (e) { ElMessage.error('同步失败') }
  finally { syncingId.value = null }
}

const toggleAi = async (val) => {
  try {
    await request.put('/admin/job/ai-auto/toggle', { enabled: val })
    ElMessage.success(val ? 'AI 托管已开启' : 'AI 托管已关闭')
    if (val) loadList()
  } catch (e) { ElMessage.error('切换失败'); aiEnabled.value = !val }
}

onMounted(async () => {
  loadList()
  try { const r = await request.get('/admin/job/ai-auto/status'); aiEnabled.value = r.data?.enabled || false } catch {}
})
</script>
