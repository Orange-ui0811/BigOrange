<template>
  <Teleport to="body">
    <div v-if="visible" class="tour-overlay">
      <!-- 镂空聚光灯 -->
      <div class="tour-spotlight" :style="spotlightStyle"></div>

      <!-- 高亮环 -->
      <div v-if="spot" class="tour-highlight-ring" :style="ringStyle"></div>

      <!-- 说话气泡 -->
      <div v-if="spot" class="tour-bubble" :style="bubbleStyle">
        <div class="tour-bubble-arrow" :class="'arrow-'+bubbleSide"></div>
        <div class="tour-mascot-row">
          <PixelOrangeMascot :expression="step.expression || 'smile'" style="width:36px;height:36px;flex-shrink:0" />
          <div class="tour-step-badge">{{ current + 1 }}/{{ steps.length }}</div>
        </div>
        <p class="tour-text">{{ step.text }}</p>
        <div class="tour-actions">
          <button class="tour-btn skip" @click="close">跳过教程</button>
          <button class="tour-btn next" @click="next">
            {{ current < steps.length - 1 ? '下一步 →' : '知道啦 🍊' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import PixelOrangeMascot from './PixelOrangeMascot.vue'

const props = defineProps({
  steps: { type: Array, required: true },       // [{ selector, text, expression }]
  storageKey: { type: String, required: true },  // localStorage key
  enabled: { type: Boolean, default: true }       // 外部可控制是否启用
})

const route = useRoute()
const current = ref(0)
const visible = ref(false)
const spot = ref(null)

const myId = sessionStorage.getItem('myId') || '0'

// 检查是否已完成
const checkDone = () => {
  return props.enabled && !localStorage.getItem('tour_done_' + props.storageKey + '_' + myId)
}

// 定位高亮元素
const locateElement = () => {
  const step = props.steps[current.value]
  if (!step) return null
  try {
    const el = document.querySelector(step.selector)
    if (!el || el.offsetParent === null) return null
    return el.getBoundingClientRect()
  } catch { return null }
}

// 更新位置
const updatePosition = () => {
  spot.value = locateElement()
}

// 聚光灯 style（四个遮罩块 + 中间镂空）
const spotlightStyle = computed(() => {
  if (!spot.value) return {}
  const r = spot.value
  const pad = 8
  const shadow = [
    `0 0 0 9999px rgba(0,0,0,.35)`,
  ].join(',')
  const cx = r.left + r.width / 2
  const cy = r.top + r.height / 2
  return {
    position: 'fixed',
    left: (r.left - pad) + 'px',
    top: (r.top - pad) + 'px',
    width: (r.width + pad * 2) + 'px',
    height: (r.height + pad * 2) + 'px',
    borderRadius: '10px',
    boxShadow: shadow,
    zIndex: 9998,
  }
})

// 高亮金环
const ringStyle = computed(() => {
  if (!spot.value) return {}
  const r = spot.value
  return {
    position: 'fixed',
    left: (r.left - 4) + 'px',
    top: (r.top - 4) + 'px',
    width: (r.width + 8) + 'px',
    height: (r.height + 8) + 'px',
    borderRadius: '12px',
    border: '3px solid var(--brand)',
    boxShadow: '0 0 16px rgba(243,112,33,.5)',
    animation: 'tour-pulse 1.5s ease-in-out infinite',
    zIndex: 9999,
    pointerEvents: 'none',
  }
})

// 气泡位置（放在四个方向中最合适的）
const bubbleSide = computed(() => {
  if (!spot.value) return 'bottom'
  const r = spot.value
  const vw = window.innerWidth
  const vh = window.innerHeight
  const bubbleH = 180 // 估算气泡高度
  // 空间不够 → 依次尝试 bottom / top / right / left
  if (r.bottom + bubbleH < vh - 20) return 'bottom'
  if (r.top - bubbleH > 20) return 'top'
  if (r.right + 330 < vw) return 'right'
  if (r.left > 330) return 'left'
  return 'bottom'
})

const bubbleStyle = computed(() => {
  if (!spot.value) return {}
  const r = spot.value
  const side = bubbleSide.value
  const style = { position: 'fixed', zIndex: 10000, maxWidth: '320px' }
  const vw = window.innerWidth
  const vh = window.innerHeight

  if (side === 'bottom') {
    style.top = Math.min(r.bottom + 14, vh - 200) + 'px'
    style.left = Math.max(16, Math.min(r.left + r.width / 2 - 150, vw - 336)) + 'px'
  } else if (side === 'top') {
    style.top = Math.max(16, r.top - 200) + 'px'
    style.left = Math.max(16, Math.min(r.left + r.width / 2 - 150, vw - 336)) + 'px'
  } else if (side === 'right') {
    style.top = Math.max(16, Math.min(r.top + r.height / 2 - 80, vh - 200)) + 'px'
    style.left = Math.min(r.right + 14, vw - 336) + 'px'
  } else if (side === 'left') {
    style.top = Math.max(16, Math.min(r.top + r.height / 2 - 80, vh - 200)) + 'px'
    style.left = Math.max(16, r.left - 336) + 'px'
  }
  return style
})

const step = computed(() => props.steps[current.value] || {})

const next = () => {
  if (current.value < props.steps.length - 1) {
    current.value++
    // 如果下一个元素不存在，继续跳到下下个
    let tries = 0
    while (tries < props.steps.length) {
      updatePosition()
      if (spot.value) break
      if (current.value < props.steps.length - 1) current.value++
      else { close(); return }
      tries++
    }
    if (!spot.value) close()
  } else {
    close()
  }
}

const close = () => {
  visible.value = false
  localStorage.setItem('tour_done_' + props.storageKey + '_' + myId, '1')
}

// 窗口 resize / scroll 重新定位
let posTimer = null
const updatePosDebounced = () => {
  clearTimeout(posTimer)
  posTimer = setTimeout(updatePosition, 10)
}

// 当路由变化时关闭（不标记完成）
watch(() => route.path, () => {
  if (visible.value) visible.value = false
})

// 轮询等待元素出现再启动
const tryStart = (attempts = 0) => {
  updatePosition()
  if (spot.value) {
    visible.value = true
    return
  }
  if (attempts < 30) setTimeout(() => tryStart(attempts + 1), 200)
}

onMounted(() => {
  if (checkDone()) {
    tryStart()
    window.addEventListener('resize', updatePosDebounced)
    window.addEventListener('scroll', updatePosDebounced, true)
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', updatePosDebounced)
  window.removeEventListener('scroll', updatePosDebounced, true)
})
</script>

<style scoped>
.tour-overlay {
  position: fixed; inset: 0;
  z-index: 9997;
  pointer-events: none;
}
.tour-spotlight { pointer-events: none; }
.tour-highlight-ring { pointer-events: none; }

.tour-bubble {
  pointer-events: auto;
  background: #fffef9;
  border: 1px solid var(--border-orange);
  border-radius: 16px;
  padding: 16px 18px;
  box-shadow: 0 12px 48px rgba(31,41,55,.15);
}
.tour-mascot-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.tour-step-badge {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--brand-strong);
  background: var(--brand-soft);
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 800;
}
.tour-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-primary);
}
.tour-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 14px;
}
.tour-btn {
  border: none;
  border-radius: 12px;
  padding: 7px 16px;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: all .15s;
}
.tour-btn.skip { background: transparent; color: var(--text-muted); }
.tour-btn.skip:hover { color: var(--text-primary); }
.tour-btn.next {
  background: var(--brand);
  color: #fff;
  box-shadow: 0 3px 0 rgba(197,83,18,.2);
}
.tour-btn.next:hover { background: var(--brand-strong); transform: translateY(-1px); }

@keyframes tour-pulse {
  0%,100% { box-shadow: 0 0 8px rgba(243,112,33,.3); }
  50% { box-shadow: 0 0 20px rgba(243,112,33,.6); }
}
</style>
