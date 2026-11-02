<template>
  <section class="persona-profile-showcase" aria-labelledby="persona-showcase-title">
    <div class="persona-stage" aria-label="用户的3D橙子形象">
      <OrangePersonaScene
        compact
        :equipment="persona.equipment"
        :appearance="persona.appearance"
        active-focus="overview"
      />
    </div>
    <div class="persona-content">
      <header>
        <div>
          <span>ORANGE PERSONA</span>
          <h4 id="persona-showcase-title">橙子画像</h4>
        </div>
        <strong>{{ persona.completion?.percent || 0 }}% 完整</strong>
      </header>
      <p class="persona-intro">这只橙子由用户确认的画像与装扮共同组成。</p>
      <div class="persona-dimensions">
        <article v-for="dimension in visibleDimensions" :key="dimension.code">
          <span>{{ dimension.label }}</span>
          <p>{{ dimension.entries?.map(item => item.content).join(' · ') || '尚未填写' }}</p>
        </article>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, defineAsyncComponent } from 'vue'

const OrangePersonaScene = defineAsyncComponent(() => import('./OrangePersonaScene.vue'))
const props = defineProps({
  persona: { type: Object, required: true },
})
const visibleDimensions = computed(() => (props.persona.dimensions || [])
  .filter(dimension => Array.isArray(dimension.entries) && dimension.entries.length > 0))
</script>

<style scoped>
.persona-profile-showcase{display:grid;grid-template-columns:minmax(260px,36%) minmax(0,1fr);min-height:330px;margin:0 0 24px;overflow:hidden;border:1px solid #f0d6c2;border-radius:24px;background:#fffaf5;box-shadow:0 18px 44px rgba(91,48,25,.1)}
.persona-stage{position:relative;min-height:330px;border-right:1px solid #f2ddcd;background:#fff7ed}
.persona-stage :deep(.orange-scene.compact){min-height:330px;border-radius:0}
.persona-stage :deep(.scene-zoom){top:12px;right:12px}
.persona-content{min-width:0;padding:26px 28px 28px}
.persona-content header{display:flex;align-items:flex-end;justify-content:space-between;gap:16px}
.persona-content header span{color:#c2410c;font:800 9px/1 var(--font-mono);letter-spacing:.1em}
.persona-content h4{margin:7px 0 0;color:#32170b;font:900 21px/1.2 var(--font-mono);letter-spacing:-.03em}
.persona-content header>strong{color:#9a3412;font:850 12px/1 var(--font-mono);font-variant-numeric:tabular-nums}
.persona-intro{max-width:58ch;margin:12px 0 0;color:#7c6658;font-size:12px;line-height:1.65}
.persona-dimensions{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px;margin-top:18px}
.persona-dimensions article{min-width:0;padding:11px 12px;border:1px solid #f3dfce;border-radius:10px;background:rgba(255,255,255,.82)}
.persona-dimensions span{color:#b45309;font-size:10px;font-weight:850}
.persona-dimensions p{display:-webkit-box;overflow:hidden;margin:5px 0 0;color:#5f4c40;font-size:12px;line-height:1.55;-webkit-box-orient:vertical;-webkit-line-clamp:2}
@media(max-width:760px){.persona-profile-showcase{grid-template-columns:1fr}.persona-stage{min-height:280px;border-right:0;border-bottom:1px solid #f2ddcd}.persona-stage :deep(.orange-scene.compact){min-height:280px}.persona-content{padding:20px}.persona-dimensions{grid-template-columns:1fr}}
</style>
