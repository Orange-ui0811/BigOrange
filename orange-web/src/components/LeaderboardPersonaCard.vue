<template>
  <aside class="leaderboard-persona-card" :class="{ 'model-hidden': !modelVisible }" aria-labelledby="leaderboard-persona-title">
    <div v-if="modelVisible" class="mini-stage">
      <OrangePersonaScene compact :equipment="snapshot.equipment" :appearance="snapshot.settings" active-focus="overview" />
      <span class="stage-chip">ORANGE GUIDE</span>
    </div>
    <div class="mini-copy">
      <div class="copy-heading">
        <div>
        <small>{{ sourceLabel }}</small>
        <strong id="leaderboard-persona-title">让橙子帮你挑项目</strong>
        </div>
        <button class="model-visibility-button" type="button" :aria-pressed="!modelVisible" @click.stop="toggleModel">
          {{ modelVisible ? '隐藏3D橙子' : '显示3D橙子' }}
        </button>
      </div>
      <p class="recommendation-copy">结合你的画像，从当前榜单里筛出更值得参考的项目。</p>
      <button class="recommend-button" type="button" aria-label="是否根据您的橙子画像推荐当前榜单可以参考的项目" @click="handleRecommend">
        用画像推荐本榜单
        <span aria-hidden="true">→</span>
      </button>
      <p v-if="!loading && !hasPersona" class="persona-hint">请先去橙子画像工坊完善您的橙子形象</p>
    </div>
  </aside>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import OrangePersonaScene from './OrangePersonaScene.vue'
import { pinia } from '../stores'
import { usePersonaStore } from '../stores/personaStore'

const props = defineProps({ source: { type: String, default: 'local' } })
const emit = defineEmits(['recommend'])
const router = useRouter()
const store = usePersonaStore(pinia)
const snapshot = store.data
const MODEL_VISIBILITY_KEY = 'orangevc.leaderboardPersonaModelVisible'
const modelVisible = ref(localStorage.getItem(MODEL_VISIBILITY_KEY) !== 'false')
const loading = computed(() => store.loading)
const hasPersona = computed(() => Number(snapshot.completion?.completedDimensions || 0) > 0 ||
  snapshot.dimensions.some(dimension => Array.isArray(dimension.entries) && dimension.entries.length > 0))
const sourceLabel = computed(() => ({
  local: '本网站榜单 · 个性推荐', github: 'GitHub 榜单 · 个性推荐',
  beginner: '新手榜 · 个性推荐', career: '职业榜 · 个性推荐',
})[props.source] || '榜单 · 个性推荐')

const handleRecommend = () => {
  if (!hasPersona.value) { router.push('/persona-workshop'); return }
  emit('recommend')
}
const toggleModel = () => {
  modelVisible.value = !modelVisible.value
  localStorage.setItem(MODEL_VISIBILITY_KEY, String(modelVisible.value))
}

onMounted(() => store.load().catch(() => {}))
</script>

<style scoped>
.leaderboard-persona-card{display:grid;width:100%;grid-template-columns:154px minmax(0,1fr);overflow:hidden;border:1px solid #f0d6c2;border-radius:22px;background:#fffaf5;box-shadow:0 14px 38px rgba(91,48,25,.09)}.leaderboard-persona-card.model-hidden{grid-template-columns:1fr}.mini-stage{position:relative;height:188px;overflow:hidden;border-right:1px solid #f2ddcd;background:#fff7ed}.mini-stage :deep(.orange-scene.compact){min-height:188px;border-radius:0}.mini-stage :deep(.scene-zoom){display:none}.stage-chip{position:absolute;z-index:6;top:10px;left:10px;padding:5px 7px;border:1px solid rgba(255,255,255,.9);border-radius:999px;background:rgba(255,255,255,.82);color:#9a3412;font:850 8px/1 var(--font-mono);letter-spacing:.08em}.mini-copy{display:flex;min-width:0;flex-direction:column;padding:16px}.copy-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.mini-copy small,.mini-copy strong{display:block}.mini-copy small{color:#c2410c;font:850 9px/1.2 var(--font-mono);letter-spacing:.06em}.mini-copy strong{margin-top:7px;color:#32170b;font:900 16px/1.2 var(--font-mono);text-wrap:balance}.mini-copy .model-visibility-button{flex:0 0 auto;margin:0;padding:5px 7px;border:0;border-radius:7px;background:#f7e9dc;color:#8b5a3c;font-size:9px;font-weight:750;line-height:1.2;cursor:pointer}.mini-copy .model-visibility-button:hover,.mini-copy .model-visibility-button:focus-visible{background:#ffedd5;color:#9a3412;outline:2px solid rgba(251,146,60,.22)}.recommendation-copy{margin:12px 0 0;color:#7c6658;font-size:10px;line-height:1.55}.mini-copy .recommend-button{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:12px;padding:10px 11px;border:1px solid #fdba74;border-radius:10px;background:#fff;color:#7c2d12;font-size:11px;font-weight:850;line-height:1.35;text-align:left;cursor:pointer}.mini-copy .recommend-button:hover,.mini-copy .recommend-button:focus-visible{border-color:#ea580c;background:#fff7ed;outline:3px solid rgba(251,146,60,.15)}.mini-copy .recommend-button span{display:grid;width:22px;height:22px;flex:0 0 22px;place-items:center;border-radius:7px;background:#ea580c;color:#fff}.mini-copy .persona-hint{margin:auto 0 0;padding-top:7px;color:#a16207;font-size:9px;line-height:1.4}.model-hidden .recommendation-copy{max-width:46ch}.model-hidden .mini-copy{min-height:154px}
@media(max-width:900px){.leaderboard-persona-card{width:100%}}
@media(max-width:560px){.leaderboard-persona-card{grid-template-columns:124px minmax(0,1fr)}.leaderboard-persona-card.model-hidden{grid-template-columns:1fr}.mini-stage{height:180px}.mini-stage :deep(.orange-scene.compact){min-height:180px}.copy-heading{display:block}.mini-copy .model-visibility-button{margin-top:8px}}
</style>
