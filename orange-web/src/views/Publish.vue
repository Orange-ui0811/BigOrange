<template>
  <div class="publish-page">
    <PageTour storageKey="publish" :steps="tourSteps" />
    <!-- ═══════ 选择页：发布作品 or 发布需求 ═══════ -->
    <template v-if="!showForm">
      <div class="publish-studio">
        <div>
          <div class="compact-head">
            <div>
              <span class="eyebrow">发布工作台</span>
              <h2>先选你要挂出的内容，再进入对应表单。</h2>
              <p>发布页只做分流和准备提示。真正的字段在下一步表单里根据作品/需求类型动态展示，避免用户一进来就被复杂表单压住。</p>
            </div>
          </div>
          <div class="publish-choices">
            <!-- 发布作品 -->
            <article class="publish-choice work">
              <div>
                <div class="tag-row">
                  <span class="publish-tag orange">作品</span>
                  <span class="publish-tag green">展示与售卖</span>
                </div>
                <h3>发布 AI 产品作品</h3>
                <p>适合已经做出 demo、原型或可演示产品的创作者。重点是让别人快速看懂价值、完成度和适用场景。</p>
                <div class="choice-preview">
                  <strong>下一步会填写</strong>
                  <span>作品封面</span><span>演示地址</span><span>展示方式</span><span>功能亮点</span>
                </div>
                <ul class="prepare-list">
                  <li><span class="check-dot">✓</span>产品标题、简介和封面</li>
                  <li><span class="check-dot">✓</span>演示链接或截图</li>
                  <li><span class="check-dot">✓</span>功能亮点和适用场景</li>
                  <li><span class="check-dot">✓</span>当前完成度和注意事项</li>
                </ul>
              </div>
              <el-button type="primary" class="choice-btn" @click="startPublish('work')">开始发布作品</el-button>
            </article>
            <!-- 发布需求 -->
            <article class="publish-choice requirement">
              <div>
                <div class="tag-row">
                  <span class="publish-tag blue">需求</span>
                  <span class="publish-tag yellow">预算与定制</span>
                </div>
                <h3>发布定制需求</h3>
                <p>适合商家或用户提出目标，先给预算范围，再和创作者通过聊天确认需求范围和实现思路。</p>
                <div class="choice-preview">
                  <strong>下一步会填写</strong>
                  <span>需求目标</span><span>预算范围</span><span>参考样例</span><span>完成标准</span>
                </div>
                <ul class="prepare-list">
                  <li><span class="check-dot blue-dot">✓</span>想解决的问题</li>
                  <li><span class="check-dot blue-dot">✓</span>预算范围和期望周期</li>
                  <li><span class="check-dot blue-dot">✓</span>参考产品或截图</li>
                  <li><span class="check-dot blue-dot">✓</span>必须包含和暂不需要的内容</li>
                </ul>
              </div>
              <el-button class="choice-btn ghost-btn" @click="startPublish('requirement')">开始发布需求</el-button>
            </article>
          </div>
        </div>
        <aside class="publish-side">
          <div class="publish-note strong">
            <h4>审核流程</h4>
            <div class="publish-flow">
              <div class="flow-step">填写内容并保存草稿</div>
              <div class="flow-step">提交平台审核</div>
              <div class="flow-step">审核通过后公开展示</div>
              <div class="flow-step">被感兴趣后进入聊天沟通</div>
            </div>
          </div>
          <div class="publish-note">
            <h4>发布前检查</h4>
            <p>标题是否清楚、演示链接是否可打开、说明范围是否明确、是否包含敏感或侵权内容。</p>
          </div>
          <div class="publish-note">
            <h4>MVP 边界</h4>
            <p>当前只做作品展示、需求展示、审核、收藏、评论和聊天沟通。</p>
            <ul class="safe-list">
              <li>作品先说明功能、演示方式和当前完成度。</li>
              <li>需求先说明目标、预算范围和完成标准。</li>
              <li>审核状态、聊天状态和收藏状态先独立流转。</li>
            </ul>
          </div>
        </aside>
      </div>
    </template>

    <!-- ═══════ 表单页 ═══════ -->
    <template v-else>
      <button class="detail-back" @click="backToChoice">← 返回发布工作台</button>
      <div class="form-section">
        <div class="compact-head form-head">
          <div>
            <span class="eyebrow">{{ isEdit ? '编辑' : '发布' }}{{ tab==='work'?'作品':'需求' }}</span>
            <h2 v-if="!isEdit">{{ tab==='work' ? '发布你的 AI 产品作品' : '发布你的定制需求' }}</h2>
            <h2 v-else>编辑{{ tab==='work'?'作品':'需求' }}</h2>
          </div>
        </div>
        <div class="panel form-panel">
          <el-tabs v-model="tab" class="pub-tabs" @tab-change="onTabChange">
            <el-tab-pane label="作品" name="work" />
            <el-tab-pane label="需求" name="requirement" />
          </el-tabs>
          <el-form label-position="top">
            <el-form-item label="封面图"><el-upload :action="API+'/app/common/upload?dir=cover'" :headers="uploadHeaders" :on-success="onCoverSuccess" :show-file-list="false" accept="image/*"><el-button size="small">上传封面</el-button></el-upload><el-image v-if="form.coverUrl" :src="form.coverUrl" class="cover-preview" fit="cover"/></el-form-item>
            <el-form-item label="演示链接">
              <template v-if="tab==='work'">
                <el-collapse class="guide-collapse">
                  <el-collapse-item title="📘 如何生成演示链接？（点击展开）" name="guide">
                    <div class="guide-body">
                      <p class="guide-intro">把你的作品部署到免费云端，粘贴链接即可。源码始终在你手里，买家只能体验产品。</p>
                      <div class="guide-block guide-block-blue"><b>🔧 前端项目（HTML/Vue/React）</b><p class="guide-text">→ vercel.com → Import Git Repository → Deploy → 1-2分钟上线</p></div>
                      <div class="guide-block guide-block-orange"><b>🔧 Python / Streamlit</b><p class="guide-text">→ share.streamlit.io → New app → 填仓库路径 → Deploy</p></div>
                      <div class="guide-block guide-block-green"><b>🔧 后端 API / 全栈</b><p class="guide-text">→ railway.app 或 render.com → Deploy from GitHub</p></div>
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </template>
              <el-input v-model="form.demoUrl" placeholder="https://你的作品演示地址（可选）" />
              <span class="field-hint">粘贴演示链接后，买家可在线体验你的作品，源码不可见</span>
            </el-form-item>
            <el-form-item label="标题" required><el-input v-model="form.title" placeholder="一句话描述你的作品/需求" /></el-form-item>
            <el-form-item label="简介"><el-input v-model="form.summary" type="textarea" :rows="2" placeholder="简短介绍" /></el-form-item>
            <el-form-item label="详情">
              <div class="detail-upload-row">
                <el-upload :action="API+'/app/common/upload?dir=detail'" :headers="uploadHeaders" :on-success="onDetailImage" :show-file-list="false" accept="image/*" multiple>
                  <el-button size="small">📷 插入图片</el-button>
                </el-upload>
                <span class="field-hint inline-hint">支持 HTML：&lt;img src=&quot;...&quot;&gt; 插入图片</span>
              </div>
              <el-input v-model="form.detail" type="textarea" :rows="6" placeholder="详细描述（支持HTML富文本）" />
            </el-form-item>
            <el-form-item label="分类"><el-select v-model="form.categoryId" placeholder="选择分类" clearable><el-option v-for="c in cats" :key="c.id" :label="c.name" :value="c.id" /></el-select></el-form-item>
            <template v-if="tab==='work' && tradeEnabled">
              <el-form-item label="售卖价格"><el-input v-model="form.price" type="number" placeholder="¥ 留空则面议" /></el-form-item>
            </template>
            <template v-else>
              <el-form-item label="预算范围">
                <el-input v-model="form.budgetMin" type="number" placeholder="最低" class="budget-input" /> —
                <el-input v-model="form.budgetMax" type="number" placeholder="最高" class="budget-input" />
              </el-form-item>
            </template>
            <el-button type="primary" @click="submit" :loading="loading" style="width:100%">{{ isEdit?'保存修改':'发布' }}</el-button>
          </el-form>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageTour from '../components/PageTour.vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { tradeEnabled } from '../stores/tradeMode'

