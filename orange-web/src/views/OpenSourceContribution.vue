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
              <Warning /><span>{{ matchError }}</span><button type="button" @click="runMatch">重试</button>
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
              <button type="button" @click="runJobMatchFor(jobTarget)">重试</button>
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
              <button
                v-for="guide in guides"
                :key="guide.id"
                type="button"
                :class="{ active: currentGuide?.id === guide.id }"
                @click="openGuide(guide.id)"
              >
                <DocumentChecked />
                <span>
                  <strong>{{ guide.issueTitle }}</strong>
                  <small>{{ guide.repositoryFullName }} · {{ formatTime(guide.updateTime) }}</small>
                </span>
                <ArrowRight />
              </button>
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
          <a :href="detail.htmlUrl" target="_blank" rel="noopener noreferrer">GitHub 原文</a>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import PageTour from '../components/PageTour.vue'
import request from '../api/request'
import AnalysisProgress from '../features/open-source/AnalysisProgress.vue'
import CandidateTray from '../features/open-source/CandidateTray.vue'
import IssueCard from '../features/open-source/IssueCard.vue'
import { useCandidateSelection } from '../features/open-source/useCandidateSelection'

const route = useRoute()
const router = useRouter()
const tourRef = ref(null)
const activeTab = ref('discover')
const advancedFiltersOpen = ref(false)
const loading = ref(false)
const matching = ref(false)
const detailOpen = ref(false)
const detailLoading = ref(false)
const verifying = ref(false)
const jobMatching = ref(false)
const guideGenerating = ref(false)
const guidesLoading = ref(false)
const guideLoading = ref(false)
const issues = ref([])
const matches = ref([])
const guides = ref([])
const jobMatches = ref([])
const detail = ref(null)
const currentGuide = ref(null)
const recommendedIssueId = ref(null)
const jobTarget = ref(null)
const guideTargetId = ref(null)
const detailId = ref(null)
const page = ref(1)
const total = ref(0)
const issuesError = ref('')
const matchError = ref('')
const jobError = ref('')
const guidesError = ref('')
const guideError = ref('')
const detailError = ref('')
const configError = ref('')
const pageSize = 12
const config = ref({ matchCost: null, jobMatchCost: null, guideCost: null })
const filters = ref({ keyword: '', tech: '', difficulty: '', participationStatus: '', updatedDays: 30 })
const matchForm = ref({ profile: '', tech: '', difficulty: '' })
const techOptions = ['TypeScript', 'JavaScript', 'React', 'Next.js', 'Vue', 'Vite', 'Storybook', 'Playwright', 'Node.js']
const profilePresets = ['第一次参与开源', '每周可投入 6 小时', '不使用 Docker', '希望两天内完成']
const {
  candidates,
  selectedIds,
  includes: isCandidate,
  toggle: toggleCandidate,
  remove: removeCandidate,
  clear: clearCandidates,
  hydrate: hydrateCandidates,
  maxCandidates,
} = useCandidateSelection()

