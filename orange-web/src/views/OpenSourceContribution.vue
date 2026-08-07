<template>
  <div class="opensource-page">
    <PageTour
      :key="activeTab"
      ref="tourRef"
      :storage-key="`open-source-${activeTab}-v3`"
      :steps="tourSteps"
    />

    <header class="source-header">
      <div class="header-toolbar">
        <button type="button" class="back-link" @click="router.push('/ai')">
          <ArrowLeft />返回 AI 工具
        </button>
        <button type="button" class="tour-trigger" @click="tourRef?.restart()">
          <Guide />使用引导
        </button>
      </div>
      <div class="header-main">
        <div>
          <span class="eyebrow">OPEN SOURCE WORKBENCH</span>
          <h2>参与开源项目</h2>
          <p>从可靠的 Issue 出发，完成筛选、匹配、能力理解和贡献准备。</p>
        </div>
        <dl class="pricing-summary" aria-label="AI 功能配额">
          <div><dt>AI 匹配</dt><dd>{{ priceText(config.matchCost) }}</dd></div>
          <div><dt>岗位匹配</dt><dd>{{ priceText(config.jobMatchCost) }}</dd></div>
          <div><dt>贡献指南</dt><dd>{{ priceText(config.guideCost) }}</dd></div>
        </dl>
      </div>
      <p v-if="configError" class="config-warning">
        <Warning />暂时无法读取最新配额，付费功能已暂停。刷新页面后重试。
      </p>
    </header>

    <nav class="stage-nav" role="tablist" aria-label="开源贡献阶段">
      <button
        v-for="stage in stages"
        :id="`source-tab-${stage.id}`"
        :key="stage.id"
        type="button"
        role="tab"
        :aria-selected="activeTab === stage.id"
        aria-controls="source-stage-panel"
        :class="{ active: activeTab === stage.id }"
        @click="switchTab(stage.id)"
      >
        <span class="stage-index">{{ stage.index }}</span>
        <component :is="stage.icon" class="stage-icon" />
        <span>
          <strong>{{ stage.title }}</strong>
          <small>{{ stage.description }}</small>
        </span>
      </button>
    </nav>

    <main
      id="source-stage-panel"
      class="stage-content"
      role="tabpanel"
      :aria-labelledby="`source-tab-${activeTab}`"
    >
      <header class="stage-heading">
        <div>
          <span>{{ currentStage.index }} · {{ currentStage.kicker }}</span>
          <h3>{{ currentStage.heading }}</h3>
          <p>{{ currentStage.intro }}</p>
        </div>
        <span v-if="currentStage.cost !== null" class="stage-cost">
          本次 {{ priceText(currentStage.cost) }}
        </span>
      </header>

      <template v-if="activeTab === 'discover'">
        <section class="filter-panel" aria-label="Issue 筛选">
          <label class="search-field">
            <span>搜索 Issue</span>
            <el-input
              v-model="filters.keyword"
              clearable
              placeholder="标题、摘要或仓库名称"
              @keyup.enter="searchIssues"
            >
              <template #prefix><Search /></template>
            </el-input>
          </label>
          <label>
            <span>技术方向</span>
            <el-select v-model="filters.tech" clearable placeholder="全部技术">
              <el-option v-for="item in techOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </label>
          <label>
            <span>难度</span>
            <el-select v-model="filters.difficulty" clearable placeholder="全部难度">
              <el-option label="入门" value="BEGINNER" />
              <el-option label="简单" value="EASY" />
              <el-option label="中等" value="MEDIUM" />
            </el-select>
          </label>
          <div class="filter-actions">
            <el-button @click="advancedFiltersOpen = !advancedFiltersOpen">
              <Operation />{{ advancedFiltersOpen ? '收起条件' : '更多条件' }}
            </el-button>
            <el-button type="primary" :loading="loading" @click="searchIssues">
              应用筛选
            </el-button>
          </div>
          <div v-if="advancedFiltersOpen" class="advanced-filters">
            <label>
              <span>参与状态</span>
              <el-select v-model="filters.participationStatus" clearable placeholder="全部状态">
                <el-option label="可尝试" value="TRY" />
                <el-option label="可能被认领" value="POSSIBLY_CLAIMED" />
                <el-option label="不建议" value="NOT_RECOMMENDED" />
              </el-select>
            </label>
            <label>
              <span>更新时间</span>
              <el-select v-model="filters.updatedDays">
                <el-option label="最近 7 天" :value="7" />
                <el-option label="最近 30 天" :value="30" />
                <el-option label="最近 90 天" :value="90" />
              </el-select>
            </label>
            <el-button text @click="resetFilters">清空全部条件</el-button>
          </div>
        </section>

        <CandidateTray
          :items="candidates"
          :maximum="maxCandidates"
          @remove="removeCandidate"
          @clear="clearCandidates"
          @proceed="switchTab('match')"
        />

        <AnalysisProgress
          v-if="loading"
          :active="loading"
          title="正在读取可参与的 Issue"
          message="正在应用筛选条件并整理当前结果。"
        />
        <section v-else-if="issuesError" class="feedback-state error-state">
          <Warning />
          <h4>Issue 列表加载失败</h4>
          <p>{{ issuesError }}</p>
          <el-button type="primary" @click="loadIssues">重新加载</el-button>
        </section>
        <section v-else-if="!issues.length" class="feedback-state">
          <Search />
          <h4>没有找到符合条件的 Issue</h4>
          <p>可以放宽技术方向、难度或更新时间后再试一次。</p>
          <el-button @click="resetFilters">清空筛选</el-button>
        </section>
        <section v-else class="issue-grid" aria-label="Issue 列表">
          <IssueCard
            v-for="issue in issues"
            :key="issue.id"
            :issue="issue"
            :selected="isCandidate(issue.id)"
            @toggle="toggleCandidate"
            @open="openIssue"
          />
        </section>
        <div v-if="total > pageSize" class="pagination-row">
          <el-pagination
            v-model:current-page="page"
            :page-size="pageSize"
            :total="total"
            layout="prev, pager, next"
            @current-change="loadIssues"
          />
        </div>
      </template>

      <template v-else-if="activeTab === 'match'">
        <div class="match-workspace">
          <section class="match-form">
            <div class="column-heading">
              <span>01</span>
              <div><h4>补充你的实际条件</h4><p>{{ candidateSourceText }}</p></div>
            </div>
            <div class="profile-presets" aria-label="常用个人条件">
              <button
                v-for="preset in profilePresets"
                :key="preset"
                type="button"
                @click="appendProfilePreset(preset)"
              >
                <Plus />{{ preset }}
              </button>
            </div>
            <label>
              <span>个人情况</span>
              <el-input
                v-model="matchForm.profile"
                type="textarea"
                :rows="7"
                maxlength="2000"
                show-word-limit
                placeholder="例如：熟悉 React、TypeScript 和 Vitest，第一次参与开源，希望两天内完成，不使用 Docker。"
              />
            </label>
            <div class="match-fields">
              <label>
                <span>优先技术</span>
                <el-select v-model="matchForm.tech" clearable placeholder="不限">
                  <el-option v-for="item in techOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </label>
              <label>
                <span>最高难度</span>
                <el-select v-model="matchForm.difficulty" clearable placeholder="不限">
                  <el-option label="入门" value="BEGINNER" />
                  <el-option label="简单" value="EASY" />
                  <el-option label="中等" value="MEDIUM" />
                </el-select>
              </label>
            </div>
            <el-button
              type="primary"
              :disabled="!pricingReady"
              :loading="matching"
              @click="runMatch"
            >
              <MagicStick />开始 AI 匹配 · {{ priceText(config.matchCost) }}
            </el-button>
          </section>

          <section class="match-results" aria-live="polite">
            <div class="results-heading">
              <div><span>02</span><h4>匹配结果</h4></div>
              <small v-if="matches.length">共 {{ matches.length }} 项</small>
            </div>
            <AnalysisProgress
              v-if="matching"
              :active="matching"
              title="正在比较候选 Issue"
              message="AI 正在核对你的技术、时间和环境限制，完成后会自动显示结果。"
            />
            <div v-else-if="matchError" class="inline-error">
              <Warning /><span>{{ matchError }}</span>
              <button type="button" @click="$router.push('/ai-records')">查看记录</button>
            </div>
            <section v-else-if="!matches.length" class="feedback-state compact">
              <MagicStick />
              <h4>等待开始匹配</h4>
              <p>AI 会给出 3–5 个候选，并标出相对容易完成的一项。</p>
            </section>
            <article
              v-for="(item, index) in matches"
              v-else
              :key="item.issue.id"
              :class="['match-result', { recommended: item.issue.id === recommendedIssueId }]"
            >
              <div class="result-rank">{{ String(index + 1).padStart(2, '0') }}</div>
              <div>
                <span class="result-label">
                  {{ item.issue.id === recommendedIssueId ? '优先建议' : difficultyText(item.issue.difficulty) }}
                </span>
                <h4>{{ issueTitle(item.issue) }}</h4>
                <p>{{ item.reason }}</p>
                <small>{{ item.risk }}</small>
              </div>
              <button type="button" @click="openIssue(item.issue.id)">查看 <ArrowRight /></button>
            </article>
          </section>
        </div>
      </template>

      <template v-else-if="activeTab === 'job'">
        <section v-if="!jobCandidates.length" class="feedback-state">
          <Briefcase />
          <h4>还没有可分析的 Issue</h4>
          <p>先去发现页加入候选，或完成一次 AI 匹配。</p>
          <el-button type="primary" @click="switchTab('discover')">前往发现 Issue</el-button>
        </section>
        <div v-else class="job-workspace">
          <section class="job-targets">
            <div class="column-heading">
              <span>01</span>
              <div><h4>选择 Issue</h4><p>候选会跨分页保留，不会因重新筛选而丢失。</p></div>
            </div>
            <button
              v-for="issue in jobCandidates"
              :key="issue.id"
              type="button"
              :class="{ active: jobTarget?.id === issue.id }"
              @click="selectJobTarget(issue)"
            >
              <span>{{ issueTitle(issue) }}</span>
              <small>{{ statusText(issue.participationStatus) }} · {{ difficultyText(issue.difficulty) }}</small>
              <ArrowRight />
            </button>
          </section>
          <section class="job-analysis" aria-live="polite">
            <div class="column-heading">
              <span>02</span>
              <div><h4>岗位能力映射</h4><p>先确认目标，开始分析时才会消耗配额。</p></div>
            </div>
            <AnalysisProgress
              v-if="jobMatching"
              :active="jobMatching"
              title="正在生成岗位能力映射"
              message="AI 正在分析能力价值和学习重点，完成后会自动显示。"
            />
            <div v-else-if="jobError" class="inline-error">
              <Warning /><span>{{ jobError }}</span>
              <button type="button" @click="$router.push('/ai-records')">查看记录</button>
            </div>
            <section v-else-if="!jobTarget" class="feedback-state compact">
              <Briefcase />
              <h4>请选择左侧 Issue</h4>
              <p>选择不会扣除配额，可以先确认标题和难度。</p>
            </section>
            <div v-else-if="!jobMatches.length" class="analysis-ready">
              <span>已选择</span>
              <h4>{{ issueTitle(jobTarget) }}</h4>
              <p>将分析对应岗位方向、匹配原因，以及需要重点学习的能力。</p>
              <el-button
                type="primary"
                :disabled="!pricingReady"
                @click="runJobMatchFor(jobTarget)"
              >
                开始岗位分析 · {{ priceText(config.jobMatchCost) }}
              </el-button>
            </div>
            <div v-else class="job-results">
              <div class="selected-issue">{{ issueTitle(jobTarget) }}</div>
              <article v-for="job in jobMatches" :key="job.position">
                <strong>{{ job.position }}</strong>
                <p>{{ job.reason }}</p>
                <small>{{ job.focus }}</small>
              </article>
            </div>
          </section>
        </div>
      </template>

      <template v-else>
        <AnalysisProgress
          v-if="guidesLoading"
          :active="guidesLoading"
          title="正在读取贡献指南"
          message="正在同步你已经保存的指南记录。"
        />
        <section v-else-if="guidesError" class="feedback-state error-state">
          <Warning /><h4>指南列表加载失败</h4><p>{{ guidesError }}</p>
          <el-button type="primary" @click="loadGuides">重新加载</el-button>
        </section>
        <template v-else>
          <section v-if="jobCandidates.length" class="guide-generator">
            <div class="guide-generator-copy">
              <span class="generator-icon"><EditPen /></span>
              <div>
                <strong>为候选 Issue 生成指南</strong>
                <p>可直接选择已加入候选或 AI 匹配出的 Issue，无需返回发现页。</p>
              </div>
            </div>
            <div class="guide-generator-controls">
              <el-select
                v-model="guideTargetId"
                filterable
                placeholder="选择一个候选 Issue"
                aria-label="选择要生成指南的 Issue"
              >
                <el-option
                  v-for="issue in jobCandidates"
                  :key="issue.id"
                  :label="`${guideSourceLabel(issue)} · ${issueTitle(issue)}`"
                  :value="Number(issue.id)"
                />
              </el-select>
              <el-button
                type="primary"
                :disabled="!guideTarget || !pricingReady"
                :loading="guideGenerating"
                @click="generateGuideFor(guideTarget)"
              >
                生成贡献指南 · {{ priceText(config.guideCost) }}
              </el-button>
            </div>
            <div v-if="guideTarget" class="guide-target-preview">
              <span>{{ guideSourceLabel(guideTarget) }}</span>
              <strong>{{ issueTitle(guideTarget) }}</strong>
              <small>{{ guideTarget.repositoryFullName || 'GitHub Issue' }} · {{ difficultyText(guideTarget.difficulty) }}</small>
            </div>
          </section>

          <section v-if="!jobCandidates.length && !guides.length" class="feedback-state">
            <DocumentChecked />
            <h4>还没有候选 Issue</h4>
            <p>先加入至少一个候选，之后就能在这里直接选择并生成指南。</p>
            <el-button type="primary" @click="switchTab('discover')">前往发现 Issue</el-button>
          </section>

          <section v-else-if="!guides.length" class="guide-first-empty">
            <DocumentChecked />
            <div>
              <h4>选择上方 Issue，生成第一份贡献指南</h4>
              <p>指南将整理复现、最小修改、测试、提交 PR 和结项检查事项。</p>
            </div>
          </section>

          <div v-else class="guide-workspace">
            <section class="guide-list">
              <header>
                <div><strong>{{ guides.length }}</strong><span>份已保存指南</span></div>
                <small>选择一份，在右侧连续阅读</small>
              </header>
              <article
                v-for="guide in guides"
                :key="guide.id"
                class="guide-entry"
                :class="{ active: currentGuide?.id === guide.id }"
                role="button"
                tabindex="0"
                @click="openGuide(guide.id)"
                @keydown.enter.prevent="openGuide(guide.id)"
              >
                <DocumentChecked />
                <span>
                  <strong>{{ guide.issueTitle }}</strong>
                  <small>{{ guide.repositoryFullName }} · {{ formatTime(guide.updateTime) }}</small>
                </span>
                <button class="guide-delete" type="button" title="删除指南"
                  :disabled="guideDeletingId === guide.id"
                  @click.stop="deleteGuide(guide)">
                  <Delete />
                </button>
              </article>
            </section>
            <section class="guide-reader" aria-live="polite">
              <AnalysisProgress
                v-if="guideLoading"
                :active="guideLoading"
                title="正在打开贡献指南"
                message="正在整理指南内容和结项检查事项。"
              />
              <div v-else-if="guideError" class="inline-error">
                <Warning /><span>{{ guideError }}</span>
              </div>
              <div v-else-if="currentGuide" class="markdown-body" v-html="renderMarkdown(currentGuide.content)"></div>
              <div v-else class="feedback-state compact">
                <Reading /><h4>选择一份指南开始阅读</h4><p>阅读位置会保持在当前工作台中。</p>
              </div>
            </section>
          </div>
        </template>
      </template>
    </main>

    <el-drawer
      v-model="detailOpen"
      size="min(720px, 94vw)"
      title="Issue 详情"
      destroy-on-close
      class="issue-drawer"
    >
      <AnalysisProgress
        v-if="detailLoading"
        :active="detailLoading"
        title="正在读取 Issue 详情"
        message="正在获取核验结果、环境要求和贡献信息。"
      />
      <section v-else-if="detailError" class="feedback-state error-state">
        <Warning /><h4>详情加载失败</h4><p>{{ detailError }}</p>
        <el-button type="primary" @click="openIssue(detailId)">重新加载</el-button>
      </section>
      <div v-else-if="detail" class="issue-detail">
        <div class="detail-heading">
          <div>
            <small>{{ detail.repositoryFullName }} · ★ {{ detail.stars || 0 }}</small>
            <h2>{{ issueTitle(detail) }}</h2>
          </div>
          <a :href="safeExternalHref(detail.htmlUrl)" @click.prevent="openExternalSafely(detail.htmlUrl)">GitHub 原文</a>
        </div>
        <p class="detail-summary">{{ detail.summaryZh || '中文摘要生成中，请以英文原文为准。' }}</p>
        <dl class="facts">
          <div><dt>最近核验</dt><dd>{{ formatTime(detail.verifiedAt) || '尚未核验' }}</dd></div>
          <div><dt>参与判断</dt><dd>{{ statusText(detail.participationStatus) }}</dd></div>
          <div><dt>关联 PR</dt><dd>{{ detail.linkedPrCount || 0 }}</dd></div>
          <div><dt>认领线索</dt><dd>{{ detail.claimStatus || '无' }}</dd></div>
          <div><dt>Node</dt><dd>{{ detail.nodeVersion || '需查看仓库' }}</dd></div>
          <div><dt>包管理器</dt><dd>{{ detail.packageManager || '需查看仓库' }}</dd></div>
          <div><dt>系统要求</dt><dd>{{ detail.systemRequirement || '仓库未说明' }}</dd></div>
          <div><dt>内存 / 磁盘</dt><dd>{{ detail.memoryRequirement || '未说明' }} / {{ detail.diskRequirement || '未说明' }}</dd></div>
        </dl>
        <details class="original-content">
          <summary>查看英文原文</summary>
          <pre>{{ detail.bodyOriginal || detail.titleOriginal }}</pre>
        </details>
        <section class="environment-note">
          <h3>环境提示</h3>
          <p>{{ detail.environmentNotes || '请在贡献前阅读仓库 README 与贡献指南。' }}</p>
        </section>
        <p v-if="detail.contributionUrl" class="contribution-link">
          <a :href="detail.contributionUrl" target="_blank" rel="noopener noreferrer">查看仓库贡献指南</a>
        </p>
        <div v-if="jobMatches.length" class="job-results drawer-results">
          <h3>匹配岗位</h3>
          <article v-for="job in jobMatches" :key="job.position">
            <strong>{{ job.position }}</strong><p>{{ job.reason }}</p><small>{{ job.focus }}</small>
          </article>
        </div>
        <footer class="detail-actions">
          <el-button :loading="verifying" @click="verifyCurrent">重新核验</el-button>
          <el-button @click="toggleCandidate(detail)">
            {{ isCandidate(detail.id) ? '移出候选' : '加入候选' }}
          </el-button>
          <el-button
            :disabled="!pricingReady"
            :loading="jobMatching"
            @click="runJobMatch"
          >
            岗位匹配 · {{ priceText(config.jobMatchCost) }}
          </el-button>
          <el-button
            type="primary"
            :disabled="!pricingReady"
            :loading="guideGenerating"
            @click="generateGuideFor(detail)"
          >
            生成指南 · {{ priceText(config.guideCost) }}
          </el-button>
        </footer>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import PageTour from '../components/PageTour.vue'
