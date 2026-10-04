<template>
  <article class="rec-card">
    <header class="rec-header">
      <div class="rec-name-area"><span class="rec-num">#{{ index + 1 }}</span><h3 class="rec-name"><a v-if="safeExternalHref(project.url)" :href="safeExternalHref(project.url)" target="_blank" rel="noopener noreferrer">{{ project.name }}</a><template v-else>{{ project.name }}</template></h3></div>
      <div class="rec-meta-area"><span class="rec-difficulty" :class="levelClass">{{ level }}</span><span v-if="score !== null" class="rec-score">{{ scoreLabel }} {{ score }}/100</span></div>
    </header>
    <p v-if="project.description" class="rec-desc">{{ text(project.description) }}</p>
    <div v-if="project.language || project.duration || project.sourceLabel" class="rec-facts"><span v-if="project.language">{{ project.language }}</span><span v-if="project.duration">周期估算：{{ project.duration }}</span><span v-if="project.sourceLabel">{{ project.sourceLabel }}</span></div>
    <p v-if="sourceNote" class="rec-source-note">{{ sourceNote }}</p>
    <div class="rec-details">
      <p v-if="project.vibeCodingReason" class="rec-vibe-box"><b>实践参考：</b>{{ text(project.vibeCodingReason) }}</p>
      <p v-if="project.recommendReason" class="rec-bullet"><b>推荐理由</b><span>{{ text(project.recommendReason) }}</span></p>
      <p v-if="project.difficultyReason" class="rec-bullet"><b>难度说明</b><span>{{ text(project.difficultyReason) }}</span></p>
      <template v-if="project.practiceAdvice?.kind==='SUGGESTION'">
        <p class="rec-bullet"><b>针对你的建议</b><span>{{ text(project.practiceAdvice.recommendationAdvice) }}</span></p>
        <p class="rec-bullet"><b>难度与起步</b><span>{{ text(project.practiceAdvice.difficultyAdvice) }}</span></p>
        <p class="rec-vibe-box"><b>Vibe Coding 实践建议：</b>{{ text(project.practiceAdvice.vibeCodingAdvice) }}</p>
      </template>
      <p v-if="project.analysisReason" class="rec-bullet"><b>已有分析</b><span>{{ text(project.analysisReason) }}</span></p>
      <p class="rec-bullet"><b>前置条件</b><span>{{ text(project.prerequisites) || '尚未核实，请结合仓库环境与依赖说明判断。' }}</span></p>
      <p v-if="project.modificationPlan" class="rec-bullet"><b>改造方向</b><span>{{ text(project.modificationPlan) }}</span></p>
      <p v-if="project.proofPoints" class="rec-bullet"><b>能力证明</b><span>{{ text(project.proofPoints) }}</span></p>
      <p v-if="project.mainRisks" class="rec-bullet rec-risk"><b>风险与门槛</b><span>{{ text(project.mainRisks) }}</span></p>
      <p v-if="project.supportedExplanation" class="rec-bullet"><b>本轮证据</b><span>{{ text(project.supportedExplanation) }}</span></p>
      <ul v-if="project.conditionGaps?.length" class="rec-gaps"><li v-for="gap in project.conditionGaps" :key="gap">{{ gap }}</li></ul>
      <div v-if="project.references?.length && !project.supportedExplanation" class="rec-reference"><b>参考：</b><template v-for="(ref, i) in project.references" :key="i"><a v-if="safeExternalHref(ref.url)" :href="safeExternalHref(ref.url)" target="_blank" rel="noopener noreferrer">{{ ref.name || ref.title || '查看来源' }}</a><span v-else>{{ typeof ref==='string' ? ref : ref.title || ref.evidenceId }}</span><span v-if="i < project.references.length-1">、</span></template></div>
    </div>
    <footer class="rec-actions"><a v-if="safeExternalHref(project.url)" :href="safeExternalHref(project.url)" target="_blank" rel="noopener noreferrer">查看仓库 <el-icon><TopRight /></el-icon></a><button type="button" class="import-plan-btn" :disabled="!importable" @click="$emit('import')">{{ importable ? '一键导入方案设计' : '条件或证据待核实，暂不能导入' }}</button></footer>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { safeExternalHref } from '../utils/safeExternalUrl'
