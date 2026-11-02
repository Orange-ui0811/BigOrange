<template>
  <section class="customer-workspace" aria-label="项目推荐客服">
    <button v-if="showHistory" class="history-scrim" aria-label="收起会话历史" @click="showHistory=false"></button>
    <aside class="session-rail" :class="{ open: showHistory }">
      <div class="rail-heading">会话<button class="mobile-only" aria-label="关闭历史" @click="showHistory=false">×</button></div>
      <button class="new-session" :disabled="!!running || loading || deleting !== null" @click="newSession"><el-icon><EditPen /></el-icon>新建会话</button>
      <p class="rail-caption">最近的咨询</p>
      <nav class="session-list" aria-label="客服会话历史">
        <div v-for="session in sessions" :key="session.id" class="session-item" :class="{ selected: String(session.id)===String(sessionId) }">
          <button class="session-open" :disabled="!!running || loading || deleting !== null" :aria-current="String(session.id)===String(sessionId) ? 'page' : undefined" @click="openSession(session.id)"><el-icon><ChatLineSquare /></el-icon><span>{{ session.title }}</span></button>
          <button class="session-delete" :disabled="!!running || loading || deleting !== null" :aria-label="`删除会话 ${session.title}`" title="删除会话" @click="confirmDelete(session)"><el-icon><Delete /></el-icon></button>
        </div>
        <p v-if="!sessions.length" class="sessions-empty">新的想法，从这里开始。<br>你的咨询会保存在这里。</p>
      </nav>
      <div class="rail-footer"><el-icon><Lock /></el-icon><span>仅你可见的会话记录</span></div>
    </aside>
    <div class="conversation-column">
      <header class="conversation-header"><div><button class="mobile-only" aria-label="打开会话历史" @click="showHistory=!showHistory"><el-icon><Fold /></el-icon></button><span class="header-symbol"><el-icon><ChatDotRound /></el-icon></span><div class="header-copy"><strong>项目客服</strong><span>发现项目 · 一起理清你的下一步</span></div></div><div class="header-actions"><button v-if="enabled" class="goal-settings" :disabled="!!running || loading || deleting !== null" @click="openGoals">目标设置</button><span class="service-badge"><i></i> {{ enabled ? '可以开始聊天' : '正在连接' }}</span></div></header>
      <div v-if="!enabled" class="availability-screen" role="status"><el-icon><Connection /></el-icon><h2>{{ initializing ? '正在连接客服' : '客服暂不可用' }}</h2><p>{{ availabilityMessage || '正在确认账号与服务状态…' }}</p><button v-if="!initializing" @click="init">重新检查客服</button></div>
      <template v-else>
        <div v-if="conditionChips.length" class="conditions-strip" aria-label="当前会话条件"><span>当前条件</span><span v-for="chip in conditionChips" :key="chip.key" class="condition-chip" :title="chip.source">{{ chip.label }}：{{ chip.value }}<small v-if="chip.source==='账号画像'">画像</small></span></div>
        <div v-if="workflowTask.id" class="conditions-strip" aria-label="当前推荐任务"><span>{{ taskStatus }}</span><span v-for="requirement in openRequirements" :key="requirement.id" class="condition-chip" :title="requirement.source?.quote">{{ requirement.strength==='HARD'?'必要':'偏好' }}：{{ requirement.text }}{{ requirement.status==='PENDING'?'（待确认）':'' }}</span></div>
        <div ref="transcript" class="transcript" aria-live="polite" @scroll="trackScroll">
          <div v-if="!messages.length" class="chat-hero"><div class="hero-symbol"><el-icon><ChatDotRound /></el-icon></div><h1>聊聊你想做的项目</h1><p>从一个想法开始。可以先推荐，再逐步确认你的偏好。</p><div class="starter-prompts"><button v-for="prompt in starters" :key="prompt.text" @click="input=prompt.text; composer?.focus()"><el-icon><component :is="prompt.icon" /></el-icon><span>{{ prompt.label }}</span><el-icon><Right /></el-icon></button></div></div>
          <div class="message-column">
            <article v-for="message in messages" :key="message.id" class="message-row" :class="message.role"><div class="message-avatar">{{ message.role==='user' ? '你' : '橙' }}</div><div class="message-body"><div class="message-author">{{ message.role==='user' ? '你' : '项目客服' }}<small v-if="message.result?.profileLoaded">已参考账号画像</small></div>
              <p v-if="message.role==='user'" class="message-text user-text">{{ message.text }}</p>
              <template v-else><div v-safe-html="renderAnswer(message.result.answer || failureText(message))" class="message-text assistant-markdown"></div>
                <p v-if="message.result.notice" class="pending-question">{{ normalizeCustomerText(message.result.notice) }}</p>
                <p v-if="message.result.rankingNotice" class="pending-question">{{ normalizeCustomerText(message.result.rankingNotice) }}</p>
                <ul v-if="message.result.pendingConditions?.length" class="pending-question"><li v-for="condition in message.result.pendingConditions" :key="condition.field">待确认：{{ condition.quote }}</li></ul>
                <CustomerRecommendationReport v-if="message.result.projects?.length" :report="message.result.recommendationReport"><ProjectRecommendationCard v-for="(project,index) in message.result.projects" :key="project.type+':'+project.id" :project="recommendationView(project)" :index="index" :importable="!!project.importable" score-label="资料评分" @import="importProject(message.run_id,project)" /></CustomerRecommendationReport>
                <details v-if="message.result.references?.length" class="evidence-panel"><summary><el-icon><Document /></el-icon>查看证据与章节 <span>{{ message.result.references.length }}</span></summary><blockquote v-for="reference in message.result.references" :key="reference.evidenceId+':'+reference.quote"><p v-if="reference.text">{{ normalizeCustomerText(reference.text) }}</p><div class="source-quote">{{ reference.quote }}</div><footer>{{ reference.title }} · {{ reference.sectionPath || '正文' }} · {{ reference.evidenceId }}</footer></blockquote></details>
                <div v-if="message.result.evidenceDecision?.projectFacts?.length && (message.result.projects?.length || message.result.verificationGaps?.length)" class="pending-question"><strong>项目资料仍待核实</strong><ul><li v-for="(fact,index) in message.result.evidenceDecision.projectFacts" :key="index">{{ fact.projectName ? `${fact.projectName}：` : '' }}{{ normalizeCustomerText(fact.text) }}</li></ul></div>
                <p v-if="message.result.pendingQuestion" class="pending-question">{{ normalizeCustomerText(message.result.pendingQuestion) }}</p>
                <div v-if="isCurrentQuestion(message.result.question)" class="workflow-options"><button v-for="option in message.result.question.options" :key="option" :disabled="!!running || loading" @click="answerQuestion(message.result.question,option)">{{ option }}</button><button v-if="message.result.question.kind!=='MIGRATION'" :disabled="!!running || loading" @click="answerQuestion(message.result.question,'先看看')">先看候选</button></div>
                <div v-if="message.result.listId" class="workflow-matches"><details v-for="(project,index) in message.result.projects || []" :key="project.type+':'+project.id"><summary>{{ project.name }} · 逐项核对</summary><ul><li v-for="match in project.requirementMatches || []" :key="match.requirementId">{{ match.requirement }}：{{ verdictLabel[match.verdict] || match.verdict }} — {{ match.reason }}</li></ul><button :disabled="!!running || loading || message.result.task?.id!==workflowTask.id" @click="chooseFromList(message.result,index)">选定这个项目</button></details></div>
              </template></div></article>
            <div v-if="running" class="run-process" role="status"><span class="processing-dot"></span>{{ progress }}<small>完成校验后显示回答</small></div>
          </div>
        </div>
        <div class="composer-dock"><div v-if="error" class="chat-error" role="alert"><span>{{ error }}</span><button :disabled="initializing || !!running" @click="init">重试加载</button></div><form class="composer-card" @submit.prevent="send"><textarea ref="composer" v-model="input" maxlength="2000" rows="3" aria-label="发送给项目客服的消息" placeholder="说说你的想法，或直接让我推荐项目…" @keydown="composerKey" /><div class="composer-toolbar"><span><el-icon><Connection /></el-icon>会话偏好仅用于本次咨询</span><button v-if="running" type="button" class="stop-button" @click="cancel"><el-icon><VideoPause /></el-icon>停止</button><button v-else type="submit" class="send-button" aria-label="发送" :disabled="loading || deleting !== null || !input.trim()"><el-icon><Top /></el-icon></button></div></form><div class="composer-footnote"><span>Enter 发送 · Shift + Enter 换行</span><span>回答需核对来源，未知信息不会被视为已满足</span></div></div>
      </template>
    </div>
    <el-dialog v-model="showGoals" title="项目目标" width="min(520px, calc(100vw - 32px))" append-to-body>
      <p class="goal-dialog-note">目标可以同时选择，也可以自定义；它们用于推荐偏好，不改变语言、周期等已确认条件。</p>
      <el-checkbox-group v-model="selectedGoals" class="customer-goal-options"><el-checkbox v-for="goal in goalOptions" :key="goal" :value="goal">{{ goal }}</el-checkbox></el-checkbox-group>
      <label class="custom-goal-label" for="customer-custom-goal">自定义目标</label><el-input id="customer-custom-goal" v-model="customGoals" maxlength="360" placeholder="例如：给家里的店做库存管理；多个目标用分号分开" type="textarea" :rows="3" />
      <template #footer><el-button @click="showGoals=false">取消</el-button><el-button type="primary" :disabled="!!running || loading" @click="applyGoals">发送并保存目标</el-button></template>
    </el-dialog>
  </section>
