(() => {
  const platform = window.PlatformArchitectureData
  const { learningChains, architectureExercises } = window.ArchitectureLearningContent
  const career = window.AgentPlatformCareerTrack
  const workbench = window.AgentLearningWorkbench
  const sourceGuide = window.OrangeVCStudySources
  const STORAGE_KEY = 'orangevc-architecture-study-atlas-v2'
  const byId = id => document.getElementById(id)
  const PAGE_LABELS = { 'study-home':'学习总览','learning-workbench':'研修中心','career-track':'岗位训练','study-sources':'学习路径','question-bank':'题库','atlas':'架构图谱','chains':'调用链','code-map':'代码地图','reliability':'故障推演','practice':'练习中心','interview':'面试演练' }
  const state = {
    graphId: platform.graphs[0].id,
    nodeId: platform.graphs[0].nodes[0].id,
    chainId: learningChains[0].id,
    fileIndex: 0,
    chainStep: 0,
    completedChains: [],
    scenarioIndex: 0,
    exerciseId: architectureExercises[0].id,
    selections: {},
    results: {},
    nodeMastery: {},
    nodeNotes: {},
    openAnswers: {},
    interviewDrafts: {},
    interviewIndex: 0,
    graphHistory: [],
    graphKind: 'all',
    graphFocus: false,
    graphZoom: 1,
    graphListMode: false,
    graphViewMode: 'free',
    searchQuery: '',
    careerDiveId: career.deepDives[0].id,
    careerQuestionIndex: 0,
    careerCompleted: {},
    careerAnswers: {},
    workbenchLabId: 'runtime',
    dailyCompleted: {},
    labChecks: {},
    experimentRecords: {},
    claimEvidence: {},
    runtimeSim: {status:'PENDING', crashed:false, events:['任务已入队，状态为 PENDING']},
    mcpResult: '',
    contextResult: null,
    interviewStage: 0,
    ladderAnswers: {},
    ladderScores: {},
    sourceCategory: 'all',
    sourcePriority: 'all',
    sourceSearch: '',
    sourceProgress: {},
    activePage: 'study-home',
  }

  try { Object.assign(state, JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')) } catch { /* 忽略损坏的本地状态 */ }
  const save = () => localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  function showPage(pageId, mode = 'push') {
    const page = PAGE_LABELS[pageId] ? pageId : 'study-home'
    state.activePage = page
    document.querySelectorAll('[data-page]').forEach(panel => { panel.hidden = panel.dataset.page !== page })
    document.querySelectorAll('[data-page-target]').forEach(button => {
      const active = button.dataset.pageTarget === page
      button.classList.toggle('active', active)
      if (active) button.setAttribute('aria-current', 'page'); else button.removeAttribute('aria-current')
    })
    byId('page-context').textContent = PAGE_LABELS[page]
    document.title = `${PAGE_LABELS[page]} · OrangeVC 项目深挖研修台`
    if (mode === 'push' && location.hash !== `#${page}`) history.pushState({page},'',`#${page}`)
    if (mode === 'replace') history.replaceState({page},'',`#${page}`)
    save()
    window.scrollTo({top:0,behavior:mode === 'silent' ? 'auto' : 'smooth'})
  }
  const graph = () => platform.graphs.find(item => item.id === state.graphId) || platform.graphs[0]
  const node = () => graph().nodes.find(item => item.id === state.nodeId) || graph().nodes[0]
  const chain = () => learningChains.find(item => item.id === state.chainId) || learningChains[0]
  const exercise = () => architectureExercises.find(item => item.id === state.exerciseId) || architectureExercises[0]
  const chainTitle = id => learningChains.find(item => item.id === id)?.title || id
  const fileName = path => path.split('/').pop()
  const escapeHtml = value => String(value).replace(/[&<>"]/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[char]))
  const kindNames = {client:'终端',edge:'边缘层',frontend:'前端应用',backend:'后端运行单元',store:'存储',external:'外部系统',domain:'业务域',foundation:'平台基础设施',service:'业务服务',data:'数据访问',module:'Maven 模块',ops:'交付运维',observe:'可观测与恢复',risk:'架构边界'}
  const masteryLabels = ['待学习', '看过', '理解', '串联', '掌握']
  const allNodes = () => platform.graphs.flatMap(item => item.nodes.map(node => ({...node, graphId:item.id, graphTitle:item.title})))
  const nodeKey = (graphId = state.graphId, nodeId = state.nodeId) => `${graphId}:${nodeId}`
  const laneLayouts = new Map()
  const laneViews = new Map()
  const relatedChainsCache = new Map()
  let nodeDetailFrame = 0
  let nodeDetailSecondFrame = 0
  let nodeDependentTimer = 0

  const graphLayerSpecs = {
    runtime: [
      ['前端与终端', ['browser','admin-browser','device','web','admin']],
      ['统一接入', ['nginx']],
      ['后端运行时', ['server','ws']],
      ['数据与对象', ['mysql','redis','oss']],
      ['外部依赖', ['ai-provider','github','identity-vendors','payment']],
    ],
    domains: [
      ['前端业务触点', ['identity','content','social','persona','agent']],
      ['交易与资产', ['trade','quota']],
      ['智能与知识', ['ai','knowledge','discovery']],
      ['平台支撑', ['platform']],
    ],
    code: [
      ['前端表现层', ['view','composable','store-api']],
      ['接入与身份', ['nginx-code','interceptors','controller']],
      ['应用与规则', ['app-service','domain-components']],
      ['基础设施与契约', ['foundation','redis-adapter','external-client','common','pojo']],
      ['数据事实', ['mapper','flyway']],
    ],
    reliability: [
      ['制品与前端交付', ['ci','release','install']],
      ['运行与观测', ['systemd','health']],
      ['数据事实', ['flyway-db','quota-ledger','vector-data']],
      ['异步运行时', ['system-task','ingestion','ai-lease']],
      ['恢复与单点', ['backup','single-host']],
    ],
  }

  const fallbackGuides = {
    frontend: ['把页面状态、流程编排和 HTTP 协议分层，避免 View 同时处理所有职责。','用户事件从 View 进入 Composable，再通过 Store 或 API 发出请求，响应以可观察状态回到页面。',['页面只负责展示和事件','跨页面状态必须有清晰生命周期'],['浏览器 Network 与 Console','对应 Store 或 Composable 的 loading、error、requestId'],['这个前端边界怎样测试？','旧请求覆盖新状态时怎样处理？']],
    domain: ['按业务能力集中规则、状态和数据所有权，避免同一规则散落在多个入口。','可信身份进入业务服务后，服务校验当前状态，执行规则，写入拥有的数据表，并发布需要的通知或任务。',['状态转换只能从合法前置状态发生','所有权和可见性在服务端保证'],['请求 ID 对应业务日志','核心表状态、审计记录和关联任务'],['这个域的聚合边界是什么？','跨域事务失败时怎样补偿？']],
    external: ['通过适配器隔离第三方协议和失败，让业务层依赖稳定能力而不是某个供应商。','业务服务调用外部客户端，客户端负责鉴权、超时和错误分类，业务再决定重试、降级或补偿。',['外部响应不能直接成为可信业务事实','重试必须判断副作用和幂等性'],['供应商状态码与延迟','本地调用日志、requestId 和补偿状态'],['哪些错误可以重试？','怎样替换供应商而不改业务流程？']],
    foundation: ['把多个业务都会使用的运行语义集中实现，但保持它不反向依赖具体业务。','业务通过窄接口提交任务、获取租约或调用外部能力，foundation 负责可靠执行和可观测状态。',['基础设施不拥有具体业务规则','状态恢复后业务 Handler 仍必须幂等'],['租约、任务或调用记录','线程池、Redis 和数据库指标'],['为什么这属于 foundation？','如何避免公共基础设施变成耦合中心？']],
  }

  function interviewGuide(selected) {
    const exact = platform.interviewGuides[selected.id]
    if (exact) return exact
    const fallback = fallbackGuides[selected.kind] || fallbackGuides.domain
    return {
      pitch: `${selected.label}在 OrangeVC 中负责${selected.summary.replace(/[。.]$/,'')}。面试时应同时说明它的边界、依赖和失败语义。`,
      decision: fallback[0], flow: fallback[1], invariants: fallback[2], diagnostics: fallback[3], questions: fallback[4],
    }
  }

  function relatedChainsForNode(selected) {
    const cacheKey = nodeKey(state.graphId, selected.id)
    if (relatedChainsCache.has(cacheKey)) return relatedChainsCache.get(cacheKey)
    const nodeFiles = selected.files || []
    const matches = learningChains.map(item => {
      let score = 0
      item.files.forEach(file => nodeFiles.forEach(path => {
        if (file.path === path) score += 8
        else if (file.path.startsWith(path) || path.startsWith(file.path)) score += 4
        else if (fileName(file.path) === fileName(path)) score += 5
      }))
      const haystack = `${item.title} ${item.subtitle} ${item.goal} ${item.steps.map(step => `${step.name} ${step.detail}`).join(' ')}`.toLowerCase()
      const tokens = [selected.id, selected.label].flatMap(value => String(value).toLowerCase().split(/[\s/_+-]+/)).filter(value => value.length > 2)
      tokens.forEach(token => { if (haystack.includes(token)) score += 1 })
      if (selected.id === 'server') score += 1
      if (selected.id === 'controller' && item.files.some(file => file.path.includes('/controller/'))) score += 3
      if (selected.id === 'app-service' && item.files.some(file => file.path.includes('/service/'))) score += 3
      if (selected.id === 'mapper' && item.files.some(file => file.path.includes('/mapper/'))) score += 3
      if (selected.id === 'flyway' && item.files.some(file => file.path.includes('/db/migration/'))) score += 3
      return {item, score}
    }).filter(result => result.score > 0).sort((a,b) => b.score - a.score).slice(0, 4).map(result => result.item)
    relatedChainsCache.set(cacheKey, matches)
    return matches
  }

  function findChainForPath(path) {
    for (const item of learningChains) {
      const index = item.files.findIndex(file => file.path === path || file.path.startsWith(path) || path.startsWith(file.path))
      if (index >= 0) return {chain:item, fileIndex:index}
    }
    return null
  }

  function stepFileIndex(current, stepIndex) {
    const step = current.steps[stepIndex]
    const words = String(step?.name || '').toLowerCase().split(/[^a-z0-9]+/).filter(word => word.length > 3)
    const exact = current.files.findIndex(file => words.some(word => file.path.toLowerCase().includes(word)))
    if (exact >= 0) return exact
    if (current.steps.length <= 1) return 0
    return Math.min(current.files.length - 1, Math.round(stepIndex * (current.files.length - 1) / (current.steps.length - 1)))
  }

  function openNodeLocation(graphId, targetNodeId, shouldScroll = true) {
    showPage('atlas')
    if (state.graphId !== graphId || state.nodeId !== targetNodeId) {
      state.graphHistory.push({graphId:state.graphId, nodeId:state.nodeId})
      state.graphHistory = state.graphHistory.slice(-20)
    }
    state.graphId = graphId
    state.nodeId = targetNodeId
    state.graphFocus = false
    renderGraph()
    renderStudyDashboard()
    save()
    if (shouldScroll) window.scrollTo({top:0,behavior:'smooth'})
  }

  function renderStudyDashboard() {
    const nodes = allNodes()
    const counts = [0,0,0,0,0]
    nodes.forEach(item => counts[Number(state.nodeMastery[nodeKey(item.graphId, item.id)] || 0)]++)
    byId('mastery-summary').innerHTML = masteryLabels.map((label,index) => `<button data-mastery-filter="${index}"><strong>${counts[index]}</strong><span>${label}</span></button>`).join('')
    const current = node()
    byId('resume-title').textContent = current.label
    byId('resume-description').textContent = `当前位于“${graph().title}”。掌握度：${masteryLabels[Number(state.nodeMastery[nodeKey()] || 0)]}。下一步建议先说清它的边界，再沿相关链路进入代码。`
  }

  function searchEntries(query) {
    const normalized = query.trim().toLowerCase()
    if (!normalized) return []
    const entries = []
    allNodes().forEach(item => {
      const text = [item.label,item.summary,item.kind,...(item.facts||[]),...(item.tables||[]),...(item.files||[])].join(' ').toLowerCase()
      if (text.includes(normalized)) entries.push({type:'node',id:item.id,graphId:item.graphId,title:item.label,meta:`${item.graphTitle} · ${kindNames[item.kind] || item.kind}`,detail:item.summary})
    })
    learningChains.forEach(item => {
      const text = [item.title,item.subtitle,item.goal,item.route,...item.files.map(file => `${file.path} ${file.role}`)].join(' ').toLowerCase()
      if (text.includes(normalized)) entries.push({type:'chain',id:item.id,title:item.title,meta:'代表性调用链',detail:item.goal})
    })
    platform.failureScenarios.forEach((item,index) => {
      const text = [item.title,...item.affects,...item.survives,item.first,item.improve].join(' ').toLowerCase()
      if (text.includes(normalized)) entries.push({type:'scenario',id:String(index),title:item.title,meta:'故障推演',detail:item.first})
    })
    platform.codeCatalog.forEach(group => group.items.forEach(([title,path,role]) => {
      if (`${title} ${path} ${role}`.toLowerCase().includes(normalized)) entries.push({type:'source',id:path,title,meta:group.group,detail:path})
    }))
    career.capabilities.forEach(item => {
      const text = [item.title,item.jd,item.evidence,item.canSay,item.boundary,...item.files].join(' ').toLowerCase()
      if (text.includes(normalized)) entries.push({type:'career',id:item.id,title:item.title,meta:`岗位对齐 · ${item.statusLabel}`,detail:item.evidence})
    })
    career.deepDives.forEach(item => {
      const text = [item.title,item.subtitle,item.thesis,...item.learn,item.question,item.task].join(' ').toLowerCase()
      if (text.includes(normalized)) entries.push({type:'career-dive',id:item.id,title:item.title,meta:'岗位深挖专题',detail:item.thesis})
    })
    workbench.labs.forEach(item => {
      const text = [item.title,item.summary,...item.knowledge,...item.files,...item.acceptance].join(' ').toLowerCase()
      if (text.includes(normalized)) entries.push({type:'lab',id:item.id,title:item.title,meta:`研修实验室 · ${item.level}`,detail:item.summary})
    })
    sourceGuide.topics.forEach(item => {
      const text = [item.title,item.summary,item.orange,...item.learn,...item.code,...item.sources.flatMap(source => [source.name,source.location])].join(' ').toLowerCase()
      if (text.includes(normalized)) entries.push({type:'study-topic',id:item.id,title:item.title,meta:`学习路径 · ${item.priority}`,detail:item.summary})
    })
    return entries.slice(0, 12)
  }

  function renderSearchResults() {
    const box = byId('search-results')
    const query = byId('global-search').value.trim()
    state.searchQuery = query
    if (!query) { box.innerHTML = '<p>输入关键词后，从完整研修内容中定位。</p>'; save(); return }
    const entries = searchEntries(query)
    box.innerHTML = entries.length ? entries.map(item => `<button data-search-type="${item.type}" data-search-id="${escapeHtml(item.id)}" ${item.graphId ? `data-search-graph="${item.graphId}"` : ''}><span>${item.meta}</span><strong>${item.title}</strong><small>${item.detail}</small></button>`).join('') : '<p>没有找到匹配内容。可以尝试类名、表名或更短的关键词。</p>'
    save()
  }

  const sourceProgressLabels = ['待学习','已浏览','已理解','能关联项目','能面试讲清']
  function renderSourceGuide() {
    const category = byId('source-category')
    if (!category.options.length) category.innerHTML = sourceGuide.categories.map(([id,label]) => `<option value="${id}">${label}</option>`).join('')
    category.value = state.sourceCategory || 'all'
    byId('source-priority').value = state.sourcePriority || 'all'
    byId('source-search').value = state.sourceSearch || ''
    const query = (state.sourceSearch || '').trim().toLowerCase()
    const topics = sourceGuide.topics.filter(item => {
      if (state.sourceCategory !== 'all' && item.category !== state.sourceCategory) return false
      if (state.sourcePriority !== 'all' && item.priority !== state.sourcePriority) return false
      const haystack = [item.title,item.summary,item.orange,...item.learn,...item.code,...item.sources.flatMap(source => [source.name,source.location])].join(' ').toLowerCase()
      return !query || haystack.includes(query)
    })
    const mastered = sourceGuide.topics.filter(item => Number(state.sourceProgress[item.id] || 0) >= 4).length
    const connected = sourceGuide.topics.filter(item => Number(state.sourceProgress[item.id] || 0) >= 3).length
    const sources = sourceGuide.topics.reduce((sum,item) => sum + item.sources.length, 0)
    byId('source-summary').innerHTML = `<article><strong>${topics.length}</strong><span>当前显示</span></article><article><strong>${sourceGuide.topics.length}</strong><span>全部知识点</span></article><article><strong>${sources}</strong><span>网页学习入口</span></article><article><strong>${connected}</strong><span>已关联项目</span></article><article><strong>${mastered}</strong><span>可面试讲清</span></article>`
    byId('source-topic-grid').innerHTML = topics.length ? topics.map(item => {
      const level = Number(state.sourceProgress[item.id] || 0)
      return `<details class="source-topic priority-${item.priority.toLowerCase()}" id="study-topic-${item.id}">
        <summary><span class="source-priority">${item.priority}</span><div><small>${sourceGuide.categories.find(([id]) => id === item.category)?.[1] || item.category}</small><h3>${escapeHtml(item.title)}</h3><p>${escapeHtml(item.summary)}</p></div><em>${sourceProgressLabels[level]}</em></summary>
        <div class="source-topic-body">
          <section class="source-project-link"><h4>在 OrangeVC 里对应什么</h4><p>${escapeHtml(item.orange)}</p>${item.graph.length === 2 ? `<button data-career-node="${item.graph[0]}:${item.graph[1]}">打开对应架构节点</button>` : ''}</section>
          <section><h4>按这个顺序学</h4><ol>${item.learn.map(point => `<li>${escapeHtml(point)}</li>`).join('')}</ol></section>
          <section><h4>网页学习路径</h4><div class="source-links">${item.sources.map(source => `<article><div><span>${escapeHtml(source.method)}</span><strong>${escapeHtml(source.name)}</strong><p>${escapeHtml(source.location)}</p></div><div><a href="${escapeHtml(source.url)}" target="_blank" rel="noreferrer">打开对应页面 ↗</a><button data-copy-location="${escapeHtml(source.location)}">复制章节名</button></div></article>`).join('')}</div></section>
          <section><h4>项目代码证据</h4><div class="source-code-paths">${item.code.length ? item.code.map(path => `<button data-open-source="${escapeHtml(path)}">${escapeHtml(path)}</button>`).join('') : '<span>这是理论扩展项，当前没有对应生产实现。</span>'}</div></section>
          <footer><label>掌握度<select data-source-progress="${item.id}">${sourceProgressLabels.map((label,index) => `<option value="${index}" ${index === level ? 'selected' : ''}>${label}</option>`).join('')}</select></label><small>资料快照：${sourceGuide.snapshotDate}</small></footer>
        </div>
      </details>`
    }).join('') : '<div class="source-empty"><strong>没有匹配的知识点</strong><p>缩短搜索词或清除主题与优先级筛选。</p></div>'
  }
  function openStudyTopic(id) {
    state.sourceCategory = 'all'; state.sourcePriority = 'all'; state.sourceSearch = ''
    renderSourceGuide(); showPage('study-sources'); save()
    requestAnimationFrame(() => { const target = byId(`study-topic-${id}`); if (target) { target.open = true; target.scrollIntoView({behavior:'smooth',block:'start'}) } })
  }
  function statusClass(status) {
    return status === 'strong' ? 'is-strong' : status === 'partial' ? 'is-partial' : 'is-gap'
  }

  function renderCareerCapabilities() {
    byId('career-capabilities').innerHTML = career.capabilities.map(item => `
      <details class="career-capability ${statusClass(item.status)}">
        <summary><span>${item.statusLabel}</span><strong>${item.title}</strong><small>${item.jd}</small></summary>
        <div class="capability-body">
          <section><h4>项目真实证据</h4><p>${escapeHtml(item.evidence)}</p></section>
          <section class="can-say"><h4>面试时可以这样讲</h4><p>${escapeHtml(item.canSay)}</p></section>
          <section class="honest-boundary"><h4>必须主动说明的边界</h4><p>${escapeHtml(item.boundary)}</p></section>
          <section><h4>代码证据</h4><div class="career-files">${item.files.map(path => `<button data-open-source="${escapeHtml(path)}">${escapeHtml(path)}</button>`).join('')}</div></section>
          <div class="career-actions"><button data-career-node="${item.graph.graphId}:${item.graph.nodeId}">打开知识节点</button><button data-open-chain="${item.chainId}">沿代表链路学习</button></div>
        </div>
      </details>`).join('')
  }

  function renderCareerPackaging() {
    const f = career.fit, p = career.positioning
    byId('career-fit').innerHTML = `<article class="fit-verdict"><span>${escapeHtml(f.verdict)}</span><strong>${escapeHtml(f.score)}</strong><small>${escapeHtml(f.snapshot)}</small></article><article><h4>判断依据</h4><p>${escapeHtml(f.reason)}</p></article><article><h4>最高风险</h4><p>${escapeHtml(f.risk)}</p></article><article><h4>最快补强</h4><p>${escapeHtml(f.fastestUpgrade)}</p></article>`
    byId('career-positioning').innerHTML = `<article><span>推荐项目名</span><h4>${escapeHtml(p.title)}</h4><p>${escapeHtml(p.oneLine)}</p></article><details open><summary>30秒项目介绍</summary><p>${escapeHtml(p.pitch30)}</p></details><details><summary>90秒项目介绍</summary><p>${escapeHtml(p.pitch90)}</p></details><article class="not-say"><span>不要这样写</span><div>${p.notSay.map(x => `<em>${escapeHtml(x)}</em>`).join('')}</div></article>`
    byId('career-resume-bullets').innerHTML = career.resumeBullets.map(x => `<article><header><span>${x.level}</span><strong>${x.status}</strong></header><p>${escapeHtml(x.text)}</p><small>证据：${escapeHtml(x.proof)}</small><small>边界：${escapeHtml(x.boundary)}</small></article>`).join('')
    byId('career-evidence-contracts').innerHTML = career.evidenceContracts.map(x => `<article><header><span>${x.level}</span><strong>${x.status}</strong><h4>${escapeHtml(x.claim)}</h4></header><p><b>仓库证据</b>${escapeHtml(x.evidence)}</p><p><b>面试验真</b>${escapeHtml(x.interview)}</p></article>`).join('')
    byId('career-answer-cards').innerHTML = career.answerCards.map((x,i) => `<details><summary>${String(i+1).padStart(2,'0')} · ${escapeHtml(x.question)}</summary><section class="answer-danger"><b>危险回答</b><p>${escapeHtml(x.dangerous)}</p></section><section class="answer-strong"><b>强回答</b><p>${escapeHtml(x.strong)}</p></section><small>代码证据：${escapeHtml(x.evidence)}</small></details>`).join('')
    byId('career-upgrade-plan').innerHTML = career.upgradePlan.map(x => `<article><header><span>${x.time}</span><h4>${escapeHtml(x.goal)}</h4></header><ol>${x.actions.map(a => `<li>${escapeHtml(a)}</li>`).join('')}</ol><p><b>交付物</b>${escapeHtml(x.artifact)}</p><p class="unlock"><b>完成后解锁</b>${escapeHtml(x.unlock)}</p></article>`).join('')
  }

  function renderDeepDive() {
    const current = career.deepDives.find(item => item.id === state.careerDiveId) || career.deepDives[0]
    byId('deep-dive-list').innerHTML = career.deepDives.map((item,index) => `<button class="${item.id === current.id ? 'active' : ''}" data-career-dive="${item.id}"><span>${String(index + 1).padStart(2,'0')}</span><strong>${item.title}</strong><small>${state.careerCompleted[`dive:${item.id}`] ? '已完成复盘' : item.subtitle}</small></button>`).join('')
    byId('deep-dive-detail').innerHTML = `
      <span>${current.subtitle}</span><h4>${current.title}</h4><p class="career-thesis">${escapeHtml(current.thesis)}</p>
      <section><h5>必须能讲清</h5><ol>${current.learn.map(item => `<li>${escapeHtml(item)}</li>`).join('')}</ol></section>
      <section class="career-challenge"><h5>面试追问</h5><p>${escapeHtml(current.question)}</p></section>
      <section><h5>动手任务</h5><p>${escapeHtml(current.task)}</p></section>
      <div class="career-actions"><button data-career-node="${current.graph.graphId}:${current.graph.nodeId}">打开知识节点</button><button data-open-chain="${current.chainId}">阅读相关链路</button><button class="${state.careerCompleted[`dive:${current.id}`] ? 'completed' : ''}" data-complete-career="dive:${current.id}">${state.careerCompleted[`dive:${current.id}`] ? '已完成复盘' : '标记完成复盘'}</button></div>`
  }

  function renderCareerStories() {
    byId('career-stories').innerHTML = career.stories.map((item,index) => `<article>
      <header><span>${String(index + 1).padStart(2,'0')}</span><h4>${item.title}</h4></header>
      <dl><dt>问题</dt><dd>${escapeHtml(item.situation)}</dd><dt>选择</dt><dd>${escapeHtml(item.decision)}</dd><dt>机制</dt><dd>${escapeHtml(item.mechanism)}</dd><dt>权衡</dt><dd>${escapeHtml(item.tradeoff)}</dd><dt>证据</dt><dd><code>${escapeHtml(item.proof)}</code></dd></dl>
    </article>`).join('')
  }

  function renderCareerGaps() {
    byId('career-gaps').innerHTML = career.gaps.map(([title,reality,next]) => `<article><h4>${title}</h4><section><span>当前事实</span><p>${escapeHtml(reality)}</p></section><section><span>合理补课</span><p>${escapeHtml(next)}</p></section></article>`).join('')
  }

  function renderCareerQuestion() {
    state.careerQuestionIndex = Math.min(Number(state.careerQuestionIndex || 0), career.questions.length - 1)
    const [question,hint] = career.questions[state.careerQuestionIndex]
    byId('career-question-list').innerHTML = career.questions.map(([item],index) => `<button class="${index === state.careerQuestionIndex ? 'active' : ''}" data-career-question="${index}"><span>${String(index + 1).padStart(2,'0')}</span><strong>${item}</strong></button>`).join('')
    byId('career-question-index').textContent = `问题 ${state.careerQuestionIndex + 1} / ${career.questions.length}`
    byId('career-question').textContent = question
    byId('career-question-draft').value = state.careerAnswers[`question:${state.careerQuestionIndex}`] || ''
    byId('career-question-hint').innerHTML = `<h5>回答提示</h5><p>${escapeHtml(hint)}</p>`
    byId('career-question-hint').hidden = true
  }

  function renderCareerLabs() {
    byId('career-labs').innerHTML = career.labs.map(item => `<article class="${state.careerCompleted[`lab:${item.id}`] ? 'completed' : ''}">
      <header><span>${item.level}</span><h4>${item.title}</h4></header><p>${escapeHtml(item.task)}</p><section><strong>应留下的证据</strong><p>${escapeHtml(item.deliverable)}</p></section>
      <button data-complete-career="lab:${item.id}">${state.careerCompleted[`lab:${item.id}`] ? '已完成，有证据' : '完成后标记'}</button>
    </article>`).join('')
  }

  function renderCareerTrack() {
    renderCareerPackaging()
    renderCareerCapabilities()
    renderDeepDive()
    renderCareerStories()
    renderCareerGaps()
    renderCareerQuestion()
    renderCareerLabs()
  }

  function labById(id = state.workbenchLabId) {
    return workbench.labs.find(item => item.id === id) || workbench.labs[0]
  }

  function evidenceCount() {
    return Object.values(state.claimEvidence).reduce((sum,items) => sum + Object.values(items || {}).filter(Boolean).length, 0)
  }

  function claimUnlocked(claim) {
    return claim.requires.every(item => state.claimEvidence[claim.id]?.[item])
  }

  function renderWorkbenchMetrics() {
    const done = Object.values(state.dailyCompleted).filter(Boolean).length
    const labsDone = workbench.labs.filter(lab => lab.acceptance.every(item => state.labChecks[lab.id]?.[item])).length
    const claims = workbench.claims.filter(claimUnlocked).length
    byId('workbench-metrics').innerHTML = [
      [done,workbench.dailyTasks.length,'已完成学习任务'],[labsDone,workbench.labs.length,'已通过专题实验'],[evidenceCount(),workbench.claims.reduce((n,c)=>n+c.requires.length,0),'已登记证据'],[claims,workbench.claims.length,'已解锁能力声明'],
    ].map(([value,total,label]) => `<article><strong>${value}<small> / ${total}</small></strong><span>${label}</span></article>`).join('')
  }

  function renderDailyStudy() {
    const pending = workbench.dailyTasks.filter(item => !state.dailyCompleted[item.id])
    const today = (pending.length ? pending : workbench.dailyTasks).slice(0,3)
    byId('daily-study').innerHTML = today.map((item,index) => `<article class="${state.dailyCompleted[item.id] ? 'completed' : ''}"><header><span>${String(index+1).padStart(2,'0')}</span><small>${item.minutes}分钟</small></header><h4>${escapeHtml(item.title)}</h4><dl><dt>应留证据</dt><dd>${escapeHtml(item.proof)}</dd><dt>完成后</dt><dd>${escapeHtml(item.unlock)}</dd></dl><div><button data-open-lab="${item.lab}">进入实验室</button><button data-daily-complete="${item.id}">${state.dailyCompleted[item.id] ? '已完成' : '完成并记录'}</button></div></article>`).join('')
  }

  function runtimePanel() {
    const sim = state.runtimeSim
    return `<section class="runtime-simulator"><header><div><span>当前状态</span><strong>${sim.status}</strong></div><small>${sim.crashed ? 'Worker已退出，等待租约恢复' : 'Worker运行状态正常'}</small></header><div class="sim-actions"><button data-runtime-action="claim">原子claim</button><button data-runtime-action="heartbeat">发送心跳</button><button data-runtime-action="crash">模拟宕机</button><button data-runtime-action="recover">租约过期恢复</button><button data-runtime-action="reset">重置</button></div><ol>${sim.events.map(item => `<li>${escapeHtml(item)}</li>`).join('')}</ol><p>追问：业务操作已经成功，但写回SUCCEEDED之前宕机，重新执行时如何避免重复副作用？</p></section>`
  }

  function mcpPanel() {
    return `<section class="tool-simulator"><label>模拟工具输入<textarea id="mcp-payload" rows="6">{"action":"submit_competition","arguments":{"name":"AI Challenge","officialUrl":"https://example.com"},"scope":"competition:write"}</textarea></label><button data-mcp-validate>校验Schema与权限边界</button><div class="sim-result">${escapeHtml(state.mcpResult || '等待校验：必须包含action、arguments和scope；服务端仍需重新验证身份与业务权限。')}</div></section>`
  }

  function tracePanel() {
    return `<section class="trace-simulator"><div class="trace-row root"><b>HTTP POST /agent/action</b><span>server span</span></div><div class="trace-row depth-1"><b>permission.validate</b><span>internal</span></div><div class="trace-row depth-1"><b>system_task.enqueue</b><span>producer</span></div><div class="trace-row depth-1"><b>system_task.execute</b><span>consumer</span></div><div class="trace-row depth-2 error"><b>ai.provider.call · attempt 1</b><span>timeout</span></div><div class="trace-row depth-2 success"><b>ai.provider.call · attempt 2</b><span>success</span></div><aside>当前这是设计演示，不是真实OpenTelemetry轨迹。完成真实接入并保存Trace截图后，才可升级声明。</aside></section>`
  }

  function contextPanel() {
    const result = state.contextResult
    return `<section class="context-simulator"><div><label>最大预算（估算Token）<input id="context-budget" type="number" min="20" max="1000" value="80"></label><label>每行一条消息<textarea id="context-messages" rows="7">SYSTEM: 你是OrangeVC任务助手，必须保留权限规则。\nUSER: 帮我投稿一个AI比赛。\nTOOL: 返回了很长的比赛页面内容和报名规则。\nASSISTANT: 请确认名称、官网和截止日期。\nUSER: 名称是Agent Challenge，官网已经核对。</textarea></label></div><button data-context-run>运行“系统提示＋最近消息”策略</button>${result ? `<div class="context-result"><strong>估算 ${result.tokens} tokens · 裁剪 ${result.removed} 条</strong><pre>${escapeHtml(result.kept.join('\n'))}</pre><p>${escapeHtml(result.note)}</p></div>` : ''}</section>`
  }

  function k8sPanel() {
    return `<section class="design-lab"><h5>Pod终止恢复验收顺序</h5><ol><li>readiness失败后停止接收新流量</li><li>preStop与terminationGracePeriod允许Worker停止claim</li><li>运行中任务通过心跳或租约暴露执行权</li><li>Pod终止后等待租约过期</li><li>新Worker重新claim，验证副作用幂等</li></ol><pre>kubectl delete pod orange-server-xxx\nkubectl get pods -w\n# 同时观察 system_task 状态与 taskId 日志</pre><p>边界：这里记录本地实验设计，不等于生产Kubernetes经验。</p></section>`
  }

  function dagPanel() {
    const failed = state.dagFailed || ''
    const nodes = [['fetch','抓取'],['clean','清洗'],['review','AI审核'],['human','人工确认'],['publish','发布']]
    return `<section class="dag-simulator"><div>${nodes.map(([id,label],index) => `<button class="${failed === id ? 'failed' : failed && index > nodes.findIndex(x=>x[0]===failed) ? 'blocked' : ''}" data-dag-fail="${id}"><span>${index+1}</span><b>${label}</b></button>`).join('<i>→</i>')}</div><p>${failed ? `${nodes.find(x=>x[0]===failed)[1]}失败：下游节点不可就绪。需要为该节点定义重试上限、失败传播，以及是否允许人工跳过或补偿。` : '点击任一节点注入失败，观察下游就绪状态。当前OrangeVC是阶段式流程，这里是演进实验。'}</p><button data-dag-reset>清除故障</button></section>`
  }

  function labInteractive(lab) {
    return lab.id === 'runtime' ? runtimePanel() : lab.id === 'mcp' ? mcpPanel() : lab.id === 'tracing' ? tracePanel() : lab.id === 'context' ? contextPanel() : lab.id === 'k8s' ? k8sPanel() : dagPanel()
  }

  function renderLab() {
    const lab = labById()
    byId('lab-list').innerHTML = workbench.labs.map(item => `<button class="${item.id === lab.id ? 'active' : ''}" data-lab="${item.id}"><span>${item.level}</span><strong>${item.title}</strong><small>${item.status === 'implemented' ? '现有代码证据' : '研修实验，不代表已实现'}</small></button>`).join('')
    const record = state.experimentRecords[lab.id] || {}
    byId('lab-detail').innerHTML = `<header><span>${lab.level} · ${lab.status === 'implemented' ? '仓库已实现相关能力' : '计划性研修实验'}</span><h3>${lab.title}</h3><p>${escapeHtml(lab.summary)}</p></header><div class="lab-knowledge">${lab.knowledge.map(item => `<em>${item}</em>`).join('')}</div>${labInteractive(lab)}<div class="lab-columns"><section><h4>通过标准</h4>${lab.acceptance.map(item => `<label><input type="checkbox" data-lab-check="${escapeHtml(item)}" ${state.labChecks[lab.id]?.[item] ? 'checked' : ''}>${escapeHtml(item)}</label>`).join('')}</section><section><h4>代码证据</h4>${lab.files.map(path => `<button data-open-source="${escapeHtml(path)}">${escapeHtml(path)}</button>`).join('')}</section></div><section class="experiment-record"><h4>实验记录</h4><label>实际结果与复盘<textarea id="experiment-note" rows="5" placeholder="记录操作、预期、实际结果、失败原因和下一步">${escapeHtml(record.note || '')}</textarea></label><label>证据路径<input id="experiment-proof" value="${escapeHtml(record.proof || '')}" placeholder="例如 docs/labs/runtime-recovery.md 或截图路径"></label><button data-save-experiment>保存实验记录</button><small>${record.savedAt ? `上次保存：${record.savedAt}` : '只保存在当前浏览器'}</small></section>`
  }

  function renderClaimGates() {
    byId('claim-gates').innerHTML = workbench.claims.map(claim => { const unlocked = claimUnlocked(claim); return `<article class="${unlocked ? 'unlocked' : ''}"><header><span>${claim.level}</span><strong>${unlocked ? '可以写' : '暂不可写'}</strong></header><h4>${escapeHtml(claim.title)}</h4><div>${claim.requires.map(item => `<label><input type="checkbox" data-claim="${claim.id}" data-requirement="${escapeHtml(item)}" ${state.claimEvidence[claim.id]?.[item] ? 'checked' : ''}>${escapeHtml(item)}</label>`).join('')}</div><p><b>安全表述</b>${escapeHtml(claim.safe)}</p><button data-open-lab="${claim.lab}">进入对应实验</button></article>` }).join('')
  }

  function renderLadderInterview() {
    const index = Math.min(state.interviewStage, workbench.interview.length-1), item = workbench.interview[index]
    const scores = state.ladderScores[item.id] || {}
    const total = workbench.rubric.reduce((sum,[name,points]) => sum + (scores[name] ? points : 0),0)
    byId('ladder-interview').innerHTML = `<nav>${workbench.interview.map((q,i) => `<button class="${i===index?'active':''}" data-ladder-stage="${i}"><span>${i+1}</span><strong>${escapeHtml(q.question)}</strong></button>`).join('')}</nav><article><span>追问 ${index+1} / ${workbench.interview.length}</span><h4>${escapeHtml(item.question)}</h4><textarea id="ladder-answer" rows="7" placeholder="至少包含约束、机制、失败、权衡和代码证据">${escapeHtml(state.ladderAnswers[item.id] || '')}</textarea><div class="rubric-grid">${workbench.rubric.map(([name,points,desc]) => `<label><input type="checkbox" data-rubric="${name}" ${scores[name]?'checked':''}><strong>${name} +${points}</strong><small>${desc}</small></label>`).join('')}</div><div class="interview-score"><strong>${total}</strong><span>/ 100</span><p>${total < 60 ? '只有概念，没有足够的OrangeVC机制和证据。' : total < 80 ? '合格，但需要补失败场景或权衡。' : '可以进入下一层追问。'}</p></div><section><b>回答必须命中</b><p>${item.points.join(' · ')}</p></section><button data-save-ladder>保存回答</button></article>`
  }

  function renderLearningWorkbench() {
    renderWorkbenchMetrics(); renderDailyStudy(); renderLab(); renderClaimGates(); renderLadderInterview()
  }

  function renderSnapshot() {
    byId('snapshot-date').textContent = platform.snapshot.date
    byId('positioning').textContent = platform.snapshot.positioning
    byId('metric-grid').innerHTML = platform.snapshot.metrics.map(([value,label]) => `<div><strong>${value}</strong><span>${label}</span></div>`).join('')
    byId('learning-path').innerHTML = platform.learningPath.map(item => `<li><button data-open-graph="${item.target}"><strong>${item.title}</strong><span>${item.body}</span></button></li>`).join('')
  }

  function relatedEdges(selectedId) {
    return graph().edges.filter(edge => edge[0] === selectedId || edge[1] === selectedId)
  }

  function graphLayers(current) {
    const known = new Set(current.nodes.map(item => item.id))
    const used = new Set()
    const layers = (graphLayerSpecs[current.id] || []).map(([label, ids]) => ({
      label,
      ids: ids.filter(id => known.has(id) && !used.has(id) && used.add(id)),
    })).filter(item => item.ids.length)
    const remaining = current.nodes.map(item => item.id).filter(id => !used.has(id))
    if (remaining.length) layers.push({label:'其他边界', ids:remaining})
    return layers
  }

  function createLaneLayout(current) {
    const layers = graphLayers(current)
    const labelWidth = 150
    const nodeWidth = 150
    const nodeHeight = 66
    const nodeGap = 24
    const sidePadding = 34
    const top = 54
    const bandHeight = 112
    const bandGap = 12
    const maxCount = Math.max(1, ...layers.map(item => item.ids.length))
    const worldWidth = Math.max(1000, labelWidth + sidePadding * 2 + maxCount * nodeWidth + Math.max(0, maxCount - 1) * nodeGap)
    const worldHeight = top + layers.length * bandHeight + Math.max(0, layers.length - 1) * bandGap + 32
    const positions = new Map()
    layers.forEach((layer, layerIndex) => {
      layer.y = top + layerIndex * (bandHeight + bandGap)
      const usableLeft = labelWidth + sidePadding
      const usableWidth = worldWidth - usableLeft - sidePadding
      const step = usableWidth / layer.ids.length
      layer.ids.forEach((id, nodeIndex) => positions.set(id, {
        x: usableLeft + step * (nodeIndex + .5),
        y: layer.y + bandHeight / 2 + 5,
        layer: layerIndex,
      }))
    })
    return {layers, positions, labelWidth, nodeWidth, nodeHeight, bandHeight, bandGap, worldWidth, worldHeight}
  }

  function laneEdgeGeometry(from, to, index) {
    const sameLayer = from.layer === to.layer || Math.abs(to.y - from.y) < 54
    if (sameLayer) {
      const direction = to.x >= from.x ? 1 : -1
      const startX = from.x + direction * 77
      const endX = to.x - direction * 82
      if (Math.abs(to.x - from.x) < 275) return {
        path:`M ${startX.toFixed(1)} ${from.y.toFixed(1)} H ${endX.toFixed(1)}`,
        labelX:((startX + endX) / 2).toFixed(1), labelY:(from.y - 8).toFixed(1),
      }
      const channelY = from.y - 45 - (index % 3) * 7
      return {
        path:`M ${from.x.toFixed(1)} ${(from.y - 34).toFixed(1)} V ${channelY.toFixed(1)} H ${to.x.toFixed(1)} V ${(to.y - 36).toFixed(1)}`,
        labelX:((from.x + to.x) / 2).toFixed(1), labelY:(channelY - 5).toFixed(1),
      }
    }
    const downward = to.y > from.y
    const startY = from.y + (downward ? 34 : -34)
    const endY = to.y + (downward ? -37 : 37)
    const channelY = startY + (downward ? 16 : -16) + ((index % 5) - 2) * 3
    return {
      path:`M ${from.x.toFixed(1)} ${startY.toFixed(1)} V ${channelY.toFixed(1)} H ${to.x.toFixed(1)} V ${endY.toFixed(1)}`,
      labelX:((from.x + to.x) / 2).toFixed(1), labelY:(channelY - 5).toFixed(1),
    }
  }

  function defaultLaneView(layout, stageWidth, stageHeight) {
    if (stageWidth < 760) {
      const selected = layout.positions.get(state.nodeId) || {x:layout.worldWidth / 2, y:layout.worldHeight / 2}
      return {x:stageWidth / 2 - selected.x * .9, y:stageHeight / 2 - selected.y * .9, k:.9}
    }
    const k = Math.min(1, (stageWidth - 18) / layout.worldWidth, (stageHeight - 18) / layout.worldHeight)
    return {x:(stageWidth - layout.worldWidth * k) / 2, y:(stageHeight - layout.worldHeight * k) / 2, k}
  }

  function renderFreeGraph(current, nodeMap) {
    const canvas = byId('graph-canvas')
    canvas.className = `graph-canvas${state.graphListMode ? ' list-mode' : ''}`
    canvas.style.height = ''
    canvas.style.setProperty('--graph-zoom', String(state.graphZoom || 1))
    canvas.innerHTML = '<svg id="graph-edges" viewBox="0 0 1000 600" preserveAspectRatio="none" aria-hidden="true"></svg><div id="graph-nodes"></div>'
    const safeX = value => Math.min(92, Math.max(8, value))
    byId('graph-edges').innerHTML = current.edges.map(([from,to,label]) => {
      const a = nodeMap[from], b = nodeMap[to]
      if (!a || !b) return ''
      const x1 = safeX(a.x) * 10, y1 = a.y * 6, x2 = safeX(b.x) * 10, y2 = b.y * 6
      const tx = (x1 + x2) / 2, ty = (y1 + y2) / 2 - 5
      const related = from === state.nodeId || to === state.nodeId
      return `<line data-from="${from}" data-to="${to}" class="${related ? 'related' : ''}" x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}"></line><text data-from="${from}" data-to="${to}" class="${related ? 'related' : ''}" x="${tx}" y="${ty}" text-anchor="middle">${escapeHtml(label)}</text>`
    }).join('')
    byId('graph-nodes').innerHTML = current.nodes.map(item => `<button class="graph-node ${item.id === state.nodeId ? 'active' : ''}" data-node="${item.id}" data-kind="${item.kind}" style="left:${safeX(item.x)}%;top:${item.y}%"><strong>${item.label}</strong><small>${kindNames[item.kind] || item.kind}</small></button>`).join('')
    canvas.onpointerup = event => {
      const target = event.target.closest('.graph-node')
      if (target) selectNode(target.dataset.node)
    }
  }

  function renderLaneGraph(current, nodeMap) {
    const canvas = byId('graph-canvas')
    canvas.className = 'graph-canvas lane-mode'
    let layout = laneLayouts.get(current.id)
    if (!layout) { layout = createLaneLayout(current); laneLayouts.set(current.id, layout) }
    const stageWidth = Math.max(320, canvas.clientWidth || 1000)
    const stageHeight = Math.max(640, layout.worldHeight + 20)
    canvas.style.height = `${Math.min(780, stageHeight)}px`
    let view = laneViews.get(current.id)
    if (!view) { view = defaultLaneView(layout, stageWidth, Math.min(780, stageHeight)); laneViews.set(current.id, view) }

    const edgeMarkup = current.edges.map(([from,to,label], index) => {
      if (!layout.positions.has(from) || !layout.positions.has(to)) return ''
      const geometry = laneEdgeGeometry(layout.positions.get(from), layout.positions.get(to), index)
      const related = from === state.nodeId || to === state.nodeId
      return `<path class="lane-edge ${related ? 'related' : ''}" data-from="${from}" data-to="${to}" d="${geometry.path}" marker-end="url(#lane-arrow)"><title>${escapeHtml(nodeMap[from]?.label || from)} — ${escapeHtml(label)} → ${escapeHtml(nodeMap[to]?.label || to)}</title></path><text class="lane-edge-label ${related ? 'related' : ''}" data-from="${from}" data-to="${to}" x="${geometry.labelX}" y="${geometry.labelY}" text-anchor="middle">${escapeHtml(label)}</text>`
    }).join('')
    canvas.innerHTML = `<svg id="graph-edges" class="lane-svg" viewBox="0 0 ${stageWidth} ${Math.min(780, stageHeight)}" role="img" aria-label="${escapeHtml(current.title)}分层泳道知识图谱">
      <defs><marker id="lane-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z"></path></marker></defs>
      <g class="lane-viewport" transform="translate(${view.x.toFixed(1)} ${view.y.toFixed(1)}) scale(${view.k.toFixed(3)})">
        <g class="lane-bands">${layout.layers.map((layer,index) => `<rect class="lane-band ${index % 2 ? 'alternate' : ''}" x="10" y="${layer.y}" width="${layout.worldWidth - 20}" height="${layout.bandHeight}" rx="8"></rect><line class="lane-divider" x1="${layout.labelWidth}" y1="${layer.y}" x2="${layout.labelWidth}" y2="${layer.y + layout.bandHeight}"></line><circle class="lane-index" cx="31" cy="${layer.y + 28}" r="15"></circle><text class="lane-index-text" x="31" y="${layer.y + 32}" text-anchor="middle">${index + 1}</text><text class="lane-label" x="18" y="${layer.y + 68}">${escapeHtml(layer.label)}</text>`).join('')}</g>
        <g class="lane-edges">${edgeMarkup}</g>
        <g id="graph-nodes">${current.nodes.map(item => {
          const point = layout.positions.get(item.id)
          return `<foreignObject class="lane-node-object" data-lane-node="${item.id}" x="${(point.x - 75).toFixed(1)}" y="${(point.y - 33).toFixed(1)}" width="150" height="66"><button xmlns="http://www.w3.org/1999/xhtml" class="graph-node lane-node ${item.id === state.nodeId ? 'active' : ''}" data-node="${item.id}" data-kind="${item.kind}"><small>${kindNames[item.kind] || item.kind}</small><strong>${item.label}</strong></button></foreignObject>`
        }).join('')}</g>
      </g></svg><div class="lane-hint"><span>${layout.layers.length} 个职责层 · 前端/入口固定在第一层</span><span>拖动节点调整 · 连线实时重排 · 点击“恢复排布”复原</span></div>`

    const viewport = canvas.querySelector('.lane-viewport')
    const updateViewport = () => viewport.setAttribute('transform', `translate(${view.x.toFixed(1)} ${view.y.toFixed(1)}) scale(${view.k.toFixed(3)})`)
    const updateGeometry = () => {
      current.nodes.forEach(item => {
        const point = layout.positions.get(item.id)
        const object = canvas.querySelector(`[data-lane-node="${CSS.escape(item.id)}"]`)
        if (object) { object.setAttribute('x', (point.x - 75).toFixed(1)); object.setAttribute('y', (point.y - 33).toFixed(1)) }
      })
      current.edges.forEach(([from,to],index) => {
        const geometry = laneEdgeGeometry(layout.positions.get(from), layout.positions.get(to), index)
        canvas.querySelectorAll(`[data-from="${CSS.escape(from)}"][data-to="${CSS.escape(to)}"]`).forEach(element => {
          if (element.tagName.toLowerCase() === 'path') element.setAttribute('d', geometry.path)
          else { element.setAttribute('x', geometry.labelX); element.setAttribute('y', geometry.labelY) }
        })
      })
    }
    const zoomAt = factor => {
      const next = Math.max(.55, Math.min(1.8, view.k * factor))
      const centerX = stageWidth / 2, centerY = Math.min(780, stageHeight) / 2
      const worldX = (centerX - view.x) / view.k, worldY = (centerY - view.y) / view.k
      view.x = centerX - worldX * next; view.y = centerY - worldY * next; view.k = next; updateViewport()
    }
    canvas._laneZoom = zoomAt
    let gesture = null
    canvas.addEventListener('pointerdown', event => {
      const button = event.target.closest('.lane-node')
      const point = button ? layout.positions.get(button.dataset.node) : null
      const rect = canvas.getBoundingClientRect()
      if (button && point) {
        gesture = {type:'node', point, button, pointerId:event.pointerId, captured:false, startX:event.clientX, startY:event.clientY, offsetX:(event.clientX - rect.left - view.x) / view.k - point.x, offsetY:(event.clientY - rect.top - view.y) / view.k - point.y, moved:false}
      } else {
        gesture = {type:'pan', startX:event.clientX, startY:event.clientY, baseX:view.x, baseY:view.y, moved:false}
        canvas.classList.add('is-panning')
        canvas.setPointerCapture?.(event.pointerId)
      }
    })
    canvas.addEventListener('pointermove', event => {
      if (!gesture) return
      const dx = event.clientX - gesture.startX, dy = event.clientY - gesture.startY
      if (Math.hypot(dx,dy) > 9) gesture.moved = true
      if (gesture.type === 'pan') { view.x = gesture.baseX + dx; view.y = gesture.baseY + dy; updateViewport() }
      else if (gesture.moved) {
        if (!gesture.captured) { canvas.setPointerCapture?.(gesture.pointerId); gesture.captured = true }
        event.preventDefault()
        const rect = canvas.getBoundingClientRect()
        gesture.point.x = Math.max(78, Math.min(layout.worldWidth - 78, (event.clientX - rect.left - view.x) / view.k - gesture.offsetX))
        gesture.point.y = Math.max(36, Math.min(layout.worldHeight - 36, (event.clientY - rect.top - view.y) / view.k - gesture.offsetY))
        updateGeometry()
      }
    })
    const endGesture = () => {
      if (gesture?.type === 'node' && gesture.moved) {
        const draggedButton = gesture.button
        draggedButton.dataset.graphDragged = 'true'
        setTimeout(() => delete draggedButton.dataset.graphDragged, 0)
      } else if (gesture?.type === 'node') {
        selectNode(gesture.button.dataset.node)
      }
      gesture = null; canvas.classList.remove('is-panning')
    }
    canvas.addEventListener('pointerup', endGesture)
    canvas.addEventListener('pointercancel', endGesture)
  }

  function restoreLaneLayout() {
    const current = graph()
    const fresh = createLaneLayout(current)
    laneLayouts.set(current.id, fresh)
    const canvas = byId('graph-canvas')
    laneViews.set(current.id, defaultLaneView(fresh, Math.max(320, canvas.clientWidth || 1000), Math.min(780, Math.max(640, fresh.worldHeight + 20))))
    renderGraph()
  }

  function renderGraph() {
    const current = graph()
    if (!current.nodes.some(item => item.id === state.nodeId)) state.nodeId = current.nodes[0].id
    byId('graph-tabs').innerHTML = platform.graphs.map(item => `<button role="tab" aria-selected="${item.id === current.id}" class="${item.id === current.id ? 'active' : ''}" data-graph="${item.id}"><strong>${item.title}</strong><small>${item.subtitle}</small></button>`).join('')
    byId('graph-title').textContent = current.title
    byId('graph-subtitle').textContent = current.subtitle
    byId('graph-node-count').textContent = `${current.nodes.length} 个节点 / ${current.edges.length} 条关系`
    const kinds = [...new Set(current.nodes.map(item => item.kind))]
    byId('graph-kind-filter').innerHTML = '<option value="all">全部</option>' + kinds.map(kind => `<option value="${kind}">${kindNames[kind] || kind}</option>`).join('')
    if (!kinds.includes(state.graphKind)) state.graphKind = 'all'
    byId('graph-kind-filter').value = state.graphKind
    byId('graph-back').disabled = state.graphHistory.length === 0
    byId('graph-focus').classList.toggle('active', state.graphFocus)
    byId('graph-free-mode').classList.toggle('active', state.graphViewMode !== 'lanes')
    byId('graph-lane-mode').classList.toggle('active', state.graphViewMode === 'lanes')
    byId('graph-free-mode').setAttribute('aria-pressed', String(state.graphViewMode !== 'lanes'))
    byId('graph-lane-mode').setAttribute('aria-pressed', String(state.graphViewMode === 'lanes'))
    byId('graph-list-mode').hidden = state.graphViewMode === 'lanes'
    byId('graph-restore-layout').hidden = state.graphViewMode !== 'lanes'
    byId('graph-list-mode').classList.toggle('active', state.graphListMode)
    byId('graph-list-mode').textContent = state.graphListMode ? '图谱模式' : '列表模式'
    const nodeMap = Object.fromEntries(current.nodes.map(item => [item.id, item]))
    if (state.graphViewMode === 'lanes') renderLaneGraph(current, nodeMap)
    else renderFreeGraph(current, nodeMap)
    byId('edge-list').innerHTML = current.edges.map(([from,to,label]) => `<span><b>${nodeMap[from]?.label}</b> ${escapeHtml(label)} → <b>${nodeMap[to]?.label}</b></span>`).join('')
    applyGraphVisibility()
    renderNodeDetail()
  }

  function applyGraphVisibility() {
    const current = graph()
    const related = new Set([state.nodeId])
    if (state.graphFocus) relatedEdges(state.nodeId).forEach(([from,to]) => { related.add(from); related.add(to) })
    const kindVisible = new Set(current.nodes.filter(item => state.graphKind === 'all' || item.kind === state.graphKind).map(item => item.id))
    document.querySelectorAll('.graph-node').forEach(item => {
      const kindFiltered = !kindVisible.has(item.dataset.node)
      item.classList.remove('filtered-out')
      item.classList.toggle('kind-filtered-out', kindFiltered)
      item.classList.toggle('focus-dimmed', !kindFiltered && state.graphFocus && !related.has(item.dataset.node))
    })
    document.querySelectorAll('#graph-edges [data-from]').forEach(item => {
      const kindFiltered = !kindVisible.has(item.dataset.from) || !kindVisible.has(item.dataset.to)
      const focusDimmed = state.graphFocus && (!related.has(item.dataset.from) || !related.has(item.dataset.to))
      item.classList.remove('filtered-out')
      item.classList.toggle('kind-filtered-out', kindFiltered)
      item.classList.toggle('focus-dimmed', !kindFiltered && focusDimmed)
    })
  }

  function renderNodeDetail(includeLinkedPanels = true) {
    const selected = node()
    const guide = interviewGuide(selected)
    const current = graph()
    const nodeMap = Object.fromEntries(current.nodes.map(item => [item.id, item]))
    const relations = relatedEdges(selected.id)
    const relatedChains = relatedChainsForNode(selected)
    const responsibilities = [selected.summary]
    relations.slice(0, 3).forEach(([from,to,label]) => {
      const outgoing = from === selected.id
      const other = nodeMap[outgoing ? to : from]
      responsibilities.push(`${outgoing ? '向' : '承接来自'} ${other?.label || (outgoing ? to : from)} ${outgoing ? '提供或执行' : '的'} ${label}`)
    })
    byId('node-kind').textContent = kindNames[selected.kind] || selected.kind
    byId('detail-title').textContent = selected.label
    byId('detail-summary').textContent = selected.summary
    byId('detail-what').textContent = `${selected.label}是 OrangeVC 的${kindNames[selected.kind] || selected.kind}。${selected.summary}`
    byId('detail-responsibilities').innerHTML = responsibilities.map(item => `<li>${escapeHtml(item)}</li>`).join('')
    byId('detail-value').textContent = `${guide.decision} 它的价值是把这项能力收敛在明确边界内，避免相关规则和失败处理散落到多个调用方。`
    byId('node-mastery').value = String(state.nodeMastery[nodeKey()] || 0)
    byId('node-note').value = state.nodeNotes[nodeKey()] || ''
    byId('detail-interview').textContent = guide.pitch
    byId('detail-decision').textContent = guide.decision
    byId('detail-flow').textContent = guide.flow
    byId('detail-facts').innerHTML = (selected.facts || []).map(item => `<li>${escapeHtml(item)}</li>`).join('')
    byId('detail-invariants').innerHTML = guide.invariants.map(item => `<li>${escapeHtml(item)}</li>`).join('')
    byId('detail-failure').textContent = selected.failure
    byId('detail-scale').textContent = selected.scale
    byId('detail-diagnostics').innerHTML = guide.diagnostics.map(item => `<li>${escapeHtml(item)}</li>`).join('')
    const tables = selected.tables || []
    byId('table-section').hidden = tables.length === 0
    byId('detail-tables').innerHTML = tables.map(item => `<code>${escapeHtml(item)}</code>`).join('')
    byId('detail-files').innerHTML = (selected.files || []).map(path => `<button data-open-source="${escapeHtml(path)}" title="定位到相关调用链或复制路径"><span>${findChainForPath(path) ? '进入链路代码' : '复制源码路径'}</span>${escapeHtml(path)}</button>`).join('')
    byId('detail-chains').innerHTML = relatedChains.length ? relatedChains.map(item => `<button data-open-chain="${item.id}"><span>沿调用链阅读</span><strong>${item.title}</strong></button>`).join('') : '<span>当前六条代表链路暂未直接覆盖该节点，可从源码入口继续阅读。</span>'
    byId('detail-relations').innerHTML = relations.map(([from,to,label]) => {
      const otherId = from === selected.id ? to : from
      const direction = from === selected.id ? '指向' : '来自'
      return `<button data-node="${otherId}"><span>${escapeHtml(label)} / ${direction}</span><strong>${escapeHtml(nodeMap[otherId]?.label || otherId)}</strong></button>`
    }).join('') || '<span>当前图中没有直接关系</span>'
    byId('detail-questions').innerHTML = guide.questions.map(item => `<li>${escapeHtml(item)}</li>`).join('')
    if (includeLinkedPanels) {
      renderOpenPractice()
      renderInterview()
    }
  }

  function scheduleNodeDependents(nodeId) {
    clearTimeout(nodeDependentTimer)
    nodeDependentTimer = setTimeout(() => {
      if (state.nodeId !== nodeId) return
      renderOpenPractice()
      renderInterview()
    }, 48)
  }

  function scheduleNodeDetail(nodeId) {
    cancelAnimationFrame(nodeDetailFrame)
    cancelAnimationFrame(nodeDetailSecondFrame)
    nodeDetailFrame = requestAnimationFrame(() => {
      nodeDetailSecondFrame = requestAnimationFrame(() => {
        if (state.nodeId !== nodeId) return
        renderNodeDetail(false)
        renderStudyDashboard()
        save()
        scheduleNodeDependents(nodeId)
      })
    })
  }

  function selectNode(nodeId) {
    if (state.nodeId === nodeId) return
    const targetNode = graph().nodes.find(item => item.id === nodeId)
    if (!targetNode) return
    if (state.graphKind !== 'all' && targetNode.kind !== state.graphKind) {
      state.graphKind = 'all'
      byId('graph-kind-filter').value = 'all'
    }
    state.graphHistory.push({graphId:state.graphId, nodeId:state.nodeId})
    state.graphHistory = state.graphHistory.slice(-20)
    state.nodeId = nodeId
    document.querySelectorAll('.graph-node').forEach(item => item.classList.toggle('active', item.dataset.node === nodeId))
    document.querySelectorAll('#graph-edges [data-from]').forEach(item => item.classList.toggle('related', item.dataset.from === nodeId || item.dataset.to === nodeId))
    byId('node-kind').textContent = kindNames[targetNode.kind] || targetNode.kind
    byId('detail-title').textContent = targetNode.label
    byId('detail-summary').textContent = targetNode.summary
    applyGraphVisibility()
    byId('graph-back').disabled = state.graphHistory.length === 0
    scheduleNodeDetail(nodeId)
  }

  function renderOpenPractice() {
    const selected = node()
    const guide = interviewGuide(selected)
    byId('open-question').textContent = `假设“${selected.failure}”已经发生。请说明影响范围、必须守住的事实、第一排查入口和扩容前应做的改进。`
    byId('open-answer').value = state.openAnswers[nodeKey()] || ''
    byId('open-rubric').innerHTML = `<h4>评分要点</h4><ol><li>先区分直接受影响能力与仍然可信的数据事实。</li><li>说明不变量：${guide.invariants.map(escapeHtml).join('；')}。</li><li>给出排查入口：${guide.diagnostics.map(escapeHtml).join('；')}。</li><li>演进方案不能跳过当前约束：${escapeHtml(selected.scale)}</li></ol>`
    byId('open-rubric').hidden = true
  }

  function interviewPrompts(selected) {
    const guide = interviewGuide(selected)
    return [
      {title:'定位与价值', question:`请用 90 秒介绍 ${selected.label}：它是什么、负责什么、为什么值得单独设计？`, guide:`先给出系统定位。再说明输入、输出和边界。最后用一句话概括价值。参考表达：${guide.pitch}`},
      {title:'设计选择', question:`为什么 ${selected.label} 要采用当前设计，而不是把逻辑直接放进调用方？`, guide:`从约束开始回答，再说当前选择如何守住边界。关键设计：${guide.decision}`},
      {title:'故障推演', question:`如果 ${selected.label} 发生故障，系统会怎样？你会先看什么？`, guide:`先区分故障影响与数据损失，再按信号给出排查顺序。故障语义：${selected.failure} 排查入口：${guide.diagnostics.join('；')}`},
      {title:'规模演进', question:`业务规模扩大十倍后，${selected.label} 应该先改哪里？`, guide:`不要直接回答拆微服务。先说瓶颈证据、当前单点和状态边界，再给渐进方案：${selected.scale}`},
      ...guide.questions.map((question,index) => ({title:`继续追问 ${index + 1}`, question, guide:`回答时必须落回真实关系、数据和源码。可引用这些不变量：${guide.invariants.join('；')}`})),
    ]
  }

  function renderInterview() {
    const selected = node()
    const prompts = interviewPrompts(selected)
    state.interviewIndex = Math.min(state.interviewIndex || 0, prompts.length - 1)
    const current = prompts[state.interviewIndex]
    const key = `${nodeKey()}:${state.interviewIndex}`
    byId('interview-context').textContent = `${graph().title} / ${selected.label}`
    byId('interview-prompts').innerHTML = prompts.map((item,index) => `<button class="${index === state.interviewIndex ? 'active' : ''}" data-interview-index="${index}"><span>${String(index + 1).padStart(2,'0')}</span><strong>${item.title}</strong></button>`).join('')
    byId('interview-question').textContent = current.question
    byId('interview-draft').value = state.interviewDrafts[key] || ''
    byId('interview-guide').innerHTML = `<h4>回答骨架</h4><p>${escapeHtml(current.guide)}</p>`
    byId('interview-guide').hidden = true
  }

  function renderDomains() {
    const domainGraph = platform.graphs.find(item => item.id === 'domains')
    byId('domain-grid').innerHTML = domainGraph.nodes.map(item => `<article class="domain-item" data-open-domain="${item.id}" tabindex="0"><header><h3>${item.label}</h3><span>${kindNames[item.kind] || item.kind}</span></header><p>${item.summary}</p><dl><dt>数据</dt><dd>${(item.tables || []).slice(0,4).join(' / ') || '跨域基础设施'}</dd><dt>风险</dt><dd>${item.failure}</dd></dl></article>`).join('')
  }

  function renderChains() {
    const current = chain()
    byId('chain-list').innerHTML = learningChains.map((item,index) => `<button class="${item.id === current.id ? 'active' : ''}" data-chain="${item.id}"><span class="number">${String(index + 1).padStart(2,'0')}</span><span><strong>${item.title}</strong><small>${item.subtitle}</small></span><b>${state.completedChains.includes(item.id) ? '完成' : ''}</b></button>`).join('')
    byId('chain-route').textContent = current.route
    byId('chain-title').textContent = current.title
    byId('chain-goal').textContent = current.goal
    byId('chain-graph').innerHTML = current.steps.map((step,index) => `<button class="chain-step ${index === state.chainStep ? 'active' : ''}" data-chain-step="${index}"><span>${index + 1}</span><strong>${step.name}</strong><p>${step.detail}</p></button>`).join('')
    byId('file-tabs').innerHTML = current.files.map((file,index) => `<button role="tab" aria-selected="${index === state.fileIndex}" class="${index === state.fileIndex ? 'active' : ''}" data-file-index="${index}">${fileName(file.path)}</button>`).join('')
    const file = current.files[state.fileIndex] || current.files[0]
    byId('file-path').textContent = file.path
    byId('file-role').textContent = file.role
    byId('code-content').textContent = file.code
    byId('code-location').textContent = `关键摘录 · ${file.code.split('\n').length} 行 · 架构基线 ${platform.snapshot.date}`
    byId('code-context').textContent = '片段用于定位设计，阅读时应回到源文件核对完整上下文'
    byId('mastery-list').innerHTML = current.mastery.map(item => `<li><b>✓</b>${escapeHtml(item)}</li>`).join('')
    byId('investigation').textContent = current.investigation
    const completed = state.completedChains.includes(current.id)
    byId('chain-complete').textContent = completed ? '已掌握' : '标记掌握'
    byId('chain-complete').classList.toggle('completed', completed)
  }

  function renderCodeCatalog() {
    byId('code-catalog').innerHTML = platform.codeCatalog.map(group => `<section class="code-group"><h3>${group.group}</h3><ul>${group.items.map(([title,path,role]) => `<li><button data-open-source="${escapeHtml(path)}"><strong>${title}</strong><code>${escapeHtml(path)}</code><small>${role}</small></button></li>`).join('')}</ul></section>`).join('')
  }

  function renderScenarios() {
    byId('scenario-list').innerHTML = platform.failureScenarios.map((item,index) => `<button class="${index === state.scenarioIndex ? 'active' : ''}" data-scenario="${index}">${item.title}</button>`).join('')
    const item = platform.failureScenarios[state.scenarioIndex] || platform.failureScenarios[0]
    byId('scenario-detail').innerHTML = `<h3>${item.title}</h3><div class="scenario-grid"><section><h4>受影响</h4><ul>${item.affects.map(value => `<li>${value}</li>`).join('')}</ul></section><section><h4>仍能工作</h4><ul>${item.survives.map(value => `<li>${value}</li>`).join('')}</ul></section><section><h4>第一排查入口</h4><p>${item.first}</p></section><section><h4>真正的改进</h4><p>${item.improve}</p></section></div>`
  }

  function renderQuiz() {
    const current = exercise()
    const selected = state.selections[current.id]
    const result = state.results[current.id]
    byId('quiz-index').innerHTML = architectureExercises.map((item,index) => {
      const status = state.results[item.id]
      return `<button class="${item.id === current.id ? 'active' : ''} ${status === true ? 'correct' : ''} ${status === false ? 'wrong' : ''}" data-exercise="${item.id}"><span>${String(index + 1).padStart(2,'0')}</span><strong>${chainTitle(item.chainId)}</strong><b>${status === true ? '✓' : status === false ? '×' : ''}</b></button>`
    }).join('')
    byId('quiz-topic').textContent = chainTitle(current.chainId)
    byId('quiz-question').textContent = current.question
    byId('quiz-options').setAttribute('aria-label', current.question)
    byId('quiz-options').innerHTML = current.options.map((option,index) => {
      const classes = [selected === index ? 'selected' : '', result !== undefined && index === current.answer ? 'correct' : '', result === false && selected === index ? 'wrong' : ''].join(' ')
      return `<button role="radio" aria-checked="${selected === index}" class="${classes}" data-option="${index}" ${result !== undefined ? 'disabled' : ''}><span>${String.fromCharCode(65 + index)}</span>${escapeHtml(option)}</button>`
    }).join('')
    byId('submit-answer').hidden = result !== undefined
    byId('submit-answer').disabled = selected === undefined
    const feedback = byId('answer-feedback')
    feedback.hidden = result === undefined
    if (result !== undefined) {
      feedback.className = `answer-feedback ${result ? 'correct' : 'wrong'}`
      feedback.innerHTML = `<strong>${result ? '回答正确' : '这次没有选对'}</strong><p>${escapeHtml(current.explanation)}</p><button id="retry-answer">重新作答</button>`
    }
  }

  async function copyPath(path, target) {
    try {
      await navigator.clipboard.writeText(path)
      const before = target.textContent
      target.textContent = '已复制路径'
      setTimeout(() => { target.textContent = before }, 1200)
    } catch {
      window.prompt('复制下面的路径', path)
    }
  }

  document.addEventListener('click', async event => {
    const target = event.target.closest('button,[data-open-domain]')
    if (!target) return
    if (target.dataset.jump) showPage(target.dataset.jump)
    if (target.dataset.copyLocation) await copyPath(target.dataset.copyLocation, target)
    if (target.id === 'source-reset') { state.sourceCategory='all'; state.sourcePriority='all'; state.sourceSearch=''; renderSourceGuide(); save() }
    if (target.dataset.openLab || target.dataset.lab) {
      state.workbenchLabId = target.dataset.openLab || target.dataset.lab
      renderLab(); save(); showPage('learning-workbench')
    }
    if (target.dataset.dailyComplete) {
      state.dailyCompleted[target.dataset.dailyComplete] = !state.dailyCompleted[target.dataset.dailyComplete]
      renderDailyStudy(); renderWorkbenchMetrics(); save()
    }
    if (target.dataset.runtimeAction) {
      const sim = state.runtimeSim, action = target.dataset.runtimeAction
      if (action === 'reset') state.runtimeSim = {status:'PENDING',crashed:false,events:['任务已入队，状态为 PENDING']}
      else if (action === 'claim' && ['PENDING','RETRY_WAIT'].includes(sim.status)) { sim.status='RUNNING'; sim.crashed=false; sim.events.push('条件UPDATE影响1行，Worker获得有期限的执行权') }
      else if (action === 'heartbeat' && sim.status === 'RUNNING' && !sim.crashed) sim.events.push('心跳续租成功，lease_until向后延长')
      else if (action === 'crash' && sim.status === 'RUNNING') { sim.crashed=true; sim.events.push('Worker在RUNNING阶段退出；数据库事实仍在，租约暂未过期') }
      else if (action === 'recover' && sim.status === 'RUNNING' && sim.crashed) { sim.status='RETRY_WAIT'; sim.crashed=false; sim.events.push('扫描发现租约过期，任务进入RETRY_WAIT，等待重新claim') }
      else sim.events.push(`操作“${action}”不满足当前状态的前置条件`)
      renderLab(); save()
    }
    if (target.hasAttribute('data-mcp-validate')) {
      try {
        const payload = JSON.parse(byId('mcp-payload').value), missing = ['action','arguments','scope'].filter(key => payload[key] === undefined)
        state.mcpResult = missing.length ? `校验失败：缺少 ${missing.join('、')}` : typeof payload.arguments !== 'object' || Array.isArray(payload.arguments) ? '校验失败：arguments必须是对象' : '结构校验通过；下一步仍必须校验设备身份、Scope、动作白名单和业务权限。'
      } catch (error) { state.mcpResult = `JSON解析失败：${error.message}` }
      renderLab(); save()
    }
    if (target.hasAttribute('data-context-run')) {
      const budget = Math.max(20,Number(byId('context-budget').value)||80), lines = byId('context-messages').value.split('\n').filter(Boolean)
      const first = lines[0] || '', kept = first ? [first] : [], rest = lines.slice(1), estimate = value => Math.ceil(value.length/4)
      let used = estimate(first)
      for (let i=rest.length-1;i>=0;i--) if (used + estimate(rest[i]) <= budget) { kept.splice(1,0,rest[i]); used += estimate(rest[i]) }
      state.contextResult = {tokens:used,removed:lines.length-kept.length,kept,note:'该演示使用字符估算Token。形成简历证据前，应替换为真实Tokenizer并为工具长输出补摘要策略。'}
      renderLab(); save()
    }
    if (target.dataset.dagFail) { state.dagFailed = target.dataset.dagFail; renderLab(); save() }
    if (target.hasAttribute('data-dag-reset')) { state.dagFailed = ''; renderLab(); save() }
    if (target.hasAttribute('data-save-experiment')) {
      state.experimentRecords[state.workbenchLabId] = {note:byId('experiment-note').value.trim(),proof:byId('experiment-proof').value.trim(),savedAt:new Date().toLocaleString('zh-CN')}
      renderLab(); renderWorkbenchMetrics(); save()
    }
    if (target.dataset.ladderStage !== undefined) { state.interviewStage=Number(target.dataset.ladderStage); renderLadderInterview(); save() }
    if (target.hasAttribute('data-save-ladder')) {
      const item=workbench.interview[state.interviewStage]; state.ladderAnswers[item.id]=byId('ladder-answer').value.trim(); save(); target.textContent='已保存'; setTimeout(()=>{target.textContent='保存回答'},1000)
    }
    if (target.dataset.openGraph) {
      openNodeLocation(target.dataset.openGraph, platform.graphs.find(item => item.id === target.dataset.openGraph).nodes[0].id)
    }
    if (target.dataset.graph) {
      openNodeLocation(target.dataset.graph, platform.graphs.find(item => item.id === target.dataset.graph).nodes[0].id, false)
    }
    if (target.dataset.node && target.dataset.graphDragged !== 'true') selectNode(target.dataset.node)
    if (target.dataset.openDomain) {
      openNodeLocation('domains', target.dataset.openDomain)
    }
    if (target.dataset.copyFile) await copyPath(target.dataset.copyFile, target)
    if (target.dataset.openSource) {
      const match = findChainForPath(target.dataset.openSource)
      if (match) {
        state.chainId = match.chain.id; state.fileIndex = match.fileIndex; state.chainStep = 0
        renderChains(); save(); showPage('chains')
      } else await copyPath(target.dataset.openSource, target)
    }
    if (target.dataset.openChain) { state.chainId = target.dataset.openChain; state.fileIndex = 0; state.chainStep = 0; renderChains(); save(); showPage('chains') }
    if (target.dataset.chain) { state.chainId = target.dataset.chain; state.fileIndex = 0; state.chainStep = 0; renderChains(); save() }
    if (target.dataset.chainStep !== undefined) { state.chainStep = Number(target.dataset.chainStep); state.fileIndex = stepFileIndex(chain(), state.chainStep); renderChains(); save() }
    if (target.dataset.fileIndex !== undefined) { state.fileIndex = Number(target.dataset.fileIndex); renderChains(); save() }
    if (target.id === 'copy-path') await copyPath(byId('file-path').textContent, target)
    if (target.id === 'chain-complete') {
      state.completedChains = state.completedChains.includes(chain().id) ? state.completedChains.filter(id => id !== chain().id) : [...state.completedChains, chain().id]
      renderChains(); save()
    }
    if (target.dataset.scenario !== undefined) { state.scenarioIndex = Number(target.dataset.scenario); renderScenarios(); save() }
    if (target.dataset.exercise) { state.exerciseId = target.dataset.exercise; renderQuiz(); save() }
    if (target.dataset.option !== undefined) { state.selections[exercise().id] = Number(target.dataset.option); renderQuiz(); save() }
    if (target.id === 'submit-answer') { const current = exercise(); state.results[current.id] = state.selections[current.id] === current.answer; renderQuiz(); save() }
    if (target.id === 'retry-answer') { delete state.selections[exercise().id]; delete state.results[exercise().id]; renderQuiz(); save() }
    if (target.id === 'resume-learning') openNodeLocation(state.graphId, state.nodeId)
    if (target.id === 'graph-free-mode') { state.graphViewMode = 'free'; renderGraph(); save() }
    if (target.id === 'graph-lane-mode') { state.graphViewMode = 'lanes'; state.graphListMode = false; renderGraph(); save() }
    if (target.id === 'graph-focus') { state.graphFocus = !state.graphFocus; target.classList.toggle('active', state.graphFocus); applyGraphVisibility(); save() }
    if (target.id === 'graph-zoom-out') {
      if (state.graphViewMode === 'lanes') byId('graph-canvas')._laneZoom?.(1 / 1.16)
      else { state.graphZoom = Math.max(.78, Number((state.graphZoom - .1).toFixed(2))); byId('graph-canvas').style.setProperty('--graph-zoom', state.graphZoom) }
      save()
    }
    if (target.id === 'graph-zoom-in') {
      if (state.graphViewMode === 'lanes') byId('graph-canvas')._laneZoom?.(1.16)
      else { state.graphZoom = Math.min(1.2, Number((state.graphZoom + .1).toFixed(2))); byId('graph-canvas').style.setProperty('--graph-zoom', state.graphZoom) }
      save()
    }
    if (target.id === 'graph-list-mode') { state.graphListMode = !state.graphListMode; renderGraph(); save() }
    if (target.id === 'graph-restore-layout') restoreLaneLayout()
    if (target.id === 'graph-reset') { state.graphFocus = false; state.graphKind = 'all'; state.graphZoom = 1; state.graphListMode = false; renderGraph(); save() }
    if (target.id === 'graph-back' && state.graphHistory.length) {
      const previous = state.graphHistory.pop(); state.graphId = previous.graphId; state.nodeId = previous.nodeId; state.graphFocus = false
      renderGraph(); renderStudyDashboard(); save()
    }
    if (target.id === 'practice-node') showPage('practice')
    if (target.id === 'start-node-interview') showPage('interview')
    if (target.id === 'save-open-answer') { state.openAnswers[nodeKey()] = byId('open-answer').value.trim(); save(); target.textContent = '已保存在本机'; setTimeout(() => { target.textContent = '保存回答' }, 1200) }
    if (target.id === 'reveal-rubric') byId('open-rubric').hidden = !byId('open-rubric').hidden
    if (target.dataset.interviewIndex !== undefined) { state.interviewIndex = Number(target.dataset.interviewIndex); renderInterview(); save() }
    if (target.id === 'save-interview') {
      state.interviewDrafts[`${nodeKey()}:${state.interviewIndex}`] = byId('interview-draft').value.trim(); save(); target.textContent = '已保存在本机'; setTimeout(() => { target.textContent = '保存回答' }, 1200)
    }
    if (target.id === 'show-interview-guide') byId('interview-guide').hidden = !byId('interview-guide').hidden
    if (target.dataset.searchType) {
      if (target.dataset.searchType === 'node') openNodeLocation(target.dataset.searchGraph, target.dataset.searchId)
      if (target.dataset.searchType === 'chain') { state.chainId = target.dataset.searchId; state.fileIndex = 0; state.chainStep = 0; renderChains(); save(); showPage('chains') }
      if (target.dataset.searchType === 'scenario') { state.scenarioIndex = Number(target.dataset.searchId); renderScenarios(); save(); showPage('reliability') }
      if (target.dataset.searchType === 'source') {
        const match = findChainForPath(target.dataset.searchId)
        if (match) { state.chainId = match.chain.id; state.fileIndex = match.fileIndex; renderChains(); save(); showPage('chains') }
        else await copyPath(target.dataset.searchId, target)
      }
      if (target.dataset.searchType === 'career') showPage('career-track')
      if (target.dataset.searchType === 'career-dive') { state.careerDiveId = target.dataset.searchId; renderDeepDive(); save(); showPage('career-track') }
      if (target.dataset.searchType === 'lab') { state.workbenchLabId=target.dataset.searchId; renderLab(); save(); showPage('learning-workbench') }
      if (target.dataset.searchType === 'study-topic') openStudyTopic(target.dataset.searchId)
    }
    if (target.dataset.masteryFilter !== undefined) {
      const level = Number(target.dataset.masteryFilter)
      const next = allNodes().find(item => Number(state.nodeMastery[nodeKey(item.graphId,item.id)] || 0) === level)
      if (next) openNodeLocation(next.graphId, next.id)
    }
    if (target.dataset.careerNode) {
      const [graphId,targetNodeId] = target.dataset.careerNode.split(':')
      openNodeLocation(graphId, targetNodeId)
    }
    if (target.dataset.careerDive) { state.careerDiveId = target.dataset.careerDive; renderDeepDive(); save() }
    if (target.dataset.careerQuestion !== undefined) { state.careerQuestionIndex = Number(target.dataset.careerQuestion); renderCareerQuestion(); save() }
    if (target.dataset.completeCareer) {
      const key = target.dataset.completeCareer
      state.careerCompleted[key] = !state.careerCompleted[key]
      if (key.startsWith('dive:')) renderDeepDive(); else renderCareerLabs()
      save()
    }
    if (target.id === 'save-career-answer') {
      state.careerAnswers[`question:${state.careerQuestionIndex}`] = byId('career-question-draft').value.trim()
      save(); target.textContent = '已保存在本机'; setTimeout(() => { target.textContent = '保存回答' }, 1200)
    }
    if (target.id === 'show-career-hint') byId('career-question-hint').hidden = !byId('career-question-hint').hidden
  })

  byId('graph-kind-filter').addEventListener('change', event => { state.graphKind = event.target.value; applyGraphVisibility(); save() })
  byId('node-mastery').addEventListener('change', event => { state.nodeMastery[nodeKey()] = Number(event.target.value); renderStudyDashboard(); save() })
  byId('node-note').addEventListener('input', event => { state.nodeNotes[nodeKey()] = event.target.value; byId('note-status').textContent = '已自动保存'; save() })
  byId('global-search').addEventListener('input', renderSearchResults)
  byId('source-search').addEventListener('input', event => { state.sourceSearch=event.target.value; renderSourceGuide(); save() })
  byId('source-category').addEventListener('change', event => { state.sourceCategory=event.target.value; renderSourceGuide(); save() })
  byId('source-priority').addEventListener('change', event => { state.sourcePriority=event.target.value; renderSourceGuide(); save() })
  document.addEventListener('change', event => {
    const target = event.target
    if (target.dataset.sourceProgress) { state.sourceProgress[target.dataset.sourceProgress]=Number(target.value); renderSourceGuide(); save() }
    if (target.dataset.labCheck) {
      state.labChecks[state.workbenchLabId] ||= {}; state.labChecks[state.workbenchLabId][target.dataset.labCheck]=target.checked
      renderWorkbenchMetrics(); save()
    }
    if (target.dataset.claim) {
      state.claimEvidence[target.dataset.claim] ||= {}; state.claimEvidence[target.dataset.claim][target.dataset.requirement]=target.checked
      renderClaimGates(); renderWorkbenchMetrics(); save()
    }
    if (target.dataset.rubric) {
      const item=workbench.interview[state.interviewStage]; state.ladderScores[item.id] ||= {}; state.ladderScores[item.id][target.dataset.rubric]=target.checked
      renderLadderInterview(); save()
    }
  })
  document.addEventListener('keydown', event => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); showPage('study-home'); byId('global-search').focus() }
  })

  document.addEventListener('keydown', event => {
    const item = event.target.closest('[data-open-domain]')
    if (item && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); item.click() }
  })

  renderSnapshot()
  renderGraph()
  renderDomains()
  renderChains()
  renderCodeCatalog()
  renderScenarios()
  renderQuiz()
  renderCareerTrack()
  renderLearningWorkbench()
  renderSourceGuide()
  renderStudyDashboard()
  byId('global-search').value = state.searchQuery || ''
  renderSearchResults()
  const initialPage = PAGE_LABELS[location.hash.slice(1)] ? location.hash.slice(1) : 'study-sources'
  showPage(initialPage, location.hash ? 'silent' : 'replace')
  window.addEventListener('popstate', () => showPage(location.hash.slice(1), 'silent'))
})()







