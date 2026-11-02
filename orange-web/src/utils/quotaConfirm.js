import { h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBalance } from '../api/quota'
import QuotaConfirmContent from '../components/QuotaConfirmContent.vue'
import AnalysisStartedContent from '../components/AnalysisStartedContent.vue'

const readCurrentBalance = async () => {
  const response = await getBalance()
  const balance = Number(response?.data?.balance)
  if (response?.code !== 1 || !Number.isFinite(balance) || balance < 0) {
    throw new Error('invalid quota balance')
  }
  return balance
}

export const confirmQuotaUsage = async ({
  actionLabel,
  cost,
  title = '确认使用额度',
  confirmButtonText = '确认开始',
  cancelButtonText = '取消',
  type = 'warning',
  notice = '',
  estimatedTime = '1～3 分钟',
}) => {
  const normalizedCost = Number(cost)
  if (!Number.isFinite(normalizedCost) || normalizedCost < 0) {
    ElMessage.warning('本次消耗额度尚未加载，请稍后再试')
    return false
  }

  let balance
  try {
    balance = await readCurrentBalance()
  } catch {
    ElMessage.error('当前可用额度读取失败，请稍后重试')
    return false
  }

  const content = h(QuotaConfirmContent, {
    actionLabel,
    balance,
    cost: normalizedCost,
    notice,
  })
  const commonOptions = {
    customClass: 'quota-confirm-dialog',
    type,
    closeOnClickModal: false,
    closeOnPressEscape: true,
  }

  if (balance < normalizedCost) {
    try {
      await ElMessageBox.alert(content, '额度不足', {
        ...commonOptions,
        confirmButtonText: '知道了',
      })
    } catch {
      // Closing the informational dialog has the same outcome as acknowledging it.
    }
    return false
  }

  try {
    await ElMessageBox.confirm(content, title, {
      ...commonOptions,
      confirmButtonText,
      cancelButtonText,
    })
    const startedContent = h(AnalysisStartedContent, { estimatedTime })
    void ElMessageBox.alert(startedContent, '分析已开始', {
      customClass: 'analysis-started-dialog',
      type: 'success',
      confirmButtonText: '知道了',
      closeOnClickModal: false,
    }).catch(() => {})
    return true
  } catch {
    return false
  }
}
