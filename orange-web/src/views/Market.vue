<template>
  <div>
    <!-- Tab + 筛选 -->
    <div style="display:flex;align-items:center;gap:12px;margin-bottom:20px">
      <el-radio-group v-model="tab" size="default">
        <el-radio-button value="works">作品</el-radio-button>
        <el-radio-button value="reqs">需求</el-radio-button>
      </el-radio-group>
      <el-select v-model="categoryId" placeholder="全部分类" clearable size="default" style="width:160px">
        <el-option label="全部分类" :value="null" />
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索..." size="default" style="width:200px" clearable @keyup.enter="load" />
    </div>
    <!-- 卡片网格 -->
    <el-row :gutter="16" v-loading="loading">
      <el-col :span="8" v-for="item in list" :key="item.id" style="margin-bottom:16px">
        <el-card shadow="hover" :body-style="{padding:'12px'}" style="border-radius:12px;cursor:pointer;transition:transform .15s;border:1px solid #f0ece6"
          @click="$router.push(tab==='works'?'/work/'+item.id:'/requirement/'+item.id)">
          <div style="display:flex;gap:12px">
            <img v-if="item.coverUrl" :src="API+item.coverUrl" style="width:80px;height:60px;border-radius:8px;object-fit:cover;flex-shrink:0" />
            <div v-else style="width:80px;height:60px;border-radius:8px;background:#f5f2ee;flex-shrink:0;display:flex;align-items:center;justify-content:center;color:#c0c4cc;font-size:20px">📦</div>
            <div style="flex:1;min-width:0">
          <div style="font-weight:600;font-size:15px;color:#2c2c2c;margin-bottom:6px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ item.title }}</div>
          <div style="font-size:12px;color:#909399;margin-bottom:8px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ item.summary || item.detail || '暂无简介' }}</div>
          <div style="display:flex;align-items:center;justify-content:space-between">
            <span style="font-size:12px;color:#6b6b6b">by {{ item.ownerName || item.publisherName || '#'+item.ownerId }}</span>
            <span v-if="tab==='works'" style="font-size:12px;color:#909399">
              <span v-if="item.favoriteCount" style="margin-right:8px">⭐{{item.favoriteCount}}</span>
              <span v-if="item.commentCount">💬{{item.commentCount}}</span>
            </span>
            <span v-if="tab==='works' && tradeEnabled" style="font-size:14px;font-weight:700;color:#e65100">¥{{ item.totalAmount || '待定' }}</span>
            <span v-else style="font-size:12px;color:#6b6b6b">¥{{ item.budgetMin }}-{{ item.budgetMax }}</span>
            </div>
          </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="!loading && !list.length" description="暂无内容" />
    <el-pagination v-if="total>pageSize" style="margin-top:20px;justify-content:center" background layout="prev,pager,next" :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="load" />
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import request from '../api/request'
import { tradeEnabled } from '../stores/tradeMode'

const API = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
const tab = ref('works')
const list = ref([])
const categories = ref([])
const categoryId = ref(null)
const keyword = ref('')
const page = ref(1)
const total = ref(0)
const pageSize = ref(12)
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    const api = tab.value === 'works' ? '/app/work/public' : '/app/requirement/public'
    const params = { page: page.value, size: pageSize.value }
    if (categoryId.value) params.categoryId = categoryId.value
    if (keyword.value) params.keyword = keyword.value
    const res = await request.get(api, { params })
    if (res.code === 1) { list.value = res.data?.records || []; total.value = res.data?.total || 0 }
  } finally { loading.value = false }
}

onMounted(async () => {
  try { const catRes = await request.get('/app/category?type=WORK'); if (catRes.code === 1) categories.value = catRes.data } catch {}
  load()
})

watch(tab, () => { page.value = 1; load() })
watch([categoryId, keyword], () => { page.value = 1; load() })
</script>
