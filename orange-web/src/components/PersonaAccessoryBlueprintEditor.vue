<template>
  <section class="blueprint-editor" :aria-label="`${title}组合式饰品编辑器`">
    <header class="blueprint-head">
      <div><span>ACCESSORY LAB</span><strong>组合式饰品编辑器</strong></div>
      <div class="blueprint-history">
        <button type="button" :disabled="historyIndex <= 0" aria-label="撤销饰品修改" @click="undo">↶</button>
        <button type="button" :disabled="historyIndex >= history.length - 1" aria-label="重做饰品修改" @click="redo">↷</button>
        <button type="button" @click="resetBlueprint">清空</button>
      </div>
    </header>
    <label class="blueprint-name">饰品名称<input v-model.trim="local.name" maxlength="48" @change="commit" /></label>

    <fieldset class="preset-library">
      <legend>复制现有建模</legend>
      <button v-for="variant in availablePresets" :key="variant" type="button" @click="loadPreset(variant)">
        <span>{{ variant === props.variantCode ? '当前' : '款式' }}</span>{{ accessoryVariantLabels[variant] || variant }}
      </button>
      <small>复制后每个零件都可以继续编辑</small>
    </fieldset>

    <fieldset class="shape-library">
      <legend>添加零件</legend>
      <button v-for="shape in availableShapes" :key="shape" type="button" :disabled="local.parts.length >= 15" @click="addPart(shape)">
        <span>{{ shapeIcons[shape] }}</span>{{ shapeLabels[shape] }}
      </button>
      <small>{{ local.parts.length }} / 15</small>
    </fieldset>

    <div class="blueprint-workspace">
      <div class="layer-panel">
        <div class="panel-label">图层</div>
        <button v-for="part in local.parts" :key="part.id" type="button" class="layer-row" :class="{ active: selectedId === part.id, muted: !part.visible }" @click="selectedId = part.id">
          <span>{{ shapeIcons[part.shape] }}</span><strong>{{ part.label }}</strong><i>{{ part.locked ? '已锁定' : part.visible ? '显示' : '隐藏' }}</i>
        </button>
        <p v-if="!local.parts.length" class="empty-layers">从上方零件库添加第一个形状</p>
      </div>

      <div v-if="selected" class="part-inspector">
        <div class="part-actions" aria-label="零件操作">
          <button type="button" @click="movePart(-1)">上移</button><button type="button" @click="movePart(1)">下移</button>
          <button type="button" @click="duplicatePart(false)">复制</button><button type="button" @click="duplicatePart(true)">镜像</button>
          <button type="button" @click="togglePart('visible')">{{ selected.visible ? '隐藏' : '显示' }}</button>
          <button type="button" @click="togglePart('locked')">{{ selected.locked ? '解锁' : '锁定' }}</button>
          <button type="button" class="danger" @click="removePart">删除</button>
        </div>
        <label class="compact-input">零件名称<input v-model.trim="selected.label" maxlength="24" :disabled="selected.locked" @change="commit" /></label>

        <details open>
          <summary>形状参数</summary>
          <div class="control-grid">
            <RangeControl v-for="control in shapeControls" :key="control.key" :model-value="selected.parameters[control.key]" v-bind="control" :disabled="selected.locked" @update:model-value="setNested('parameters', control.key, $event, false)" @change="commit" />
          </div>
        </details>
        <details>
          <summary>位置、缩放与旋转</summary>
          <div class="control-grid">
            <RangeControl v-for="control in transformControls" :key="control.key" :model-value="selected.transform[control.key]" v-bind="control" :disabled="selected.locked" @update:model-value="setNested('transform', control.key, $event, false)" @change="commit" />
          </div>
        </details>
        <details>
          <summary>颜色与材质</summary>
          <div class="material-row">
            <label>颜色<input v-model="selected.material.color" type="color" :disabled="selected.locked" @input="preview" @change="commit" /></label>
          </div>
          <div class="control-grid">
            <RangeControl v-for="control in materialControls" :key="control.key" :model-value="selected.material[control.key]" v-bind="control" :disabled="selected.locked" @update:model-value="setNested('material', control.key, $event, false)" @change="commit" />
          </div>
        </details>
      </div>
      <div v-else class="part-inspector empty-inspector">选择右侧列表中的零件进行编辑</div>
    </div>
    <p class="blueprint-note">零件只能在当前画像维度允许的锚点范围内组合；左侧仅实时预览。</p>
  </section>
