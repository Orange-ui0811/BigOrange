import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

export const usePersonaStoryRail = () => {
  const storyRail = ref(null)
  const activeFocus = ref('overview')
  const compactMode = ref(false)
  let observer
  let mediaQuery
  const focusCopy = {
    overview: ['你的原创橙子', '每件装备对应一种稳定画像维度'], goal: ['方向罗盘', '目标决定朝哪里走，不定义你是谁'],
    interest: ['兴趣星环', '正在吸引你的主题，环绕而不固化'], skill: ['能力目镜', '只表达已经使用或完成过的能力'],
    resource: ['资源能量包', '时间、设备、预算和协作条件'], growth: ['成长足迹', '由你确认后才留下的真实行动'],
    recommendation: ['下一步入口', '用已确认画像连接平台现有内容'],
  }
  const focusMeta = computed(() => {
    const [title, caption] = focusCopy[activeFocus.value] || focusCopy.overview
    return { title, caption }
  })
  const observeNodes = () => {
    observer?.disconnect()
    if (compactMode.value || typeof IntersectionObserver === 'undefined') return
    observer = new IntersectionObserver(entries => {
      const visible = entries.filter(entry => entry.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0]
      if (visible?.target?.dataset.focus) activeFocus.value = visible.target.dataset.focus
    }, { rootMargin: '-28% 0px -40% 0px', threshold: [0, .25, .55] })
    document.querySelectorAll('.story-rail [data-focus]').forEach(element => observer.observe(element))
  }
  const scrollToFocus = code => {
    document.querySelector(`[data-focus="${code}"]`)?.scrollIntoView({
      behavior: compactMode.value ? 'auto' : 'smooth', block: 'center',
    })
    activeFocus.value = code
  }
  const sync = event => { compactMode.value = event.matches; nextTick(observeNodes) }
  onMounted(() => {
    mediaQuery = window.matchMedia('(max-width: 900px)')
    compactMode.value = mediaQuery.matches
    if (mediaQuery.addEventListener) mediaQuery.addEventListener('change', sync)
    else mediaQuery.addListener(sync)
  })
  onBeforeUnmount(() => {
    observer?.disconnect()
    if (mediaQuery?.removeEventListener) mediaQuery.removeEventListener('change', sync)
    else mediaQuery?.removeListener(sync)
  })
  return { storyRail, activeFocus, compactMode, focusMeta, observeNodes, scrollToFocus }
}
