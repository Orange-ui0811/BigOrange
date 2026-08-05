<template>
  <aside class="leaderboard-persona-card" aria-labelledby="leaderboard-persona-title">
    <div class="mini-stage">
      <OrangePersonaScene compact :equipment="snapshot.equipment" :appearance="snapshot.settings" active-focus="overview" />
      <span class="stage-chip">ORANGE GUIDE</span>
    </div>
    <div class="mini-copy">
      <div>
        <small>{{ sourceLabel }}</small>
        <strong id="leaderboard-persona-title">让橙子帮你挑项目</strong>
      </div>
      <button type="button" @click="handleRecommend">
        是否根据您的橙子画像推荐当前榜单可以参考的项目
        <span aria-hidden="true">→</span>
      </button>
      <p v-if="!loading && !hasPersona">请先去橙子画像工坊完善您的橙子形象</p>
    </div>
  </aside>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import OrangePersonaScene from './OrangePersonaScene.vue'
import { pinia } from '../stores'
import { usePersonaStore } from '../stores/personaStore'

const props = defineProps({ source: { type: String, default: 'local' } })
const emit = defineEmits(['recommend'])
const router = useRouter()
const store = usePersonaStore(pinia)
const snapshot = store.data
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

onMounted(() => store.load().catch(() => {}))
</script>

<style scoped>
.leaderboard-persona-card{display:grid;width:min(100%,440px);grid-template-columns:168px minmax(0,1fr);overflow:hidden;border:1px solid #f0d6c2;border-radius:22px;background:#fffaf5;box-shadow:0 14px 38px rgba(91,48,25,.09)}.mini-stage{position:relative;height:190px;overflow:hidden;border-right:1px solid #f2ddcd;background:#fff7ed}.mini-stage :deep(.orange-scene.compact){min-height:190px;border-radius:0}.mini-stage :deep(.scene-zoom){display:none}.stage-chip{position:absolute;z-index:6;top:10px;left:10px;padding:5px 7px;border:1px solid rgba(255,255,255,.9);border-radius:999px;background:rgba(255,255,255,.82);color:#9a3412;font:850 8px/1 var(--font-mono);letter-spacing:.08em}.mini-copy{display:flex;min-width:0;flex-direction:column;padding:17px 16px 14px}.mini-copy small,.mini-copy strong{display:block}.mini-copy small{color:#c2410c;font:850 9px/1.2 var(--font-mono);letter-spacing:.06em}.mini-copy strong{margin-top:7px;color:#32170b;font:900 16px/1.2 var(--font-mono)}.mini-copy button{display:flex;align-items:flex-start;justify-content:space-between;gap:10px;margin-top:14px;padding:11px 12px;border:1px solid #fdba74;border-radius:11px;background:#fff;color:#7c2d12;font-size:10px;font-weight:800;line-height:1.55;text-align:left;cursor:pointer}.mini-copy button:hover,.mini-copy button:focus-visible{border-color:#ea580c;background:#fff7ed;outline:3px solid rgba(251,146,60,.15)}.mini-copy button span{display:grid;width:22px;height:22px;flex:0 0 22px;place-items:center;border-radius:7px;background:#ea580c;color:#fff}.mini-copy p{margin:auto 0 0;padding-top:8px;color:#a16207;font-size:9px;line-height:1.45}
@media(max-width:900px){.leaderboard-persona-card{width:100%}}
@media(max-width:560px){.leaderboard-persona-card{grid-template-columns:130px minmax(0,1fr)}.mini-stage{height:180px}.mini-stage :deep(.orange-scene.compact){min-height:180px}}
</style>
