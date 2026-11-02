<template>
  <div class="ai-record-result">
    <template v-if="isTrendV2">
      <section class="record-section insight-section">
        <div class="section-heading">
          <span>01</span>
          <div><small>FIELD INSIGHT</small><h4>领域洞察</h4></div>
        </div>
        <div class="insight-grid">
          <article v-for="item in insightItems" :key="item.key" class="insight-card">
            <span class="card-icon" aria-hidden="true">{{ item.icon }}</span>
            <strong>{{ item.label }}</strong>
            <p>{{ item.value || '暂未生成相关结论' }}</p>
          </article>
        </div>
        <p v-if="result.analysis?.summary" class="insight-summary">{{ result.analysis.summary }}</p>
      </section>

      <section v-if="recommendations.length" class="record-section">
        <div class="section-heading">
          <span>02</span>
          <div><small>RECOMMENDATIONS</small><h4>为你推荐</h4></div>
        </div>
        <div class="recommendation-list">
          <article v-for="(item, index) in recommendations" :key="`${item.name}-${index}`" class="recommendation-card">
            <header>
              <span class="recommendation-index">#{{ index + 1 }}</span>
              <div><h5>{{ item.name || '推荐方向' }}</h5><p>{{ item.description }}</p></div>
              <span v-if="item.score" class="score-badge">{{ item.score }} 分</span>
            </header>
            <div class="recommendation-meta">
              <span v-if="item.difficulty">难度 · {{ item.difficulty }}</span>
              <span v-if="item.prerequisites">前置 · {{ item.prerequisites }}</span>
            </div>
            <dl>
              <template v-if="item.vibeCodingReason"><dt>Vibe Coding</dt><dd>{{ item.vibeCodingReason }}</dd></template>
              <template v-if="item.recommendReason"><dt>推荐理由</dt><dd>{{ item.recommendReason }}</dd></template>
              <template v-if="item.difficultyReason"><dt>难度说明</dt><dd>{{ item.difficultyReason }}</dd></template>
            </dl>
          </article>
        </div>
      </section>

      <section v-if="hasLearning" class="record-section learning-section">
        <div class="section-heading">
          <span>03</span>
          <div><small>LEARNING PATH</small><h4>学习路径</h4></div>
        </div>
        <p v-if="result.learning?.timeline" class="timeline"><strong>预计进度</strong>{{ result.learning.timeline }}</p>
        <ol v-if="learningSteps.length" class="learning-steps">
          <li v-for="(step, index) in learningSteps" :key="index"><span>{{ index + 1 }}</span><p>{{ step }}</p></li>
        </ol>
        <p v-if="result.learning?.tips" class="learning-tip">{{ result.learning.tips }}</p>
      </section>
    </template>

    <section v-else-if="isMatchResult" class="record-section">
      <div class="section-heading">
        <span>RESULT</span>
        <div><small>MATCH REPORT</small><h4>{{ matchHeading }}</h4></div>
      </div>
      <div class="match-list">
        <article v-for="(item, index) in matchItems" :key="index" class="match-card">
          <header><span>#{{ index + 1 }}</span><h5>{{ matchTitle(item) }}</h5><em v-if="matchDifficulty(item)">{{ matchDifficulty(item) }}</em></header>
          <dl>
            <template v-if="matchReason(item)"><dt>匹配原因</dt><dd>{{ matchReason(item) }}</dd></template>
            <template v-if="matchFocus(item)"><dt>学习重点</dt><dd>{{ matchFocus(item) }}</dd></template>
            <template v-if="matchRisk(item)"><dt>注意事项</dt><dd>{{ matchRisk(item) }}</dd></template>
          </dl>
        </article>
      </div>
    </section>

    <article v-else-if="markdownHtml" v-safe-html="markdownHtml" class="record-markdown"></article>

    <section v-else class="record-section generic-section">
      <div class="section-heading"><span>RESULT</span><div><small>STRUCTURED RECORD</small><h4>分析结果</h4></div></div>
      <dl v-if="genericEntries.length" class="generic-list">
        <div v-for="item in genericEntries" :key="item.key"><dt>{{ item.label }}</dt><dd>{{ item.value }}</dd></div>
      </dl>
      <p v-else class="empty-result">这条记录暂时没有可展示的正文。</p>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps({
  businessType: { type: String, default: '' },
  result: { type: Object, default: () => ({}) },
})