</template>

<script setup>
import { computed, defineComponent, h, reactive, ref, watch } from 'vue'
import { accessoryPreset, accessoryVariantLabels, variantsForSlot } from '../features/persona-scene/accessoryPresetCatalog'

const RangeControl = defineComponent({
  props: { modelValue: Number, label: String, min: Number, max: Number, step: Number, disabled: Boolean, angle: Boolean },
  emits: ['update:modelValue', 'change'],
  setup(props, { emit }) {
    const format = () => props.angle ? `${Math.round(props.modelValue || 0)}°` : Number(props.modelValue || 0).toFixed(2)
    return () => h('label', { class: 'range-control' }, [
      h('span', [h('strong', props.label), h('output', format())]),
      h('input', { type: 'range', min: props.min, max: props.max, step: props.step, value: props.modelValue, disabled: props.disabled,
        'aria-label': props.label,
        onInput: event => emit('update:modelValue', Number(event.target.value)),
        onChange: () => emit('change') }),
    ])
  },
})

const props = defineProps({
  modelValue: { type: Object, default: null }, slotCode: { type: String, required: true },
  title: { type: String, default: '饰品' }, defaultColor: { type: String, default: '#F37021' },
  variantCode: { type: String, default: 'CLASSIC' },
})
const emit = defineEmits(['update:modelValue', 'change'])
const clone = value => JSON.parse(JSON.stringify(value))
const slotShapes = {
  GOAL_CROWN: ['TORUS', 'CONE', 'STAR', 'SPHERE', 'CYLINDER'],
  INTEREST_ORBIT: ['TORUS', 'SPHERE', 'STAR', 'OCTAHEDRON'],
  SKILL_VISOR: ['TORUS', 'BOX', 'CYLINDER', 'SPHERE'],
  RESOURCE_PACK: ['BOX', 'CYLINDER', 'SPHERE', 'OCTAHEDRON'],
  GROWTH_TRAIL: ['TORUS', 'BOX', 'STAR', 'SPHERE'],
  LEFT_HAND_WEAPON: ['BOX', 'CYLINDER', 'CONE', 'SPHERE', 'TORUS', 'OCTAHEDRON'],
  RIGHT_HAND_WEAPON: ['BOX', 'CYLINDER', 'CONE', 'SPHERE', 'TORUS', 'OCTAHEDRON'],
}
const shapeLabels = { TORUS: '圆环', CONE: '锥体', STAR: '星形', SPHERE: '球体', CYLINDER: '圆柱', BOX: '方块', OCTAHEDRON: '菱形' }
const shapeIcons = { TORUS: '○', CONE: '△', STAR: '★', SPHERE: '●', CYLINDER: '▰', BOX: '■', OCTAHEDRON: '◆' }
const availableShapes = computed(() => slotShapes[props.slotCode] || [])
const availablePresets = computed(() => variantsForSlot(props.slotCode))
const initial = () => props.modelValue ? clone(props.modelValue) : { name: `我的${props.title}`, anchor: props.slotCode, parts: [] }
const local = reactive(initial())
const selectedId = ref(local.parts[0]?.id || null)
const history = ref([clone(local)])
const historyIndex = ref(0)
let serial = 0
let syncing = false

