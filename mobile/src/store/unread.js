import { ref } from 'vue'
import { platformApi } from '../api/platform.js'

// 全局未读消息数：入口角标用。各入口页 onShow 时刷新，消息页在读后增减。
export const unread = ref(0)

export async function refreshUnread() {
  try { unread.value = await platformApi.unreadCount() }
  catch { /* 未读数获取失败不阻断页面，角标保持上一次的值 */ }
}