import AnalysisProgress from '../features/open-source/AnalysisProgress.vue'
import CandidateTray from '../features/open-source/CandidateTray.vue'
import IssueCard from '../features/open-source/IssueCard.vue'
import { useCandidateSelection } from '../features/open-source/useCandidateSelection'
import { useOpenSourceIssues } from '../composables/useOpenSourceIssues'
import { useContributionMatch } from '../composables/useContributionMatch'
import { useContributionGuides } from '../composables/useContributionGuides'
import { openExternalSafely } from '../composables/useSafeExternalNavigation'
import { safeExternalHref } from '../utils/safeExternalUrl'

const route = useRoute()
const router = useRouter()
const tourRef = ref(null)
const activeTab = ref('discover')
const guideTargetId = ref(null)
const techOptions = ['TypeScript', 'JavaScript', 'React', 'Next.js', 'Vue', 'Vite', 'Storybook', 'Playwright', 'Node.js']
const profilePresets = ['第一次参与开源', '每周可投入 6 小时', '不使用 Docker', '希望两天内完成']

const selection = useCandidateSelection()
const {
  candidates,
  includes: isCandidate,
  toggle: toggleCandidate,
  remove: removeCandidate,
  clear: clearCandidates,
  maxCandidates,
} = selection

const issueFlow = useOpenSourceIssues(selection)
const {
  advancedFiltersOpen, loading, detailOpen, detailLoading, verifying, issues, detail,
  detailId, page, total, issuesError, detailError, filters, pageSize,
  loadIssues, searchIssues, resetFilters, verifyCurrent,
} = issueFlow