const stages = [
  { id: 'discover', index: '01', title: '发现 Issue', icon: 'Search', description: '筛选与核验', kicker: 'DISCOVER', heading: '找到值得投入的任务', intro: '浏览近期仍可参与的 Issue，比较难度、认领风险和更新时间。' },
  { id: 'match', index: '02', title: 'AI 匹配', icon: 'MagicStick', description: '缩小选择范围', kicker: 'MATCH', heading: '从候选中找到合适的一项', intro: '结合你的技术栈、时间和环境限制，让 AI 给出可解释的选择建议。', costKey: 'matchCost' },
  { id: 'job', index: '03', title: '岗位匹配', icon: 'Briefcase', description: '理解能力价值', kicker: 'CAREER', heading: '理解贡献对应的岗位能力', intro: '把 Issue 所需能力映射到岗位方向，明确值得深入学习的部分。', costKey: 'jobMatchCost' },
  { id: 'guides', index: '04', title: '贡献指南', icon: 'DocumentChecked', description: '按步骤完成', kicker: 'GUIDE', heading: '阅读和保存完成指南', intro: '按照仓库要求完成复现、修改、测试、提交和结项检查。' },
]
const validTabs = new Set(stages.map(item => item.id))
const hasPrice = value => value !== null && value !== '' && Number.isFinite(Number(value))
const pricingReady = computed(() => ['matchCost', 'jobMatchCost', 'guideCost'].every(key => hasPrice(config.value[key])))
const currentStage = computed(() => {
  const stage = stages.find(item => item.id === activeTab.value) || stages[0]
  return { ...stage, cost: stage.costKey ? config.value[stage.costKey] : null }
})
const jobCandidates = computed(() => {
  const byId = new Map()
  candidates.value.forEach(issue => byId.set(Number(issue.id), issue))
  matches.value.map(item => item.issue).filter(Boolean).forEach(issue => byId.set(Number(issue.id), issue))
  return [...byId.values()]
})
const guideTarget = computed(() => jobCandidates.value.find(issue => Number(issue.id) === Number(guideTargetId.value)) || null)
const candidateSourceText = computed(() => selectedIds.value.length >= 3
  ? `将比较你保留的 ${selectedIds.value.length} 个候选 Issue。`
  : '候选不足 3 项时，将结合当前筛选条件寻找合适任务。')

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

const requestOk = response => response?.code === 1
const responseMessage = (response, fallback) => response?.msg || fallback
const issueTitle = issue => issue?.titleZh || issue?.titleOriginal || '未命名 Issue'
const statusText = value => ({ TRY: '可尝试', POSSIBLY_CLAIMED: '可能被认领', NOT_RECOMMENDED: '不建议' }[value] || '待核验')
const difficultyText = value => ({ BEGINNER: '入门', EASY: '简单', MEDIUM: '中等' }[value] || '待评估')
const formatTime = value => value ? String(value).replace('T', ' ').substring(0, 16) : ''
const renderMarkdown = value => DOMPurify.sanitize(marked.parse(value || ''))
const priceText = value => hasPrice(value) ? `${Number(value)} 配额` : '读取中'

const loadIssues = async () => {
  loading.value = true
  issuesError.value = ''
  try {
    const response = await request.get('/app/open-source/issues', {
      params: { page: page.value, size: pageSize, ...filters.value },
    })
    if (!requestOk(response)) throw new Error(responseMessage(response, '服务暂时没有返回 Issue'))
    issues.value = response.data?.records || []
    total.value = Number(response.data?.total || 0)
    hydrateCandidates(issues.value)
  } catch (error) {
    issuesError.value = error?.message || '请检查网络连接后重试'
  } finally {
    loading.value = false
  }
}

const searchIssues = () => {
  page.value = 1
  loadIssues()
}

const resetFilters = () => {
  filters.value = { keyword: '', tech: '', difficulty: '', participationStatus: '', updatedDays: 30 }
  advancedFiltersOpen.value = false
  searchIssues()
}

const openIssue = async id => {
  detailId.value = id
  detailOpen.value = true
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  jobMatches.value = []
  try {
    const response = await request.get(`/app/open-source/issues/${id}`)
    if (!requestOk(response)) throw new Error(responseMessage(response, 'Issue 详情读取失败'))
    detail.value = response.data
    hydrateCandidates([response.data])
  } catch (error) {
    detailError.value = error?.message || '请稍后重试'
  } finally {
    detailLoading.value = false
  }
}

const verifyCurrent = async () => {
  if (!detail.value?.id) return
  verifying.value = true
  try {
    const response = await request.post(`/app/open-source/issues/${detail.value.id}/verify`, {}, { timeout: 90000 })
    if (!requestOk(response)) throw new Error(responseMessage(response, '核验失败'))
    detail.value = response.data
    hydrateCandidates([response.data])
    ElMessage.success('已按 GitHub 当前状态重新核验')
  } catch (error) {
    ElMessage.error(error?.message || '实时核验失败')
  } finally {
    verifying.value = false
  }
}

