<template>
  <div class="career-page">
    <PageTour storageKey="career-center-v1" :steps="tourSteps" />

    <button class="back-link" type="button" @click="$router.push('/ai')">
      <ArrowLeft />
      返回 AI 工具
    </button>

    <header class="page-heading">
      <div>
        <span class="eyebrow">AI 大赛与能力参考</span>
        <h1>用真实任务，校准你的成长方向</h1>
        <p>参加 AI 大赛积累可展示的项目，再对照岗位要求补齐工程能力。</p>
      </div>
      <div class="section-switch" role="tablist" aria-label="功能分区">
        <button
          type="button"
          role="tab"
          :aria-selected="section === 'competitions'"
          :class="{ active: section === 'competitions' }"
          @click="section = 'competitions'"
        >
          <Trophy />
          大赛列表
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="section === 'career'"
          :class="{ active: section === 'career' }"
          @click="section = 'career'"
        >
          <Briefcase />
          工作能力参考
        </button>
      </div>
    </header>

    <main v-if="section === 'competitions'" class="competition-section">
      <div class="section-toolbar">
        <div>
          <h2>{{ competitionScope === 'ACTIVE' ? '正在进行与即将开始' : '历史比赛' }}</h2>
          <p>{{ competitionScope === 'ACTIVE' ? '选择一个与你当前能力匹配的真实赛题。' : '回看往期主题，寻找项目灵感。' }}</p>
        </div>
        <div class="toolbar-actions">
          <div class="scope-switch" aria-label="比赛范围">
            <button type="button" :class="{ active: competitionScope === 'ACTIVE' }" @click="changeScope('ACTIVE')">可参加</button>
            <button type="button" :class="{ active: competitionScope === 'HISTORY' }" @click="changeScope('HISTORY')">历史</button>
          </div>
          <el-button type="primary" @click="competitionDialog = true">
            <Plus />
            投稿比赛
          </el-button>
        </div>
      </div>

      <div class="reward-banner competition-reward">
        <Present />
        <div>
          <strong>{{ rewardText(rewards.competition, '投稿通过可获得') }}</strong>
          <span>请提交真实、可公开访问的比赛信息。</span>
        </div>
      </div>

      <div v-if="competitionLoading" class="competition-grid" aria-label="正在加载比赛">
        <div v-for="n in 6" :key="n" class="competition-card skeleton-card"><el-skeleton :rows="4" animated /></div>
      </div>

      <div v-else-if="competitionError" class="state-panel" role="alert">
        <Warning />
        <h3>比赛列表暂时无法加载</h3>
        <p>{{ competitionError }}</p>
        <el-button @click="loadCompetitions">重新加载</el-button>
      </div>

      <div v-else-if="!competitions.length" class="state-panel">
        <Trophy />
        <h3>{{ competitionScope === 'ACTIVE' ? '暂时没有可参加的比赛' : '暂时没有历史比赛' }}</h3>
        <p>你可以投稿自己发现的比赛，审核通过后会展示在这里。</p>
        <el-button type="primary" @click="competitionDialog = true">投稿比赛</el-button>
      </div>

      <div v-else class="competition-grid">
        <article v-for="item in competitions" :key="item.id" class="competition-card">
          <div class="card-topline">
            <span class="phase-tag" :class="item.phase.toLowerCase()">{{ phaseText(item.phase) }}</span>
            <span class="organizer"><OfficeBuilding />{{ item.organizer }}</span>
          </div>
          <h3>{{ item.name }}</h3>
          <p class="summary">{{ item.summary }}</p>
          <dl class="time-grid">
            <div><dt>开始</dt><dd>{{ formatTime(item.startTime) }}</dd></div>
            <div><dt>结束</dt><dd>{{ formatTime(item.endTime) }}</dd></div>
          </dl>
          <button type="button" class="detail-link" @click="openCompetition(item.sourceUrl)">
            查看详情
            <TopRight />
          </button>
        </article>
      </div>

      <el-pagination
        v-if="competitionTotal > competitionPageSize"
        class="pagination"
        layout="prev, pager, next"
        :total="competitionTotal"
        :page-size="competitionPageSize"
        v-model:current-page="competitionPage"
        @current-change="loadCompetitions"
      />
    </main>

    <main v-else class="career-section">
      <div class="section-toolbar">
        <div>
          <h2>从岗位要求反推学习重点</h2>
          <p>选择技术方向和岗位，查看真实 JD 中反复出现的能力要求。</p>
        </div>
        <el-button type="primary" @click="jobDialog = true">
          <Plus />
          投稿岗位
        </el-button>
      </div>

      <div class="reward-banner job-reward">
        <Present />
        <div>
          <strong>{{ rewardText(rewards.job, '投稿通过可获得') }}</strong>
          <span>请对招聘信息进行原创整理，避免直接搬运完整文案。</span>
        </div>
      </div>

      <div class="career-layout">
        <section class="job-browser" aria-labelledby="job-browser-title">
          <h3 id="job-browser-title">岗位能力库</h3>
          <div class="field-block">
            <span class="field-label">技术方向</span>
            <div v-if="jobDirections.length" class="choice-list">
              <button
                v-for="direction in jobDirections"
                :key="direction"
                type="button"
                :class="{ active: jobDirection === direction }"
                @click="selectDirection(direction)"
              >{{ direction }}</button>
            </div>
            <el-empty v-else description="暂无已审核岗位" :image-size="64" />
          </div>
          <div v-if="jobDirection" class="field-block">
            <span class="field-label">岗位</span>
            <div v-if="jobList.length" class="choice-list">
              <button
                v-for="job in jobList"
                :key="job.id"
                type="button"
                :class="{ active: jobId === job.id }"
                @click="selectJob(job.id)"
              >{{ job.position }}</button>
            </div>
            <el-empty v-else description="该方向暂无岗位" :image-size="64" />
          </div>
          <div v-if="jobLoading" class="job-content"><el-skeleton :rows="8" animated /></div>
          <div v-else-if="jobDetail" class="job-content">
            <div v-safe-html="jobDetailHtml"></div>
            <p class="disclaimer">岗位信息由用户投稿，仅供学习参考。如涉及侵权，请联系 {{ LEGAL_CONTACT }}。</p>
          </div>
          <div v-else class="job-placeholder">
            <Search />
            <p>选择方向和岗位后，在这里查看详细要求。</p>
          </div>
        </section>

        <aside class="ability-panel" aria-labelledby="ability-title">
          <span class="panel-kicker">能力摘要</span>
          <h3 id="ability-title">{{ selectedJobName || '选择岗位后生成参考' }}</h3>
          <dl>
            <div><dt>技术栈</dt><dd>{{ jobTechStack || '等待选择' }}</dd></div>
            <div><dt>作品证据</dt><dd>Demo / 项目文档</dd></div>
            <div><dt>学习重点</dt><dd>工程化与最佳实践</dd></div>
            <div><dt>相关方向</dt><dd>{{ jobDirection || '等待选择' }}</dd></div>
          </dl>
        </aside>
      </div>
    </main>

    <el-dialog v-model="competitionDialog" title="投稿 AI 大赛" width="min(640px, calc(100vw - 32px))" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="比赛名称" required>
          <el-input v-model="competitionForm.name" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="官方网站" required>
          <el-input v-model="competitionForm.sourceUrl" placeholder="https://..." maxlength="768" />
          <span class="helper-text">必须是可公开访问的 HTTPS 官方页面。</span>
        </el-form-item>
        <el-form-item label="比赛简介" required>
          <el-input v-model="competitionForm.summary" type="textarea" :rows="4" maxlength="1200" show-word-limit />
        </el-form-item>
        <el-form-item label="举办方" required>
          <el-input v-model="competitionForm.organizer" maxlength="200" />
        </el-form-item>
        <div class="date-fields">
          <el-form-item label="开始时间（选填）">
            <el-date-picker v-model="competitionForm.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="尚未公布可留空" />
          </el-form-item>
          <el-form-item label="结束时间（选填）">
            <el-date-picker v-model="competitionForm.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="尚未公布可留空" />
          </el-form-item>
        </div>
        <div class="dialog-reward"><Present />{{ rewardText(rewards.competition, '审核通过后，奖励') }}</div>
      </el-form>
      <template #footer>
        <el-button @click="competitionDialog = false">取消</el-button>
        <el-button type="primary" :loading="competitionSubmitting" @click="submitCompetitionForm">提交审核</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="jobDialog" title="投稿岗位信息" width="min(680px, calc(100vw - 32px))" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="岗位名称" required><el-input v-model="jobForm.position" maxlength="200" /></el-form-item>
        <el-form-item label="技术方向" required>
          <el-select v-model="jobForm.direction" placeholder="选择方向" style="width:100%">
            <el-option v-for="direction in submitDirections" :key="direction" :label="direction" :value="direction" />
          </el-select>
        </el-form-item>
        <el-form-item label="JD 内容（Markdown）" required>
          <el-input v-model="jobForm.content" type="textarea" :rows="10" placeholder="请原创整理岗位职责与要求" />
        </el-form-item>
        <div class="notice-box">
          <strong>投稿须知</strong>
          <p>禁止直接复制企业官网完整招聘文案或搬运他人原创内容。平台会保留投稿账号、IP 和时间用于审核与侵权追溯。</p>
          <el-checkbox v-model="jobAgreed">我已阅读并同意投稿规则</el-checkbox>
        </div>
        <div class="dialog-reward"><Present />{{ rewardText(rewards.job, '审核通过后，奖励') }}</div>
      </el-form>
      <template #footer>
        <el-button @click="jobDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!jobAgreed" :loading="jobSubmitting" @click="submitJobForm">提交审核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { useCareerCenter } from '../composables/useCareerCenter'

const { PageTour, ArrowLeft, Briefcase, OfficeBuilding, Present, TopRight, Plus, Search, Trophy,
  Warning, LEGAL_CONTACT, rewards, rewardText, tourSteps, section, competitionScope, competitions,
  competitionLoading, competitionError, competitionPage, competitionPageSize, competitionTotal,
  competitionDialog, competitionSubmitting, competitionForm, loadCompetitions, changeScope,
  phaseText, formatTime, openCompetition, submitCompetitionForm, jobDirections, submitDirections,
  jobDirection, jobList, jobId, jobDetailHtml, jobLoading, jobDialog, jobAgreed, jobSubmitting,
  jobForm, selectDirection, selectJob, selectedJobName, jobTechStack, submitJobForm } = useCareerCenter()
</script>

<style scoped src="../styles/views/career-center.css"></style>
