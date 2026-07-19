<template>
  <div>
    <h3 style="margin:0 0 4px;font-weight:700">AI 消费定价</h3>
    <p style="color:#909399;font-size:13px;margin:0 0 16px">
      管控用户端所有 AI 服务消费金额，修改后即时生效
    </p>

    <!-- 定价表格 -->
    <el-table :data="pricingList" border stripe v-loading="loading" style="margin-bottom:24px">
      <el-table-column prop="serviceName" label="服务名称" min-width="140" />
      <el-table-column prop="serviceKey" label="标识" min-width="120">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.serviceKey }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="segmentCost" label="当前定价 (segments)" width="160" align="center">
        <template #default="{ row }">
          <span style="font-weight:700;font-size:16px;color:#e65100">{{ row.segmentCost }}</span>
        </template>
      </el-table-column>
      <el-table-column label="新定价" width="180" align="center">
        <template #default="{ row }">
          <el-input-number
            v-model="editMap[row.serviceKey]"
            :min="1"
            :max="10000"
            size="small"
            controls-position="right"
            style="width:120px"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center">
        <template #default="{ row }">
          <el-button
            type="primary"
            size="small"
            :disabled="!isChanged(row)"
            @click="handleSave(row)"
          >保存</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 变更日志 -->
    <h4 style="font-weight:600;margin-bottom:12px">变更记录</h4>
    <el-table :data="changeLogs" border stripe size="small" empty-text="暂无变更记录">
      <el-table-column prop="serviceKey" label="服务标识" width="130">
        <template #default="{ row }">
          <el-tag size="small">{{ row.serviceKey }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="oldCost" label="旧定价" width="80" align="center" />
      <el-table-column prop="newCost" label="新定价" width="80" align="center">
        <template #default="{ row }">
          <span style="font-weight:700;color:#e65100">{{ row.newCost }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="adminId" label="操作人 ID" width="100" align="center" />
      <el-table-column prop="changedAt" label="变更时间" min-width="160">
        <template #default="{ row }">
          {{ formatTime(row.changedAt) }}
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'

const loading = ref(false)
const pricingList = ref([])
const changeLogs = ref([])
const editMap = reactive({})

const loadData = async () => {
  loading.value = true
  try {
    const r = await request.get('/admin/pricing')
    if (r.data) {
      pricingList.value = r.data.pricing || []
      changeLogs.value = r.data.changeLogs || []
      // 初始化编辑值 = 当前值
      for (const p of pricingList.value) {
        editMap[p.serviceKey] = p.segmentCost
      }
    }
  } catch (e) {
    ElMessage.error('加载定价失败')
  } finally { loading.value = false }
}

const isChanged = (row) => {
  return editMap[row.serviceKey] !== row.segmentCost
}

const handleSave = async (row) => {
  const newCost = editMap[row.serviceKey]
  if (newCost < 1 || newCost > 10000) {
    ElMessage.warning('定价范围：1 ~ 10000 segments')
    return
  }

  try {
    await ElMessageBox.confirm(
      `"${row.serviceName}"：${row.segmentCost} → ${newCost}，确认修改？`,
      '二次确认',
      { confirmButtonText: '确认修改', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }

  try {
    const response = await request.post('/admin/pricing/save', {
      serviceKey: row.serviceKey,
      newCost: newCost,
      serviceName: row.serviceName
    })
    if (response.code !== 1) return
    ElMessage.success('定价已更新，即时生效')
    await loadData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.msg || '保存失败')
  }
}

const formatTime = (t) => {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 19)
}

onMounted(() => loadData())
</script>