const appendProfilePreset = preset => {
  if (matchForm.value.profile.includes(preset)) return
  matchForm.value.profile = [matchForm.value.profile.trim(), preset].filter(Boolean).join('；')
}

const confirmCharge = async (title, cost) => {
  if (!hasPrice(cost)) {
    ElMessage.warning('最新配额仍在读取，请稍后再试')
    return false
  }
  try {
    await ElMessageBox.confirm(
      `${title}将消耗 ${Number(cost)} 配额。确认后才会开始调用 AI。`,
      '确认使用配额',
      { confirmButtonText: '确认开始', cancelButtonText: '暂不开始', type: 'warning' },
    )
    return true
  } catch {
    return false
  }
}

const runMatch = async () => {
  if (matchForm.value.profile.trim().length < 10) {
    ElMessage.warning('请先简要描述你的技术能力、时间或环境限制')
    return
  }
  if (!(await confirmCharge('AI 匹配', config.value.matchCost))) return
  matching.value = true
  matchError.value = ''
  matches.value = []
  try {
    const response = await request.post('/app/open-source/match', {
      ...matchForm.value,
      selectedIssueIds: selectedIds.value,
    }, { timeout: 150000 })
    if (!requestOk(response)) throw new Error(responseMessage(response, 'AI 未返回匹配结果'))
    matches.value = response.data?.matches || []
    recommendedIssueId.value = response.data?.recommendedIssueId
    matches.value.map(item => item.issue).filter(Boolean).forEach(issue => {
      if (!isCandidate(issue.id) && candidates.value.length < maxCandidates) toggleCandidate(issue)
    })
    ElMessage.success('匹配完成')
  } catch (error) {
    matchError.value = error?.message || '匹配失败，请稍后重试'
  } finally {
    matching.value = false
  }
}

const selectJobTarget = issue => {
  jobTarget.value = issue
  jobMatches.value = []
  jobError.value = ''
}

const runJobMatchFor = async issue => {
  if (!issue?.id) return
  if (!(await confirmCharge('岗位匹配', config.value.jobMatchCost))) return
  jobTarget.value = issue
  jobMatches.value = []
  jobError.value = ''
  jobMatching.value = true
  try {
    const response = await request.post(`/app/open-source/issues/${issue.id}/job-match`, {}, { timeout: 150000 })
    if (!requestOk(response)) throw new Error(responseMessage(response, 'AI 未返回岗位匹配结果'))
    jobMatches.value = response.data?.matches || []
  } catch (error) {
    jobError.value = error?.message || '岗位匹配失败，请稍后重试'
  } finally {
    jobMatching.value = false
  }
}

const runJobMatch = () => runJobMatchFor(detail.value)

const guideSourceLabel = issue => {
  if (Number(issue?.id) === Number(recommendedIssueId.value)) return 'AI 优先推荐'
  if (matches.value.some(item => Number(item.issue?.id) === Number(issue?.id))) return 'AI 匹配'
  return '已加入候选'
}

const generateGuideFor = async issue => {
  if (!issue?.id) return
  if (!(await confirmCharge('生成贡献指南', config.value.guideCost))) return
  guideGenerating.value = true
  try {
    const response = await request.post(`/app/open-source/issues/${issue.id}/guide`, {}, { timeout: 150000 })
    if (!requestOk(response)) throw new Error(responseMessage(response, 'AI 未返回贡献指南'))
    currentGuide.value = response.data?.guide
    detailOpen.value = false
    await loadGuides(false)
    switchTab('guides')
    ElMessage.success('贡献指南已生成')
  } catch (error) {
    ElMessage.error(error?.message || '指南生成失败，请稍后重试')
  } finally {
    guideGenerating.value = false
  }
}

const loadGuides = async (selectFirst = true) => {
  guidesLoading.value = true
  guidesError.value = ''
  try {
    const response = await request.get('/app/open-source/guides')
    if (!requestOk(response)) throw new Error(responseMessage(response, '指南列表读取失败'))
    guides.value = response.data || []
    if (selectFirst && guides.value.length && !currentGuide.value) await openGuide(guides.value[0].id)
  } catch (error) {
    guidesError.value = error?.message || '请检查网络连接后重试'
  } finally {
    guidesLoading.value = false
  }
}

