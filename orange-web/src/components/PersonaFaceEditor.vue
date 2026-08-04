<template>
  <section class="face-editor" aria-label="橙子五官参数编辑器">
    <div class="editor-toolbar">
      <div class="part-tabs" role="tablist" aria-label="选择编辑部位">
        <button v-for="part in parts" :key="part.key" type="button" role="tab" :aria-selected="activePart === part.key" :class="{ active: activePart === part.key }" @click="activePart = part.key">{{ part.label }}</button>
      </div>
      <div class="history-actions">
        <button type="button" :disabled="historyIndex <= 0" aria-label="撤销外观修改" @click="undo">↶</button>
        <button type="button" :disabled="historyIndex >= history.length - 1" aria-label="重做外观修改" @click="redo">↷</button>
        <button type="button" @click="randomize">随机</button>
        <button type="button" @click="reset">恢复默认</button>
      </div>
    </div>

    <div v-if="activePart === 'eyes'" class="preset-row" aria-label="表情预设">
      <button v-for="preset in presets" :key="preset.key" type="button" @click="applyPreset(preset.values)">{{ preset.label }}</button>
    </div>

    <div class="parameter-grid">
      <label v-for="control in activeControls" :key="control.key" class="parameter-control">
        <span><strong>{{ control.label }}</strong><output>{{ format(local[control.key], control) }}</output></span>
        <el-slider v-model="local[control.key]" :min="control.min" :max="control.max" :step="control.step" :show-tooltip="false" @input="preview" @change="commit" />
      </label>
    </div>
    <p class="editor-note">所有调整仅通过右侧面板完成，左侧橙子只负责实时预览。</p>
  </section>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'

const props = defineProps({ modelValue: { type: Object, required: true } })
const emit = defineEmits(['update:modelValue', 'change'])

const defaults = {
  bodyWidth: 1, bodyHeight: 1,
  eyeSize: 1, eyeSpacing: 1, eyeHeight: 0, pupilSize: 1,
  browLength: 1, browThickness: 1, browHeight: 0, browAngle: 0,
  mouthWidth: 1, mouthCurve: 1, mouthHeight: 0, mouthThickness: 1, mouthOpen: 0,
}
const parts = [
  { key: 'body', label: '本体' }, { key: 'eyes', label: '眼睛' },
  { key: 'brows', label: '眉毛' }, { key: 'mouth', label: '嘴巴' },
]
const controls = {
  body: [
    { key: 'bodyWidth', label: '胖瘦', min: .82, max: 1.2, step: .01 },
    { key: 'bodyHeight', label: '高矮', min: .84, max: 1.16, step: .01 },
  ],
  eyes: [
    { key: 'eyeSize', label: '眼睛大小', min: .68, max: 1.4, step: .01 },
    { key: 'eyeSpacing', label: '眼睛间距', min: .72, max: 1.32, step: .01 },
    { key: 'eyeHeight', label: '上下位置', min: -.22, max: .24, step: .01, signed: true },
    { key: 'pupilSize', label: '瞳孔大小', min: .62, max: 1.35, step: .01 },
  ],
  brows: [
    { key: 'browLength', label: '眉毛长度', min: .65, max: 1.4, step: .01 },
    { key: 'browThickness', label: '眉毛粗细', min: .65, max: 1.45, step: .01 },
    { key: 'browHeight', label: '上下位置', min: -.16, max: .24, step: .01, signed: true },
    { key: 'browAngle', label: '眉毛角度', min: -.48, max: .48, step: .01, angle: true },
  ],
  mouth: [
    { key: 'mouthWidth', label: '嘴巴宽度', min: .62, max: 1.45, step: .01 },
    { key: 'mouthCurve', label: '嘴型弧度', min: -1.15, max: 1.25, step: .01, signed: true },
    { key: 'mouthHeight', label: '上下位置', min: -.22, max: .2, step: .01, signed: true },
    { key: 'mouthThickness', label: '嘴巴粗细', min: .65, max: 1.5, step: .01 },
    { key: 'mouthOpen', label: '张嘴程度', min: 0, max: 1, step: .01 },
  ],
}
const presets = [
  { key: 'cute', label: '可爱', values: { eyeSize: 1.22, eyeSpacing: .94, pupilSize: 1.16, browAngle: .06, mouthWidth: .88, mouthCurve: 1.18, mouthOpen: .08 } },
  { key: 'smart', label: '机灵', values: { eyeSize: .94, eyeSpacing: 1.06, pupilSize: .86, browAngle: -.12, mouthWidth: 1.02, mouthCurve: .62, mouthOpen: 0 } },
  { key: 'cool', label: '酷感', values: { eyeSize: .82, eyeSpacing: 1.12, pupilSize: .78, browAngle: -.3, browHeight: -.04, mouthWidth: 1.12, mouthCurve: .12, mouthOpen: 0 } },
  { key: 'surprised', label: '惊喜', values: { eyeSize: 1.25, eyeSpacing: 1, pupilSize: .82, browHeight: .16, mouthWidth: .72, mouthCurve: 0, mouthOpen: .88 } },
]
const activePart = ref('eyes')
const local = reactive({ ...defaults, ...props.modelValue })
const snapshot = () => Object.fromEntries(Object.keys(defaults).map(key => [key, Number(local[key])]))
const history = ref([snapshot()])
const historyIndex = ref(0)
let applyingHistory = false

