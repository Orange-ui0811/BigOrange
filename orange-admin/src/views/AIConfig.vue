<template>
  <div class="config-page">
    <header class="page-heading">
      <div>
        <h3>AI 调用设置</h3>
        <p>项目创作、开源参与、翻译和审核使用独立配置；保存到 Redis 后即时用于新请求，Embedding 仍由服务器环境变量固定。</p>
      </div>
    </header>

    <el-alert
      v-if="!encryptionReady"
      title="尚未配置 AI_CONFIG_ENCRYPTION_KEY，动态 API Key 暂时不能保存"
      type="warning"
      :closable="false"
      show-icon
    />

    <div class="config-grid" v-loading="loading">
      <section v-for="item in editableItems" :key="item.key" class="config-panel">
        <div class="panel-heading">
          <div>
            <strong>{{ item.title }}</strong>
            <span>{{ item.data.source === 'REDIS' ? 'Redis 动态配置' : '环境变量兜底' }}</span>
          </div>
          <el-tag size="small" :type="item.data.source === 'REDIS' ? 'success' : 'info'">
            {{ item.data.version || 'env' }}
          </el-tag>
        </div>

        <el-form label-position="top">
          <el-form-item label="Endpoint">
            <el-input v-model="item.edit.endpoint" :disabled="!item.editing" />
          </el-form-item>
          <el-form-item label="API Key">
            <el-input
              v-model="item.edit.apiKey"
              :disabled="!item.editing"
              :placeholder="item.data.apiKey || '未配置'"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item label="Model">
            <el-input v-model="item.edit.model" :disabled="!item.editing" />
          </el-form-item>
        </el-form>

        <div class="panel-actions">
          <el-button :loading="item.testing" @click="test(item)">测试连接</el-button>
          <el-button v-if="!item.editing" type="primary" @click="item.editing = true">编辑</el-button>
          <template v-else>
            <el-button @click="cancel(item)">取消</el-button>
            <el-button type="primary" :loading="item.saving" @click="save(item)">保存新版本</el-button>
          </template>
        </div>
      </section>

      <section class="config-panel fixed-panel">
        <div class="panel-heading">
          <div><strong>RAG 知识库 Embedding</strong><span>启动时固定配置</span></div>
          <el-tag size="small" type="warning">需重启</el-tag>
        </div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="Endpoint">{{ embedding.endpoint || '未配置' }}</el-descriptions-item>
          <el-descriptions-item label="API Key">{{ embedding.apiKey || '未配置' }}</el-descriptions-item>
          <el-descriptions-item label="Model">{{ embedding.model || '未配置' }}</el-descriptions-item>
          <el-descriptions-item label="来源">服务器环境变量</el-descriptions-item>
        </el-descriptions>
        <p class="fixed-note">为避免向量维度不一致，管理端不能动态切换 Embedding。修改服务器环境变量后，需要重启应用并验收向量检索。</p>
        <el-button :loading="embeddingTesting" @click="testEmbedding">检查配置状态</el-button>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'

const loading = ref(false)
const embeddingTesting = ref(false)
const embedding = ref({})
const items = reactive([
  { key: 'project', title: '做自己的项目', data: {}, edit: {}, editing: false, saving: false, testing: false },
  { key: 'contribution', title: '参与开源项目', data: {}, edit: {}, editing: false, saving: false, testing: false },
  { key: 'trend', title: '文本生成与翻译', data: {}, edit: {}, editing: false, saving: false, testing: false },
  { key: 'audit', title: 'AI 托管审核', data: {}, edit: {}, editing: false, saving: false, testing: false },
])
const editableItems = computed(() => items)
const encryptionReady = computed(() => items.every(item => item.data.encryptionReady !== false))

const applyData = (item, data) => {
  item.data = data || {}
  item.edit = { endpoint: data?.endpoint || '', model: data?.model || '', apiKey: '' }
}

const loadData = async () => {
  loading.value = true
  try {
    const response = await request.get('/admin/ai-config')
    for (const item of items) applyData(item, response.data?.[item.key])
    embedding.value = response.data?.embedding || {}
  } finally {
    loading.value = false
  }
}

const cancel = item => {
  item.editing = false
  applyData(item, item.data)
}

const save = async item => {
  item.saving = true
  try {
    await request.post('/admin/ai-config/save', {
      prefix: `ai:${item.key}`,
      endpoint: item.edit.endpoint,
      model: item.edit.model,
      apiKey: item.edit.apiKey,
    })
    ElMessage.success('已保存为新配置版本，新任务将使用该版本')
    item.editing = false
    await loadData()
  } finally {
    item.saving = false
  }
}

const test = async item => {
  item.testing = true
  try {
    const response = await request.post('/admin/ai-config/test', { type: item.key }, { timeout: 130000 })
    ElMessage.success(response.data?.message || '连接成功')
  } finally {
    item.testing = false
  }
}

const testEmbedding = async () => {
  embeddingTesting.value = true
  try {
    const response = await request.post('/admin/ai-config/test', { type: 'embedding' })
    ElMessage.success(response.data?.message || '检查完成')
  } finally {
    embeddingTesting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.config-page { display: grid; gap: 16px; }
.page-heading h3 { margin: 0; color: #303133; font-size: 18px; }
.page-heading p { margin: 5px 0 0; color: #727985; font-size: 13px; }
.config-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.config-panel { min-width: 0; padding: 18px; border: 1px solid #e2e5ea; border-radius: 8px; background: #fff; }
.panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.panel-heading strong, .panel-heading span { display: block; }
.panel-heading strong { color: #303133; font-size: 15px; }
.panel-heading span { margin-top: 4px; color: #8b929d; font-size: 12px; }
.panel-actions { display: flex; justify-content: flex-end; gap: 8px; min-height: 32px; }
.fixed-note { margin: 14px 0; color: #606975; font-size: 12px; line-height: 1.65; }
@media (max-width: 1100px) { .config-grid { grid-template-columns: 1fr 1fr; } }
@media (max-width: 720px) { .config-grid { grid-template-columns: 1fr; } }
</style>
