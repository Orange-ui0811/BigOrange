<template>
  <el-drawer
    :model-value="modelValue"
    class="work-inline-drawer"
    size="min(720px, 96vw)"
    :close-on-click-modal="!saving"
    :close-on-press-escape="!saving"
    :before-close="closeDrawer"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="drawer-heading">
        <span>已上传作品</span>
        <strong>直接修改作品内容</strong>
        <small>修改会保存到同一件作品，不会新建副本。</small>
      </div>
    </template>

    <div v-loading="loading" class="editor-body">
      <el-alert
        v-if="requiresReviewAgain"
        type="warning"
        :closable="false"
        show-icon
        title="此作品正在审核或已通过"
        description="保存修改后将退回草稿；你可以直接选择“保存并重新提交”。"
        class="review-alert"
      />

      <el-form label-position="top" @submit.prevent>
        <div class="form-grid">
          <el-form-item label="作品标题" required>
            <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="一句话说清楚作品价值" />
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="form.categoryId" clearable placeholder="选择分类" class="full-width">
              <el-option v-for="category in categories" :key="category.id" :label="category.name" :value="category.id" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="作品封面">
          <div class="media-edit-row">
            <div v-if="form.coverUrl" class="cover-preview-wrap">
              <el-image :src="form.coverUrl" fit="cover" class="cover-preview" />
              <button type="button" class="media-remove" aria-label="移除封面" @click="form.coverUrl = ''">×</button>
            </div>
            <el-upload
              :action="API + '/app/common/upload?dir=cover'"
              :headers="uploadHeaders"
              :show-file-list="false"
              :on-success="onCoverSuccess"
              accept="image/*"
            >
              <el-button>{{ form.coverUrl ? '更换封面' : '上传封面' }}</el-button>
            </el-upload>
          </div>
        </el-form-item>

        <el-form-item label="作品简介">
          <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明作品为谁解决什么问题" />
        </el-form-item>

        <el-form-item label="作品详情">
          <div class="detail-tools">
            <el-upload
              :action="API + '/app/common/upload?dir=detail'"
              :headers="uploadHeaders"
              :show-file-list="false"
              :on-success="onDetailImage"
              accept="image/*"
              multiple
            >
              <el-button size="small">插入详情图片</el-button>
            </el-upload>
            <span>图片会插入到详情 HTML 末尾</span>
          </div>
          <el-input v-model="form.detail" type="textarea" :rows="8" placeholder="详细介绍功能、完成度、使用方式和交付边界" />
        </el-form-item>

        <div class="form-grid">
          <el-form-item label="演示链接">
            <el-input v-model="form.demoUrl" placeholder="https://example.com/demo" />
          </el-form-item>
          <el-form-item label="公开参考价">
            <el-input v-model="form.price" type="number" min="0" step="0.01" placeholder="留空表示价格面议">
              <template #prepend>¥</template>
            </el-input>
            <span class="field-help">仅用于展示，不改变现有交易流程。</span>
          </el-form-item>
        </div>

        <el-form-item label="作品标签">
          <el-input v-model="form.tags" placeholder="例如：Vue, AI, 数据可视化" />
        </el-form-item>

        <el-form-item label="作品视频">
          <div class="video-controls">
            <el-upload
              :action="API + '/app/common/upload/video'"
              :headers="uploadHeaders"
              :show-file-list="false"
              :before-upload="beforeVideoUpload"
              :on-progress="onVideoProgress"
              :on-success="onVideoSuccess"
              :on-error="onVideoError"
              accept=".mp4,.mov,.m4v,.webm,video/mp4,video/quicktime,video/x-m4v,video/webm"
            >
              <el-button :loading="videoUploading">{{ form.videoUrl ? '重新上传视频' : '上传视频' }}</el-button>
            </el-upload>
            <el-button v-if="form.videoUrl" type="danger" plain @click="removeVideo">移除视频</el-button>
          </div>
          <el-progress v-if="videoUploading" :percentage="videoProgress" :stroke-width="8" class="video-progress" />
          <p v-if="legacyVideoUrl && !form.videoUrl" class="legacy-tip">原视频为旧版外部链接，请重新上传文件；保存后将移除旧链接。</p>
          <VideoShowcase v-if="form.videoUrl" :url="form.videoUrl" :poster="form.coverUrl" :title="form.title || '作品预览'" compact class="video-preview" />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="drawer-actions">
        <el-button :disabled="saving" @click="closeDrawer">取消</el-button>
        <el-button :loading="saving && saveAction === 'draft'" :disabled="saving && saveAction !== 'draft'" @click="save(false)">保存修改</el-button>
        <el-button type="primary" :loading="saving && saveAction === 'review'" :disabled="saving && saveAction !== 'review'" @click="save(true)">保存并重新提交</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'
