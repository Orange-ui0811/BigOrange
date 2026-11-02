<template>
  <div class="customer-report">
    <section v-if="report" class="report-insight" aria-label="领域洞察">
      <header class="report-heading"><span>01</span><div><small>FIELD INSIGHT</small><h3>领域洞察</h3></div></header>
      <p>{{ text(report.direction) }}</p>
      <p>{{ text(report.approach) }}</p>
      <details class="report-boundary"><summary>本次建议的依据</summary><p>{{ text(report.scopeNote) }}</p></details>
    </section>
    <section aria-label="为你推荐">
      <header v-if="report" class="report-heading"><span>02</span><div><small>RECOMMENDATIONS</small><h3>为你推荐</h3></div></header>
      <div class="customer-recommendations"><slot /></div>
    </section>
    <section v-if="report?.learning?.length" class="report-learning" aria-label="学习路径">
      <header class="report-heading"><span>03</span><div><small>LEARNING PATH</small><h3>学习路径</h3></div></header>
      <p class="report-timeline">{{ text(report.timeline) }}</p>
      <article v-for="plan in report.learning" :key="plan.key" class="project-learning">
        <h4>{{ plan.name }}<small>{{ plan.personalized ? '实践建议' : '起步建议' }}</small></h4>
        <ol><li v-for="(step,index) in plan.steps" :key="index">{{ text(step) }}</li></ol>
      </article>
      <p class="report-footnote">路径是待实施的建议；项目功能与依赖请核对卡片引用，完成时间以实际验证为准。</p>
    </section>
  </div>
</template>
<script setup>
import { normalizeCustomerText as text } from '../utils/customerText'
defineProps({ report: { type: Object, default: null } })
</script>
<style scoped>
.customer-report{display:grid;gap:26px;margin-top:22px;min-width:0}.report-heading{display:flex;align-items:center;gap:12px;margin-bottom:16px}.report-heading>span{display:grid;place-items:center;width:34px;height:38px;border-left:3px solid var(--brand,#f37021);background:#fff1df;color:#a24c1c;font-size:16px;font-weight:700}.report-heading small{font-size:10px;letter-spacing:.14em;color:#8b7564}.report-heading h3{margin:3px 0 0;font-size:17px;color:#4a3425}.report-insight,.report-learning{padding:20px;border:1px solid #eadccd;border-radius:12px;background:#fffdf8}.report-insight>p,.report-timeline{font-size:14px;line-height:1.9;color:#5a4637;white-space:pre-wrap;margin:10px 0}.report-boundary{font-size:12px;color:#84705e;margin-top:14px;line-height:1.8}.report-boundary summary{cursor:pointer}.report-boundary summary:focus-visible{outline:2px solid var(--brand,#f37021);outline-offset:4px}.project-learning+.project-learning{border-top:1px solid #eadccd;margin-top:20px;padding-top:16px}.project-learning h4{overflow-wrap:anywhere;margin:16px 0 12px;font-size:14px;color:#684326}.project-learning h4 small{display:inline-block;margin-left:10px;padding:3px 7px;background:#f5eddf;border-radius:5px;font-size:11px;font-weight:400}.project-learning ol{margin:0;padding-left:25px}.project-learning li{padding:3px 0 10px 5px;font-size:13px;line-height:1.9;color:#584d44;white-space:pre-wrap;overflow-wrap:anywhere}.project-learning li::marker{color:#ad5a24;font-weight:700}.report-footnote{border-top:1px solid #eadccd;padding-top:12px;margin:14px 0 0;font-size:12px;line-height:1.8;color:#857365}@media(max-width:600px){.report-insight,.report-learning{padding:14px}.customer-report{gap:20px}}
</style>
