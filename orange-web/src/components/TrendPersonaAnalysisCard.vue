<template>
  <section class="persona-analysis-card" aria-labelledby="persona-analysis-title">
    <div class="persona-stage">
      <div class="stage-label"><span></span> 画像实时预览</div>
      <OrangePersonaScene
        compact
        :equipment="snapshot.equipment"
        :appearance="snapshot.settings"
        active-focus="overview"
      />
      <div v-if="!loading && !hasPersona" class="persona-prompt" role="status">
        <strong>这颗橙子还在等你赋予它个性</strong>
        <p>请先去橙子画像工坊完善您的橙子形象</p>
        <button type="button" @click="goToWorkshop">前往画像工坊</button>
      </div>
      <div v-else-if="loading" class="persona-loading">正在唤醒你的橙子…</div>
    </div>

    <div class="persona-controls">
      <div class="section-kicker">PERSONA ANALYSIS</div>
      <h3 id="persona-analysis-title">让你的橙子参与这次分析</h3>
      <p class="section-intro">把你逐步积累的目标、兴趣、技能与资源带入分析，让建议更贴近真实的你。</p>

      <button
        type="button"
        class="persona-primary-toggle"
        :class="{ active: usePersona, locked: !hasPersona }"
        :aria-pressed="usePersona"
        @click="togglePersona"
      >
        <span class="toggle-mark">{{ usePersona ? '✓' : '○' }}</span>
        <span>
          <strong>使用橙子画像进行分析</strong>
          <small>{{ hasPersona ? `${completedDimensions} 个画像维度已可参与本次分析` : '完善至少一个画像维度后即可开启' }}</small>
        </span>
      </button>

      <div class="analysis-options" aria-label="分析增强选项">
        <button type="button" :class="{ active: useRag }" :aria-pressed="useRag" @click="emit('update:useRag', !useRag)">
          <span class="option-icon">库</span>
          <span><strong>知识库增强</strong><small>补充方法、案例与真实项目依据</small></span>
          <i>{{ useRag ? '已开启' : '未开启' }}</i>
        </button>
        <button type="button" :class="{ active: preferBeginnerProjects }" :aria-pressed="preferBeginnerProjects" @click="toggleBeginner">
          <span class="option-icon mint">新</span>
          <span><strong>优先新手</strong><small>优先参考已核验的新手友好项目</small></span>
          <i>{{ preferBeginnerProjects ? '已开启' : '未开启' }}</i>
        </button>
        <button type="button" :class="{ active: preferCareerProjects }" :aria-pressed="preferCareerProjects" @click="toggleCareer">
          <span class="option-icon blue">职</span>
          <span><strong>优先职业</strong><small>强调岗位能力与作品证明方式</small></span>
          <i>{{ preferCareerProjects ? '已开启' : '未开启' }}</i>
        </button>
      </div>

      <button type="button" class="start-analysis-button" :disabled="analyzing" @click="emit('analyze')">
        <span>{{ analyzing ? '正在分析中…' : '开始智能分析' }}</span>
        <b aria-hidden="true">{{ analyzing ? '●' : '→' }}</b>
      </button>

      <div class="persona-footnote">
        <span>{{ hasPersona ? `画像完成度 ${snapshot.completion?.percent || 0}%` : '尚未开始画像' }}</span>
        <button type="button" @click="goToWorkshop">{{ hasPersona ? '继续完善画像' : '开始创建画像' }} →</button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import OrangePersonaScene from './OrangePersonaScene.vue'
import { pinia } from '../stores'
import { usePersonaStore } from '../stores/personaStore'

