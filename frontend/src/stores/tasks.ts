import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { workerApi } from '@/api/endpoints'
import { ApiError } from '@/api/client'
import type { LeaveRow, WorkerTaskDetail, WorkerTaskRow } from '@/api/types'

export const useTaskStore = defineStore('tasks', () => {
  const accepting = ref(false)
  const tasks = ref<WorkerTaskRow[]>([])
  const leaves = ref<LeaveRow[]>([])
  const detail = ref<WorkerTaskDetail | null>(null)
  const loading = ref(false)
  const busy = ref(false)
  const error = ref('')

  const pending = computed(() => tasks.value.filter((task) => task.status === 'WAITING_ACCEPTANCE').length)
  const running = computed(() => tasks.value.filter((task) => task.status === 'IN_SERVICE').length)
  const finished = computed(() => tasks.value.filter((task) => task.status === 'COMPLETED').length)

  async function load() {
    loading.value = true
    error.value = ''
    try {
      const [dashboard, leaveList] = await Promise.all([workerApi.dashboard(), workerApi.leaves()])
      accepting.value = dashboard.accepting
      tasks.value = dashboard.tasks
      leaves.value = leaveList
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '任务加载失败'
    } finally {
      loading.value = false
    }
  }

  async function openTask(orderId: number) {
    busy.value = true
    try {
      detail.value = await workerApi.task(orderId)
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '任务详情加载失败'
    } finally {
      busy.value = false
    }
  }

  function closeTask() {
    detail.value = null
  }

  async function setAccepting(next: boolean): Promise<boolean> {
    try {
      await workerApi.setAccepting(next)
      accepting.value = next
      return true
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '接单状态更新失败'
      return false
    }
  }

  async function act(action: 'accept' | 'start' | 'submit', orderId: number): Promise<boolean> {
    busy.value = true
    try {
      if (action === 'accept') await workerApi.accept(orderId)
      if (action === 'start') await workerApi.start(orderId)
      if (action === 'submit') await workerApi.submit(orderId)
      await load()
      return true
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '操作失败'
      return false
    } finally {
      busy.value = false
    }
  }

  async function reject(orderId: number, reason: string): Promise<boolean> {
    try {
      await workerApi.reject(orderId, reason)
      await load()
      return true
    } catch (err) {
      error.value = err instanceof ApiError ? err.message : '拒单失败'
      return false
    }
  }

  async function interrupt(orderId: number, reason: string): Promise<boolean> {
    try {
      await workerApi.interrupt(orderId, reason)
      await load()
      return true
    } catch (err) {
      error.value = err instanceof ApiError ? err.message : '上报失败'
      return false
    }
  }

  async function requestLeave(payload: { startAt: string; endAt: string; reason: string }): Promise<boolean> {
    try {
      await workerApi.requestLeave(payload)
      leaves.value = await workerApi.leaves()
      return true
    } catch (err) {
      error.value = err instanceof ApiError ? err.message : '请假提交失败'
      return false
    }
  }

  return {
    accepting,
    tasks,
    leaves,
    detail,
    loading,
    busy,
    error,
    pending,
    running,
    finished,
    load,
    openTask,
    closeTask,
    setAccepting,
    act,
    reject,
    interrupt,
    requestLeave,
  }
})