const openGuide = async id => {
  guideLoading.value = true
  guideError.value = ''
  try {
    const response = await request.get(`/app/open-source/guides/${id}`)
    if (!requestOk(response)) throw new Error(responseMessage(response, '指南内容读取失败'))
    currentGuide.value = response.data
  } catch (error) {
    guideError.value = error?.message || '请稍后重试'
  } finally {
    guideLoading.value = false
  }
}

const switchTab = name => {
  if (!validTabs.has(name)) return
  activeTab.value = name
  router.replace({ path: route.path, query: { ...route.query, tab: name, issue: undefined } })
  if (name === 'guides' && !guides.value.length) loadGuides()
}

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
  try {
    const response = await request.get('/app/open-source/config')
    if (!requestOk(response)) throw new Error(responseMessage(response, '配额读取失败'))
    config.value = {
      matchCost: response.data?.matchCost ?? null,
      jobMatchCost: response.data?.jobMatchCost ?? null,
      guideCost: response.data?.guideCost ?? null,
    }
    if (!pricingReady.value) throw new Error('配额配置不完整')
  } catch (error) {
    configError.value = error?.message || '配额配置读取失败'
  }
  await loadIssues()
  if (activeTab.value === 'guides') await loadGuides()
  if (route.query.issue) openIssue(route.query.issue)
})
</script>

