<template>
  <div>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <h3 style="margin:0">知识库管理</h3>
      <el-upload :before-upload="handleUpload" :show-file-list="false" accept=".md,.txt" :http-request="() => {}">
        <el-button type="primary" :loading="uploading"><el-icon style="margin-right:4px"><Upload /></el-icon>上传文档</el-button>
      </el-upload>
    </div>

    <!-- 统计概览 -->
    <el-row :gutter="12" style="margin-bottom:16px" v-if="stats.totalDocs">
      <el-col :span="6"><el-card shadow="never" style="text-align:center"><div style="font-size:24px;font-weight:700;color:#e65100">{{ stats.totalDocs }}</div><div style="font-size:12px;color:#909399">文档总数</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" style="text-align:center"><div style="font-size:24px;font-weight:700;color:#67c23a">{{ stats.githubCount }}</div><div style="font-size:12px;color:#909399">GitHub 采集</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" style="text-align:center"><div style="font-size:24px;font-weight:700;color:#409eff">{{ stats.manualCount }}</div><div style="font-size:12px;color:#909399">手动上传</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" style="text-align:center"><div style="font-size:24px;font-weight:700;color:#909399">{{ stats.totalChunks }}</div><div style="font-size:12px;color:#909399">总切片数</div></el-card></el-col>
    </el-row>

    <!-- 搜索 -->
    <div style="display:flex;gap:12px;margin-bottom:12px;align-items:center">
      <el-input v-model="search" placeholder="搜索文档标题..." clearable style="max-width:300px" size="small" />
      <el-radio-group v-model="sourceFilter" size="small" @change="() => {}">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="MANUAL">手动上传</el-radio-button>
        <el-radio-button value="GITHUB">GitHub采集</el-radio-button>
        <el-radio-button value="JOB_POSTING">岗位招聘</el-radio-button>
      </el-radio-group>
    </div>

    <el-empty v-if="filtered.length === 0 && !loading" :description="search ? '无匹配文档' : '暂无文档'" />
    <div v-loading="loading">
      <el-card v-for="doc in filtered" :key="doc.id" shadow="never" style="margin-bottom:8px">
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div style="flex:1;min-width:0">
            <div style="display:flex;align-items:center;gap:8px;margin-bottom:4px;flex-wrap:wrap">
              <span style="font-weight:600;font-size:14px;color:#e65100;cursor:pointer" @click="openDetail(doc)">{{ doc.source === 'GITHUB' ? '📦' : '📄' }} {{ doc.title }}</span>
              <el-tag size="small" :type="doc.source==='GITHUB'?'success':doc.source==='JOB_POSTING'?'danger':''">{{ doc.source==='GITHUB'?'GitHub采集':doc.source==='JOB_POSTING'?'岗位招聘':'手动上传' }}</el-tag>
              <el-tag size="small" :type="doc.period==='week'||doc.period==='new'?'warning':'info'">{{ doc.period==='week'||doc.period==='new'?'30天后清理':'永久' }}</el-tag>
            </div>
            <div style="color:#909399;font-size:12px">{{ doc.chunkCount }} 个切片 <el-divider direction="vertical" /> {{ doc.createTime || '-' }}</div>
          </div>
          <el-popconfirm title="确认删除？" @confirm="handleDelete(doc.id)">
            <template #reference><el-button type="danger" text size="small">删除</el-button></template>
          </el-popconfirm>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="800px" top="5vh">
      <div v-if="detailLoading" v-loading="true" style="min-height:200px" />
      <div v-else v-safe-html="rendered" style="max-height:70vh;overflow:auto;background:#fafafa;padding:20px;border-radius:8px;font-size:14px;line-height:1.8" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { uploadDocument, listDocuments, deleteDocument } from '../api/knowledge'
import request from '../api/request'

const docs = ref([])
const loading = ref(false)
const uploading = ref(false)
const search = ref('')
const sourceFilter = ref('')
const dialogVisible = ref(false)
const dialogTitle = ref('')
const detailContent = ref('')
const detailLoading = ref(false)
const stats = ref({ totalDocs: 0, githubCount: 0, manualCount: 0, totalChunks: 0 })

const filtered = computed(() => {
  let result = docs.value
  if (search.value) {
    const kw = search.value.toLowerCase()
    result = result.filter(d => d.title.toLowerCase().includes(kw))
  }
  if (sourceFilter.value) {
    result = result.filter(d => d.source === sourceFilter.value)
  }
  return result
})

const rendered = computed(() => {
  if (!detailContent.value) return ''
  let html = detailContent.value
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  // headings
  html = html.replace(/^### (.+)$/gm, '<h3 style="margin:16px 0 8px;color:#303133">$1</h3>')
  html = html.replace(/^## (.+)$/gm, '<h2 style="margin:20px 0 10px;color:#303133;border-bottom:1px solid #eee;padding-bottom:6px">$1</h2>')
  html = html.replace(/^# (.+)$/gm, '<h1 style="margin:24px 0 12px;color:#303133">$1</h1>')
  // bold
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  // inline code
  html = html.replace(/`([^`]+)`/g, '<code style="background:#f0f0f0;padding:2px 6px;border-radius:3px;font-size:13px">$1</code>')
  // links
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" style="color:#e65100">$1</a>')
  // newlines
  html = html.replace(/\n\n/g, '</p><p style="margin:8px 0">')
  html = html.replace(/\n/g, '<br>')
  return '<p style="margin:8px 0">' + html + '</p>'
})

const load = async () => {
  loading.value = true
  try {
    const [listRes, statsRes] = await Promise.all([
      listDocuments(),
      request.get('/admin/knowledge/document/stats')
    ])
    docs.value = listRes.data || []
    if (statsRes.data) stats.value = statsRes.data
  } finally { loading.value = false }
}

const openDetail = async (doc) => {
  dialogTitle.value = doc.title; dialogVisible.value = true; detailLoading.value = true
  try {
    const r = await request.get('/admin/knowledge/document/' + doc.id)
    detailContent.value = (r.data && r.data.content) ? r.data.content : '(暂无内容)'
  } catch { detailContent.value = '(加载失败)' } finally { detailLoading.value = false }
}

const handleUpload = async (file) => {
  const l = file.name.toLowerCase()
  if (!l.endsWith('.md') && !l.endsWith('.txt')) { ElMessage.warning('仅支持 .md / .txt'); return false }
  if (file.size > 1048576) { ElMessage.warning('最大 1MB'); return false }
  uploading.value = true
  try { await uploadDocument(file); ElMessage.success('上传成功'); await load() } catch { ElMessage.error('上传失败') } finally { uploading.value = false }
  return false
}

const handleDelete = async (id) => { try { await deleteDocument(id); ElMessage.success('已删除'); await load() } catch { ElMessage.error('删除失败') } }

onMounted(() => load())
</script>