const selected = computed(() => local.parts.find(part => part.id === selectedId.value))
const shapeDefaults = shape => {
  if (shape === 'TORUS') return { radius: .55, tube: .06 }
  if (shape === 'SPHERE' || shape === 'OCTAHEDRON') return { radius: .18 }
  if (shape === 'CONE' || shape === 'CYLINDER') return { radius: .16, height: .5 }
  if (shape === 'STAR') return { radius: .28, depth: .08, points: 5 }
  return { width: .45, height: .35, depth: .16 }
}
const anchorPosition = () => {
  const positions = {
    GOAL_CROWN: { x: 0, y: 1.95, z: 0 }, INTEREST_ORBIT: { x: 1.7, y: .1, z: 0 },
    SKILL_VISOR: { x: 0, y: .35, z: 1.42 }, RESOURCE_PACK: { x: -.95, y: .05, z: -.9 },
    GROWTH_TRAIL: { x: 0, y: -1.5, z: 0 },
    LEFT_HAND_WEAPON: { x: 0, y: .2, z: 0 }, RIGHT_HAND_WEAPON: { x: 0, y: .2, z: 0 },
  }
  return positions[props.slotCode] || { x: 0, y: 0, z: 0 }
}
const transformDefaults = shape => {
  const transform = { ...anchorPosition(), scaleX: 1, scaleY: 1, scaleZ: 1, rotationX: 0, rotationY: 0, rotationZ: 0 }
  if (props.slotCode === 'GOAL_CROWN') {
    if (shape === 'STAR' || shape === 'SPHERE') Object.assign(transform, { x: .62, y: 2.08, z: .92 })
    if (shape === 'CONE') Object.assign(transform, { y: 2.24, z: .08 })
    if (shape === 'CYLINDER') Object.assign(transform, { y: 2.08, z: .08 })
  }
  if (props.slotCode === 'INTEREST_ORBIT' && shape !== 'TORUS') Object.assign(transform, { x: 1.55, y: .55, z: .35 })
  if (props.slotCode === 'SKILL_VISOR' && shape !== 'TORUS') Object.assign(transform, { x: .58, y: .36, z: 1.46 })
  if (props.slotCode === 'RESOURCE_PACK' && shape !== 'BOX') Object.assign(transform, { x: -1.12, y: .28, z: -.82 })
  if (props.slotCode === 'GROWTH_TRAIL' && shape !== 'TORUS') Object.assign(transform, { x: .72, y: -1.42, z: .16 })
  if (props.slotCode.endsWith('_HAND_WEAPON')) {
    if (shape === 'CYLINDER' || shape === 'CONE') Object.assign(transform, { y: .58, rotationY: 45 })
    if (shape === 'BOX') Object.assign(transform, { y: .42 })
    if (shape === 'TORUS' || shape === 'SPHERE') Object.assign(transform, { y: .18, z: .12 })
  }
  if (shape === 'TORUS' && ['GOAL_CROWN', 'GROWTH_TRAIL'].includes(props.slotCode)) transform.rotationX = 90
  if (shape === 'TORUS' && props.slotCode === 'INTEREST_ORBIT') Object.assign(transform, { rotationX: 64, rotationY: 14, rotationZ: 10 })
  return transform
}
const materialDefaults = () => ({ color: props.defaultColor, opacity: 1, metalness: .1, roughness: .45, emissiveIntensity: 0 })
const makeId = () => `part-${Date.now().toString(36)}-${++serial}`
const publish = save => { const value = clone(local); emit('update:modelValue', value); if (save) emit('change', value) }
const preview = () => publish(false)
const pushHistory = () => {
  const value = clone(local)
  if (JSON.stringify(value) === JSON.stringify(history.value[historyIndex.value])) return
  history.value = [...history.value.slice(0, historyIndex.value + 1), value].slice(-40)
  historyIndex.value = history.value.length - 1
}
const commit = () => { pushHistory(); publish(true) }
const addPart = shape => {
  if (local.parts.length >= 15) return
  const id = makeId()
  local.parts.push({ id, label: `${shapeLabels[shape]} ${local.parts.length + 1}`, shape, visible: true, locked: false, parameters: shapeDefaults(shape), transform: transformDefaults(shape), material: materialDefaults() })
  selectedId.value = id
  commit()
}
const setNested = (group, key, value, save = false) => { if (!selected.value || selected.value.locked) return; selected.value[group][key] = Number(value); publish(save) }
const togglePart = key => { if (!selected.value) return; selected.value[key] = !selected.value[key]; commit() }
const movePart = delta => {
  const index = local.parts.findIndex(part => part.id === selectedId.value)
  const target = index + delta
  if (index < 0 || target < 0 || target >= local.parts.length) return
  const [part] = local.parts.splice(index, 1); local.parts.splice(target, 0, part); commit()
}
const duplicatePart = mirror => {
  if (!selected.value || local.parts.length >= 15) return
  const copy = clone(selected.value); copy.id = makeId(); copy.label = `${copy.label}${mirror ? ' 镜像' : ' 副本'}`.slice(0, 24); copy.locked = false
  if (mirror) { copy.transform.x *= -1; copy.transform.rotationY *= -1; copy.transform.rotationZ *= -1 }
  local.parts.push(copy); selectedId.value = copy.id; commit()
}
const removePart = () => { const index = local.parts.findIndex(part => part.id === selectedId.value); if (index < 0) return; local.parts.splice(index, 1); selectedId.value = local.parts[Math.min(index, local.parts.length - 1)]?.id || null; commit() }
const resetBlueprint = () => { local.parts.splice(0); local.name = `我的${props.title}`; selectedId.value = null; commit() }
const loadPreset = variant => {
  const preset = accessoryPreset(props.slotCode, variant, props.defaultColor)
  local.name = `${props.title} · ${accessoryVariantLabels[variant] || variant}`
  local.anchor = preset.anchor
  local.parts.splice(0, local.parts.length, ...preset.parts)
  selectedId.value = local.parts[0]?.id || null
  commit()
}
const applyHistory = index => { syncing = true; historyIndex.value = index; Object.assign(local, clone(history.value[index])); selectedId.value = local.parts[0]?.id || null; publish(true); syncing = false }
const undo = () => { if (historyIndex.value > 0) applyHistory(historyIndex.value - 1) }
const redo = () => { if (historyIndex.value < history.value.length - 1) applyHistory(historyIndex.value + 1) }