<style scoped>
.opensource-page {
  width: min(1280px, calc(100% - 32px));
  margin: 0 auto;
  padding: 20px 0 54px;
  color: var(--text-primary);
}
.source-header {
  padding: 0 2px 20px;
  border-bottom: 1px solid var(--border-light);
}
.header-toolbar,
.header-main,
.stage-heading,
.results-heading,
.column-heading,
.detail-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}
.back-link,
.tour-trigger {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  gap: 7px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font-weight: 700;
  cursor: pointer;
}
.back-link:hover,
.tour-trigger:hover {
  color: var(--brand-strong);
}
.back-link :deep(svg),
.tour-trigger :deep(svg) {
  width: 16px;
}
.header-main {
  align-items: flex-end;
  margin-top: 12px;
}
.eyebrow,
.stage-heading > div > span {
  color: var(--brand-strong);
  font: 800 11px/1.2 var(--font-mono);
}
.header-main h2 {
  margin: 8px 0 4px;
  font-size: clamp(27px, 3vw, 38px);
  line-height: 1.2;
  letter-spacing: 0;
}
.header-main p,
.stage-heading p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.pricing-summary {
  display: grid;
  min-width: 360px;
  grid-template-columns: repeat(3, 1fr);
  margin: 0;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: #fff;
}
.pricing-summary div {
  padding: 11px 14px;
  border-right: 1px solid var(--border-light);
}
.pricing-summary div:last-child {
  border-right: 0;
}
.pricing-summary dt {
  color: var(--text-muted);
  font-size: 10px;
}
.pricing-summary dd {
  margin: 4px 0 0;
  color: var(--brand-strong);
  font: 800 12px/1.2 var(--font-mono);
}
.config-warning {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 13px 0 0;
  color: #92400e;
  font-size: 12px;
}
.config-warning :deep(svg) {
  width: 15px;
}
.stage-nav {
  position: sticky;
  z-index: 18;
  top: 64px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-top: 18px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  overflow: hidden;
  background: rgba(255, 255, 255, .96);
  box-shadow: 0 8px 24px rgba(34, 24, 18, .05);
  backdrop-filter: blur(10px);
}
.stage-nav button {
  display: grid;
  min-height: 68px;
  grid-template-columns: 24px 22px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  padding: 11px 14px;
  border: 0;
  border-right: 1px solid var(--border-light);
  background: transparent;
  color: var(--text-secondary);
  text-align: left;
  cursor: pointer;
}
.stage-nav button:last-child {
  border-right: 0;
}
.stage-nav button:hover,
.stage-nav button:focus-visible {
  background: var(--surface-warm);
  outline: none;
}
.stage-nav button.active {
  box-shadow: inset 0 -3px 0 var(--brand);
  background: var(--brand-light);
  color: var(--brand-strong);
}
.stage-index {
  color: var(--text-muted);
  font: 800 10px/1 var(--font-mono);
}
.stage-icon {
  width: 18px;
}
.stage-nav strong,
.stage-nav small {
  display: block;
}
.stage-nav strong {
  font-size: 13px;
}
.stage-nav small {
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 10px;
}
.stage-content {
  min-height: 560px;
  padding-top: 26px;
}
.stage-heading {
  align-items: flex-end;
  margin-bottom: 20px;
}
.stage-heading h3 {
  margin: 6px 0 4px;
  font-size: 22px;
  line-height: 1.35;
  letter-spacing: 0;
}
.stage-cost {
  flex: 0 0 auto;
  padding: 8px 10px;
  border: 1px solid var(--border-orange);
  border-radius: 7px;
  color: var(--brand-strong);
  background: var(--brand-light);
  font: 800 11px/1 var(--font-mono);
}
.filter-panel {
  display: grid;
  grid-template-columns: minmax(260px, 1.5fr) repeat(2, minmax(160px, .7fr)) auto;
  align-items: end;
  gap: 12px;
  padding: 16px;
  border-top: 1px solid var(--border-light);
  border-bottom: 1px solid var(--border-light);
  background: var(--surface-warm);
}
.filter-panel label,
.match-form label {
  display: grid;
  gap: 7px;
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 700;
}
.filter-actions {
  display: flex;
  align-items: center;
  gap: 7px;
}
.advanced-filters {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: repeat(2, minmax(180px, 260px)) auto;
  align-items: end;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-light);
}
.issue-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.pagination-row {
  display: flex;
  justify-content: center;
  padding-top: 22px;
}
.match-workspace,
.job-workspace {
  display: grid;
  grid-template-columns: minmax(330px, .8fr) minmax(0, 1.35fr);
  gap: 28px;
}
.match-form,
.job-targets {
  padding-right: 28px;
  border-right: 1px solid var(--border-light);
}
.column-heading {
  justify-content: flex-start;
  align-items: flex-start;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border-light);
}
.column-heading > span,
.results-heading span {
  color: var(--brand-strong);
  font: 900 13px/1 var(--font-mono);
}
.column-heading h4,
.results-heading h4 {
  margin: 0;
  font-size: 15px;
}
.column-heading p {
  margin: 5px 0 0;
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.6;
}
.profile-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin: 16px 0;
}
.profile-presets button {
  display: inline-flex;
  min-height: 34px;
  align-items: center;
  gap: 5px;
  padding: 0 9px;
  border: 1px solid var(--border-light);
  border-radius: 7px;
  background: #fff;
  color: var(--text-secondary);
  font-size: 11px;
  cursor: pointer;
}
.profile-presets button:hover {
  border-color: var(--border-orange);
  color: var(--brand-strong);
}
.profile-presets :deep(svg) {
  width: 12px;
}
.match-form > label {
  margin-bottom: 14px;
}
.match-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}
.match-form > :deep(.el-button) {
  width: 100%;
  min-height: 44px;
}
.results-heading {
  min-height: 45px;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-light);
}
.results-heading > div {
  display: flex;
  align-items: center;
  gap: 10px;
}
.results-heading small {
  color: var(--text-muted);
}
.match-result {
  display: grid;
  min-height: 116px;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 14px;
  align-items: start;
  padding: 16px 8px;
  border-bottom: 1px solid var(--border-light);
}
.match-result.recommended {
  padding-left: 12px;
  border-left: 3px solid var(--brand);
  background: var(--brand-light);
}
.result-rank {
  color: var(--text-muted);
  font: 900 15px/1.4 var(--font-mono);
}
.result-label {
  color: var(--brand-strong);
  font-size: 10px;
  font-weight: 800;
}
.match-result h4 {
  margin: 4px 0 6px;
  font-size: 14px;
  line-height: 1.5;
}
.match-result p {
  margin: 0 0 5px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.65;
}
.match-result small {
  color: #92400e;
  line-height: 1.5;
}
.match-result > button {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  gap: 4px;
  border: 0;
  background: transparent;
  color: #2563eb;
  font-weight: 800;
  cursor: pointer;
}
.match-result > button :deep(svg) {
  width: 14px;
}
.job-targets > button,
.guide-list > button {
  display: grid;
  width: 100%;
  min-height: 74px;
  align-items: center;
  gap: 5px 10px;
  padding: 12px 10px;
  border: 0;
  border-bottom: 1px solid var(--border-light);
  background: #fff;
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
}
.job-targets > button {
  grid-template-columns: minmax(0, 1fr) 16px;
}
.job-targets > button span {
  font-size: 13px;
  font-weight: 800;
  line-height: 1.45;
}
.job-targets > button small,
.guide-list > button small {
  color: var(--text-muted);
  font-size: 10px;
}
.job-targets > button :deep(svg) {
  grid-row: 1 / 3;
  grid-column: 2;
  width: 14px;
  color: var(--text-muted);
}
.job-targets > button:hover,
.job-targets > button.active {
  background: var(--brand-light);
}
.job-targets > button.active span {
  color: var(--brand-strong);
}
.analysis-ready {
  padding: 28px 0;
}
.analysis-ready > span {
  color: var(--brand-strong);
  font: 900 11px/1.2 var(--font-mono);
}
.analysis-ready h4 {
  margin: 8px 0;
  font-size: 18px;
  line-height: 1.45;
}
.analysis-ready p {
  margin: 0 0 17px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.selected-issue {
  margin: 18px 0 4px;
  color: var(--text-muted);
  font-size: 11px;
}
.job-results article {
  padding: 15px 0;
  border-bottom: 1px solid var(--border-light);
}
.job-results article strong {
  font-size: 14px;
}
.job-results p {
  margin: 7px 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.7;
}
.job-results small {
  color: #92400e;
  line-height: 1.6;
}
.guide-generator {
  display: grid;
  grid-template-columns: minmax(250px, .8fr) minmax(360px, 1.15fr);
  gap: 16px 24px;
  align-items: center;
  margin-bottom: 18px;
  padding: 16px;
  border: 1px solid var(--border-orange);
  border-radius: 8px;
  background: var(--brand-light);
}
.guide-generator-copy {
  display: flex;
  align-items: center;
  gap: 12px;
}
.generator-icon {
  display: grid;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 8px;
  background: #fff;
  color: var(--brand-strong);
}
.generator-icon :deep(svg) {
  width: 18px;
}
.guide-generator-copy strong,
.guide-generator-copy p {
  display: block;
}
.guide-generator-copy strong {
  font-size: 14px;
}
.guide-generator-copy p {
  margin: 5px 0 0;
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.55;
}
.guide-generator-controls {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}
.guide-generator-controls :deep(.el-button) {
  min-height: 40px;
}
.guide-target-preview {
  display: grid;
  grid-column: 2;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 4px 9px;
  padding-top: 12px;
  border-top: 1px solid rgba(243, 112, 33, .18);
}
.guide-target-preview > span {
  grid-row: 1 / 3;
  align-self: start;
  padding: 5px 7px;
  border: 1px solid var(--border-orange);
  border-radius: 6px;
  background: #fff;
  color: var(--brand-strong);
  font-size: 10px;
  font-weight: 800;
}
.guide-target-preview strong {
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.guide-target-preview small {
  color: var(--text-muted);
  font-size: 10px;
}
.guide-first-empty {
  display: flex;
  min-height: 190px;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 26px;
  border: 1px dashed var(--border-orange);
  border-radius: 8px;
  background: #fff;
}
.guide-first-empty > :deep(svg) {
  width: 34px;
  color: var(--brand-strong);
}
.guide-first-empty h4 {
  margin: 0 0 6px;
  font-size: 15px;
}
.guide-first-empty p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.guide-workspace {
  display: grid;
  grid-template-columns: minmax(280px, .72fr) minmax(0, 1.35fr);
  min-height: 560px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  overflow: hidden;
}
.guide-list {
  border-right: 1px solid var(--border-light);
  background: var(--surface-soft);
}
.guide-list header {
  padding: 17px;
  border-bottom: 1px solid var(--border-light);
}
.guide-list header div {
  display: flex;
  align-items: baseline;
  gap: 7px;
}
.guide-list header strong {
  color: var(--brand-strong);
  font: 900 24px/1 var(--font-mono);
}
.guide-list header span {
  font-size: 12px;
  font-weight: 800;
}
.guide-list header small {
  display: block;
  margin-top: 5px;
  color: var(--text-muted);
}
.guide-list > button {
  grid-template-columns: 24px minmax(0, 1fr) 15px;
  padding: 12px 15px;
}
.guide-list > button.active {
  box-shadow: inset 3px 0 0 var(--brand);
  background: #fff;
}
.guide-list > button > :deep(svg) {
  width: 17px;
  color: var(--brand-strong);
}
.guide-list > button span strong,
.guide-list > button span small {
  display: block;
}
.guide-list > button span strong {
  font-size: 12px;
  line-height: 1.5;
}
.guide-list > button span small {
  margin-top: 4px;
}
.guide-reader {
  min-width: 0;
  padding: 24px 28px;
  background: #fff;
}
.feedback-state {
  display: grid;
  min-height: 300px;
  place-items: center;
  align-content: center;
  padding: 30px;
  text-align: center;
}
.feedback-state.compact {
  min-height: 250px;
}
.feedback-state > :deep(svg) {
  width: 34px;
  height: 34px;
  padding: 8px;
  border-radius: 8px;
  color: var(--brand-strong);
  background: var(--brand-light);
}
.feedback-state h4 {
  margin: 13px 0 5px;
  font-size: 16px;
}
.feedback-state p {
  max-width: 430px;
  margin: 0 0 15px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.65;
}
.error-state > :deep(svg) {
  color: #b91c1c;
  background: #fef2f2;
}
.inline-error {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  margin: 18px 0;
  padding: 13px;
  border: 1px solid #fecaca;
  border-radius: 7px;
  background: #fef2f2;
  color: #991b1b;
  font-size: 12px;
}
.inline-error :deep(svg) {
  width: 17px;
}
.inline-error button {
  border: 0;
  background: transparent;
  color: #991b1b;
  font-weight: 800;
  cursor: pointer;
}
.detail-heading {
  align-items: flex-start;
}
.detail-heading h2 {
  margin: 6px 0 0;
  font-size: 22px;
  line-height: 1.4;
  letter-spacing: 0;
}
.detail-heading small {
  color: var(--text-muted);
}
.detail-heading a,
.contribution-link a {
  flex: 0 0 auto;
  color: #2563eb;
  font-weight: 700;
}
.detail-summary {
  margin: 18px 0;
  padding: 14px;
  border-left: 3px solid var(--brand);
  background: var(--brand-light);
  line-height: 1.7;
}
.facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  border: 1px solid var(--border-light);
  background: var(--border-light);
}
.facts div {
  padding: 12px;
  background: #fff;
}
.facts dt {
  color: var(--text-muted);
  font-size: 10px;
}
.facts dd {
  margin: 5px 0 0;
  font-size: 12px;
}
.original-content,
.environment-note {
  margin-top: 20px;
}
.original-content summary {
  min-height: 42px;
  padding: 11px 0;
  color: var(--text-secondary);
  font-weight: 800;
  cursor: pointer;
}
.original-content pre {
  max-height: 320px;
  overflow: auto;
  padding: 14px;
  background: #f6f7f9;
  white-space: pre-wrap;
  word-break: break-word;
  font: 12px/1.7 var(--font-mono);
}
.environment-note h3,
.drawer-results h3 {
  font-size: 14px;
}
.environment-note p {
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.detail-actions {
  position: sticky;
  z-index: 2;
  bottom: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 22px;
  padding: 12px 0;
  border-top: 1px solid var(--border-light);
  background: #fff;
}
.detail-actions :deep(.el-button) {
  min-height: 42px;
  margin: 0;
}
.drawer-results {
  margin-top: 20px;
}
.markdown-body {
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.8;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  letter-spacing: 0;
}
.markdown-body :deep(pre) {
  overflow-x: auto;
  padding: 14px;
  background: #25272a;
  color: #f5f7fa;
}
.markdown-body :deep(table) {
  display: block;
  width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
}
.markdown-body :deep(td),
.markdown-body :deep(th) {
  padding: 8px;
  border: 1px solid #ddd;
}
:deep(.el-button) {
  border-radius: 8px;
  letter-spacing: 0;
}
:deep(.el-input__wrapper),
:deep(.el-select__wrapper),
:deep(.el-textarea__inner) {
  border-radius: 7px;
}
@media (max-width: 1040px) {
  .filter-panel {
    grid-template-columns: 1fr 1fr;
  }
  .search-field {
    grid-column: 1 / -1;
  }
  .filter-actions {
    justify-content: flex-end;
  }
  .match-workspace,
  .job-workspace {
    grid-template-columns: 1fr;
  }
  .guide-generator {
    grid-template-columns: 1fr;
  }
  .guide-target-preview {
    grid-column: 1;
  }
  .match-form,
  .job-targets {
    padding-right: 0;
    padding-bottom: 24px;
    border-right: 0;
    border-bottom: 1px solid var(--border-light);
  }
}
@media (max-width: 760px) {
  .opensource-page {
    width: min(100% - 24px, 1280px);
    padding-top: 12px;
  }
  .header-main,
  .stage-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .pricing-summary {
    width: 100%;
    min-width: 0;
  }
  .stage-nav {
    position: static;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .stage-nav button:nth-child(2) {
    border-right: 0;
  }
  .stage-nav button:nth-child(-n+2) {
    border-bottom: 1px solid var(--border-light);
  }
  .stage-nav button.active {
    box-shadow: inset 3px 0 0 var(--brand);
  }
  .filter-panel,
  .advanced-filters,
  .issue-grid,
  .match-fields,
  .guide-workspace {
    grid-template-columns: 1fr;
  }
  .search-field {
    grid-column: auto;
  }
  .filter-actions {
    justify-content: stretch;
  }
  .filter-actions :deep(.el-button) {
    flex: 1;
  }
  .advanced-filters {
    grid-column: auto;
  }
  .guide-generator-controls {
    grid-template-columns: 1fr;
  }
  .guide-list {
    max-height: 330px;
    overflow: auto;
    border-right: 0;
    border-bottom: 1px solid var(--border-light);
  }
  .guide-reader {
    padding: 20px 16px;
  }
  .match-result {
    grid-template-columns: 28px minmax(0, 1fr);
  }
  .match-result > button {
    grid-column: 2;
    justify-self: start;
  }
  .facts {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 480px) {
  .pricing-summary {
    grid-template-columns: 1fr;
  }
  .pricing-summary div {
    display: flex;
    justify-content: space-between;
    border-right: 0;
    border-bottom: 1px solid var(--border-light);
  }
  .pricing-summary div:last-child {
    border-bottom: 0;
  }
  .pricing-summary dd {
    margin: 0;
  }
  .stage-nav button {
    min-height: 72px;
    grid-template-columns: 20px minmax(0, 1fr);
  }
  .stage-nav .stage-icon {
    display: none;
  }
  .detail-actions :deep(.el-button) {
    flex: 1 1 calc(50% - 7px);
  }
}
@media (prefers-reduced-motion: reduce) {
  .stage-nav button {
    transition: none;
  }
}
</style>
