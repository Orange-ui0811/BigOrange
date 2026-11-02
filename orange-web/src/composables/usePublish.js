import { ref, reactive, watch, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageTour from '../components/PageTour.vue'
import VideoShowcase from '../components/VideoShowcase.vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { DEFAULT_REWARDS, getRewardPricing } from '../api/rewards'
import { inspectExternalUrl } from '../utils/safeExternalUrl'

export const usePublish = () => {
const API = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
const route = useRoute()
const router = useRouter()
const workReward = ref(DEFAULT_REWARDS.work)
const workRewardText = computed(() => workReward.value > 0
  ? `发布一个作品并通过审核，可获得 ${workReward.value} segments`
  : '作品通过审核后当前暂不发放 segments 奖励')
const tourSteps = [
  { selector: '.publish-choices', text: '选左边的「发布作品」展示你的 AI 产品，或选右边的「发布需求」找人帮你做。每边都列了要准备什么～', expression: 'wink' },
  { selector: '.publish-note.strong', text: '发布的流程：填写内容→提交审核→审核通过→公开展示→有人感兴趣→聊天沟通。', expression: 'smile' },
  { selector: '.publish-note:last-child', text: '当前 MVP 只做作品展示、需求展示、审核、收藏、评论和聊天。后续会逐步完善交易和定制功能～', expression: 'smile' },
]

const isEdit = !!route.params.id
const showForm = computed(() => isEdit || !!route.query.type)
const tab = ref(isEdit ? (route.params.type === 'work' ? 'work' : 'requirement') : (route.query.type || 'work'))
const cats = ref([])
const editId = ref(isEdit ? route.params.id : null)
const loading = ref(false)
const saveAction = ref('')
const form = reactive({ title: '', summary: '', detail: '', categoryId: null, price: '', budgetMin: '', budgetMax: '', coverUrl: '', demoUrl: '', videoUrl: '' })
const videoUploading = ref(false)
const videoProgress = ref(0)
const legacyVideoUrl = ref('')
const uploadHeaders = { 'App-Token': sessionStorage.getItem('appToken') || '' }
const uploadedVideoPattern = /^\/(?:uploads\/video|api\/media\/video)\/(?:[1-9][0-9]*\/)?[0-9a-f-]{36}\.(mp4|mov|m4v|webm)$/i

const onCoverSuccess = (res) => { if (res.code === 1) form.coverUrl = res.data }
const onDetailImage = (res) => {
  if (res.code === 1 && res.data) {
    const url = res.data.startsWith('http') ? res.data : res.data
    form.detail = (form.detail || '') + '<img src="' + url + '" style="max-width:100%">\n'
  }
}
const beforeVideoUpload = (file) => {
  const ext = (file.name.match(/\.[^.]+$/)?.[0] || '').toLowerCase()
  const allowedExtensions = ['.mp4', '.mov', '.m4v', '.webm']
  const allowedTypes = ['video/mp4', 'video/quicktime', 'video/x-m4v', 'video/webm']
  if (!allowedExtensions.includes(ext) || !allowedTypes.includes(file.type)) {
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
const onVideoProgress = (event) => {
  videoUploading.value = true
  videoProgress.value = Math.min(99, Math.round(event.percent || 0))
}
const onVideoSuccess = (res) => {
  videoUploading.value = false
  videoProgress.value = 100
  if (res?.code === 1 && uploadedVideoPattern.test(res.data || '')) {
    form.videoUrl = res.data
    legacyVideoUrl.value = ''
    ElMessage.success('视频上传成功')
    return
  }
  ElMessage.error(res?.msg || '视频上传失败')
}
const onVideoError = () => {
  videoUploading.value = false
  videoProgress.value = 0
  ElMessage.error('视频上传失败，请检查文件格式、大小或网络连接')
}
const removeVideo = () => {
  form.videoUrl = ''
  videoProgress.value = 0
}

const startPublish = (type) => {
  tab.value = type
  router.push({ path: '/publish', query: { type } })
}
const backToChoice = () => {
  router.push('/publish')
}
const onTabChange = () => {
  if (!isEdit) form.categoryId = null
  loadCats()
}

const loadCats = async () => {
  try { const res = await request.get('/app/category?type=' + (tab.value==='work'?'WORK':'REQUIREMENT')); if (res.code===1) cats.value = res.data } catch {}
}
watch(tab, () => { if (!isEdit) form.categoryId = null; loadCats() })
onMounted(async () => {
  getRewardPricing().then(value => { workReward.value = value.work })
  loadCats()
  if (isEdit) {
    loading.value = true
    try {
      const api = tab.value === 'work' ? `/app/work/${editId.value}` : `/app/requirement/${editId.value}`
      const res = await request.get(api)
      if (res.code === 1) {
        const d = res.data
        form.title = d.title || ''; form.summary = d.summary || ''; form.detail = d.detail || ''
        form.categoryId = d.categoryId || null; form.coverUrl = d.coverUrl || ''; form.demoUrl = d.demoUrl || ''
        if (uploadedVideoPattern.test(d.videoUrl || '')) form.videoUrl = d.videoUrl
        else { form.videoUrl = ''; legacyVideoUrl.value = d.videoUrl || '' }
        form.price = d.saleOptions?.[0]?.price || ''
        form.budgetMin = d.budgetMin || ''; form.budgetMax = d.budgetMax || ''
      }
    } catch {} finally { loading.value = false }
  }
})

const submit = async (submitReview = false) => {
  if (!form.title.trim()) return ElMessage.warning('标题必填')
  if (videoUploading.value) return ElMessage.warning('请等待视频上传完成')
  if (tab.value === 'work' && form.demoUrl.trim() && !inspectExternalUrl(form.demoUrl).safe) {
    return ElMessage.warning('演示链接仅支持安全的 HTTPS 公网地址')
  }
  saveAction.value = submitReview ? 'review' : 'draft'
  loading.value = true
  try {
    const baseApi = tab.value === 'work' ? '/app/work' : '/app/requirement'
    const api = isEdit ? `${baseApi}/${editId.value}` : baseApi
    const data = tab.value === 'work'
      ? {
          title: form.title,
          summary: form.summary,
          detail: form.detail,
          categoryId: form.categoryId,
          coverUrl: form.coverUrl,
          demoUrl: form.demoUrl,
          videoUrl: form.videoUrl,
          saleOptions: form.price === '' || form.price === null
            ? []
            : [{ saleType: 'FIXED_PRICE', price: Number(form.price), description: '公开参考价格' }],
        }
      : { title: form.title, detail: form.detail, categoryId: form.categoryId, budgetMin: form.budgetMin||null, budgetMax: form.budgetMax||null }
    const res = isEdit ? await request.put(api, data) : await request.post(api, data)
    if (res.code === 1) {
      const savedId = isEdit ? editId.value : res.data?.id
      if (submitReview) {
        if (!savedId) throw new Error('保存成功，但未返回内容 ID')
        const reviewRes = await request.post(`${baseApi}/${savedId}/submit`)
        if (reviewRes.code === 1) {
          ElMessage.success('已保存并提交审核')
          await router.push('/me/works')
        }
      } else {
        ElMessage.success('草稿已保存')
        if (!isEdit) await router.push('/me/works')
      }
    }
  } finally {
    loading.value = false
    saveAction.value = ''
  }
}
  return { PageTour, VideoShowcase, API, workRewardText, tourSteps, isEdit, showForm, tab, cats,
    loading, saveAction, form, videoUploading, videoProgress, legacyVideoUrl, uploadHeaders,
    onCoverSuccess, onDetailImage, beforeVideoUpload, onVideoProgress, onVideoSuccess, onVideoError,
    removeVideo, startPublish, backToChoice, onTabChange, submit }}