const API = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
const route = useRoute()
const router = useRouter()
const tourSteps = [
  { selector: '.publish-choices', text: '选左边的「发布作品」展示你的 AI 产品，或选右边的「发布需求」找人帮你做。每边都列了要准备什么～', expression: 'wink' },
  { selector: '.publish-note.strong', text: '发布的流程：填写内容→提交审核→审核通过→公开展示→有人感兴趣→聊天沟通。', expression: 'smile' },
  { selector: '.publish-note:last-child', text: '当前 MVP 只做作品展示、需求展示、审核、收藏、评论和聊天。后续会逐步完善交易和定制功能～', expression: 'smile' },
]

const isEdit = !!route.params.id
const showForm = computed(() => isEdit || !!route.query.type)
const tab = ref(isEdit ? (route.params.type === 'work' ? 'work' : 'requirement') : (route.query.type || 'work'))
const cats = ref([])
const editId = ref(isEdit ? route.params.id : null)
const loading = ref(false)
const form = reactive({ title: '', summary: '', detail: '', categoryId: null, price: '', budgetMin: '', budgetMax: '', coverUrl: '', demoUrl: '' })
const uploadHeaders = { 'App-Token': sessionStorage.getItem('appToken') || '' }

const onCoverSuccess = (res) => { if (res.code === 1) form.coverUrl = res.data }
const onDetailImage = (res) => {
  if (res.code === 1 && res.data) {
    const url = res.data.startsWith('http') ? res.data : res.data
    form.detail = (form.detail || '') + '<img src="' + url + '" style="max-width:100%">\n'
  }
}