const matchFlow = useContributionMatch(selection)
const {
  matching, jobMatching, matches, jobMatches, recommendedIssueId, jobTarget, matchError,
  jobError, matchForm, config, configError, pricingReady, jobCandidates, candidateSourceText,
  confirmCharge, appendProfilePreset, runMatch, selectJobTarget, runJobMatchFor, loadConfig,
} = matchFlow

const stages = [
  { id: 'discover', index: '01', title: '发现 Issue', icon: 'Search', description: '筛选与核验', kicker: 'DISCOVER', heading: '找到值得投入的任务', intro: '浏览近期仍可参与的 Issue，比较难度、认领风险和更新时间。' },
  { id: 'match', index: '02', title: 'AI 匹配', icon: 'MagicStick', description: '缩小选择范围', kicker: 'MATCH', heading: '从候选中找到合适的一项', intro: '结合你的技术栈、时间和环境限制，让 AI 给出可解释的选择建议。', costKey: 'matchCost' },
  { id: 'job', index: '03', title: '岗位匹配', icon: 'Briefcase', description: '理解能力价值', kicker: 'CAREER', heading: '理解贡献对应的岗位能力', intro: '把 Issue 所需能力映射到岗位方向，明确值得深入学习的部分。', costKey: 'jobMatchCost' },
  { id: 'guides', index: '04', title: '贡献指南', icon: 'DocumentChecked', description: '按步骤完成', kicker: 'GUIDE', heading: '阅读和保存完成指南', intro: '按照仓库要求完成复现、修改、测试、提交和结项检查。' },
]
const validTabs = new Set(stages.map(item => item.id))
const hasPrice = value => value !== null && value !== '' && Number.isFinite(Number(value))
const currentStage = computed(() => {
  const stage = stages.find(item => item.id === activeTab.value) || stages[0]
  return { ...stage, cost: stage.costKey ? config.value[stage.costKey] : null }
})
const guideTarget = computed(() =>
  jobCandidates.value.find(issue => Number(issue.id) === Number(guideTargetId.value)) || null)

