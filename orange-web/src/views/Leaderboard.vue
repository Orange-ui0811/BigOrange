<template>
  <div>
    <!-- 筛选栏 -->
    <div style="display:flex;align-items:center;gap:12px;margin-bottom:20px;flex-wrap:wrap">
      <span style="font-size:13px;color:#909399;font-weight:600">数据源</span>
      <el-radio-group v-model="source" size="default" @change="onFilterChange">
        <el-radio-button value="local">本网站</el-radio-button>
        <el-radio-button value="github">GitHub</el-radio-button>
      </el-radio-group>

      <span style="font-size:13px;color:#909399;font-weight:600;margin-left:8px">时间</span>
      <el-radio-group v-model="period" size="default" @change="onFilterChange">
        <el-radio-button value="all">全时段</el-radio-button>
        <el-radio-button value="week">本周</el-radio-button>
      </el-radio-group>

      <span style="font-size:13px;color:#909399;font-weight:600;margin-left:8px">分类</span>
      <el-select v-model="category" placeholder="全部分类" clearable size="default" style="width:180px" @change="onFilterChange">
        <el-option label="全部分类" :value="null" />
        <el-option-group v-for="g in categoryGroups" :key="g.label" :label="g.label">
          <el-option v-for="c in g.children" :key="c.id" :label="c.name" :value="source==='github'?c.topicName:c.id" />
        </el-option-group>
      </el-select>
    </div>

    <!-- 子 Tab -->
    <el-tabs v-model="type" style="margin-bottom:16px" @tab-change="load">
      <el-tab-pane label="作品" name="works" v-if="source==='local'" />
      <el-tab-pane label="仓库" name="repos" v-if="source==='github'" />
      <el-tab-pane label="创作者" name="creators" v-if="source==='local'" />
    </el-tabs>

    <!-- 加载态 -->
    <el-row :gutter="16" v-if="loading">
      <el-col :span="6" v-for="i in 8" :key="i" style="margin-bottom:16px">
        <el-skeleton :loading="true" animated style="padding:16px"><template #template><el-skeleton-item variant="text" style="width:80%" /><el-skeleton-item variant="text" style="width:50%" /><el-skeleton-item variant="text" style="width:30%" /></template></el-skeleton>
      </el-col>
    </el-row>

    <!-- 数据列表 -->
    <el-row :gutter="16" v-else>
      <el-col :span="6" v-for="(item, idx) in list" :key="item.id||idx" style="margin-bottom:16px">
        <el-card shadow="hover" :body-style="{padding:'14px'}" style="border-radius:12px;cursor:pointer;transition:transform .15s;border:1px solid #f0ece6;height:100%"
          @click="onClick(item)">

          <!-- 排名徽章 -->
          <div style="position:absolute;top:-6px;left:-6px;width:28px;height:28px;border-radius:50%;background:linear-gradient(135deg,#e65100,#ff9800);color:#fff;font-size:12px;font-weight:700;display:flex;align-items:center;justify-content:center;z-index:2">
            {{ (page-1)*pageSize + idx + 1 }}
          </div>

          <!-- 本网站作品 -->
          <template v-if="source==='local' && type==='works'">
            <div style="font-weight:600;font-size:15px;color:#2c2c2c;margin-bottom:8px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ item.title || item.name }}</div>
            <div style="font-size:12px;color:#909399;margin-bottom:8px;display:flex;align-items:center;gap:8px">
              <span>👤 {{ item.ownerName || item.username }}</span>
              <span v-if="item.category_name" style="color:#e65100">🏷 {{ item.category_name }}</span>
            </div>
            <div style="display:flex;align-items:center;justify-content:space-between">
              <span style="font-size:13px;color:#e65100;font-weight:700">⭐ {{ item.favoriteCount || item.favorite_count || 0 }}</span>
            </div>
          </template>

          <!-- 本网站创作者 -->
          <template v-if="source==='local' && type==='creators'">
            <div style="display:flex;align-items:center;gap:10px;margin-bottom:8px">
              <el-avatar :size="36" :src="item.avatarUrl||item.avatar_url" style="background:#e65100;font-size:14px">{{ (item.username||'?')[0] }}</el-avatar>
              <div style="font-weight:600;font-size:15px;color:#2c2c2c">{{ item.username }}</div>
            </div>
            <div style="font-size:12px;color:#909399;margin-bottom:8px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ item.bio || '' }}</div>
            <div style="display:flex;gap:12px;font-size:13px">
              <span>⭐ {{ item.totalFavorites || item.total_favorites || 0 }}</span>
              <span>📦 {{ item.workCount || item.work_count || 0 }}作品</span>
            </div>
          </template>

          <!-- GitHub 仓库 -->
          <template v-if="source==='github'">
            <div style="font-weight:600;font-size:15px;color:#2c2c2c;margin-bottom:6px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ item.name }}</div>
            <div style="font-size:11px;color:#6b6b6b;margin-bottom:8px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ item.fullName }}</div>
            <div style="font-size:12px;color:#909399;margin-bottom:8px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;line-height:1.4;min-height:34px">
              {{ item.description || '暂无描述' }}
            </div>
            <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:4px">
              <span style="font-size:13px;color:#e65100;font-weight:700">⭐ {{ item.stars }}</span>
              <span v-if="item.language" style="font-size:11px;color:#fff;background:#6b6b6b;padding:2px 8px;border-radius:10px">{{ item.language }}</span>
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>

    <!-- 空态 -->
    <el-empty v-if="!loading && !list.length" :description="emptyText" />

    <!-- 分页 -->
    <div v-if="total > pageSize" style="display:flex;justify-content:center;margin-top:20px">
      <el-pagination background layout="total, prev, pager, next" :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="load" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import request from '../api/request'

