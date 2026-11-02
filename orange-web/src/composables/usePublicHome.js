import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/request'
import heroVisual from '../assets/home/orangevc-maker-marketplace.jpg'

export const usePublicHome = () => {
  const router = useRouter()
  const searchTerm = ref('')
  const works = ref([])
  const worksState = ref('loading')
  const hasSession = Boolean(sessionStorage.getItem('appToken'))

  const submitSearch = () => {
    const query = searchTerm.value ? { keyword: searchTerm.value } : undefined
    router.push({ path: '/market', query })
  }

  const openWork = id => router.push('/work/' + id)
  const workCover = work => work.coverUrl || heroVisual
  const workSummary = work => work.summary || work.detail || '打开详情，查看演示、说明和创作者信息。'
  const compactImagePosition = index => ['50% 38%', '50% 58%', '50% 72%'][index] || '50% 50%'

  const loadWorks = async () => {
    worksState.value = 'loading'
    try {
      const response = await request.get('/app/work/public', { params: { page: 1, size: 4 } })
      if (response.code !== 1) throw new Error(response.msg || '作品加载失败')
      works.value = response.data?.records || []
      worksState.value = 'ready'
    } catch {
      worksState.value = 'error'
    }
  }

  onMounted(loadWorks)
  return {
    searchTerm, works, worksState, hasSession, submitSearch, openWork,
    workCover, workSummary, compactImagePosition, loadWorks,
  }
}
