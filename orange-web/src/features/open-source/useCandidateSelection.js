import { computed, ref } from 'vue'

const MAX_CANDIDATES = 20

const candidateSnapshot = issue => ({
  id: issue.id,
  titleZh: issue.titleZh || '',
  titleOriginal: issue.titleOriginal || '',
  repositoryFullName: issue.repositoryFullName || '',
  participationStatus: issue.participationStatus || '',
  difficulty: issue.difficulty || '',
  summaryZh: issue.summaryZh || '',
})

export const useCandidateSelection = () => {
  const accountId = sessionStorage.getItem('myId') || 'anonymous'
  const storageKey = `orangevc:open-source:candidates:${accountId}`
  const candidates = ref([])

  const persist = () => {
    sessionStorage.setItem(storageKey, JSON.stringify(candidates.value))
  }

  const restore = () => {
    try {
      const parsed = JSON.parse(sessionStorage.getItem(storageKey) || '[]')
      candidates.value = Array.isArray(parsed)
        ? parsed.filter(item => Number.isFinite(Number(item?.id))).slice(0, MAX_CANDIDATES)
        : []
    } catch {
      candidates.value = []
    }
  }

  const selectedIds = computed(() => candidates.value.map(item => Number(item.id)))
  const includes = id => selectedIds.value.includes(Number(id))

  const toggle = issue => {
    if (!issue?.id) return
    if (includes(issue.id)) {
      candidates.value = candidates.value.filter(item => Number(item.id) !== Number(issue.id))
    } else {
      candidates.value = [...candidates.value, candidateSnapshot(issue)].slice(-MAX_CANDIDATES)
    }
    persist()
  }

  const remove = id => {
    candidates.value = candidates.value.filter(item => Number(item.id) !== Number(id))
    persist()
  }

  const clear = () => {
    candidates.value = []
    sessionStorage.removeItem(storageKey)
  }

  const hydrate = issues => {
    if (!Array.isArray(issues) || !issues.length || !candidates.value.length) return
    const fresh = new Map(issues.map(issue => [Number(issue.id), issue]))
    let changed = false
    candidates.value = candidates.value.map(item => {
      const issue = fresh.get(Number(item.id))
      if (!issue) return item
      changed = true
      return candidateSnapshot(issue)
    })
    if (changed) persist()
  }

  restore()

  return {
    candidates,
    selectedIds,
    includes,
    toggle,
    remove,
    clear,
    hydrate,
    maxCandidates: MAX_CANDIDATES,
  }
}
