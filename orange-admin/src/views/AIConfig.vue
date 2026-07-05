<template>
  <div>
    <h3 style="margin:0 0 4px;font-weight:700">AI 调用设置</h3>
    <p style="color:#909399;font-size:13px;margin:0 0 16px">查看和修改各 AI 模块调用的 API 地址、模型，保存后即时生效</p>

    <el-row :gutter="16" v-loading="loading">
      <el-col :span="8" v-for="item in items" :key="item.key">
        <el-card shadow="never" style="margin-bottom:16px">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:600">{{ item.title }}</span>
              <el-tag size="small" :type="item.editing?'warning':'info'">{{ item.editing?'编辑中':'只读' }}</el-tag>
            </div>
          </template>

          <template v-if="item.key==='leaderboard'">
            <el-descriptions :column="1" size="small" border>
              <el-descriptions-item label="类型">{{ item.data.type }}</el-descriptions-item>
              <el-descriptions-item label="地址">{{ item.data.url }}</el-descriptions-item>
              <el-descriptions-item label="说明">{{ item.data.desc }}</el-descriptions-item>
            </el-descriptions>
          </template>

          <template v-else>
            <div style="margin-bottom:12px">
              <div style="font-size:12px;color:#909399;margin-bottom:4px">Endpoint</div>
              <el-input v-model="item.edit.endpoint" :disabled="!item.editing" size="small" placeholder="https://ai.gitee.com/v1" />
            </div>
            <div style="margin-bottom:12px">
              <div style="font-size:12px;color:#909399;margin-bottom:4px">API Key <span style="color:#e65100">(当前: {{ item.data.apiKey }})</span></div>
              <el-input v-model="item.edit.apiKey" :disabled="!item.editing" size="small" placeholder="sk-xxx" show-password />
            </div>
            <div style="margin-bottom:12px">
              <div style="font-size:12px;color:#909399;margin-bottom:4px">Model</div>
              <el-input v-model="item.edit.model" :disabled="!item.editing" size="small" placeholder="DeepSeek-V4-Pro" />
            </div>
            <div style="display:flex;gap:8px">
              <el-button v-if="!item.editing" size="small" type="primary" @click="item.editing=true">编辑</el-button>
              <template v-else>
                <el-button size="small" type="success" @click="save(item)">保存</el-button>
                <el-button size="small" @click="item.editing=false; loadData()">取消</el-button>
              </template>
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'

const loading = ref(false)
const items = reactive([
  { key: 'trend', title: '趋势分析', editing: false, data: {}, edit: { endpoint: '', apiKey: '', model: '' } },
  { key: 'audit', title: 'AI 托管审核', editing: false, data: {}, edit: { endpoint: '', apiKey: '', model: '' } },
  { key: 'embedding', title: 'RAG 知识库 Embedding', editing: false, data: {}, edit: { endpoint: '', apiKey: '', model: '' } },
  { key: 'leaderboard', title: '榜单抓取', editing: false, data: {}, edit: {} }
])

const loadData = async () => {
  loading.value = true
  try {
    const r = await request.get('/admin/ai-config')
    if (r.data) {
      for (const item of items) {
        if (r.data[item.key]) {
          item.data = r.data[item.key]
          if (item.key !== 'leaderboard') {
            item.edit = { ...r.data[item.key] }
            // 编辑时清空 masked key，让用户输入新的
            item.edit.apiKey = ''
          }
        }
      }
    }
  } finally { loading.value = false }
}

const save = async (item) => {
  try {
    await request.post('/admin/ai-config/save', {
      prefix: 'ai:' + item.key,
      endpoint: item.edit.endpoint || item.data.endpoint,
      apiKey: item.edit.apiKey || '',
      model: item.edit.model || item.data.model
    })
    ElMessage.success('保存成功，调用即时生效')
    item.editing = false
    loadData()
  } catch { ElMessage.error('保存失败') }
}

onMounted(() => loadData())
</script>
