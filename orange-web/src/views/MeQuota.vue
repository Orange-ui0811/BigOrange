<template>
  <div>
    <h3 class="page-title">我的配额</h3>
    <p class="page-subtitle">管理 <PixelOrangeSlice :size="14" style="display:inline-block;vertical-align:middle" /> 余额，用于 AI 趋势分析和方案设计</p>

    <!-- 余额卡片 -->
    <el-row :gutter="16" class="balance-row">
      <el-col :span="8">
        <el-card shadow="never" class="stat-card">
          <div class="balance-num">
            <PixelOrangeSlice class="segment-coin" />
            {{ balance }}
          </div>
          <div class="balance-label">
            <PixelOrangeSlice :size="16" />
            segments
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="stat-card">
          <div class="balance-yuan">&#8776; &yen;{{ (balance / 100).toFixed(2) }}</div>
          <div class="balance-note">100 <PixelOrangeSlice :size="12" style="display:inline-block;vertical-align:middle" /> = 1 元</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 兑换 + 充值 -->
    <el-row :gutter="16" class="action-row">
      <el-col :span="12">
        <el-card shadow="never" class="action-card">
          <template #header><span class="card-header-title">兑换充值码</span></template>
          <div class="redeem-row">
            <el-input v-model="code" placeholder="输入 48 位充值码" clearable class="redeem-input" />
            <el-button type="primary" @click="doRedeem" :loading="redeeming" class="btn-primary">兑换</el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="action-card">
          <template #header><span class="card-header-title">在线充值</span></template>
          <el-radio-group v-model="payMethod" size="small" class="pay-method-group">
            <el-radio-button value="wxpay">微信</el-radio-button>
            <el-radio-button value="alipay">支付宝</el-radio-button>
          </el-radio-group>
          <el-select v-model="payAmount" size="small" class="pay-select">
            <el-option :value="1000" label="¥10 = 1000 🍊" />
            <el-option :value="3000" label="¥30 = 3000 🍊" />
            <el-option :value="5000" label="¥50 = 5000 🍊" />
            <el-option :value="10000" label="¥100 = 10000 🍊" />
          </el-select>
          <el-button @click="doPay" type="primary" :loading="paying" class="btn-primary btn-block">前往支付</el-button>
        </el-card>
      </el-col>
    </el-row>

    <!-- 消费记录 -->
    <el-card shadow="never" class="log-card">
      <template #header><span class="card-header-title">消费记录</span></template>
      <el-table :data="logs" v-loading="logLoading" empty-text="暂无记录" size="small" class="log-table">
        <el-table-column prop="createTime" label="时间" width="160" />
        <el-table-column prop="type" label="类型" width="80">
          <template #default="{row}">
            <el-tag size="small" :type="row.type==='RECHARGE'?'success':row.type==='CONSUME'?'warning':'info'" class="log-type-tag">
              {{ row.type==='RECHARGE'?'充值':row.type==='CONSUME'?'消费':row.type==='REFUND'?'退款':row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变动" width="90">
          <template #default="{row}">
            <span :class="row.amount>0?'amount-positive':'amount-negative'">{{ row.amount>0?'+':'' }}{{ row.amount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="余额" width="90">
          <template #default="{row}">
            <span class="balance-cell">
              <PixelOrangeSlice :size="14" />
              {{ row.balance }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import PixelOrangeSlice from '../components/PixelOrangeSlice.vue'
import { getBalance, redeemCode, getLog, createEpayOrder } from '../api/quota'

const balance = ref(0)
const code = ref('')
const redeeming = ref(false)
const paying = ref(false)
const payMethod = ref('wxpay')
const payAmount = ref(1000)
const logs = ref([])
const logLoading = ref(false)

const loadBalance = async () => {
  try { const r = await getBalance(); balance.value = r.data?.balance || 0 } catch {}
}
const loadLogs = async () => {
  logLoading.value = true
  try { const r = await getLog(20); logs.value = r.data || [] } finally { logLoading.value = false }
}

const doRedeem = async () => {
  if (!code.value) { ElMessage.warning('请输入充值码'); return }
  redeeming.value = true
  try {
    const r = await redeemCode(code.value)
    balance.value = r.data
    ElMessage.success('兑换成功')
    code.value = ''
    loadLogs()
  } catch (e) { ElMessage.error(e?.response?.data?.msg || '兑换失败') }
  finally { redeeming.value = false }
}

const doPay = async () => {
  paying.value = true
  try {
    const r = await createEpayOrder(payAmount.value, payMethod.value)
    if (r.data?.payUrl) {
      window.open(r.data.payUrl, '_blank')
      ElMessage.info('已打开支付页面，支付完成后刷新查看余额')
    } else {
      ElMessage.error('创建支付失败')
    }
  } catch (e) { ElMessage.error('支付服务暂不可用') }
  finally { paying.value = false }
}

onMounted(() => { loadBalance(); loadLogs() })
</script>
<style scoped>
.page-title {
  margin: 0 0 4px;
  font-family: "Courier New","SimHei",monospace;
  font-size: 16px;
  color: #3a302a;
  font-weight: 700;
}
.page-subtitle {
  color: #9f9890;
  font-size: 13px;
  margin: 0 0 16px;
  font-family: "Microsoft YaHei","PingFang SC",sans-serif;
}
.balance-row {
  margin-bottom: 16px;
}
.stat-card {
  text-align: center;
  border: 2px solid #f0ece6;
  border-radius: 2px;
}
.balance-num {
  font-size: 36px;
  font-weight: 700;
  color: var(--brand);
  font-family: var(--font-mono);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}
.segment-coin {
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  filter: drop-shadow(0 3px 0 rgba(92, 38, 15, .10));
}
.balance-label {
  font-size: 13px;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
}
.balance-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}
.balance-yuan {
  font-size: 14px;
  color: #3a302a;
}
.balance-note {
  font-size: 12px;
  color: #bfb8b0;
}
.action-row {
  margin-bottom: 16px;
}
.action-card {
  border: 2px solid #f0ece6;
  border-radius: 2px;
}
.card-header-title {
  font-weight: 600;
  font-family: "Courier New","SimHei",monospace;
  font-size: 14px;
  color: #3a302a;
}
.redeem-row {
  display: flex;
  gap: 8px;
}
.redeem-input {
  flex: 1;
}
.btn-primary {
  background: #f37021;
  border-color: #f37021;
  border-radius: 0;
  font-family: "Courier New","SimHei",monospace;
  box-shadow: 0 3px 0 rgba(197,83,18,0.15);
}
.btn-block {
  width: 100%;
}
.pay-method-group {
  margin-bottom: 8px;
}
.pay-select {
  width: 100%;
  margin-bottom: 8px;
}
.log-card {
  border: 2px solid #f0ece6;
  border-radius: 2px;
}
.log-table {
  border-color: #f0ece6;
}
.log-table :deep(th) {
  background: #faf9f7;
  font-family: "Courier New","SimHei",monospace;
  font-size: 11px;
  text-transform: uppercase;
  color: #3a302a;
  border-color: #f0ece6;
}
.log-table :deep(td) {
  border-color: #f0ece6;
  color: #3a302a;
}
.log-type-tag {
  border-radius: 0;
  font-family: "Courier New","SimHei",monospace;
  font-size: 10px;
  font-weight: bold;
}
.amount-positive {
  color: #f37021;
}
.amount-negative {
  color: #c55312;
}
</style>
