<template>
  <section class="candidate-tray" aria-label="候选 Issue">
    <div class="tray-summary">
      <span class="tray-icon"><CollectionTag /></span>
      <div>
        <strong>候选 Issue</strong>
        <small>{{ ready ? '已可以开始 AI 匹配' : `再选择 ${Math.max(0, minimum - items.length)} 项即可匹配` }}</small>
      </div>
      <b>{{ items.length }}/{{ maximum }}</b>
    </div>

    <div v-if="items.length" class="candidate-chips">
      <button
        v-for="item in items"
        :key="item.id"
        type="button"
        :title="titleOf(item)"
        @click="$emit('remove', item.id)"
      >
        <span>{{ titleOf(item) }}</span>
        <Close aria-hidden="true" />
        <span class="sr-only">移除</span>
      </button>
    </div>
    <p v-else class="tray-empty">在 Issue 卡片上选择“加入候选”，这里会持续保留你的选择。</p>

    <div class="tray-actions">
      <el-button v-if="items.length" text @click="$emit('clear')">清空</el-button>
      <el-button type="primary" :disabled="!ready" @click="$emit('proceed')">
        开始匹配 <ArrowRight />
      </el-button>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] },
  minimum: { type: Number, default: 3 },
  maximum: { type: Number, default: 20 },
})

defineEmits(['remove', 'clear', 'proceed'])

const ready = computed(() => props.items.length >= props.minimum)
const titleOf = item => item.titleZh || item.titleOriginal || '未命名 Issue'
</script>

<style scoped>
.candidate-tray {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  min-height: 72px;
  margin: 14px 0 18px;
  padding: 10px 12px;
  border: 1px solid var(--border-orange);
  border-radius: 8px;
  background: rgba(255, 253, 250, .96);
  box-shadow: 0 8px 22px rgba(92, 38, 15, .07);
  backdrop-filter: blur(10px);
}
.tray-summary { display: flex; min-width: 190px; align-items: center; gap: 10px; }
.tray-icon { display: grid; width: 36px; height: 36px; flex: 0 0 auto; place-items: center; border-radius: 7px; color: var(--brand-strong); background: var(--brand-soft); }
.tray-icon :deep(svg) { width: 18px; }
.tray-summary strong,.tray-summary small { display: block; }
.tray-summary strong { font-size: 13px; }
.tray-summary small { margin-top: 3px; color: var(--text-muted); font-size: 11px; }
.tray-summary b { margin-left: auto; color: var(--brand-strong); font: 800 13px/1 var(--font-mono); }
.candidate-chips { display: flex; min-width: 0; gap: 6px; overflow-x: auto; padding: 4px 2px; scrollbar-width: thin; }
.candidate-chips button { display: inline-flex; min-width: 150px; max-width: 220px; min-height: 36px; align-items: center; gap: 7px; padding: 0 9px; border: 1px solid var(--border-light); border-radius: 7px; background: #fff; color: var(--text-secondary); cursor: pointer; }
.candidate-chips button:hover,.candidate-chips button:focus-visible { border-color: var(--brand); color: var(--brand-strong); }
.candidate-chips span:first-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.candidate-chips :deep(svg) { width: 13px; flex: 0 0 auto; }
.tray-empty { min-width: 0; margin: 0; color: var(--text-muted); font-size: 12px; }
.tray-actions { display: flex; align-items: center; gap: 4px; }
.tray-actions :deep(.el-button) { min-height: 44px; margin: 0; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; }
@media (max-width: 900px) {
  .candidate-tray { position: static; grid-template-columns: minmax(0,1fr) auto; }
  .candidate-chips,.tray-empty { grid-row: 2; grid-column: 1 / -1; }
}
@media (max-width: 560px) {
  .candidate-tray { grid-template-columns: 1fr; }
  .tray-actions { justify-content: stretch; }
  .tray-actions :deep(.el-button--primary) { flex: 1; }
  .candidate-chips,.tray-empty { grid-row: auto; grid-column: auto; }
}
</style>