const shapeControls = computed(() => {
  if (!selected.value) return []
  const all = {
    radius: { key: 'radius', label: '半径', min: .06, max: 1.8, step: .01 }, tube: { key: 'tube', label: '粗细', min: .015, max: .35, step: .005 },
    width: { key: 'width', label: '宽度', min: .05, max: 2.5, step: .01 }, height: { key: 'height', label: '高度', min: .05, max: 2.5, step: .01 },
    depth: { key: 'depth', label: '厚度', min: .03, max: 1.5, step: .01 }, points: { key: 'points', label: '星角数量', min: 3, max: 12, step: 1 },
  }
  return Object.keys(selected.value.parameters).map(key => all[key]).filter(Boolean)
})
const transformControls = [
  { key: 'x', label: '左右', min: -2.5, max: 2.5, step: .01 }, { key: 'y', label: '上下', min: -2.5, max: 2.8, step: .01 }, { key: 'z', label: '前后', min: -2, max: 2, step: .01 },
  { key: 'scaleX', label: '横向缩放', min: .2, max: 2.5, step: .01 }, { key: 'scaleY', label: '纵向缩放', min: .2, max: 2.5, step: .01 }, { key: 'scaleZ', label: '厚度缩放', min: .2, max: 2.5, step: .01 },
  { key: 'rotationX', label: '前后旋转', min: -180, max: 180, step: 1, angle: true }, { key: 'rotationY', label: '左右旋转', min: -180, max: 180, step: 1, angle: true }, { key: 'rotationZ', label: '平面旋转', min: -180, max: 180, step: 1, angle: true },
]
const materialControls = [
  { key: 'opacity', label: '透明度', min: .15, max: 1, step: .01 }, { key: 'metalness', label: '金属感', min: 0, max: 1, step: .01 },
  { key: 'roughness', label: '粗糙度', min: .05, max: 1, step: .01 }, { key: 'emissiveIntensity', label: '发光', min: 0, max: .8, step: .01 },
]

watch(() => props.modelValue, value => {
  if (syncing || !value || JSON.stringify(value) === JSON.stringify(local)) return
  Object.assign(local, clone(value)); selectedId.value = local.parts[0]?.id || null; history.value = [clone(local)]; historyIndex.value = 0
}, { deep: true })
</script>

