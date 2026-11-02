<template>
  <div class="project-tools-page">
    <PageTour ref="tourRef" storageKey="project-tools" :steps="tourSteps" />

    <header class="project-hero">
      <button type="button" class="back-link" @click="router.push('/ai')"><ArrowLeft />返回 AI 工具</button>
      <div class="hero-layout">
        <div>
          <span class="eyebrow">BUILD YOUR OWN</span>
          <h2>把一个想法，推进成可以上线的项目。</h2>
          <p>从观察热门项目开始，逐步完成方向判断、方案拆解和上线前检查。</p>
        </div>
        <button type="button" class="tour-trigger" @click="tourRef?.restart()"><Guide />查看使用引导</button>
      </div>
      <div class="progress-line" aria-label="项目推进顺序">
        <span v-for="(step, index) in steps" :key="step"><b>{{ String(index + 1).padStart(2, '0') }}</b>{{ step }}</span>
      </div>
    </header>

    <main class="tool-grid" aria-label="自己的项目工具">
      <button v-for="tool in tools" :key="tool.title" type="button" :class="['tool-card', `tool-${tool.id}`]" @click="router.push(tool.route)">
        <span class="tool-topline"><span>{{ tool.stage }}</span><component :is="tool.icon" /></span>
        <strong>{{ tool.title }}</strong>
        <p>{{ tool.description }}</p>
        <span class="tool-outcome">{{ tool.outcome }}</span>
        <span class="tool-action">进入工具 <ArrowRight /></span>
      </button>
    </main>

    <section class="working-note">
      <span>建议顺序</span>
      <p>第一次使用时按 01–04 推进；已有明确方案的项目可以直接进入上线体检。</p>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import PageTour from '../components/PageTour.vue'

const router = useRouter()
const tourRef = ref(null)
const steps = ['寻找灵感', '判断趋势', '形成方案', '上线验收']
const tools = [
  { id: 'github', stage: '01 · DISCOVER', title: '榜单排行', icon: 'Star', route: '/leaderboard', description: '在本网站、GitHub 与新手推荐之间切换，寻找热门作品、技术趋势和能跑起来的参考项目。', outcome: '获得项目灵感与技术参考' },
  { id: 'trend', stage: '02 · ANALYZE', title: '趋势分析', icon: 'TrendCharts', route: '/trend', description: '围绕技术或产品问题进行检索、分析和方向判断。', outcome: '形成可解释的方向结论' },
  { id: 'plan', stage: '03 · PLAN', title: '方案设计', icon: 'Document', route: '/plan', description: '把想法拆成需求、风险、任务计划和岗位能力要求。', outcome: '获得可执行的项目方案' },
  { id: 'launch', stage: '04 · VERIFY', title: '上线体检', icon: 'DocumentChecked', route: '/launch-check', description: '按阶段检查部署形态、安全边界、生产验收和后续运维。', outcome: '获得上线准备报告' },
]
const tourSteps = [
  { selector: '.project-hero', text: '这条路径按“发现、分析、规划、验收”组织，你也可以直接进入当前需要的阶段。', expression: 'smile' },
  { selector: '.progress-line', text: '进度线展示建议顺序，它不是强制流程，不会限制你跳过已有结论的阶段。', expression: 'wink' },
  { selector: '.tool-grid', text: '四个工具各自保存结果；带 AI 生成的操作会在执行前显示配额消耗。', expression: 'sparkle' },
]
</script>