const props = defineProps({
  usePersona: { type: Boolean, default: false },
  useRag: { type: Boolean, default: true },
  preferBeginnerProjects: { type: Boolean, default: false },
  preferCareerProjects: { type: Boolean, default: false },
  analyzing: { type: Boolean, default: false },
})
const emit = defineEmits([
  'update:usePersona', 'update:useRag',
  'update:preferBeginnerProjects', 'update:preferCareerProjects', 'analyze',
])
const router = useRouter()
const store = usePersonaStore(pinia)
const snapshot = store.data
const loading = computed(() => store.loading)
const completedDimensions = computed(() => Number(snapshot.completion?.completedDimensions || 0))
const hasPersona = computed(() => completedDimensions.value > 0 ||
  snapshot.dimensions.some(dimension => Array.isArray(dimension.entries) && dimension.entries.length > 0))

const goToWorkshop = () => router.push('/persona-workshop')
const togglePersona = () => {
  if (!hasPersona.value) { goToWorkshop(); return }
  emit('update:usePersona', !props.usePersona)
}
const toggleBeginner = () => {
  const enabled = !props.preferBeginnerProjects
  if (enabled) emit('update:useRag', true)
  emit('update:preferBeginnerProjects', enabled)
}
const toggleCareer = () => {
  const enabled = !props.preferCareerProjects
  if (enabled) emit('update:useRag', true)
  emit('update:preferCareerProjects', enabled)
}

watch([hasPersona, loading], ([available, busy]) => {
  if (!busy && store.lastUpdatedAt && !available && props.usePersona) emit('update:usePersona', false)
})

onMounted(() => store.load().catch(() => {}))
</script>

