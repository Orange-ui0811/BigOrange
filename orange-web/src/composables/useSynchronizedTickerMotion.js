import { nextTick, onBeforeUnmount, onMounted, reactive } from 'vue'

export const WEEKLY_REFERENCE_SECONDS = 30

export const calculateTickerDurations = (distances, referenceSeconds = WEEKLY_REFERENCE_SECONDS) => {
  const referenceDistance = Number(distances.weekly || 0)
  if (referenceDistance <= 0) return Object.fromEntries(Object.keys(distances).map(key => [key, referenceSeconds]))
  const pixelsPerSecond = referenceDistance / referenceSeconds
  return Object.fromEntries(Object.entries(distances).map(([key, distance]) => [
    key,
    Number(distance) > 0 ? Math.max(1, Number((Number(distance) / pixelsPerSecond).toFixed(2))) : referenceSeconds,
  ]))
}

export const useSynchronizedTickerMotion = () => {
  const elements = new Map()
  const durations = reactive({ weekly: WEEKLY_REFERENCE_SECONDS, beginner: WEEKLY_REFERENCE_SECONDS, career: WEEKLY_REFERENCE_SECONDS })
  let resizeObserver
  let animationFrame

  const measure = () => {
    const distances = Object.fromEntries([...elements.entries()].map(([key, element]) => [key, element.scrollWidth / 2]))
    Object.assign(durations, calculateTickerDurations(distances))
  }

  const scheduleMeasure = () => nextTick(() => {
    if (typeof requestAnimationFrame !== 'function') { measure(); return }
    cancelAnimationFrame(animationFrame)
    animationFrame = requestAnimationFrame(measure)
  })

  const setTrackElement = (key, element) => {
    const previous = elements.get(key)
    if (previous && resizeObserver) resizeObserver.unobserve(previous)
    if (!element) { elements.delete(key); scheduleMeasure(); return }
    elements.set(key, element)
    if (resizeObserver) resizeObserver.observe(element)
    scheduleMeasure()
  }

  const tickerStyle = key => ({ '--ticker-duration': `${durations[key] || WEEKLY_REFERENCE_SECONDS}s` })

  onMounted(() => {
    if (typeof ResizeObserver === 'function') {
      resizeObserver = new ResizeObserver(scheduleMeasure)
      elements.forEach(element => resizeObserver.observe(element))
    }
    scheduleMeasure()
  })
  onBeforeUnmount(() => {
    resizeObserver?.disconnect()
    if (typeof cancelAnimationFrame === 'function') cancelAnimationFrame(animationFrame)
  })

  return { durations, setTrackElement, tickerStyle }
}