const startPublish = (type) => {
  tab.value = type
  router.push({ path: '/publish', query: { type } })
}
const backToChoice = () => {
  router.push('/publish')
}
const onTabChange = () => {
  if (!isEdit) form.categoryId = null
  loadCats()
}

const loadCats = async () => {
  try { const res = await request.get('/app/category?type=' + (tab.value==='work'?'WORK':'REQUIREMENT')); if (res.code===1) cats.value = res.data } catch {}
}
watch(tab, () => { if (!isEdit) form.categoryId = null; loadCats() })
onMounted(async () => {
  loadCats()
  if (isEdit) {
    loading.value = true
    try {
      const api = tab.value === 'work' ? `/app/work/${editId.value}` : `/app/requirement/${editId.value}`
      const res = await request.get(api)
      if (res.code === 1) {
        const d = res.data
        form.title = d.title || ''; form.summary = d.summary || ''; form.detail = d.detail || ''
        form.categoryId = d.categoryId || null; form.coverUrl = d.coverUrl || ''; form.demoUrl = d.demoUrl || ''
        form.price = d.saleOptions?.[0]?.price || ''
        form.budgetMin = d.budgetMin || ''; form.budgetMax = d.budgetMax || ''
      }
    } catch {} finally { loading.value = false }
  }
})

const submit = async () => {
  if (!form.title.trim()) return ElMessage.warning('标题必填')
  loading.value = true
  try {
    const baseApi = tab.value === 'work' ? '/app/work' : '/app/requirement'
    const api = isEdit ? `${baseApi}/${editId.value}` : baseApi
    const data = tab.value === 'work'
      ? { title: form.title, summary: form.summary, detail: form.detail, categoryId: form.categoryId, coverUrl: form.coverUrl, demoUrl: form.demoUrl }
      : { title: form.title, detail: form.detail, categoryId: form.categoryId, budgetMin: form.budgetMin||null, budgetMax: form.budgetMax||null }
    const res = isEdit ? await request.put(api, data) : await request.post(api, data)
    if (res.code === 1) { ElMessage.success(isEdit?'保存成功':'发布成功'); form.title=''; form.summary=''; form.detail=''; form.price=''; form.budgetMin=''; form.budgetMax='' }
  } finally { loading.value = false }
}
</script>

<style scoped>
.publish-page { /* full width */ }

