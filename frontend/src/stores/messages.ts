import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { userApi } from '@/api/endpoints'
import { ApiError } from '@/api/client'
import type { NotificationRow, Page } from '@/api/types'

export type MessageFilter = 'all' | 'order' | 'system'

/** 后端按同一口径过滤，这里保留一份映射用于请求参数 */
const KIND_PARAM: Record<MessageFilter, string | undefined> = {
  all: undefined,
  order: 'order',
  system: 'system',
}

export const useMessageStore = defineStore('messages', () => {
  const paged = ref<Page<NotificationRow>>({ items: [], page: 1, size: 20, total: 0 })
  const filter = ref<MessageFilter>('all')
  const loading = ref(false)
  const error = ref('')

  const list = computed(() => paged.value.items)
  const total = computed(() => paged.value.total)

  async function load(page = paged.value.page) {
    loading.value = true
    error.value = ''
    try {
      paged.value = await userApi.notifications(page, paged.value.size, KIND_PARAM[filter.value])
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '消息加载失败'
    } finally {
      loading.value = false
    }
  }

  /** 切换分类时回到第一页，由后端按同一口径重新计数 */
  async function setFilter(next: MessageFilter) {
    filter.value = next
    await load(1)
  }

  async function markRead(id: number): Promise<boolean> {
    const row = paged.value.items.find((item) => item.id === id)
    if (!row || row.read_at) return true
    try {
      await userApi.readNotification(id)
      row.read_at = new Date().toISOString()
      return true
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '标记已读失败'
      return false
    }
  }

  /** 把当前页所有未读标为已读；跨页未读需要逐页处理 */
  async function markAllRead(): Promise<number> {
    const unreadRows = paged.value.items.filter((row) => !row.read_at)
    let done = 0
    for (const row of unreadRows) {
      if (await markRead(row.id)) done += 1
    }
    return done
  }

  return {
    paged,
    list,
    total,
    filter,
    loading,
    error,
    load,
    setFilter,
    markRead,
    markAllRead,
  }
})