const stageTourSteps = {
  discover: [
    { selector: '.filter-panel', text: '先用常用条件缩小范围，需要时再展开参与状态和更新时间。浏览不消耗配额。', expression: 'sparkle' },
    { selector: '.candidate-tray', text: '候选 Issue 会跨分页保留，选择至少 3 项后即可开始 AI 匹配。', expression: 'smile' },
  ],
  match: [
    { selector: '.match-form', text: '补充技术能力、时间和环境限制。常用条件可以一键加入描述。', expression: 'sparkle' },
    { selector: '.match-results', text: 'AI 会解释推荐理由和风险；调用前会再次确认配额。', expression: 'smile' },
  ],
  job: [
    { selector: '.job-workspace, .feedback-state', text: '选择一个候选 Issue，理解它对应的岗位方向和学习重点。', expression: 'wink' },
  ],
  guides: [
    { selector: '.guide-generator, .feedback-state', text: '直接从候选或 AI 匹配结果中选择 Issue，并在当前页面生成指南。', expression: 'sparkle' },
    { selector: '.guide-workspace, .guide-first-empty', text: '已生成的指南采用列表加阅读区布局，方便连续查看和执行。', expression: 'smile' },
  ],
}
const tourSteps = computed(() => [
  { selector: '.source-header', text: '顶部集中显示工作台入口和三项动态配额。', expression: 'smile' },
  { selector: '.stage-nav', text: '四个阶段可以独立进入，当前阶段会保留在页面地址中。', expression: 'wink' },
  ...(stageTourSteps[activeTab.value] || stageTourSteps.discover),
])