import { inspectExternalUrl } from '../utils/safeExternalUrl'
import VideoShowcase from './VideoShowcase.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  workId: { type: [Number, String], default: null },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const API = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
const uploadHeaders = { 'App-Token': sessionStorage.getItem('appToken') || '' }
const uploadedVideoPattern = /^\/(?:uploads\/video|api\/media\/video)\/(?:[1-9][0-9]*\/)?[0-9a-f-]{36}\.(mp4|mov|m4v|webm)$/i
const loading = ref(false)
const saving = ref(false)
const saveAction = ref('')
const categories = ref([])
const auditStatus = ref('DRAFT')
const videoUploading = ref(false)
const videoProgress = ref(0)
const legacyVideoUrl = ref('')
const form = reactive({
  title: '', summary: '', detail: '', categoryId: null, coverUrl: '', demoUrl: '',
  videoUrl: '', tags: '', price: '',
})

const requiresReviewAgain = computed(() => ['PENDING_REVIEW', 'APPROVED'].includes(auditStatus.value))

const resetForm = () => {
  Object.assign(form, { title: '', summary: '', detail: '', categoryId: null, coverUrl: '', demoUrl: '', videoUrl: '', tags: '', price: '' })
  auditStatus.value = 'DRAFT'
  legacyVideoUrl.value = ''
  videoProgress.value = 0
}

const load = async () => {
  if (!props.workId) return
  loading.value = true
  resetForm()
  try {
    const [workResponse, categoryResponse] = await Promise.all([
      request.get(`/app/work/${props.workId}`),
      request.get('/app/category?type=WORK'),
    ])
    if (categoryResponse.code === 1) categories.value = categoryResponse.data || []
    if (workResponse.code !== 1) throw new Error(workResponse.msg || '作品加载失败')
    const work = workResponse.data || {}
    Object.assign(form, {
      title: work.title || '',
      summary: work.summary || '',
      detail: work.detail || '',
      categoryId: work.categoryId ?? null,
      coverUrl: work.coverUrl || '',
      demoUrl: work.demoUrl || '',
      tags: work.tags || '',
      price: work.saleOptions?.[0]?.price ?? '',
    })
    auditStatus.value = work.auditStatus || 'DRAFT'
    if (uploadedVideoPattern.test(work.videoUrl || '')) form.videoUrl = work.videoUrl
    else legacyVideoUrl.value = work.videoUrl || ''
  } catch (error) {
    ElMessage.error(error?.message || '作品加载失败')
  } finally {
    loading.value = false
  }
}

watch(() => [props.modelValue, props.workId], ([visible, id], previous = []) => {
  if (visible && id && (!previous[0] || id !== previous[1])) load()
})

const closeDrawer = done => {
  if (saving.value || videoUploading.value) return
  if (typeof done === 'function') done()
  else emit('update:modelValue', false)
}

const onCoverSuccess = response => {
  if (response?.code === 1 && response.data) form.coverUrl = response.data
  else ElMessage.error(response?.msg || '封面上传失败')
}
const onDetailImage = response => {
  if (response?.code === 1 && response.data) {
    form.detail = `${form.detail || ''}<img src="${response.data}" style="max-width:100%">\n`
  } else ElMessage.error(response?.msg || '详情图片上传失败')
}
const beforeVideoUpload = file => {
  const extension = (file.name.match(/\.[^.]+$/)?.[0] || '').toLowerCase()
  const allowedExtensions = ['.mp4', '.mov', '.m4v', '.webm']
  const allowedTypes = ['video/mp4', 'video/quicktime', 'video/x-m4v', 'video/webm']
  if (!allowedExtensions.includes(extension) || !allowedTypes.includes(file.type)) {
    ElMessage.warning('仅支持 MP4、MOV、M4V、WebM 视频文件')
    return false
  }
  if (file.size > 200 * 1024 * 1024) {
    ElMessage.warning('视频文件不能超过 200MB')
    return false
  }
  videoProgress.value = 0
  videoUploading.value = true
  return true
}
const onVideoProgress = event => {
  videoUploading.value = true
  videoProgress.value = Math.min(99, Math.round(event.percent || 0))
}
const onVideoSuccess = response => {
  videoUploading.value = false
  videoProgress.value = 100
  if (response?.code === 1 && uploadedVideoPattern.test(response.data || '')) {
    form.videoUrl = response.data
    legacyVideoUrl.value = ''
    ElMessage.success('视频上传成功')
  } else ElMessage.error(response?.msg || '视频上传失败')
}
const onVideoError = () => {
  videoUploading.value = false
  videoProgress.value = 0
  ElMessage.error('视频上传失败，请检查文件格式、大小或网络连接')
}
const removeVideo = () => {
  form.videoUrl = ''
  legacyVideoUrl.value = ''
  videoProgress.value = 0
}

