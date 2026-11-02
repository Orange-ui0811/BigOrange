<template>
  <fieldset class="hand-editor">
    <legend>双手 DIY</legend>
    <header><div><strong>两侧橙色圆点就是手</strong><span>分别调整大小与位置；武器在对应画像维度中解锁。</span></div><button type="button" @click="reset">恢复默认</button></header>
    <div class="hand-grid">
      <section v-for="side in sides" :key="side.code" :aria-label="`${side.label}参数`">
        <div class="hand-title"><i :class="side.code"></i><strong>{{ side.label }}</strong><span>{{ side.hint }}</span></div>
        <label v-for="control in controls" :key="control.key">
          <span><strong>{{ control.label }}</strong><output>{{ format(value(side.code, control.key)) }}</output></span>
          <input type="range" :min="range(side.code, control).min" :max="range(side.code, control).max" :step="control.step" :value="value(side.code, control.key)" :aria-label="`${side.label}${control.label}`" @input="update(side.code, control.key, $event)" @change="$emit('change')" />
        </label>
      </section>
    </div>
  </fieldset>
</template>

<script setup>
const props = defineProps({ modelValue: { type: Object, default: () => ({}) } })
const emit = defineEmits(['update:modelValue', 'change'])
const defaults = { leftSize: 1, leftX: -1.72, leftY: -.12, leftZ: .18, rightSize: 1, rightX: 1.72, rightY: -.12, rightZ: .18 }
const sides = [{ code: 'left', label: '左手', hint: '避雷清单' }, { code: 'right', label: '右手', hint: '期望成果' }]
const controls = [
  { key: 'Size', label: '大小', min: .55, max: 1.65, step: .01 },
  { key: 'X', label: '左右', min: 1.05, max: 2.6, step: .01 },
  { key: 'Y', label: '上下', min: -1.25, max: 1.1, step: .01 },
  { key: 'Z', label: '前后', min: -.8, max: 1.4, step: .01 },
]
const current = () => ({ ...defaults, ...(props.modelValue || {}) })
const value = (side, key) => current()[`${side}${key}`]
const range = (side, control) => control.key === 'X' && side === 'left'
  ? { min: -control.max, max: -control.min } : control
const format = number => Number(number).toFixed(2)
const update = (side, key, event) => emit('update:modelValue', { ...current(), [`${side}${key}`]: Number(event.target.value) })
const reset = () => { emit('update:modelValue', { ...defaults }); emit('change') }
</script>

<style scoped>
.hand-editor{grid-column:1/-1;min-width:0;margin:0;padding:14px;border:1px solid #f1dfd1;border-radius:12px;background:rgba(255,255,255,.82)}.hand-editor legend{padding:0 5px;color:#7c2d12;font:850 10px/1 var(--font-mono);letter-spacing:.04em}.hand-editor>header{display:flex;align-items:flex-start;justify-content:space-between;gap:14px}.hand-editor header strong,.hand-editor header span{display:block}.hand-editor header strong{font-size:12px}.hand-editor header span{margin-top:4px;color:#806b5f;font-size:9px;line-height:1.5}.hand-editor header button{flex:0 0 auto;padding:6px 8px;border:1px solid #ead8c9;border-radius:7px;background:#fff;color:#9a3412;font-size:9px;font-weight:800;cursor:pointer}.hand-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:12px}.hand-grid section{padding:11px;border:1px solid #f0dfd1;border-radius:10px;background:#fff}.hand-title{display:flex;align-items:center;gap:7px;margin-bottom:8px}.hand-title i{width:13px;height:13px;border-radius:50%;background:#f37021;box-shadow:inset -2px -2px 3px rgba(124,45,18,.25)}.hand-title i.right{order:2}.hand-title strong{font-size:10px}.hand-title span{margin-left:auto;color:#b45309;font-size:8px;font-weight:800}.hand-grid label{display:block;margin-top:7px}.hand-grid label>span{display:flex;justify-content:space-between;color:#725f53;font-size:8px}.hand-grid output{color:#c2410c;font-family:var(--font-mono)}.hand-grid input{width:100%;height:16px;accent-color:#f37021}@media(max-width:560px){.hand-grid{grid-template-columns:1fr}}
</style>