<style scoped>
.project-tools-page { max-width: 1240px; margin: 0 auto; }
.project-hero { padding: 26px 28px 0; border: 1px solid var(--border-orange); border-radius: 16px; background: linear-gradient(118deg,#fff7ed 0%,#fff 68%); box-shadow: var(--shadow-card); }
.back-link,.tour-trigger { display: inline-flex; min-height: 44px; align-items: center; gap: 7px; border: 0; background: transparent; color: var(--text-secondary); font-weight: 700; cursor: pointer; }.back-link svg,.tour-trigger svg { width: 16px; }
.back-link:hover,.back-link:focus-visible,.tour-trigger:hover,.tour-trigger:focus-visible { color: var(--brand-strong); outline: 2px solid rgba(243,112,33,.22); outline-offset: 2px; }
.hero-layout { display: flex; align-items: flex-end; justify-content: space-between; gap: 24px; padding: 18px 0 26px; }
.eyebrow { color: var(--brand-strong); font: 900 11px/1.2 var(--font-mono); }.project-hero h2 { max-width: 760px; margin: 9px 0; font: 900 clamp(27px,4vw,40px)/1.15 var(--font-mono); letter-spacing: 0; }.project-hero p { max-width: 680px; margin: 0; color: var(--text-secondary); font-size: 14px; line-height: 1.75; }
.tour-trigger { flex-shrink: 0; padding: 0 13px; border: 1px solid var(--border-orange); border-radius: 8px; background: #fff; }
.progress-line { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); border-top: 1px solid var(--border-orange); }
.progress-line span { position: relative; display: flex; min-height: 64px; align-items: center; gap: 9px; color: var(--text-secondary); font-size: 12px; font-weight: 700; }.progress-line span:not(:last-child)::after { position: absolute; right: 18px; width: 36px; height: 1px; background: #fdba74; content: ''; }.progress-line b { color: var(--brand-strong); font: 900 11px/1 var(--font-mono); }
.tool-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 14px; margin-top: 22px; }
.tool-card { --tool-color: var(--brand); --tool-soft: var(--brand-soft); display: grid; min-height: 262px; align-content: start; padding: 22px; border: 1px solid var(--border-weak); border-radius: 8px; background: #fff; color: var(--text-primary); text-align: left; cursor: pointer; transition: border-color .18s,box-shadow .18s,transform .18s; }.tool-card:hover,.tool-card:focus-visible { border-color: var(--tool-color); box-shadow: var(--shadow-lift); outline: none; transform: translateY(-3px); }
.tool-github { --tool-color: #24292f; --tool-soft: #f3f4f6; }.tool-trend { --tool-color: #2563eb; --tool-soft: #eff6ff; }.tool-plan { --tool-color: #a16207; --tool-soft: #fffbeb; }.tool-launch { --tool-color: #166534; --tool-soft: #f0fdf4; }
.tool-topline { display: flex; align-items: center; justify-content: space-between; color: var(--tool-color); font: 900 11px/1.2 var(--font-mono); }.tool-topline :deep(svg) { width: 25px; height: 25px; padding: 5px; border-radius: 6px; background: var(--tool-soft); }
.tool-card > strong { margin-top: 28px; font: 900 21px/1.3 var(--font-mono); }.tool-card p { max-width: 520px; margin: 9px 0 18px; color: var(--text-secondary); font-size: 13px; line-height: 1.7; }.tool-outcome { color: var(--text-muted); font-size: 12px; }
.tool-action { display: flex; align-items: center; justify-content: space-between; margin-top: auto; padding-top: 16px; border-top: 1px solid var(--border-weak); color: var(--tool-color); font-size: 12px; font-weight: 800; }.tool-action :deep(svg) { width: 16px; }
.working-note { display: flex; align-items: center; gap: 16px; margin-top: 18px; padding: 16px 18px; border-left: 3px solid var(--brand); background: #fffaf5; }.working-note span { flex-shrink: 0; color: var(--brand-strong); font-weight: 800; }.working-note p { margin: 0; color: var(--text-secondary); font-size: 13px; line-height: 1.65; }
@media (max-width: 760px) { .project-hero { padding: 20px 20px 0; border-radius: 12px; }.hero-layout { align-items: flex-start; flex-direction: column; }.progress-line { grid-template-columns: 1fr 1fr; }.progress-line span:nth-child(2)::after { display: none; }.tool-grid { grid-template-columns: 1fr; }.tool-card { min-height: 230px; }.working-note { align-items: flex-start; flex-direction: column; gap: 6px; } }
@media (max-width: 460px) { .progress-line { grid-template-columns: 1fr; }.progress-line span::after { display: none; }.progress-line span { min-height: 48px; border-bottom: 1px solid rgba(253,186,116,.5); } }
@media (prefers-reduced-motion: reduce) { .tool-card { transition: none; } }
</style>