const save = async submitReview => {
  if (!form.title.trim()) return ElMessage.warning('作品标题必填')
  if (videoUploading.value) return ElMessage.warning('请等待视频上传完成')
  if (form.demoUrl.trim() && !inspectExternalUrl(form.demoUrl).safe) return ElMessage.warning('演示链接仅支持安全的 HTTPS 公网地址')
  if (form.price !== '' && form.price !== null && (!Number.isFinite(Number(form.price)) || Number(form.price) < 0)) {
    return ElMessage.warning('请填写有效的非负价格')
  }
  if (requiresReviewAgain.value) {
    try {
      await ElMessageBox.confirm(
        submitReview ? '修改后将退回草稿并立即重新提交审核，是否继续？' : '修改后将退回草稿，若已公开展示也会暂时从市场移除，是否继续？',
        '确认修改',
        { confirmButtonText: '继续保存', cancelButtonText: '取消', type: 'warning' },
      )
    } catch { return }
  }

  saving.value = true
  saveAction.value = submitReview ? 'review' : 'draft'
  try {
    const payload = {
      title: form.title.trim(), summary: form.summary, detail: form.detail,
      categoryId: form.categoryId, coverUrl: form.coverUrl, demoUrl: form.demoUrl.trim(),
      videoUrl: form.videoUrl, tags: form.tags,
      saleOptions: form.price === '' || form.price === null
        ? []
        : [{ saleType: 'FIXED_PRICE', price: Number(form.price), description: '公开参考价格' }],
    }
    const updateResponse = await request.put(`/app/work/${props.workId}`, payload)
    if (updateResponse.code !== 1) throw new Error(updateResponse.msg || '作品保存失败')
    if (submitReview) {
      const reviewResponse = await request.post(`/app/work/${props.workId}/submit`)
      if (reviewResponse.code !== 1) throw new Error(reviewResponse.msg || '提交审核失败')
    }
    ElMessage.success(submitReview ? '已保存并重新提交审核' : '作品修改已保存')
    emit('saved')
    emit('update:modelValue', false)
  } catch (error) {
    ElMessage.error(error?.message || '作品保存失败')
  } finally {
    saving.value = false
    saveAction.value = ''
  }
}
</script>

<style scoped>
.editor-body { min-height: 420px; padding: 0 4px 30px; }
.drawer-heading { display: grid; gap: 3px; color: #3a302a; }
.drawer-heading span { color: #f37021; font: 700 11px/1.4 "Courier New", monospace; letter-spacing: 1.4px; text-transform: uppercase; }
.drawer-heading strong { font-size: 20px; line-height: 1.35; }
.drawer-heading small { color: #847a71; font-size: 12px; font-weight: 400; }
.review-alert { margin-bottom: 18px; }
.form-grid { display: grid; grid-template-columns: 1.25fr .75fr; gap: 16px; }
.full-width { width: 100%; }
.media-edit-row, .video-controls, .detail-tools { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; width: 100%; }
.cover-preview-wrap { position: relative; width: 150px; height: 94px; overflow: hidden; border: 1px solid #e6ddd4; background: #faf9f7; }
.cover-preview { width: 100%; height: 100%; }
.media-remove { position: absolute; top: 5px; right: 5px; width: 24px; height: 24px; border: 0; border-radius: 50%; color: white; background: rgba(32, 26, 21, .72); cursor: pointer; }
.detail-tools { margin-bottom: 8px; }
.detail-tools span, .field-help { color: #91867d; font-size: 12px; }
.field-help { display: block; margin-top: 6px; }
.video-progress { width: 100%; margin-top: 10px; }
.legacy-tip { width: 100%; margin: 10px 0 0; color: #b66824; font-size: 12px; line-height: 1.6; }
.video-preview { width: 100%; margin-top: 12px; }
.drawer-actions { display: flex; justify-content: flex-end; gap: 8px; }
:deep(.el-form-item__label) { color: #51473f; font-weight: 650; }
:deep(.el-input__wrapper), :deep(.el-textarea__inner), :deep(.el-button), :deep(.el-alert) { border-radius: 2px; }
@media (max-width: 640px) {
  .form-grid { grid-template-columns: 1fr; gap: 0; }
  .drawer-actions { display: grid; grid-template-columns: 1fr 1fr; }
  .drawer-actions .el-button { margin-left: 0; }
  .drawer-actions .el-button:last-child { grid-column: 1 / -1; }
}
</style>
