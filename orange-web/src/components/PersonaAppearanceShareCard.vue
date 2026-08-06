<template>
  <div class="node-card appearance-share-card">
    <header class="share-heading">
      <div><span>ORANGE LOOK CODE · 只分享造型</span><h3>橙子形象码</h3></div>
      <el-tag :type="isComplete ? 'success' : 'info'" effect="plain">{{ isComplete ? '可以分享与导入' : '画像完整后解锁' }}</el-tag>
    </header>
    <p class="share-intro">形象码只包含外观、穿戴和 DIY 参数，不包含画像文字、素材、账号信息或 AI 记录。</p>

    <div class="share-grid">
      <section class="share-panel">
        <strong>分享我的橙子</strong>
        <p>造型没有变化时会复用原码；重新设计后会生成新码。</p>
        <el-button type="primary" :loading="exporting" :disabled="!isComplete" @click="generateCode">生成形象码</el-button>
        <div v-if="generatedCode" class="code-output">
          <code>{{ generatedCode }}</code>
          <el-button size="small" @click="copyCode">复制</el-button>
        </div>
      </section>

      <section class="share-panel">
        <strong>一键导入形象</strong>
        <p>先预览，再确认覆盖当前造型；画像内容不会发生变化。</p>
        <div class="import-row">
          <el-input v-model="importCode" maxlength="13" placeholder="例如 OVC-ABCD-2345" :disabled="!isComplete" @keyup.enter="loadPreview" />
          <el-button :loading="previewing" :disabled="!isComplete || !importCode.trim()" @click="loadPreview">预览</el-button>
        </div>
      </section>
    </div>

    <div v-if="preview" class="import-preview">
      <div class="import-preview-stage" aria-label="形象码3D造型预览">
        <OrangePersonaScene compact :equipment="preview.equipment" :appearance="preview.appearance" />
        <span>3D 造型预览</span>
      </div>
      <div class="import-preview-copy">
        <span>形象码 {{ preview.code }}</span>
        <strong>确认这是你想导入的橙子造型</strong>
        <p v-if="preview.blockedEquipment?.length">尚未解锁：{{ preview.blockedEquipment.join('、') }}</p>
        <p v-else>全部装备均可导入，原画像和素材箱保持不变。</p>
      </div>
      <el-button type="primary" :loading="importing" :disabled="!preview.readyToImport" @click="applyCode">确认导入</el-button>
    </div>
    <p v-if="!isComplete" class="share-lock">请先完成全部画像维度，防止通过形象码绕过装备解锁。</p>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent } from 'vue'
import { usePersonaAppearanceShare } from '../composables/usePersonaAppearanceShare'

const OrangePersonaScene = defineAsyncComponent(() => import('./OrangePersonaScene.vue'))

const props = defineProps({ completion: { type: Object, default: () => ({}) } })
const emit = defineEmits(['imported'])
const completion = computed(() => props.completion)
const { generatedCode, importCode, preview, exporting, previewing, importing,
  isComplete, generateCode, copyCode, loadPreview, applyCode } = usePersonaAppearanceShare({
  completion,
  afterImport: () => emit('imported'),
})
</script>

<style scoped>
.appearance-share-card{border-color:#fdba74;background:linear-gradient(145deg,#fffdf9,#fff7ed)}
.share-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:18px}.share-heading span{color:#b45309;font:800 9px/1.2 var(--font-mono);letter-spacing:.09em}.share-heading h3{margin:8px 0 0;font:900 25px/1.12 var(--font-mono)}
.share-intro{margin:14px 0 18px;color:#725f53;font-size:13px;line-height:1.7}.share-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.share-panel{padding:16px;border:1px solid #f0dfd1;border-radius:12px;background:rgba(255,255,255,.86)}.share-panel>strong{font:850 13px/1.3 var(--font-mono)}.share-panel>p{min-height:42px;margin:7px 0 12px;color:#725f53;font-size:11px;line-height:1.6}.code-output,.import-row{display:flex;align-items:center;gap:8px;margin-top:11px}.code-output{justify-content:space-between;padding:8px;border:1px dashed #fb923c;border-radius:8px;background:#fff}.code-output code{color:#9a3412;font:900 13px/1 var(--font-mono);letter-spacing:.05em}.import-row :deep(.el-input){flex:1}.import-preview{display:grid;grid-template-columns:minmax(220px,.8fr) minmax(0,1fr) auto;align-items:center;gap:16px;margin-top:14px;padding:14px;border:1px solid #86efac;border-radius:12px;background:#f0fdf4}.import-preview-stage{position:relative;height:238px;min-width:0;overflow:hidden;border:1px solid #fed7aa;border-radius:14px;background:#fffaf5}.import-preview-stage :deep(.orange-scene.compact){height:100%;min-height:238px;border-radius:13px}.import-preview-stage :deep(.scene-zoom){display:none}.import-preview-stage>span{position:absolute;z-index:5;top:10px;left:10px;padding:5px 8px;border:1px solid rgba(255,255,255,.9);border-radius:999px;background:rgba(255,255,255,.86);color:#9a3412;font:850 8px/1 var(--font-mono);letter-spacing:.06em;pointer-events:none}.import-preview-copy span,.import-preview-copy strong{display:block}.import-preview-copy span{color:#15803d;font:800 9px/1 var(--font-mono)}.import-preview-copy strong{margin-top:7px;font-size:13px}.import-preview-copy p{margin:5px 0 0;color:#587060;font-size:10px;line-height:1.5}.share-lock{margin:13px 0 0;color:#a16207;font-size:11px;font-weight:750}
@media(max-width:760px){.import-preview{grid-template-columns:1fr}.import-preview-stage{height:280px}.import-preview-stage :deep(.orange-scene.compact){min-height:280px}.import-preview>:deep(.el-button){width:100%}}
@media(max-width:620px){.share-heading{flex-direction:column}.share-grid{grid-template-columns:1fr}.share-panel>p{min-height:0}}
</style>
