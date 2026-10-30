import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api/endpoints'
import { ApiError } from '@/api/client'
import type { SessionUser } from '@/api/types'

export const useSessionStore = defineStore('session', () => {
  const user = ref<SessionUser | null>(null)
  const loading = ref(false)
  const error = ref('')
  const ready = ref(false)

  const isLoggedIn = computed(() => user.value !== null)
  const role = computed(() => user.value?.role ?? null)
  const name = computed(() => user.value?.name ?? '')

  /** 启动时恢复会话：401 视为未登录，不跳转 */
  async function load() {
    if (ready.value) return
    loading.value = true
    try {
      user.value = await authApi.me()
    } catch {
      user.value = null
    } finally {
      loading.value = false
      ready.value = true
    }
  }

  async function login(username: string, password: string): Promise<SessionUser> {
    loading.value = true
    error.value = ''
    try {
      user.value = await authApi.login(username, password)
      ready.value = true
      return user.value
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '登录失败，请稍后重试'
      throw reason
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      /* 会话可能已过期，忽略 */
    }
    user.value = null
    ready.value = true
  }

  function clear() {
    user.value = null
  }

  return { user, loading, error, ready, isLoggedIn, role, name, load, login, logout, clear }
})
