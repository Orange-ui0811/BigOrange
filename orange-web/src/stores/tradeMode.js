import { ref } from 'vue'

const transactionUiConfigured = String(import.meta.env.VITE_TRANSACTION_UI_ENABLED || 'false').toLowerCase() === 'true'

export const transactionUiEnabled = transactionUiConfigured
export const tradeEnabled = ref(false)

export const applyTradeMode = (serverEnabled) => {
  tradeEnabled.value = transactionUiEnabled && Boolean(serverEnabled)
}
