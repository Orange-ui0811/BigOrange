<template>
  <div class="analysis-progress" role="status" aria-live="polite">
    <span class="progress-mark"><Loading /></span>
    <div>
      <strong>{{ title }}</strong>
      <p>{{ message }}</p>
    </div>
    <time>{{ elapsedText }}</time>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  active: { type: Boolean, default: false },
  title: { type: String, default: 'AI 正在分析' },
  message: { type: String, default: '正在整理候选信息，请保持当前页面打开。' },
})

const elapsed = ref(0)
let timer

const stop = () => {
  if (timer) window.clearInterval(timer)
  timer = undefined
}

watch(() => props.active, active => {
  stop()
  elapsed.value = 0
  if (active) timer = window.setInterval(() => { elapsed.value += 1 }, 1000)
}, { immediate: true })

onBeforeUnmount(stop)

const elapsedText = computed(() => {
  const minutes = Math.floor(elapsed.value / 60)
  const seconds = String(elapsed.value % 60).padStart(2, '0')
  return minutes ? `${minutes}:${seconds}` : `${seconds} 秒`
})
</script>

<style scoped>
.analysis-progress { display: grid; min-height: 170px; grid-template-columns: 42px minmax(0,1fr) auto; align-items: center; gap: 14px; padding: 22px; border: 1px solid var(--border-orange); border-radius: 8px; background: var(--brand-light); }
.progress-mark { display: grid; width: 42px; height: 42px; place-items: center; border-radius: 8px; color: var(--brand-strong); background: #fff; }
.progress-mark :deep(svg) { width: 20px; animation: spin 1.2s linear infinite; }
.analysis-progress strong { font-size: 15px; }
.analysis-progress p { margin: 6px 0 0; color: var(--text-secondary); font-size: 13px; line-height: 1.6; }
.analysis-progress time { color: var(--brand-strong); font: 800 12px/1 var(--font-mono); }
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .progress-mark :deep(svg) { animation: none; } }
</style>
