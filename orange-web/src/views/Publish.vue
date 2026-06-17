<template>
  <el-card shadow="never" style="max-width:680px;margin:0 auto;border-radius:12px">
    <h3 style="margin:0 0 20px;font-weight:700">发布{{ tab==='work'?'作品':'需求' }}</h3>
    <el-tabs v-model="tab" style="margin-bottom:16px">
      <el-tab-pane label="作品" name="work" /><el-tab-pane label="需求" name="requirement" />
    </el-tabs>
    <el-form label-position="top">
      <el-form-item label="封面图"><el-upload :action="API+'/app/common/upload?dir=cover'" :headers="uploadHeaders" :on-success="onCoverSuccess" :show-file-list="false" accept="image/*"><el-button size="small">上传封面</el-button></el-upload><el-image v-if="form.coverUrl" :src="API+form.coverUrl" style="width:120px;height:80px;border-radius:8px;margin-top:8px" fit="cover"/></el-form-item>
      <el-form-item label="演示链接">
        <template v-if="tab==='work'">
          <el-collapse style="margin-bottom:8px">
            <el-collapse-item title="📖 如何生成演示链接？（点击展开）" name="guide">
              <div style="font-size:12px;line-height:1.9;color:#2c2c2c;padding:4px 0">
                <p style="margin:0 0 8px;color:#909399">把你的作品部署到免费云端，粘贴链接即可。源码始终在你手里，买家只能体验产品。</p>

                <div style="background:#f0f7ff;padding:10px 12px;border-radius:8px;margin-bottom:10px;border-left:3px solid #1565c0">
                  <b style="color:#1565c0">🟦 前端项目（HTML/Vue/React）</b>
                  <p style="margin:4px 0 0">
                    👉 <a href="https://vercel.com" target="_blank">vercel.com</a> → Import Git Repository → 选你的 GitHub 仓库 → Deploy<br/>
                    👉 <a href="https://www.netlify.com" target="_blank">netlify.com</a> → Add new site → Import an existing project → GitHub → Deploy<br/>
                    1-2 分钟上线，获得 <code>https://xxx.vercel.app</code> 链接
                  </p>
                </div>

                <div style="background:#fff8e1;padding:10px 12px;border-radius:8px;margin-bottom:10px;border-left:3px solid #e65100">
                  <b style="color:#e65100">🟧 Python / Streamlit</b>
                  <p style="margin:4px 0 0">
                    ① 先把代码推到 GitHub<br/>
                    ② 👉 <a href="https://share.streamlit.io" target="_blank">share.streamlit.io</a> → Sign in with GitHub → New app<br/>
                    ③ 填：Repository（你的仓库）、Branch（main）、Main file path（如 <code>src/webui.py</code>）<br/>
                    ④ 点 Deploy → 2 分钟后获得 <code>https://xxx.streamlit.app</code> 链接
                  </p>
                </div>

                <div style="background:#e8f5e9;padding:10px 12px;border-radius:8px;margin-bottom:4px;border-left:3px solid #2e7d32">
                  <b style="color:#2e7d32">🟩 后端 API / 全栈项目</b>
                  <p style="margin:4px 0 0">
                    👉 <a href="https://railway.app" target="_blank">railway.app</a> → New Project → Deploy from GitHub → 自动识别 Dockerfile<br/>
                    👉 <a href="https://render.com" target="_blank">render.com</a> → New Web Service → 连接 GitHub 仓库 → Deploy<br/>
                    免费额度足够演示使用
                  </p>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
        </template>
        <el-input v-model="form.demoUrl" placeholder="https://你的作品演示地址（可选）" />
        <span style="font-size:11px;color:#909399">粘贴演示链接后，买家可在线体验你的作品，源码不可见</span>
      </el-form-item>
      <el-form-item label="标题" required><el-input v-model="form.title" placeholder="一句话描述你的作品/需求" /></el-form-item>
      <el-form-item label="简介"><el-input v-model="form.summary" type="textarea" :rows="2" placeholder="简短介绍" /></el-form-item>
      <el-form-item label="详情"><el-input v-model="form.detail" type="textarea" :rows="4" placeholder="详细描述" /></el-form-item>
      <el-form-item label="分类"><el-select v-model="form.categoryId" placeholder="选择分类" clearable><el-option v-for="c in cats" :key="c.id" :label="c.name" :value="c.id" /></el-select></el-form-item>
      <template v-if="tab==='work' && tradeEnabled">
        <el-form-item label="售卖价格"><el-input v-model="form.price" type="number" placeholder="¥ 留空则面议" /></el-form-item>
      </template>
      <template v-else>
        <el-form-item label="预算范围">
          <el-input v-model="form.budgetMin" type="number" placeholder="最低" style="width:120px" /> —
          <el-input v-model="form.budgetMax" type="number" placeholder="最高" style="width:120px" />
        </el-form-item>
      </template>
      <el-button type="primary" @click="submit" :loading="loading" style="background:#e65100;border-color:#e65100">{{ isEdit?'保存修改':'发布' }}</el-button>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { tradeEnabled } from '../stores/tradeMode'

const API = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
const route = useRoute()
const isEdit = !!route.params.id
const tab = ref(isEdit ? (route.params.type === 'work' ? 'work' : 'requirement') : 'work')
const cats = ref([])
const editId = ref(isEdit ? route.params.id : null)
const loading = ref(false)
const form = reactive({ title: '', summary: '', detail: '', categoryId: null, price: '', budgetMin: '', budgetMax: '', coverUrl: '', demoUrl: '' })
const uploadHeaders = { 'App-Token': sessionStorage.getItem('appToken') || '' }
const onCoverSuccess = (res) => { if (res.code === 1) form.coverUrl = res.data }

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