<style scoped>
.persona-analysis-card{display:grid;grid-template-columns:minmax(360px,.92fr) minmax(420px,1.08fr);overflow:hidden;margin-top:18px;border:1px solid #edd8c8;border-radius:26px;background:#fff;box-shadow:0 22px 60px rgba(91,48,25,.09)}
.persona-stage{position:relative;min-height:410px;padding:12px;background:#fff8ef}.stage-label{position:absolute;z-index:6;top:23px;left:25px;display:flex;align-items:center;gap:8px;padding:7px 10px;border:1px solid rgba(255,255,255,.9);border-radius:999px;background:rgba(255,255,255,.82);color:#7c2d12;font:800 10px/1 var(--font-mono);letter-spacing:.06em;backdrop-filter:blur(10px)}.stage-label span{width:7px;height:7px;border-radius:50%;background:#22c55e;box-shadow:0 0 0 5px rgba(34,197,94,.12)}
.persona-prompt,.persona-loading{position:absolute;z-index:7;right:22px;bottom:22px;left:22px;padding:15px 17px;border:1px solid rgba(255,255,255,.9);border-radius:15px;background:rgba(255,255,255,.9);box-shadow:0 12px 32px rgba(124,45,18,.12);backdrop-filter:blur(12px)}.persona-prompt strong,.persona-prompt p{display:block;margin:0}.persona-prompt strong{color:#431407;font:850 13px/1.4 var(--font-mono)}.persona-prompt p{margin-top:5px;color:#806455;font-size:12px}.persona-prompt button{margin-top:11px;padding:8px 12px;border:0;border-radius:8px;background:#ea580c;color:#fff;font-size:11px;font-weight:800;cursor:pointer}.persona-loading{color:#9a3412;font-size:12px;font-weight:800;text-align:center}
.persona-controls{display:flex;min-width:0;flex-direction:column;padding:38px 40px 28px}.section-kicker{color:#c2410c;font:900 10px/1 var(--font-mono);letter-spacing:.14em}.persona-controls h3{margin:12px 0 9px;color:#2b160d;font:900 clamp(24px,2.5vw,34px)/1.08 var(--font-mono);letter-spacing:-.035em}.section-intro{max-width:600px;margin:0 0 22px;color:#765f52;font-size:13px;line-height:1.75}
.persona-primary-toggle{display:flex;width:100%;align-items:center;gap:14px;padding:16px;border:1px solid #efd5c2;border-radius:15px;background:#fffaf5;color:#39180b;text-align:left;cursor:pointer;transition:border-color .18s,box-shadow .18s,transform .18s}.persona-primary-toggle:hover,.persona-primary-toggle:focus-visible{border-color:#fb923c;box-shadow:0 10px 28px rgba(234,88,12,.1);outline:3px solid rgba(251,146,60,.14);transform:translateY(-1px)}.persona-primary-toggle.active{border-color:#ea580c;background:#fff7ed}.persona-primary-toggle.locked{border-style:dashed}.toggle-mark{display:grid;width:35px;height:35px;flex:0 0 35px;place-items:center;border-radius:11px;background:#ffedd5;color:#c2410c;font:900 18px/1 var(--font-mono)}.persona-primary-toggle.active .toggle-mark{background:#ea580c;color:#fff}.persona-primary-toggle strong,.persona-primary-toggle small{display:block}.persona-primary-toggle strong{font-size:14px}.persona-primary-toggle small{margin-top:5px;color:#8b6e5d;font-size:10px;line-height:1.45}
.analysis-options{display:grid;gap:8px;margin-top:13px}.analysis-options button{display:grid;grid-template-columns:34px minmax(0,1fr) auto;align-items:center;gap:11px;padding:11px 12px;border:1px solid #eee2d9;border-radius:12px;background:#fff;color:#3d251a;text-align:left;cursor:pointer}.analysis-options button:hover,.analysis-options button:focus-visible{border-color:#fdba74;outline:3px solid rgba(251,146,60,.11)}.analysis-options button.active{border-color:#fed7aa;background:#fffbf5}.option-icon{display:grid;width:32px;height:32px;place-items:center;border-radius:9px;background:#ffedd5;color:#9a3412;font:850 11px/1 var(--font-mono)}.option-icon.mint{background:#d1fae5;color:#047857}.option-icon.blue{background:#dbeafe;color:#1d4ed8}.analysis-options strong,.analysis-options small{display:block}.analysis-options strong{font-size:12px}.analysis-options small{margin-top:3px;color:#8a7568;font-size:10px;line-height:1.4}.analysis-options i{padding:4px 7px;border-radius:999px;background:#f4eee9;color:#8a7568;font-size:9px;font-style:normal;font-weight:800}.analysis-options button.active i{background:#ffedd5;color:#9a3412}
.start-analysis-button{display:flex;width:100%;min-height:48px;align-items:center;justify-content:space-between;margin-top:16px;padding:0 17px;border:1px solid #c2410c;border-radius:13px;background:#c2410c;color:#fff;font:850 13px/1 var(--font-mono);cursor:pointer;box-shadow:0 12px 26px rgba(194,65,12,.2)}.start-analysis-button:hover,.start-analysis-button:focus-visible{background:#9a3412;outline:3px solid rgba(251,146,60,.25);transform:translateY(-1px)}.start-analysis-button:disabled{border-color:#d7c5b8;background:#b9a79c;box-shadow:none;cursor:wait;transform:none}.start-analysis-button b{display:grid;width:26px;height:26px;place-items:center;border-radius:8px;background:rgba(255,255,255,.15);font-size:15px}
.persona-footnote{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-top:auto;padding-top:18px;color:#8a7568;font-size:10px}.persona-footnote button{padding:0;border:0;background:transparent;color:#c2410c;font-size:10px;font-weight:850;cursor:pointer}
@media(max-width:1050px){.persona-analysis-card{grid-template-columns:1fr}.persona-stage{min-height:360px}.persona-controls{padding:30px}}
@media(max-width:640px){.persona-analysis-card{border-radius:20px}.persona-stage{min-height:320px;padding:8px}.persona-controls{padding:24px 18px}.analysis-options button{grid-template-columns:32px minmax(0,1fr)}.analysis-options i{display:none}}
@media(prefers-reduced-motion:reduce){.persona-primary-toggle{transition:none}.persona-primary-toggle:hover{transform:none}}
</style>