</template>
<script setup>
import { computed,nextTick,onMounted,ref,watch } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { normalizeCustomerText } from '../utils/customerText'
import ProjectRecommendationCard from './ProjectRecommendationCard.vue'
import CustomerRecommendationReport from './CustomerRecommendationReport.vue'
const renderAnswer = text => DOMPurify.sanitize(marked.parse(normalizeCustomerText(text)), { FORBID_TAGS: ['img', 'iframe'], FORBID_ATTR: ['style'] })
import { useCustomerChat,customerRunError } from '../composables/useCustomerChat'
import { safeExternalHref } from '../utils/safeExternalUrl'
import { customerImportDraft } from '../api/customer'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
const router=useRouter()
const { enabled,sessions,sessionId,messages,input,running,progress,error,state,loading,initializing,availabilityMessage,deleting,init,select,send,cancel,fresh,remove }=useCustomerChat()
const transcript=ref(null),composer=ref(null),showHistory=ref(false)
const workflowTask=computed(()=>state.value.workflowV2?.task || {})
const openRequirements=computed(()=>(workflowTask.value.requirements || []).filter(r=>r.field==='open' && r.status!=='REMOVED'))
const taskStatus=computed(()=>({DISCOVERING:'正在理清需求',WAITING_USER:'等待补充信息',READY_TO_SEARCH:'可以查找项目',EVALUATING:'正在核对候选',PRESENTED:'候选已展示',PAUSED:'任务已暂停',COMPLETED:'已选定项目',CANCELLED:'任务已取消'})[workflowTask.value.status] || '推荐任务')
const verdictLabel={SATISFIED:'已满足',PARTIAL:'仅部分满足',CONFLICT:'不符合',UNKNOWN:'尚未核实'}
function isCurrentQuestion(question){return question?.id && question.id===workflowTask.value.openQuestionId && !['PAUSED','CANCELLED','COMPLETED'].includes(workflowTask.value.status)}
async function answerQuestion(question,answer){if(input.value.trim()){ElMessage.info('请先发送或清空输入框内容。');return}input.value=answer;await send({taskId:workflowTask.value.id,questionId:question.id})}
async function chooseFromList(result,index){if(input.value.trim()){ElMessage.info('请先发送或清空输入框内容。');return}input.value=`就选第${index+1}个项目`;await send({taskId:result.task.id,listId:result.listId})}
const showGoals=ref(false),selectedGoals=ref([]),customGoals=ref('')
const goalOptions=['学习','求职','赚钱/接单/创业','探索技术兴趣']
function openGoals(){
  const goals=Array.isArray(state.value.goal)?state.value.goal:state.value.goal?[state.value.goal]:[]
  selectedGoals.value=goals.filter(goal=>goalOptions.includes(goal));customGoals.value=goals.filter(goal=>!goalOptions.includes(goal)).join('；');showGoals.value=true
}
async function applyGoals(){
  const custom=customGoals.value.split(/[；;\n]+/).map(s=>s.trim()).filter(Boolean)
  const goals=[...new Set([...selectedGoals.value,...custom])]
  if(goals.length>6 || goals.some(s=>s.length>120)){ElMessage.warning('最多选择 6 个目标，每项不超过 120 字。');return}
  if(input.value.trim()){ElMessage.warning('请先发送或清空当前输入，避免覆盖未发送的消息。');return}
  input.value=goals.length?`请将项目目标设置为：${goals.join('；')}。其他条件保持不变，先记录目标，不用推荐。`:'项目目标不限，取消目标要求。其他条件保持不变，先记录，不用推荐。'
  showGoals.value=false;await send()
}
let followScroll=true
const labels={sourceType:'项目分类',goal:'目标',experience:'基础',language:'语言',topic:'主题',difficulty:'难度',maxDurationDays:'天数上限',minDurationDays:'天数下限',allowDocker:'Docker',allowDatabase:'数据库',allowSecrets:'密钥'}
const conditionChips=computed(()=>Object.entries(labels).filter(([key])=>key in state.value).map(([key,label])=>({key,label,value:Array.isArray(state.value[key]) ? state.value[key].join('、') : typeof state.value[key]==='boolean' ? state.value[key] ? '允许' : '不使用' : state.value[key],source:state.value.provenance?.[key]?.source==='PROFILE' ? '账号画像' : '本次对话'})))
const starters=[{label:'先推荐几个项目',text:'先给我推荐几个项目，偏好可以边聊边补充',icon:'Compass'},{label:'找适合学习的项目',text:'我想通过项目学习，帮我推荐一些项目',icon:'Reading'},{label:'准备求职作品',text:'我想做求职作品，帮我找几个真实项目',icon:'Briefcase'}]
function recommendationView(project){return {
  ...project,description:project.whatIsIt || project.description,score:project.quality,
  sourceLabel:project.type==='EXTERNAL_PROJECT'?'仓库资料':project.type==='CAREER_PROJECT'?'职业项目资料':'学习项目资料',
  sourceNote:project.type==='EXTERNAL_PROJECT'?'来自已入库仓库资料，结构化信息待核实':project.status!=='PUBLISHED'?'尚未在项目专区发布，按已有资料推荐；已有分析并不等于本轮引用已核实':'项目身份来自业务库；已有分析仍需结合本轮证据核对',
}}
function trackScroll(){const el=transcript.value;if(el)followScroll=el.scrollHeight-el.scrollTop-el.clientHeight<100}
watch(()=>[messages.value.length,progress.value],async()=>{await nextTick();if(followScroll&&transcript.value)transcript.value.scrollTop=messages.value.length || running.value ? transcript.value.scrollHeight : 0})
async function openSession(id){try{await select(id);showHistory.value=false;followScroll=true;await nextTick();if(transcript.value)transcript.value.scrollTop=transcript.value.scrollHeight}catch{error.value='会话加载失败，请重试。'}}
async function confirmDelete(session){
  if(running.value || loading.value || deleting.value !== null) return
  try {
    await ElMessageBox.confirm(`删除“${session.title}”后，该会话将从历史记录中移除。`, '删除会话', {confirmButtonText:'删除会话',cancelButtonText:'保留会话',type:'warning',autofocus:false})
    await remove(session.id)
    ElMessage.success('会话已删除')
  } catch(ex) {
    if(ex!=='cancel' && ex!=='close') ElMessage.error(ex?.message || '删除失败，请稍后重试。')
  }
}
function newSession(){fresh();showHistory.value=false;followScroll=true;nextTick(()=>composer.value?.focus())}
function composerKey(event){if(event.key==='Enter'&&!event.shiftKey&&!event.isComposing&&event.keyCode!==229){event.preventDefault();send()}}
function failureText(message){return customerRunError({status:message.result.status||message.status,error_code:message.error_code||message.result.errorCode})}
async function importProject(runId,project){try{const response=await customerImportDraft(runId,project);if(response.code!==1||typeof response.data?.draft!=='string')throw new Error('导入未通过');sessionStorage.setItem('plan_import',response.data.draft);router.push('/plan')}catch{ElMessage.error('项目已失效、条件已变化或证据不足，请重新推荐。')}}
onMounted(init)
</script>
<style scoped>
@import '../styles/customer-chat.css';
.workflow-options,.workflow-matches { margin-top:12px; display:flex; flex-wrap:wrap; gap:8px; }
.workflow-options button,.workflow-matches button { padding:7px 12px; border:1px solid #d8dfda; border-radius:8px; background:white; color:inherit; cursor:pointer; }
.workflow-matches { display:block; font-size:13px; line-height:1.7; }
.workflow-matches details { margin:8px 0; }
.workflow-options button:disabled,.workflow-matches button:disabled { opacity:.5; cursor:default; }
</style>