const router = useRouter()
const source = ref('local')
const period = ref('all')
const category = ref(null)
const type = ref('works')
const list = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const categories = ref([])

// 14 个 Topic 按四大领域分组
const categoryGroups = [
  { label: '创意技术', children: [] },
  { label: 'AI 智能', children: [] },
  { label: '自托管', children: [] },
  { label: '硬件/IoT', children: [] }
]
const groupMap = {
  '创意技术': ['creative-coding','game-development','WebGL','Canvas','Web-Audio'],
  'AI 智能': ['ai-agents','RAG'],
  '自托管': ['self-hosted','home-assistant','awesome-self-hosted'],
  '硬件/IoT': ['ESP32','raspberry-pi','home-automation','3d-printing']
}

const emptyText = computed(() => {
  if (source.value === 'github') return 'GitHub 榜单数据暂不可用'
  return '暂无数据'
})

const loadCategories = async () => {
  try {
    const r = await request.get('/app/category', { params: { type: 'WORK' } })
    if (r.code === 1) {
      const all = r.data || []
      const active = all.filter(c => c.status === 'ACTIVE' || c.status === '1')
      categories.value = active
      // 分组
      categoryGroups.forEach(g => { g.children = [] })
      active.forEach(c => {
        const tn = c.topicName || ''
        for (const [gname, topics] of Object.entries(groupMap)) {
          if (topics.includes(tn)) {
            const grp = categoryGroups.find(g => g.label === gname)
            if (grp) grp.children.push(c)
            break
          }
        }
      })
    }
  } catch {}
}

const load = async () => {
  loading.value = true
  try {
    let endpoint, params = { page: page.value, size: pageSize.value }

    if (source.value === 'local') {
      if (type.value === 'works') {
        endpoint = '/app/leaderboard/works'
        if (category.value) params.categoryId = category.value
        params.period = period.value
      } else {
        endpoint = '/app/leaderboard/creators'
        params.period = period.value
      }
    } else {
      endpoint = '/app/leaderboard/github'
      if (category.value) params.topicName = category.value
      params.period = period.value
    }

    const r = await request.get(endpoint, { params })
    if (r.code === 1 && r.data) {
      list.value = r.data.records || []
      total.value = r.data.total || 0
    } else {
      list.value = []
      total.value = 0
    }
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const onFilterChange = () => {
  page.value = 1
  if (source.value === 'github') type.value = 'repos'
  else if (type.value === 'repos') type.value = 'works'
  load()
}

const onClick = (item) => {
  if (source.value === 'local' && type.value === 'works') {
    router.push('/work/' + item.id)
  } else if (source.value === 'local' && type.value === 'creators') {
    router.push('/profile/' + item.id)
  } else if (source.value === 'github') {
    window.open(item.htmlUrl, '_blank')
  }
}

onMounted(async () => {
  await loadCategories()
  load()
})
</script>
