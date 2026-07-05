<template>
  <div>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
      <h3 style="margin:0">充值码管理</h3>
    </div>

    <!-- 生成 -->
    <el-card shadow="never" style="margin-bottom:16px">
      <template #header><span style="font-weight:600">生成充值码</span></template>
      <div style="display:flex;gap:12px;align-items:flex-end">
        <div>
          <div style="font-size:12px;color:#909399;margin-bottom:4px">数量</div>
          <el-input-number v-model="genCount" :min="1" :max="500" size="small" />
        </div>
        <div>
          <div style="font-size:12px;color:#909399;margin-bottom:4px">每个配额（segments）</div>
          <el-input-number v-model="genQuota" :min="1" :max="1000000" :step="100" size="small" />
        </div>
        <el-button type="primary" @click="doGenerate" :loading="genLoading" style="background:#e65100;border-color:#e65100">生成</el-button>
      </div>
      <div v-if="newCodes.length" style="margin-top:12px;background:#fafafa;padding:12px;border-radius:8px;max-height:200px;overflow:auto">
        <div v-for="(c,i) in newCodes" :key="i" style="font-family:monospace;font-size:13px;margin:2px 0;cursor:pointer" @click="copyCode(c)">{{ c }}</div>
        <p style="color:#909399;font-size:12px;margin-top:8px">点击码可复制 · 共 {{ newCodes.length }} 个</p>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <template #header><span style="font-weight:600">充值码列表</span></template>
      <el-table :data="list" v-loading="loading" empty-text="暂无记录" size="small">
        <el-table-column prop="code" label="充值码" width="400" show-overflow-tooltip>
          <template #default="{row}">
            <span style="font-family:monospace;font-size:12px">{{ row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="quota" label="配额" width="80" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{row}">
            <el-tag size="small" :type="row.status===1?'success':row.status===3?'info':'danger'">
              {{ row.status===1?'未使用':row.status===3?'已使用':'已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="使用人" width="80">
          <template #default="{row}">
            <span v-if="row.usedBy">#{{ row.usedBy }}</span>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="usedTime" label="使用时间" width="160" />
        <el-table-column prop="createTime" label="生成时间" width="160" />
      </el-table>
      <el-pagination
        v-if="total > size"
        style="margin-top:12px;justify-content:center"
        layout="prev, next"
        :total="total"
        :page-size="size"
        v-model:current-page="page"
        @current-change="loadList"
        small
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'

const genCount = ref(10); const genQuota = ref(500); const genLoading = ref(false)
const newCodes = ref([])
const list = ref([]); const loading = ref(false)
const page = ref(1); const size = 50; const total = ref(0)

const doGenerate = async () => {
  genLoading.value = true
  try {
    const r = await request.post('/admin/redemption/generate', { count: genCount.value, quota: genQuota.value })
    newCodes.value = r.data || []
    ElMessage.success(`已生成 ${newCodes.value.length} 个充值码`)
    loadList()
  } catch { ElMessage.error('生成失败') }
  finally { genLoading.value = false }
}

const loadList = async () => {
  loading.value = true
  try {
    const r = await request.get('/admin/redemption/list', { params: { page: page.value, size } })
    list.value = r.data?.records || []; total.value = r.data?.total || 0
  } finally { loading.value = false }
}

const copyCode = (code) => {
  navigator.clipboard.writeText(code).then(() => ElMessage.success('已复制'))
}

onMounted(() => loadList())
</script>
