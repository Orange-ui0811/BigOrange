<template>
  <el-card shadow="never" style="max-width:680px;margin:0 auto;border-radius:12px">
    <h3 style="margin:0 0 20px;font-weight:700">发布{{ tab==='work'?'作品':'需求' }}</h3>
    <el-tabs v-model="tab" style="margin-bottom:16px">
      <el-tab-pane label="作品" name="work" /><el-tab-pane label="需求" name="requirement" />
    </el-tabs>
    <el-form label-position="top">
      <el-form-item label="封面图"><el-upload action="http://127.0.0.1:8080/app/common/upload?dir=cover" :headers="uploadHeaders" :on-success="onCoverSuccess" :show-file-list="false" accept="image/*"><el-button size="small">上传封面</el-button></el-upload><el-image v-if="form.coverUrl" :src="'http://127.0.0.1:8080'+form.coverUrl" style="width:120px;height:80px;border-radius:8px;margin-top:8px" fit="cover"/></el-form-item>
      <el-form-item label="演示链接"><el-input v-model="form.demoUrl" placeholder="https://你的作品演示地址（可选）" /><span style="font-size:11px;color:#909399">买家可在线体验你的作品，但看不到源码</span></el-form-item>
      <el-form-item label="标题" required><el-input v-model="form.title" placeholder="一句话描述你的作品/需求" /></el-form-item>
      <el-form-item label="简介"><el-input v-model="form.summary" type="textarea" :rows="2" placeholder="简短介绍" /></el-form-item>
      <el-form-item label="详情"><el-input v-model="form.detail" type="textarea" :rows="4" placeholder="详细描述" /></el-form-item>
      <el-form-item label="分类"><el-select v-model="form.categoryId" placeholder="选择分类" clearable><el-option v-for="c in cats" :key="c.id" :label="c.name" :value="c.id" /></el-select></el-form-item>
      <template v-if="tab==='work'">
        <el-form-item label="售卖价格"><el-input v-model="form.price" type="number" placeholder="¥ 留空则面议" /></el-form-item>
      </template>
      <template v-else>
        <el-form-item label="预算范围">
          <el-input v-model="form.budgetMin" type="number" placeholder="最低" style="width:120px" /> —
          <el-input v-model="form.budgetMax" type="number" placeholder="最高" style="width:120px" />
        </el-form-item>
      </template>
      <el-button type="primary" @click="submit" :loading="loading" style="background:#e65100;border-color:#e65100">发布</el-button>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'

const tab = ref('work')
const cats = ref([])
const loading = ref(false)
const form = reactive({ title: '', summary: '', detail: '', categoryId: null, price: '', budgetMin: '', budgetMax: '', coverUrl: '', demoUrl: '' })
const uploadHeaders = { 'App-Token': sessionStorage.getItem('appToken') || '' }
const onCoverSuccess = (res) => { if (res.code === 1) form.coverUrl = res.data }

const loadCats = async () => {
  try { const res = await request.get('/app/category?type=' + (tab.value==='work'?'WORK':'REQUIREMENT')); if (res.code===1) cats.value = res.data } catch {}
}
watch(tab, () => { form.categoryId = null; loadCats() })
onMounted(loadCats)

const submit = async () => {
  if (!form.title.trim()) return ElMessage.warning('标题必填')
  loading.value = true
  try {
    const api = tab.value === 'work' ? '/app/work' : '/app/requirement'
    const data = tab.value === 'work'
      ? { title: form.title, summary: form.summary, detail: form.detail, categoryId: form.categoryId, coverUrl: form.coverUrl, demoUrl: form.demoUrl }
      : { title: form.title, detail: form.detail, categoryId: form.categoryId, budgetMin: form.budgetMin||null, budgetMax: form.budgetMax||null }
    const res = await request.post(api, data)
    if (res.code === 1) { ElMessage.success('发布成功'); form.title=''; form.summary=''; form.detail=''; form.price=''; form.budgetMin=''; form.budgetMax='' }
  } finally { loading.value = false }
}
</script>