const activeControls = computed(() => controls[activePart.value])
const format = (value, control) => control.angle
  ? `${Math.round(value * 57.2958)}°`
  : control.signed ? `${value > 0 ? '+' : ''}${Number(value).toFixed(2)}` : `${Math.round(value * 100)}%`
const publish = notify => {
  const value = snapshot()
  emit('update:modelValue', value)
  if (notify) emit('change', value)
}
const preview = () => publish(false)
const commit = () => {
  const value = snapshot()
  const current = history.value[historyIndex.value]
  if (JSON.stringify(current) !== JSON.stringify(value)) {
    history.value = [...history.value.slice(0, historyIndex.value + 1), value].slice(-30)
    historyIndex.value = history.value.length - 1
  }
  publish(true)
}
const applyValues = values => {
  Object.assign(local, values)
  commit()
}
const applyPreset = values => applyValues(values)
const reset = () => applyValues(defaults)
const randomBetween = (min, max) => Math.round((min + Math.random() * (max - min)) * 100) / 100
const randomize = () => {
  const values = {}
  Object.values(controls).flat().forEach(control => { values[control.key] = randomBetween(control.min, control.max) })
  values.mouthOpen = Math.min(values.mouthOpen, .55)
  applyValues(values)
}
const applyHistory = index => {
  applyingHistory = true
  historyIndex.value = index
  Object.assign(local, history.value[index])
  publish(true)
  applyingHistory = false
}
const undo = () => { if (historyIndex.value > 0) applyHistory(historyIndex.value - 1) }
const redo = () => { if (historyIndex.value < history.value.length - 1) applyHistory(historyIndex.value + 1) }

watch(() => props.modelValue, value => {
  if (!applyingHistory) Object.assign(local, defaults, value || {})
}, { deep: true })
</script>

<style scoped>
.face-editor{grid-column:1/-1;padding:14px;border:1px solid #f0d5bf;border-radius:14px;background:rgba(255,255,255,.82)}.editor-toolbar{display:flex;align-items:center;justify-content:space-between;gap:10px}.part-tabs,.history-actions,.preset-row{display:flex;gap:5px;flex-wrap:wrap}.part-tabs button,.history-actions button,.preset-row button{min-height:30px;padding:0 9px;border:1px solid #e7d2c2;border-radius:8px;background:#fff;color:#725f53;font-size:10px;font-weight:800;cursor:pointer}.part-tabs button.active,.part-tabs button:focus-visible,.preset-row button:hover,.history-actions button:hover:not(:disabled){border-color:#f37021;background:#fff2e6;color:#9a3412;outline:2px solid rgba(243,112,33,.14)}.history-actions button:disabled{cursor:not-allowed;opacity:.38}.preset-row{margin-top:10px;padding-top:10px;border-top:1px dashed #ead8c9}.parameter-grid{display:grid;grid-template-columns:1fr 1fr;gap:9px 16px;margin-top:13px}.parameter-control>span{display:flex;align-items:center;justify-content:space-between;gap:10px}.parameter-control strong{color:#4a2c1c;font-size:10px}.parameter-control output{min-width:42px;color:#c2410c;font:800 10px/1 var(--font-mono);text-align:right}.parameter-control :deep(.el-slider){height:24px}.parameter-control :deep(.el-slider__bar){background:#f37021}.parameter-control :deep(.el-slider__button){width:14px;height:14px;border-color:#ea580c}.editor-note{margin:7px 0 0;color:#927c6c;font-size:9px;line-height:1.5}@media(max-width:560px){.editor-toolbar{align-items:flex-start;flex-direction:column}.parameter-grid{grid-template-columns:1fr}}
</style>
