<template>
  <el-popover placement="bottom-end" width="340" trigger="click">
    <template #reference>
      <el-button class="trend-history-trigger" size="small" text>
        {{ history.length ? `记录 (${history.length})` : '记录' }}
      </el-button>
    </template>
    <div class="history-popover">
      <div v-if="!history.length" class="history-empty">暂无记录</div>
      <button
        v-for="(item, index) in history"
        :key="item.id || index"
        type="button"
        class="history-item"
        @click="emit('select', item)"
      >
        <span class="history-item-text">
          <strong class="history-topic">{{ title(item) }}</strong>
          <small class="history-time">{{ String(item.time || '').substring(0, 16) }}</small>
        </span>
        <span
          class="history-delete"
          role="button"
          tabindex="0"
          aria-label="删除这条趋势分析记录"
          @click.stop="emit('delete', index)"
          @keydown.enter.stop="emit('delete', index)"
        >✕</span>
      </button>
    </div>
    <div v-if="history.length" class="history-clear-row">
      <el-button size="small" type="danger" text @click="emit('clear')">清空全部</el-button>
    </div>
  </el-popover>
</template>

<script setup>
defineProps({ history: { type: Array, default: () => [] } })
const emit = defineEmits(['select', 'delete', 'clear'])

const title = item => {
  if (Array.isArray(item.topics) && item.topics.length) return item.topics.join('、')
  if (item.usePersona) return '橙子画像分析'
  return '综合趋势分析'
}
</script>

<style scoped>
.trend-history-trigger{font-weight:750}.history-popover{display:grid;max-height:360px;overflow:auto}.history-empty{padding:24px;color:#8a7568;text-align:center}.history-item{display:flex;width:100%;align-items:center;justify-content:space-between;gap:12px;padding:11px 8px;border:0;border-bottom:1px solid #f1e5dc;background:#fff;color:#3d251a;text-align:left;cursor:pointer}.history-item:hover,.history-item:focus-visible{background:#fff7ed;outline:2px solid rgba(251,146,60,.18)}.history-item-text,.history-topic,.history-time{display:block;min-width:0}.history-topic{overflow:hidden;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.history-time{margin-top:4px;color:#9a8375;font-size:10px}.history-delete{display:grid;width:27px;height:27px;flex:0 0 27px;place-items:center;border-radius:7px;color:#b45309}.history-delete:hover,.history-delete:focus-visible{background:#fee2e2;color:#b91c1c;outline:none}.history-clear-row{display:flex;justify-content:flex-end;padding-top:8px}
</style>
