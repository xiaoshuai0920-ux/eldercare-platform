import { defineStore } from 'pinia'
import { ref } from 'vue'
import { userApi } from '@/api/endpoints'
import { ApiError } from '@/api/client'
import type { DashboardUser } from '@/api/types'

export const useUserDashboardStore = defineStore('dashboard', () => {
  const data = ref<DashboardUser | null>(null)
  const loading = ref(false)
  const error = ref('')

  async function load() {
    loading.value = true
    error.value = ''
    try {
      data.value = await userApi.dashboard()
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '数据加载失败'
    } finally {
      loading.value = false
    }
  }

  return { data, loading, error, load }
})
