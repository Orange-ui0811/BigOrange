<template>
  <div>
    <h3 style="margin:0 0 4px;font-weight:700">我的配额</h3>
    <p style="color:#909399;font-size:13px;margin:0 0 16px">管理 segments 余额，用于 AI 趋势分析和方案设计</p>

    <!-- 余额卡片 -->
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="8">
        <el-card shadow="never" style="text-align:center;border-radius:12px">
          <div style="font-size:36px;font-weight:700;color:#e65100">{{ balance }}</div>
          <div style="font-size:13px;color:#909399">segments</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" style="text-align:center;border-radius:12px">
          <div style="font-size:14px;color:#606266">≈ ¥{{ (balance / 100).toFixed(2) }}</div>
          <div style="font-size:12px;color:#909399">100 segments = 1 元</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 兑换 + 充值 -->
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="12">
        <el-card shadow="never" style="border-radius:12px">
          <template #header><span style="font-weight:600">兑换充值码</span></template>
          <div style="display:flex;gap:8px">
            <el-input v-model="code" placeholder="输入 48 位充值码" clearable style="flex:1" />
            <el-button type="primary" @click="doRedeem" :loading="redeeming" style="background:#e65100;border-color:#e65100">兑换</el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" style="border-radius:12px">
          <template #header><span style="font-weight:600">在线充值</span></template>
          <el-radio-group v-model="payMethod" size="small" style="margin-bottom:8px">
            <el-radio-button value="wxpay">微信</el-radio-button>
            <el-radio-button value="alipay">支付宝</el-radio-button>
          </el-radio-group>
          <el-select v-model="payAmount" size="small" style="width:100%;margin-bottom:8px">
            <el-option :value="1000" label="¥10 = 1000 segments" />
            <el-option :value="3000" label="¥30 = 3000 segments" />
            <el-option :value="5000" label="¥50 = 5000 segments" />
            <el-option :value="10000" label="¥100 = 10000 segments" />
          </el-select>
          <el-button @click="doPay" type="primary" :loading="paying" style="background:#e65100;border-color:#e65100;width:100%">前往支付</el-button>
        </el-card>
      </el-col>
    </el-row>

    <!-- 消费记录 -->
    <el-card shadow="never" style="border-radius:12px">
      <template #header><span style="font-weight:600">消费记录</span></template>
      <el-table :data="logs" v-loading="logLoading" empty-text="暂无记录" size="small">
        <el-table-column prop="createTime" label="时间" width="160" />
        <el-table-column prop="type" label="类型" width="80">
          <template #default="{row}">
            <el-tag size="small" :type="row.type==='RECHARGE'?'success':row.type==='CONSUME'?'warning':'info'">
              {{ row.type==='RECHARGE'?'充值':row.type==='CONSUME'?'消费':row.type==='REFUND'?'退款':row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变动" width="90">
          <template #default="{row}">
            <span :style="{color:row.amount>0?'#67c23a':'#e65100'}">{{ row.amount>0?'+':'' }}{{ row.amount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="balance" label="余额" width="80" />
        <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
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
