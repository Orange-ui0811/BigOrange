<template>
  <div>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <h3 style="margin:0;font-weight:700">知识库管理</h3>
      <el-upload
        :before-upload="handleUpload"
        :show-file-list="false"
        accept=".md,.txt"
        :http-request="() => {}"
      >
        <el-button type="primary" :loading="uploading" style="background:#e65100;border-color:#e65100">
          <el-icon style="margin-right:4px"><Upload /></el-icon>上传文档
        </el-button>
      </el-upload>
    </div>

    <p style="color:#909399;font-size:13px;margin:0 0 12px">
      支持 Markdown（.md）和纯文本（.txt），单文件最大 1MB。上传后自动切片向量化，增强趋势分析效果。
    </p>

    <el-empty v-if="docs.length === 0 && !loading" description="暂无文档，点击上传按钮添加" />

    <div v-loading="loading">
      <el-card
        v-for="doc in docs" :key="doc.id"
        shadow="never"
        style="margin-bottom:8px;border-radius:10px"
      >
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div style="flex:1;min-width:0">
            <div style="display:flex;align-items:center;gap:8px;margin-bottom:4px">
              <span style="font-weight:600;font-size:14px">
                {{ doc.source === 'GITHUB' ? '📦' : '📄' }} {{ doc.title }}
              </span>
              <el-tag size="small" :type="doc.source === 'GITHUB' ? 'success' : ''">
                {{ doc.source === 'GITHUB' ? 'GitHub' : '手动上传' }}
              </el-tag>
            </div>
            <div style="color:#909399;font-size:12px">
              {{ doc.chunkCount }} 个切片
              <el-divider direction="vertical" />
              {{ doc.createTime || '-' }}
              <template v-if="doc.fileSize">
                <el-divider direction="vertical" />
                {{ formatSize(doc.fileSize) }}
              </template>
            </div>
          </div>
          <el-popconfirm title="确认删除此文档？" @confirm="handleDelete(doc.id)">
            <template #reference>
              <el-button type="danger" text size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { uploadDocument, listDocuments, deleteDocument } from '../api/knowledge'

const docs = ref([])
const loading = ref(false)
const uploading = ref(false)

const load = async () => {
  loading.value = true
  try {
    const res = await listDocuments()
    docs.value = res.data || []
  } finally {
    loading.value = false
  }
}

const handleUpload = async (file) => {
  const lower = file.name.toLowerCase()
  if (!lower.endsWith('.md') && !lower.endsWith('.txt')) {
    ElMessage.warning('仅支持 .md 和 .txt 格式')
    return false
  }
  if (file.size > 1048576) {
    ElMessage.warning('文件最大 1MB')
    return false
  }
  uploading.value = true
  try {
    await uploadDocument(file)
    ElMessage.success(`上传成功：${file.name}`)
    await load()
  } catch (e) {
    ElMessage.error('上传失败，请重试')
  } finally {
    uploading.value = false
  }
  return false // 阻止 el-upload 默认上传
}

const handleDelete = async (id) => {
  try {
    await deleteDocument(id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

const formatSize = (bytes) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

onMounted(() => load())
</script>
