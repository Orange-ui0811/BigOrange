<template>
  <details v-if="metrics" class="usage-panel">
    <summary><el-icon><DataLine /></el-icon><span>{{ usage.totalTokens.toLocaleString() }} Token{{ usage.complete ? '' : '（已返回部分）' }}</span><span v-if="duration">· {{ duration }}s</span></summary>
    <div class="usage-detail">
      <span>输入 <b>{{ usage.inputTokens.toLocaleString() }}</b></span><span>输出（含思考） <b>{{ usage.outputTokens.toLocaleString() }}</b></span>
      <span>模型尝试 <b>{{ usage.calls }}</b></span><span>费用 <b>{{ usage.costUsd === 0 ? '$0' : '未核实' }}</b></span>
      <p v-if="!usage.complete">部分失败调用没有返回用量，总数不是完整账单。</p>
      <p>这是本轮全部模型尝试的已报告用量；不包含未返回用量的 Embedding。</p>
    </div>
  </details>
</template>
<script setup>
import { computed } from 'vue'
import { customerUsage, customerDuration } from '../utils/customerUsage'
const props = defineProps({ metrics: [String, Object], started: String, finished: String, durationMs: [Number, String] })
const usage = computed(() => customerUsage(props.metrics))
const duration = computed(() => customerDuration(props.durationMs, props.started, props.finished))
</script>
<style scoped>
.usage-panel { margin-top: 10px; color: #818793; font-size: 12px; font-variant-numeric: tabular-nums; }
summary { display: flex; align-items: center; gap: 6px; width: fit-content; cursor: pointer; padding: 5px 7px; border-radius: 6px; }
summary:hover { background: #f1f2f4; color: #343b46; }.usage-detail { display: flex; flex-wrap: wrap; gap: 14px; padding: 12px; margin-top: 4px; border: 1px solid #e7e9ee; border-radius: 8px; }.usage-detail b { color: #404753; font-weight: 500; }.usage-detail p { flex-basis: 100%; margin: 0; }
</style>