const issueTitle = issue => issue?.titleZh || issue?.titleOriginal || '未命名 Issue'
const statusText = value => ({ TRY: '可尝试', POSSIBLY_CLAIMED: '可能被认领', NOT_RECOMMENDED: '不建议' }[value] || '待核验')
const difficultyText = value => ({ BEGINNER: '入门', EASY: '简单', MEDIUM: '中等' }[value] || '待评估')
const formatTime = value => value ? String(value).replace('T', ' ').substring(0, 16) : ''
const renderMarkdown = value => DOMPurify.sanitize(marked.parse(value || ''))
const priceText = value => hasPrice(value) ? `${Number(value)} 配额` : '读取中'
const guideSourceLabel = issue => {
  if (Number(issue?.id) === Number(recommendedIssueId.value)) return 'AI 优先推荐'
  if (matches.value.some(item => Number(item.issue?.id) === Number(issue?.id))) return 'AI 匹配'
  return '已加入候选'
}

const openIssue = async id => {
  jobMatches.value = []
  await issueFlow.openIssue(id)
}
const runJobMatch = () => runJobMatchFor(detail.value)

let loadGuides = async () => {}
const switchTab = name => {
  if (!validTabs.has(name)) return
  activeTab.value = name
  router.replace({ path: route.path, query: { ...route.query, tab: name, issue: undefined } })
  if (name === 'guides' && !guides.value.length) loadGuides()
}