const isTrendV2 = computed(() => props.businessType === 'TREND_V2')
const insightItems = computed(() => [
  { key: 'trend', icon: '↗', label: '发展趋势', value: props.result.analysis?.trend },
  { key: 'prospect', icon: '◎', label: '发展前景', value: props.result.analysis?.prospect },
  { key: 'demand', icon: '⌁', label: '市场需求', value: props.result.analysis?.demand },
])
const recommendations = computed(() => Array.isArray(props.result.recommendations) ? props.result.recommendations : [])
const learningSteps = computed(() => Array.isArray(props.result.learning?.steps) ? props.result.learning.steps : [])
const hasLearning = computed(() => Boolean(props.result.learning?.timeline || props.result.learning?.tips || learningSteps.value.length))

const matchItems = computed(() => Array.isArray(props.result.matches) ? props.result.matches : [])
const isMatchResult = computed(() => ['JOB_MATCH', 'OPEN_SOURCE_MATCH'].includes(props.businessType) && matchItems.value.length > 0)
const matchHeading = computed(() => props.businessType === 'OPEN_SOURCE_MATCH' ? '开源 Issue 匹配结果' : '岗位匹配结果')
const matchTitle = item => item?.position || item?.issue?.titleZh || item?.issue?.titleOriginal || item?.issue?.repositoryFullName || '匹配结果'
const matchDifficulty = item => item?.difficulty || item?.issue?.difficulty || ''
const matchReason = item => item?.reason || ''
const matchFocus = item => item?.focus || item?.issue?.techStack || ''
const matchRisk = item => item?.risk || ''

const markdownContent = computed(() => {
  if (typeof props.result.content === 'string') return props.result.content
  if (typeof props.result.guide?.content === 'string') return props.result.guide.content
  return ''
})
const markdownHtml = computed(() => markdownContent.value
  ? DOMPurify.sanitize(marked.parse(markdownContent.value))
  : '')

const labels = {
  phase: '方案阶段', phaseTitle: '阶段名称', stageNo: '体检阶段', stageTitle: '阶段名称',
  summary: '摘要', position: '岗位', reason: '原因', focus: '学习重点', difficulty: '难度',
  recommendedIssueId: '优先推荐 Issue',
}
const ignoredKeys = new Set(['raw', 'result', 'balance', 'segmentCost', 'historySaved'])
const friendlyLabel = key => labels[key] || String(key).replace(/([A-Z])/g, ' $1').trim()
const readableValue = value => {
  if (value === null || value === undefined || value === '') return '—'
  if (Array.isArray(value)) {
    if (value.every(item => ['string', 'number', 'boolean'].includes(typeof item))) return value.join('、')
    return `${value.length} 项结果`
  }
  if (typeof value === 'object') {
    return Object.entries(value)
      .filter(([key]) => !ignoredKeys.has(key))
      .map(([key, nested]) => `${friendlyLabel(key)}：${readableValue(nested)}`)
      .join('；') || '—'
  }
  return String(value)
}
const genericEntries = computed(() => Object.entries(props.result || {})
  .filter(([key]) => !ignoredKeys.has(key))
  .map(([key, value]) => ({ key, label: friendlyLabel(key), value: readableValue(value) })))
</script>