/* ═══ Compact Head ═══ */
.compact-head {
  display: flex; align-items: flex-end; justify-content: space-between; gap: 18px;
  margin-bottom: 20px; padding: 22px;
  border: 1px solid rgba(254, 215, 170, .76);
  border-radius: 28px;
  background: rgba(255,255,255,.86);
  box-shadow: var(--shadow-card-lg);
  flex-wrap: wrap;
}
.compact-head h2 {
  margin: 0;
  font-family: var(--font-mono);
  font-size: clamp(24px, 4vw, 34px);
  line-height: 1.08; letter-spacing: -.06em;
}
.compact-head p {
  max-width: 680px; margin: 6px 0 0;
  color: var(--text-secondary); line-height: 1.65; font-size: 13px;
}
.eyebrow {
  display: inline-flex; align-items: center; gap: 8px;
  min-height: 28px; padding: 0 12px;
  border: 1px solid var(--border-orange);
  border-radius: 999px;
  color: var(--brand-strong); background: #fffaf5;
  font-size: 12px; font-weight: 900; font-family: var(--font-mono);
}

/* ═══ Choice Layout ═══ */
.publish-studio {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 18px;
  align-items: start;
}
.publish-choices {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}
.publish-choice {
  min-height: 420px;
  padding: 24px;
  border: 1px solid rgba(229,231,235,.96);
  border-radius: 30px;
  background: #fff;
  box-shadow: var(--shadow-card-lg);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition: transform .2s, box-shadow .2s, border-color .2s;
}
.publish-choice:hover {
  transform: translateY(-4px);
  border-color: var(--border-orange);
  box-shadow: var(--shadow-lift);
}
.publish-choice.work {
  border-color: var(--border-orange);
  background:
    linear-gradient(135deg, rgba(255,237,213,.82), rgba(255,255,255,.96)),
    repeating-linear-gradient(0deg, rgba(243,112,33,.04) 0, rgba(243,112,33,.04) 1px, transparent 1px, transparent 14px);
}
.publish-choice.requirement {
  background:
    linear-gradient(135deg, rgba(255,250,244,.94), rgba(255,255,255,.96)),
    radial-gradient(circle at 90% 10%, rgba(245,158,11,.12), transparent 34%);
}
.publish-choice h3 {
  margin: 12px 0 10px;
  font-family: var(--font-mono);
  font-size: clamp(24px, 3.5vw, 34px);
  line-height: 1.08; letter-spacing: -.06em;
}
.publish-choice p {
  color: var(--text-secondary);
  line-height: 1.7; margin: 0; font-size: 13px;
}
.choice-btn { width: 100%; margin-top: 16px; }
.ghost-btn {
  border: 1px solid var(--border-orange) !important;
  color: var(--brand-strong) !important;
  background: #fffaf5 !important;
}

