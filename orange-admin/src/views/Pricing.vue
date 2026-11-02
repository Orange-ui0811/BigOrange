<template>
  <div class="pricing-page">
    <header class="page-header">
      <div>
        <h3>配额定价管理</h3>
        <p>统一管理 AI 功能消费和内容贡献奖励，保存后立即生效。</p>
      </div>
      <el-button :loading="loading" @click="loadData">刷新</el-button>
    </header>

    <section class="pricing-section">
      <div class="section-heading">
        <div>
          <h4>AI 消费定价</h4>
          <p>用户每次使用对应 AI 功能时扣除的 segments，最低为 1。</p>
        </div>
        <el-tag type="warning" effect="plain">消费</el-tag>
      </div>
      <pricing-table
        :rows="aiPricing"
        :edit-map="editMap"
        :loading="loading"
        @update-cost="handleEdit"
        @save="handleSave"
      />
    </section>

    <section class="pricing-section">
      <div class="section-heading">
        <div>
          <h4>奖励定价</h4>
          <p>审核通过后实际发放的 segments。设置为 0 可暂停该项奖励，不影响审核流程。</p>
        </div>
        <el-tag type="success" effect="plain">奖励</el-tag>
      </div>
      <pricing-table
        :rows="rewardPricing"
        :edit-map="editMap"
        :loading="loading"
        reward
        @update-cost="handleEdit"
        @save="handleSave"
      />
    </section>

    <section class="pricing-section">
      <div class="section-heading">
        <div>
          <h4>变更记录</h4>
          <p>记录管理员对消费和奖励配额的每次修改。</p>
        </div>
      </div>
      <el-table :data="changeLogs" border size="small" empty-text="暂无变更记录">
        <el-table-column prop="serviceKey" label="定价标识" min-width="190">
          <template #default="{ row }">
            <el-tag size="small" :type="isReward(row.serviceKey) ? 'success' : 'info'">
              {{ row.serviceKey }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="oldCost" label="修改前" width="100" align="center" />
        <el-table-column prop="newCost" label="修改后" width="100" align="center">
          <template #default="{ row }">
            <strong class="cost-value">{{ row.newCost }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="adminId" label="管理员 ID" width="110" align="center" />
        <el-table-column prop="changedAt" label="变更时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.changedAt) }}</template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue'
import { ElButton, ElInputNumber, ElMessage, ElMessageBox, ElTable, ElTableColumn, ElTag } from 'element-plus'
import request from '../api/request'

const PricingTable = defineComponent({
  name: 'PricingTable',
  props: {
    rows: { type: Array, required: true },
    editMap: { type: Object, required: true },
    loading: Boolean,
    reward: Boolean
  },
  emits: ['save', 'update-cost'],
  setup(props, { emit }) {
    return () => h(ElTable, {
      data: props.rows,
      border: true,
      loading: props.loading,
      emptyText: '暂无定价项'
    }, {
      default: () => [
        h(ElTableColumn, { prop: 'serviceName', label: '项目名称', minWidth: 180 }),
        h(ElTableColumn, { prop: 'serviceKey', label: '定价标识', minWidth: 210 }, {
          default: ({ row }) => h(ElTag, { size: 'small', type: props.reward ? 'success' : 'info' }, () => row.serviceKey)
        }),
        h(ElTableColumn, { prop: 'segmentCost', label: '当前配额', width: 120, align: 'center' }, {
          default: ({ row }) => h('strong', { class: 'cost-value' }, String(row.segmentCost))
        }),
        h(ElTableColumn, { label: '新配额', width: 180, align: 'center' }, {
          default: ({ row }) => h(ElInputNumber, {
            modelValue: props.editMap[row.serviceKey],
            'onUpdate:modelValue': value => emit('update-cost', row.serviceKey, value),
            min: props.reward ? 0 : 1,
            max: 10000,
            size: 'small',
            controlsPosition: 'right'
          })
        }),
        h(ElTableColumn, { label: '操作', width: 100, align: 'center' }, {
          default: ({ row }) => h(ElButton, {
            type: 'primary',
            size: 'small',
            disabled: props.editMap[row.serviceKey] === row.segmentCost,
            onClick: () => emit('save', row)
          }, () => '保存')
        })
      ]
    })
  }
})

const loading = ref(false)
const pricingList = ref([])
const changeLogs = ref([])
const editMap = reactive({})

const isReward = key => key?.startsWith('reward:')
const aiPricing = computed(() => pricingList.value.filter(item => !isReward(item.serviceKey)))
const rewardPricing = computed(() => pricingList.value.filter(item => isReward(item.serviceKey)))

const handleEdit = (serviceKey, value) => {
  editMap[serviceKey] = value
}

const loadData = async () => {
  loading.value = true
  try {
    const response = await request.get('/admin/pricing')
    pricingList.value = response.data?.pricing || []
    changeLogs.value = response.data?.changeLogs || []
    for (const item of pricingList.value) editMap[item.serviceKey] = item.segmentCost
  } catch {
    ElMessage.error('加载定价失败')
  } finally {
    loading.value = false
  }
}

const handleSave = async row => {
  const newCost = editMap[row.serviceKey]
  const minimum = isReward(row.serviceKey) ? 0 : 1
  if (!Number.isInteger(newCost) || newCost < minimum || newCost > 10000) {
    ElMessage.warning(`${isReward(row.serviceKey) ? '奖励' : '消费'}配额必须在 ${minimum}-10000 之间`)
    return
  }

  try {
    await ElMessageBox.confirm(
      `将“${row.serviceName}”从 ${row.segmentCost} 调整为 ${newCost} segments，确认保存？`,
      '确认修改',
      { type: 'warning', confirmButtonText: '确认保存', cancelButtonText: '取消' }
    )
    const response = await request.post('/admin/pricing/save', {
      serviceKey: row.serviceKey,
      serviceName: row.serviceName,
      newCost
    })
    if (response.code !== 1) return
    ElMessage.success('定价已更新并立即生效')
    await loadData()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.msg || '保存失败')
    }
  }
}

const formatTime = value => value ? value.replace('T', ' ').substring(0, 19) : ''

onMounted(loadData)
</script>

<style scoped>
.pricing-page { display: grid; gap: 18px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.page-header h3, .section-heading h4 { margin: 0; color: #202124; }
.page-header h3 { font-size: 22px; }
.page-header p, .section-heading p { margin: 6px 0 0; color: #73777f; font-size: 13px; line-height: 1.6; }
.pricing-section { padding: 20px; background: #fff; border: 1px solid #e7e9ee; border-radius: 8px; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.section-heading h4 { font-size: 16px; }
.cost-value { color: #d45113; font-size: 16px; }
@media (max-width: 720px) {
  .page-header { align-items: center; }
  .pricing-section { padding: 14px; overflow-x: auto; }
}
</style>
