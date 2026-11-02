import { storeToRefs } from 'pinia'
import { pinia } from './index'
import { transactionUiEnabled, useTradeModeStore } from './tradeModeStore'

const store = useTradeModeStore(pinia)

export { transactionUiEnabled }
export const { tradeEnabled } = storeToRefs(store)
export const applyTradeMode = store.applyServerEnabled
export const loadTradeMode = store.load
export const resetTradeMode = store.reset