<style scoped>
.ai-record-result { color: #433229; }
.record-section { display: grid; gap: 16px; margin-bottom: 24px; }
.section-heading { display: flex; align-items: center; gap: 12px; padding-bottom: 10px; border-bottom: 1px solid #eadfd7; }
.section-heading > span { min-width: 34px; color: #d16827; font: 800 11px/1 var(--font-mono); letter-spacing: .08em; }
.section-heading small { color: #a18b7d; font: 700 9px/1 var(--font-mono); letter-spacing: .12em; }
.section-heading h4 { margin: 4px 0 0; color: #3f2a1e; font-size: 17px; }
.insight-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.insight-card { padding: 16px; border: 1px solid #edd8c6; border-radius: 12px; background: #fffaf5; }
.card-icon { display: grid; width: 28px; height: 28px; place-items: center; margin-bottom: 12px; border-radius: 8px; background: #f5e0ce; color: #b85a20; font-weight: 900; }
.insight-card strong { color: #6b4027; font-size: 13px; }
.insight-card p, .insight-summary { margin: 8px 0 0; color: #65554c; font-size: 13px; line-height: 1.75; }
.insight-summary { padding: 14px 16px; border-left: 3px solid #d46a27; border-radius: 0 9px 9px 0; background: #fff6ec; }
.recommendation-list, .match-list { display: grid; gap: 12px; }
.recommendation-card, .match-card { padding: 17px; border: 1px solid #e7ddd6; border-radius: 12px; background: #fff; }
.recommendation-card header, .match-card header { display: flex; align-items: flex-start; gap: 12px; }
.recommendation-index, .match-card header > span { color: #cf6828; font: 800 11px/1.5 var(--font-mono); }
.recommendation-card h5, .match-card h5 { flex: 1; margin: 0; color: #3e2b20; font-size: 15px; }
.recommendation-card header p { margin: 5px 0 0; color: #756359; font-size: 12px; line-height: 1.65; }
.score-badge, .match-card em { padding: 4px 8px; border-radius: 999px; background: #fff0e2; color: #b65a22; font-size: 11px; font-style: normal; white-space: nowrap; }
.recommendation-meta { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 12px; }
.recommendation-meta span { padding: 5px 8px; border-radius: 6px; background: #f7f3ef; color: #725d51; font-size: 11px; }
.recommendation-card dl, .match-card dl { display: grid; grid-template-columns: 88px minmax(0, 1fr); gap: 8px 12px; margin: 14px 0 0; }
.recommendation-card dt, .match-card dt { color: #a16a47; font-size: 12px; font-weight: 700; }
.recommendation-card dd, .match-card dd { margin: 0; color: #605048; font-size: 12px; line-height: 1.65; }
.timeline, .learning-tip { margin: 0; padding: 13px 15px; border-radius: 9px; background: #f7faf5; color: #53604f; font-size: 13px; line-height: 1.7; }
.timeline strong { margin-right: 10px; color: #47754d; }
.learning-steps { display: grid; gap: 10px; margin: 0; padding: 0; list-style: none; }
.learning-steps li { display: grid; grid-template-columns: 28px 1fr; gap: 10px; align-items: flex-start; }
.learning-steps span { display: grid; width: 26px; height: 26px; place-items: center; border-radius: 50%; background: #e8f2e6; color: #397247; font-size: 11px; font-weight: 800; }
.learning-steps p { margin: 2px 0 0; color: #62534b; font-size: 13px; line-height: 1.7; }
.record-markdown { color: #44352d; font-size: 14px; line-height: 1.85; }
.record-markdown :deep(h1), .record-markdown :deep(h2), .record-markdown :deep(h3) { color: #3e291d; }
.record-markdown :deep(pre) { overflow: auto; padding: 14px; border-radius: 9px; background: #f7f4f1; }
.generic-list { display: grid; gap: 10px; margin: 0; }
.generic-list > div { display: grid; grid-template-columns: 120px minmax(0, 1fr); gap: 12px; padding: 12px 0; border-bottom: 1px solid #f0e9e4; }
.generic-list dt { color: #9d6948; font-size: 12px; font-weight: 700; }
.generic-list dd { margin: 0; color: #5d4e46; font-size: 13px; line-height: 1.7; }
.empty-result { color: #8b786d; font-size: 13px; }
@media (max-width: 720px) {
  .insight-grid { grid-template-columns: 1fr; }
  .recommendation-card header, .match-card header { flex-wrap: wrap; }
  .recommendation-card dl, .match-card dl, .generic-list > div { grid-template-columns: 1fr; gap: 4px; }
}
</style>