const guideFlow = useContributionGuides({
  config,
  confirmCharge,
  closeDetail: () => { detailOpen.value = false },
  openGuides: () => switchTab('guides'),
})
const {
  guideGenerating, guidesLoading, guideLoading, guideDeletingId, guides, currentGuide,
  guidesError, guideError, openGuide, generateGuideFor, deleteGuide,
} = guideFlow
loadGuides = guideFlow.loadGuides

watch(() => route.query.tab, value => {
  if (validTabs.has(value) && activeTab.value !== value) {
    activeTab.value = value
    if (value === 'guides' && !guides.value.length) loadGuides()
  }
})
watch(() => route.query.issue, value => {
  if (value) openIssue(value)
})
watch(jobCandidates, items => {
  if (!items.length) {
    guideTargetId.value = null
    return
  }
  if (items.some(issue => Number(issue.id) === Number(guideTargetId.value))) return
  const preferred = items.find(issue => Number(issue.id) === Number(recommendedIssueId.value)) || items[0]
  guideTargetId.value = Number(preferred.id)
}, { immediate: true })

onMounted(async () => {
  if (validTabs.has(route.query.tab)) activeTab.value = route.query.tab
  await loadConfig()
  await loadIssues()
  if (activeTab.value === 'guides') await loadGuides()
  if (route.query.issue) openIssue(route.query.issue)
})
</script>

<style scoped src="../styles/open-source-contribution.css"></style>
