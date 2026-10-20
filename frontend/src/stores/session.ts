import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, resetCsrf } from '../api/client'
import type { SessionUser } from '../types/api'

export const useSessionStore = defineStore('session', () => {
  const user = ref<SessionUser | null>(null)
  const loaded = ref(false)

  async function refresh() {
    try { user.value = await api<SessionUser>('/auth/me') }
    catch { user.value = null }
    finally { loaded.value = true }
  }

  async function login(username: string, password: string) {
    user.value = await api<SessionUser>('/auth/login', {
      method: 'POST', body: JSON.stringify({ username, password }),
    })
    loaded.value = true
  }

  async function logout() {
    await api<void>('/auth/logout', { method: 'POST' })
    user.value = null
    resetCsrf()
  }

  return { user, loaded, refresh, login, logout }
})
