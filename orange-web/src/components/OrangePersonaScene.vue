<template>
  <div ref="host" class="orange-scene" :class="{ unavailable, compact }">
    <div v-if="!unavailable" class="scene-zoom" aria-label="3D 视图缩放控制">
      <button type="button" aria-label="放大橙子" title="放大" @click="adjustZoom(-0.12)">＋</button>
      <span aria-live="polite">{{ zoomPercent }}%</span>
      <button type="button" aria-label="缩小橙子" title="缩小" @click="adjustZoom(0.12)">−</button>
      <button type="button" class="reset-zoom" aria-label="重置缩放" title="重置缩放" @click="resetZoom">复位</button>
      <button type="button" class="reset-zoom" aria-label="查看橙子全身" title="查看全身" @click="showFullBody">全身</button>
    </div>
    <div v-if="unavailable" class="scene-fallback" role="status">
      <PersonaOrangeBadge :equipment="equipment" />
      <strong>3D 预览暂不可用</strong>
      <span>画像编辑与装备设置仍可正常使用</span>
    </div>
    <div v-else class="scene-status" aria-live="polite">{{ ready ? '' : '正在唤醒橙子…' }}</div>
  </div>
</template>

<script setup>
import PersonaOrangeBadge from './PersonaOrangeBadge.vue'
import { usePersonaScene } from '../features/persona-scene/usePersonaScene'

const props = defineProps({
  equipment: { type: Array, default: () => [] },
  activeFocus: { type: String, default: 'overview' },
  appearance: { type: Object, default: () => ({}) },
  compact: { type: Boolean, default: false },
})
const emit = defineEmits(['ready', 'reset-view'])
const { host, ready, unavailable, zoomPercent, adjustZoom, resetZoom, showFullBody } = usePersonaScene(props, emit)
</script>

<style scoped>
.orange-scene{position:relative;width:100%;height:100%;min-height:560px;overflow:hidden;border-radius:26px;background:#fffbf5}.orange-scene :deep(canvas){display:block;width:100%;height:100%;cursor:grab;outline:none}.orange-scene :deep(canvas:active){cursor:grabbing}.orange-scene :deep(canvas:focus-visible){box-shadow:inset 0 0 0 3px rgba(243,112,33,.55)}.scene-zoom{position:absolute;z-index:4;top:20px;right:20px;display:flex;align-items:center;gap:4px;padding:5px;border:1px solid rgba(234,216,201,.95);border-radius:12px;background:rgba(255,255,255,.88);box-shadow:0 8px 26px rgba(87,45,20,.1);backdrop-filter:blur(12px)}.scene-zoom button{display:grid;width:34px;height:34px;place-items:center;border:0;border-radius:8px;background:#fff7ed;color:#9a3412;font:900 18px/1 var(--font-mono);cursor:pointer}.scene-zoom button:hover,.scene-zoom button:focus-visible{background:#ffedd5;outline:2px solid rgba(243,112,33,.3)}.scene-zoom span{min-width:44px;color:#7c2d12;font:800 10px/1 var(--font-mono);text-align:center}.scene-zoom .reset-zoom{width:auto;padding:0 9px;font-size:10px}.scene-status{position:absolute;inset:0;display:grid;place-items:center;color:#9a3412;font:800 12px/1.4 var(--font-mono);pointer-events:none}.scene-fallback{display:grid;height:100%;place-content:center;justify-items:center;gap:9px;color:var(--text-secondary);text-align:center}.scene-fallback strong{margin-top:8px;color:var(--text-primary)}.scene-fallback span{font-size:12px}
.orange-scene.compact{min-height:386px;border-radius:19px}.orange-scene.compact .scene-zoom{top:12px;right:12px;transform:scale(.82);transform-origin:top right}
@media(max-width:1100px){.orange-scene{min-height:500px}}
</style>