<style scoped>
.blueprint-editor{box-sizing:border-box;width:100%;max-width:100%;padding:14px;border:1px solid #e9c9aa;border-radius:13px;background:#fffdf9;overflow:hidden}.blueprint-head{display:flex;align-items:center;justify-content:space-between;gap:10px}.blueprint-head>div:first-child span,.blueprint-head>div:first-child strong{display:block}.blueprint-head span{color:#c2410c;font:800 8px/1 var(--font-mono);letter-spacing:.1em}.blueprint-head strong{margin-top:4px;font:900 13px/1.2 var(--font-mono)}.blueprint-history,.part-actions{display:flex;gap:4px;flex-wrap:wrap}.blueprint-history button,.part-actions button,.shape-library button{min-height:27px;padding:0 7px;border:1px solid #ead8c9;border-radius:7px;background:#fff;color:#725f53;font-size:9px;font-weight:800;cursor:pointer}.blueprint-history button:disabled,.shape-library button:disabled{cursor:not-allowed;opacity:.35}.blueprint-history button:hover:not(:disabled),.part-actions button:hover,.shape-library button:hover:not(:disabled){border-color:#f37021;color:#9a3412}.blueprint-name,.compact-input{display:flex;align-items:center;gap:8px;margin-top:10px;color:#725f53;font-size:9px;font-weight:800}.blueprint-name input,.compact-input input{box-sizing:border-box;min-width:0;flex:1;padding:7px 9px;border:1px solid #ead8c9;border-radius:7px;background:#fff;color:#3f2416}.shape-library{box-sizing:border-box;display:flex;align-items:center;gap:5px;flex-wrap:wrap;min-width:0;margin:10px 0 0;padding:10px;border:1px dashed #e6c9b1;border-radius:10px}.shape-library legend{padding:0 5px;color:#9a3412;font:850 9px/1 var(--font-mono)}.shape-library button span{margin-right:4px;color:#ea580c}.shape-library small{margin-left:auto;color:#927c6c}.blueprint-workspace{display:grid;min-width:0;grid-template-columns:minmax(94px,112px) minmax(0,1fr);gap:9px;margin-top:10px}.layer-panel,.part-inspector{box-sizing:border-box;min-width:0;padding:9px;border:1px solid #f0dfd1;border-radius:10px;background:#fff}.panel-label{margin-bottom:6px;color:#9a3412;font:850 9px/1 var(--font-mono)}.layer-row{box-sizing:border-box;display:grid;width:100%;grid-template-columns:18px minmax(0,1fr);gap:1px 4px;margin-top:4px;padding:7px;border:1px solid transparent;border-radius:7px;background:#fffaf5;color:#725f53;text-align:left;cursor:pointer}.layer-row strong{overflow:hidden;font-size:9px;text-overflow:ellipsis;white-space:nowrap}.layer-row i{grid-column:2;color:#a38b7c;font-size:7px;font-style:normal}.layer-row.active{border-color:#f37021;background:#fff2e6;color:#9a3412}.layer-row.muted{opacity:.5}.empty-layers,.empty-inspector{color:#a38b7c;font-size:9px;line-height:1.5}.part-actions .danger{color:#dc2626}.compact-input{margin:8px 0}.part-inspector details{padding:7px 0;border-top:1px dashed #ead8c9}.part-inspector summary{color:#7c2d12;font-size:9px;font-weight:850;cursor:pointer}.control-grid{display:grid;grid-template-columns:minmax(0,1fr);gap:6px;margin-top:7px}.range-control{display:block;min-width:0}.range-control>span{display:flex;justify-content:space-between;gap:8px}.range-control strong,.range-control output{font-size:8px}.range-control output{color:#c2410c;font-family:var(--font-mono)}.range-control input{box-sizing:border-box;width:100%;height:15px;accent-color:#f37021}.material-row{margin:8px 0}.material-row label{display:flex;align-items:center;justify-content:space-between;color:#725f53;font-size:9px;font-weight:800}.material-row input{width:34px;height:25px;border:0;background:transparent}.blueprint-note{margin:8px 0 0;color:#927c6c;font-size:8px;line-height:1.5}@media(max-width:560px){.blueprint-workspace{grid-template-columns:1fr}.layer-panel{max-height:150px;overflow:auto}}
.preset-library{box-sizing:border-box;display:flex;align-items:center;gap:5px;flex-wrap:wrap;min-width:0;margin:10px 0 0;padding:10px;border:1px solid #fdba74;border-radius:10px;background:#fff7ed}.preset-library legend{padding:0 5px;color:#9a3412;font:850 9px/1 var(--font-mono)}.preset-library button{min-height:30px;padding:0 8px;border:1px solid #fed7aa;border-radius:7px;background:#fff;color:#7c2d12;font-size:9px;font-weight:850;cursor:pointer}.preset-library button:hover,.preset-library button:focus-visible{border-color:#f37021;outline:2px solid rgba(243,112,33,.14)}.preset-library button span{margin-right:5px;padding:2px 4px;border-radius:4px;background:#ffedd5;color:#c2410c;font-size:7px}.preset-library small{margin-left:auto;color:#927c6c;font-size:8px}@media(max-width:560px){.preset-library small{width:100%;margin-left:0}}
</style>
