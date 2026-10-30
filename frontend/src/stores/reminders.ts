import { defineStore } from 'pinia'
import { ref } from 'vue'
import { userApi } from '@/api/endpoints'
import { ApiError } from '@/api/client'
import type { ReminderRow } from '@/api/types'

export const useReminderStore = defineStore('reminders', () => {
  const list = ref<ReminderRow[]>([])
  const loading = ref(false)
  const error = ref('')

  async function load() {
    loading.value = true
    error.value = ''
    try {
      list.value = await userApi.reminders()
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '提醒加载失败'
    } finally {
      loading.value = false
    }
  }

  async function stop(id: number): Promise<boolean> {
    try {
      await userApi.stopReminder(id)
      await load()
      return true
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '停用失败'
      return false
    }
  }

  async function create(payload: {
    elderId: number
    title: string
    recurrence: string
    firstAt: string
  }): Promise<boolean> {
    try {
      await userApi.createReminder(payload)
      await load()
      return true
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '提醒创建失败'
      return false
    }
  }

  async function update(
    id: number,
    payload: { title: string; recurrence: string; nextFireAt: string },
  ): Promise<boolean> {
    try {
      await userApi.updateReminder(id, payload)
      await load()
      return true
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '提醒修改失败'
      return false
    }
  }

  return { list, loading, error, load, stop, create, update }
})
