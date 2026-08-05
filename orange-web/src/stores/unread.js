import { ref } from 'vue'
import { storeToRefs } from 'pinia'
import { pinia } from './index'
import { useNotificationStore } from './notificationStore'

const notificationStore = useNotificationStore(pinia)

export const { data: unreadCount } = storeToRefs(notificationStore)
export const loadUnreadCount = notificationStore.loadUnread
export const resetUnreadCount = notificationStore.reset
export const avatarUrl = ref('')
export const nickname = ref('')

export const resetShellIdentity = () => {
  avatarUrl.value = ''
  nickname.value = ''
}

export const resetNotificationState = () => {
  resetUnreadCount()
  resetShellIdentity()
}
