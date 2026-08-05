<template>
  <div class="plan-app">
    <PageTour storageKey="plan" :steps="tourSteps" />
    <div class="plan-main">
      <!-- Compact Head -->
      <div class="compact-head">
        <div>
          <span class="eyebrow">方案设计</span>
          <h2>从一个想法，生成六阶段项目方案。</h2>
          <p>AI 驱动项目规划：想法、需求、成功标准、风险、任务、岗位匹配。</p>
        </div>
        <el-popover placement="bottom-end" width="320" trigger="click" v-if="hasToken">
          <template #reference><el-button size="small">记录 ({{ planHistory.length }})</el-button></template>
          <div class="history-popover">
            <div v-if="!planHistory.length" class="history-empty">暂无记录</div>
            <div v-for="(h,i) in planHistory" :key="i" class="history-item" @click="loadPlan(h)">
              <div class="history-item-text">
                <span class="history-summary">{{ (h.summary||'').substring(0,40) }}</span>
                <span class="history-time">{{ (h.createTime||'').substring(0,16) }}</span>
              </div>
              <el-button size="small" text type="danger" @click.stop="delPlanHistory(i,h.id)">✕</el-button>
            </div>
            <el-button v-if="planHistory.length" class="history-clear" text type="danger"
              @click="clearPlanHistory">清空全部记录</el-button>
          </div>
        </el-popover>
      </div>

      <!-- 阶段导航始终可见 -->
      <div class="phase-nav">
        <div v-for="(p, i) in phases" :key="i"
          class="phase-nav-item" :class="{ active: currentPhase === i+1, done: p.done, generating: p.generating }"
          @click="switchPhase(i+1)">
          <div class="phase-dot">{{ p.done ? '✓' : i+1 }}</div>
          <div>
            <div class="phase-nav-label">{{ p.title }}</div>
            <div class="phase-nav-status" v-if="p.generating">生成中...</div>
            <div class="phase-nav-status" v-else-if="!p.done && currentPhase !== i+1">待生成</div>
          </div>
        </div>
      </div>

      <div v-if="allDone && hasToken" class="save-row">
        <button class="orange-btn save-btn" :disabled="planSaving" @click="savePlan">
          {{ planSaving ? '保存中…' : '保存方案到我的记录' }}
        </button>
      </div>

      <div class="phase-content">
        <div class="phase-title">{{ phases[currentPhase-1]?.title }}</div>
        <div class="phase-subtitle">{{ phaseSubtitles[currentPhase-1] }}</div>

        <!-- Phase 0 输入区 -->
        <div v-if="currentPhase === 1 && !phases[0].done" class="card">
          <el-input v-model="summary" type="textarea" :rows="4" placeholder="描述你的项目想法，越详细越好..." :autosize="{minRows:4,maxRows:12}" />
          <el-input v-model="extra" type="textarea" :rows="2" placeholder="补充信息（可选）：技术偏好、时间限制、特殊需求..." class="mt-12" :autosize="{minRows:2,maxRows:8}" />
          <div class="mode-select-row">
            <span class="mode-label">生成模式</span>
            <el-radio-group v-model="planMode" size="small">
              <el-radio-button value="full">详细方案</el-radio-button>
              <el-radio-button value="lite">精简方案</el-radio-button>
            </el-radio-group>
          </div>
          <button class="orange-btn mt-16" @click="generatePhase(1)" :disabled="!summary.trim()">确认想法</button>
        </div>

        <!-- 未生成状态 -->
        <div v-else-if="!phases[currentPhase-1]?.done && !phases[currentPhase-1]?.generating" class="card">
          <p class="card-placeholder">点击下方按钮，AI 将基于项目描述生成此阶段的分析内容。</p>
          <button class="orange-btn mt-16" @click="generatePhase(currentPhase)">生成本阶段</button>
        </div>

        <!-- 生成中 -->
        <div v-if="phases[currentPhase-1]?.generating" class="card generating-card">
          <span class="generating-spinner">🍊</span>
          <p class="generating-text">AI 正在分析和生成...</p>
        </div>

        <!-- 已生成 -->
        <div v-if="phases[currentPhase-1]?.done" class="card result-card">
          <div v-if="!editing" class="result-content" v-safe-html="renderedContent" @dblclick="startEdit"></div>
          <el-input v-else v-model="editContent" type="textarea" :rows="12" class="mb-12" />
          <div class="btn-row">
            <template v-if="!editing">
              <button class="orange-btn" @click="generatePhase(currentPhase)">重新生成</button>
              <button class="nav-btn" @click="startEdit">编辑</button>
              <button v-if="currentPhase === 5" class="nav-btn import-launch-btn" @click="importToLaunchCheck">
                一键导入上线体检
              </button>
              <button v-if="currentPhase < 6" class="nav-btn nav-btn-next" @click="switchPhase(currentPhase+1)">下一步 →</button>
            </template>
            <template v-else>
              <button class="orange-btn" @click="saveEdit">保存修改</button>
              <button class="nav-btn" @click="cancelEdit">取消</button>
            </template>
          </div>
        </div>

        <!-- 报错 -->
        <div v-if="phaseError" class="card error-card">
          <p class="error-text">{{ phaseError }}</p>
          <button class="nav-btn mt-8" @click="$router.push('/ai-records')">查看 AI 记录</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { usePlanDesigner } from '../composables/usePlanDesigner'

const { PageTour, tourSteps, summary, extra, planMode, currentPhase, phaseError,
  phases, phaseSubtitles, renderedContent,
  generatePhase, editing, editContent, startEdit, saveEdit, cancelEdit, importToLaunchCheck,
  hasToken, planHistory, planSaving, allDone, savePlan, loadPlan, delPlanHistory,
  clearPlanHistory, switchPhase } = usePlanDesigner()
</script>

<style scoped src="../styles/views/plan-designer.css"></style>
