import { defineStore } from 'pinia'
import { ref } from 'vue'
import { userApi } from '@/api/endpoints'
import { ApiError } from '@/api/client'
import type { UserOrderDetail, UserOrderRow } from '@/api/types'

export type FilterKey = 'ALL' | 'action' | 'active' | 'upcoming' | 'done'

const ACTION_STATUS = ['WAITING_CONFIRMATION']
const ACTIVE_STATUS = ['IN_SERVICE']
const UPCOMING_STATUS = ['WAITING_ASSIGNMENT', 'WAITING_ACCEPTANCE', 'WAITING_SERVICE']
const DONE_STATUS = ['COMPLETED', 'CANCELLED', 'ABNORMAL_CLOSED', 'INTERRUPTED']

export const useOrderStore = defineStore('orders', () => {
  const list = ref<UserOrderRow[]>([])
  const loading = ref(false)
  const error = ref('')
  const detail = ref<UserOrderDetail | null>(null)
  const detailLoading = ref(false)
  const page = ref(1)
  const size = 10
  const total = ref(0)
  const selectedGroup = ref<FilterKey>('ALL')
  const counts = ref({ total: 0, pending: 0, active: 0, upcoming: 0, done: 0 })
  let latestRequest = 0

  function group(key: FilterKey): UserOrderRow[] {
    const rules: Record<FilterKey, string[] | null> = {
      ALL: null,
      action: ACTION_STATUS,
      active: ACTIVE_STATUS,
      upcoming: UPCOMING_STATUS,
      done: DONE_STATUS,
    }
    const statuses = rules[key]
    return statuses ? list.value.filter((row) => statuses.includes(row.status)) : list.value
  }

  async function load(requestedPage = page.value, group: FilterKey = selectedGroup.value) {
    const request = ++latestRequest
    selectedGroup.value = group
    loading.value = true
    error.value = ''
    try {
      let result = await userApi.orders(requestedPage, size, group)
      if (request !== latestRequest) return
      const lastPage = Math.max(1, Math.ceil(result.total / size))
      if (requestedPage > lastPage) result = await userApi.orders(lastPage, size, group)
      if (request !== latestRequest) return
      list.value = result.items
      page.value = result.page
      total.value = result.total
      counts.value = result.counts
    } catch (reason) {
      if (request !== latestRequest) return
      list.value = []
      total.value = 0
      error.value = reason instanceof ApiError ? reason.message : '订单加载失败'
    } finally {
      if (request === latestRequest) loading.value = false
    }
  }

  async function openDetail(id: number) {
    detailLoading.value = true
    try {
      detail.value = await userApi.order(id)
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '订单详情加载失败'
    } finally {
      detailLoading.value = false
    }
  }

  function closeDetail() {
    detail.value = null
  }

  async function confirm(id: number): Promise<boolean> {
    try {
      await userApi.confirm(id)
      await load()
      if (detail.value?.id === id) await openDetail(id)
      return true
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '确认失败'
      return false
    }
  }

  async function cancel(id: number): Promise<boolean> {
    try {
      await userApi.cancel(id)
      await load()
      if (detail.value?.id === id) closeDetail()
      return true
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '取消失败'
      return false
    }
  }

  return {
    list,
    loading,
    error,
    detail,
    detailLoading,
    counts,
    page,
    size,
    total,
    group,
    load,
    openDetail,
    closeDetail,
    confirm,
    cancel,
  }
})
