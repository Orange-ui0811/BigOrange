<template>
  <details class="transform-editor">
    <summary>调整大小、位置与旋转</summary>
    <div class="transform-grid">
      <label v-for="control in controls" :key="control.key">
        <span><strong>{{ control.label }}</strong><output>{{ format(local[control.key], control) }}</output></span>
        <el-slider v-model="local[control.key]" :min="control.min" :max="control.max" :step="control.step" :show-tooltip="false" @input="preview" @change="commit" />
      </label>
    </div>
    <button type="button" class="transform-reset" @click="reset">恢复饰品默认位置</button>
  </details>
</template>

<script setup>
import { reactive, watch } from 'vue'

const props = defineProps({ modelValue: { type: Object, default: () => ({}) } })
const emit = defineEmits(['update:modelValue', 'change'])
const defaults = { scale: 1, offsetX: 0, offsetY: 0, offsetZ: 0, rotationX: 0, rotationY: 0, rotationZ: 0 }
const local = reactive({ ...defaults, ...props.modelValue })
const controls = [
  { key: 'scale', label: '整体大小', min: .55, max: 1.6, step: .01 },
  { key: 'offsetX', label: '左右位置', min: -.8, max: .8, step: .01, signed: true },
  { key: 'offsetY', label: '上下位置', min: -.8, max: .8, step: .01, signed: true },
  { key: 'offsetZ', label: '前后位置', min: -.55, max: .55, step: .01, signed: true },
  { key: 'rotationX', label: '前后旋转', min: -45, max: 45, step: 1, angle: true },
  { key: 'rotationY', label: '左右旋转', min: -45, max: 45, step: 1, angle: true },
  { key: 'rotationZ', label: '平面旋转', min: -45, max: 45, step: 1, angle: true },
]
const value = () => Object.fromEntries(Object.keys(defaults).map(key => [key, Number(local[key])]))
const publish = notify => { const next = value(); emit('update:modelValue', next); if (notify) emit('change', next) }
const preview = () => publish(false)
const commit = () => publish(true)
const reset = () => { Object.assign(local, defaults); commit() }
const format = (current, control) => control.angle ? `${Math.round(current)}°` : control.signed ? `${current > 0 ? '+' : ''}${Number(current).toFixed(2)}` : `${Math.round(current * 100)}%`
watch(() => props.modelValue, next => Object.assign(local, defaults, next || {}), { deep: true })
</script>

<style scoped>
.transform-editor{width:100%;padding-top:8px;border-top:1px dashed #edcfb5}.transform-editor summary{color:#9a3412;font-size:10px;font-weight:850;cursor:pointer}.transform-editor summary:focus-visible{outline:2px solid rgba(243,112,33,.3);outline-offset:3px}.transform-grid{display:grid;grid-template-columns:1fr 1fr;gap:7px 16px;margin-top:10px}.transform-grid label>span{display:flex;align-items:center;justify-content:space-between}.transform-grid strong{font-size:9px}.transform-grid output{color:#c2410c;font:800 9px/1 var(--font-mono)}.transform-grid :deep(.el-slider){height:21px}.transform-grid :deep(.el-slider__bar){background:#f37021}.transform-grid :deep(.el-slider__button){width:13px;height:13px;border-color:#ea580c}.transform-reset{margin-top:5px;padding:6px 9px;border:1px solid #ead8c9;border-radius:7px;background:#fff;color:#725f53;font-size:9px;font-weight:800;cursor:pointer}.transform-reset:hover,.transform-reset:focus-visible{border-color:#f37021;color:#9a3412;outline:2px solid rgba(243,112,33,.14)}@media(max-width:560px){.transform-grid{grid-template-columns:1fr}}
</style>
