<template>
  <article :class="['issue-card', { selected }]">
    <div class="issue-topline">
      <span class="repository-name">{{ issue.repositoryFullName || 'GitHub Issue' }}</span>
      <button
        type="button"
        :class="['candidate-toggle', { selected }]"
        :aria-pressed="selected"
        @click="$emit('toggle', issue)"
      >
        <Check v-if="selected" />
        <Plus v-else />
        {{ selected ? '已加入' : '加入候选' }}
      </button>
    </div>
    <button type="button" class="issue-main" @click="$emit('open', issue.id)">
      <h4>{{ title }}</h4>
      <p>{{ issue.summaryZh || '中文摘要生成中，可先查看 Issue 原文与核验信息。' }}</p>
    </button>
    <div class="issue-signals">
      <span :class="['state-pill', statusClass]">{{ statusText }}</span>
      <span class="difficulty-pill">{{ difficultyText }}</span>
      <span><ChatDotRound />{{ issue.commentsCount || 0 }}</span>
    </div>
    <div class="issue-footer">
      <span>{{ updatedText }}</span>
      <button type="button" @click="$emit('open', issue.id)">查看详情 <ArrowRight /></button>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  issue: { type: Object, required: true },
  selected: { type: Boolean, default: false },
})

defineEmits(['toggle', 'open'])

const title = computed(() => props.issue.titleZh || props.issue.titleOriginal || '未命名 Issue')
const statusText = computed(() => ({ TRY: '可尝试', POSSIBLY_CLAIMED: '可能被认领', NOT_RECOMMENDED: '不建议' }[props.issue.participationStatus] || '待核验'))
const statusClass = computed(() => ({ TRY: 'try', POSSIBLY_CLAIMED: 'claimed', NOT_RECOMMENDED: 'avoid' }[props.issue.participationStatus] || ''))
const difficultyText = computed(() => ({ BEGINNER: '入门', EASY: '简单', MEDIUM: '中等' }[props.issue.difficulty] || '待评估'))
const updatedText = computed(() => props.issue.githubUpdatedAt ? `更新于 ${String(props.issue.githubUpdatedAt).replace('T', ' ').substring(0, 10)}` : '更新时间未知')
</script>

<style scoped>
.issue-card { display: flex; min-height: 238px; flex-direction: column; padding: 17px; border: 1px solid var(--border-light); border-radius: 8px; background: #fff; transition: border-color .18s, box-shadow .18s; }
.issue-card:hover { border-color: #cbd5e1; box-shadow: 0 10px 24px rgba(31,41,55,.06); }
.issue-card.selected { border-color: var(--border-orange); box-shadow: inset 3px 0 0 var(--brand); }
.issue-topline,.issue-signals,.issue-footer { display: flex; align-items: center; }
.issue-topline,.issue-footer { justify-content: space-between; gap: 12px; }
.repository-name { min-width: 0; overflow: hidden; color: #2563eb; font: 700 11px/1.3 var(--font-mono); text-overflow: ellipsis; white-space: nowrap; }
.candidate-toggle { display: inline-flex; min-height: 36px; flex: 0 0 auto; align-items: center; gap: 5px; padding: 0 8px; border: 1px solid var(--border-light); border-radius: 7px; background: #fff; color: var(--text-secondary); font-size: 11px; font-weight: 700; cursor: pointer; }
.candidate-toggle.selected { border-color: var(--border-orange); background: var(--brand-light); color: var(--brand-strong); }
.candidate-toggle :deep(svg) { width: 13px; }
.issue-main { display: block; width: 100%; padding: 0; border: 0; background: transparent; color: inherit; text-align: left; cursor: pointer; }
.issue-main h4 { margin: 14px 0 7px; font: 800 16px/1.45 var(--font-body); letter-spacing: 0; }
.issue-main p { display: -webkit-box; overflow: hidden; margin: 0; color: var(--text-secondary); font-size: 13px; line-height: 1.65; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.issue-signals { flex-wrap: wrap; gap: 7px; margin-top: 14px; color: var(--text-muted); font-size: 11px; }
.issue-signals > span { display: inline-flex; align-items: center; gap: 4px; }
.issue-signals :deep(svg) { width: 13px; }
.state-pill,.difficulty-pill { min-height: 26px; padding: 4px 8px; border: 1px solid #dcdfe6; border-radius: 999px; }
.state-pill.try { color: #166534; border-color: #bbf7d0; background: #f0fdf4; }
.state-pill.claimed { color: #92400e; border-color: #fde68a; background: #fffbeb; }
.state-pill.avoid { color: #991b1b; border-color: #fecaca; background: #fef2f2; }
.difficulty-pill { color: #475569; background: #f8fafc; }
.issue-footer { margin-top: auto; padding-top: 14px; border-top: 1px solid var(--border-light); color: var(--text-muted); font-size: 11px; }
.issue-footer button { display: inline-flex; min-height: 40px; align-items: center; gap: 5px; padding: 0 2px; border: 0; background: transparent; color: #2563eb; font-weight: 700; cursor: pointer; }
.issue-footer button :deep(svg) { width: 14px; }
@media (prefers-reduced-motion: reduce) { .issue-card { transition: none; } }
</style>