import { normalizeCustomerText, customerDifficulty } from '../utils/customerText'
const props = defineProps({project:{type:Object,required:true},index:{type:Number,default:0},importable:{type:Boolean,default:true},scoreLabel:{type:String,default:'匹配分'}})
defineEmits(['import'])
const text=normalizeCustomerText
const sourceNote=computed(()=>props.project.sourceNote || ({VERIFIED_CANDIDATE:'项目与链接来自本次已验证候选；推荐理由仍需结合项目实际情况判断。',SOURCE_MATCHED:'参考链接可在本次资料中找到，推荐理由仍需结合项目实际情况判断。',PARTIAL:'部分链接缺少本次资料支持，已移除；保留的链接可追溯。'}[props.project.evidenceStatus] || (props.project.evidenceStatus?'本次资料未能支持推荐链接，当前内容为未验证建议。':'')))
const level=computed(()=>customerDifficulty(props.project.difficulty))
const levelClass=computed(()=>level.value==='入门'?'easy':level.value==='进阶'?'advanced':'medium')
const score=computed(()=>{const raw=props.project.score;return typeof raw==='number'&&Number.isFinite(raw)?Math.round(Math.max(0,Math.min(100,raw))):null})
</script>

<style scoped>
.rec-card{box-sizing:border-box;height:100%;padding:20px;background:#fff;border:1px solid #eadccd;border-left:4px solid var(--brand,#f37021);border-radius:3px 14px 14px 3px;color:#3a302a;box-shadow:0 4px 16px #6f482305}
.rec-header{display:flex;flex-wrap:wrap;justify-content:space-between;align-items:flex-start;gap:12px;margin-bottom:14px}.rec-name-area{display:flex;align-items:flex-start;gap:10px;min-width:0;flex:1}.rec-num{flex-shrink:0;background:var(--brand-deep,#c55312);color:#fff;font-size:12px;font-weight:700;padding:3px 9px;border-radius:5px}.rec-name a{color:inherit;text-decoration:none}.rec-name a:hover{text-decoration:underline}.rec-name{margin:2px 0 0;font-size:16px;font-weight:700;line-height:1.6;overflow-wrap:anywhere}.rec-meta-area{display:flex;align-items:center;flex-wrap:wrap;gap:9px;font-size:12px}.rec-difficulty{padding:3px 8px;border-radius:5px;background:#fff1d9;color:#825316}.rec-difficulty.easy{background:#edf3e7;color:#486336}.rec-difficulty.advanced{background:#fae8de;color:#a34324}.rec-score{font-weight:600;color:#a34618}.rec-desc{font-size:14px;line-height:1.8;margin:0 0 12px;white-space:pre-wrap;overflow-wrap:anywhere}.rec-facts{display:flex;flex-wrap:wrap;gap:7px;margin-bottom:12px}.rec-facts span{font-size:12px;padding:3px 8px;border-radius:5px;background:#f5f0e9;color:#6b5847}.rec-source-note{font-size:12px;color:#776a5e;line-height:1.7}.rec-details{border-top:1px solid #efe5d9;padding-top:12px}.rec-bullet{display:grid;grid-template-columns:72px minmax(0,1fr);gap:10px;font-size:13px;line-height:1.85;margin:10px 0}.rec-bullet b{color:#8f4c25;font-weight:600}.rec-bullet span{white-space:pre-wrap;overflow-wrap:anywhere}.rec-vibe-box{background:#faf5ee;border:1px solid #efe0cc;border-radius:8px;padding:10px 12px;font-size:13px;white-space:pre-wrap;overflow-wrap:anywhere}.rec-risk b{color:#97542b}.rec-gaps{padding:10px 12px 10px 28px;background:#fff5e5;color:#7e5429;border-radius:8px;font-size:12px;line-height:1.8}.rec-reference{font-size:12px;overflow-wrap:anywhere;line-height:1.8}.rec-reference a{color:#a34618}.rec-actions{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;margin-top:18px;padding-top:14px;border-top:1px solid #efe5d9}.rec-actions a{display:inline-flex;align-items:center;gap:5px;min-height:44px;color:#9a451d;font-size:13px;text-decoration:none}.rec-actions a:hover{text-decoration:underline}.import-plan-btn{min-height:44px;padding:10px 14px;border:1px solid #ecc29f;border-radius:8px;background:#fff0df;color:#924219;font:inherit;font-size:12px;cursor:pointer}.import-plan-btn:hover:not(:disabled){background:#ffe4c7;border-color:#d99a69}.import-plan-btn:disabled{background:#f7f4ef;border-color:#e7e0d6;color:#786c60;cursor:not-allowed}.rec-actions a:focus-visible,.import-plan-btn:focus-visible{outline:2px solid #c55312;outline-offset:3px}@media(max-width:600px){.rec-card{padding:15px}.rec-name{font-size:14px}.rec-bullet{grid-template-columns:1fr;gap:3px}.rec-meta-area{margin-left:39px}.rec-actions{align-items:stretch}.import-plan-btn{flex:1}}@media(prefers-reduced-motion:reduce){*{transition:none}}
</style>