/* Tags */
.tag-row { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 4px; }
.publish-tag {
  display: inline-flex; align-items: center;
  min-height: 24px; padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: 999px;
  font-size: 11px; font-weight: 800; font-family: var(--font-mono);
}
.publish-tag.orange { color: var(--brand-strong); background: var(--brand-soft); border-color: var(--border-orange); }
.publish-tag.green  { color: #9a5a24; background: #fff7ed; border-color: #fed7aa; }
.publish-tag.blue   { color: #9a5a24; background: #fffaf4; border-color: #fcd9bd; }
.publish-tag.yellow { color: #92400e; background: #fffbeb; border-color: #fde68a; }

/* Choice preview */
.choice-preview {
  margin: 16px 0 0;
  padding: 14px;
  border: 1px solid rgba(254, 215, 170, .76);
  border-radius: 20px;
  background: rgba(255, 255, 255, .72);
}
.choice-preview strong { display: block; margin-bottom: 8px; font-family: var(--font-mono); font-size: 12px; letter-spacing: -.03em; }
.choice-preview span {
  display: inline-flex; margin: 0 5px 5px 0;
  padding: 5px 7px; border-radius: 999px;
  color: var(--text-secondary); background: #fff;
  border: 1px solid var(--line); font-size: 11px; font-weight: 800;
}

/* Prepare list */
.prepare-list { margin: 18px 0 0; padding: 0; list-style: none; display: grid; gap: 8px; }
.prepare-list li { display: flex; align-items: center; gap: 10px; color: var(--text-primary); font-size: 13px; }
.check-dot {
  width: 20px; height: 20px; border-radius: 7px;
  display: inline-flex; align-items: center; justify-content: center;
  color: #fff; background: var(--brand);
  font-size: 11px; font-weight: 900; flex-shrink: 0;
}
.blue-dot { background: #9a5a24; }

/* Side notes */
.publish-side { display: grid; gap: 14px; }
.publish-note {
  padding: 18px; border-radius: 24px;
  border: 1px solid var(--line); background: #fff;
  box-shadow: var(--shadow-card);
  transition: transform .18s, box-shadow .18s, border-color .18s;
}
.publish-note:hover { transform: translateY(-2px); border-color: var(--border-orange); box-shadow: var(--shadow-card-lg); }
.publish-note.strong {
  border-color: var(--border-orange);
  background:
    linear-gradient(135deg, rgba(255,247,237,.94), rgba(255,255,255,.98)),
    repeating-linear-gradient(90deg, rgba(243,112,33,.035) 0, rgba(243,112,33,.035) 1px, transparent 1px, transparent 14px);
}
.publish-note h4 { margin: 0 0 8px; font-family: var(--font-mono); letter-spacing: -.03em; font-size: 15px; }
.publish-note p { margin: 0; color: var(--text-secondary); line-height: 1.7; font-size: 13px; }

.publish-flow { display: grid; gap: 8px; counter-reset: step; }
.flow-step {
  position: relative; padding: 11px 11px 11px 38px;
  border-radius: 16px; background: #f9fafb;
  border: 1px solid var(--line); color: var(--text-secondary);
  font-size: 12px; line-height: 1.4;
}
.flow-step::before {
  counter-increment: step; content: counter(step);
  position: absolute; left: 10px; top: 10px;
  width: 20px; height: 20px; display: grid; place-items: center;
  border-radius: 7px; color: #fff; background: var(--brand);
  font-family: var(--font-mono); font-size: 10px; font-weight: 900;
}
.safe-list { margin: 10px 0 0; padding: 0; list-style: none; display: grid; gap: 7px; }
.safe-list li { display: flex; align-items: flex-start; gap: 8px; color: var(--text-secondary); font-size: 12px; line-height: 1.5; }

/* ═══ Form ═══ */
.detail-back {
  margin-bottom: 14px;
  display: inline-flex; align-items: center; gap: 6px;
  min-height: 36px;
  border: 1px solid var(--line);
  border-radius: 14px; padding: 0 14px;
  color: var(--text-secondary); background: #fff;
  font-family: var(--font-mono); font-weight: 800; font-size: 12px;
  cursor: pointer; transition: background .15s, color .15s;
}
.detail-back:hover { background: #fff7ed; color: var(--brand-strong); }
.form-section { max-width: 780px; }
.form-head { margin-bottom: 16px; }
.form-panel {
  padding: 24px;
  border: 1px solid rgba(254, 215, 170, .8);
  border-radius: 28px;
  background: rgba(255,255,255,.86);
  box-shadow: var(--shadow-card-lg);
}
.pub-tabs { margin-bottom: 16px; }
.pub-tabs :deep(.el-tabs__nav-wrap::after) { background-color: var(--border-orange); height: 1px; }
.pub-tabs :deep(.el-tabs__item.is-active) { color: var(--brand-strong); }
.pub-tabs :deep(.el-tabs__item) { font-family: var(--font-mono); }
.cover-preview { width: 120px; height: 80px; border-radius: 12px; margin-top: 8px; }
.field-hint { font-size: 11px; color: var(--text-muted); }
.inline-hint { margin-left: 6px; }
.guide-collapse { margin-bottom: 8px; }
.guide-body { font-size: 12px; line-height: 1.8; color: var(--text-primary); padding: 4px 0; }
.guide-intro { margin: 0 0 8px; color: var(--text-secondary); }
.guide-block { padding: 10px 12px; border-radius: 12px; margin-bottom: 8px; border: 1px solid var(--border-light); border-left: 3px solid; }
.guide-block-blue { background: #faf9f7; border-left-color: var(--text-primary); }
.guide-block-orange { background: #fffaf5; border-left-color: var(--brand); }
.guide-block-green { background: #faf9f7; border-left-color: var(--text-primary); }
.guide-text { margin: 4px 0 0; }
.detail-upload-row { margin-bottom: 4px; }
.budget-input { width: 120px; }

@media (max-width: 1120px) { .publish-studio { grid-template-columns: 1fr; } }
@media (max-width: 780px) { .publish-choices { grid-template-columns: 1fr; } }
</style>
