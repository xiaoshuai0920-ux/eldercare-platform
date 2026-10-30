import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface Toast {
  id: number
  message: string
  tone: 'default' | 'success' | 'danger'
}

const STORAGE_KEY = 'eldercare-ui-senior'

/**
 * 全局界面偏好。适老模式必须落在 <html data-senior> 上，
 * 因为 tokens.css 以该属性覆盖字号、行高与触达变量。
 */
export const useUiStore = defineStore('ui', () => {
  const seniorMode = ref(false)
  const toasts = ref<Toast[]>([])
  let initialized = false
  let seed = 0

  /** 写操作的轻提示：3 秒后自动消失 */
  function notify(message: string, tone: Toast['tone'] = 'success') {
    const id = ++seed
    toasts.value = [...toasts.value, { id, message, tone }]
    window.setTimeout(() => {
      toasts.value = toasts.value.filter((item) => item.id !== id)
    }, 3000)
  }

  function apply(on: boolean) {
    const root = document.documentElement
    if (on) root.setAttribute('data-senior', 'on')
    else root.removeAttribute('data-senior')
  }

  function set(on: boolean) {
    seniorMode.value = on
    apply(on)
    try {
      window.localStorage.setItem(STORAGE_KEY, on ? 'on' : 'off')
    } catch {
      /* 隐私模式下忽略 */
    }
  }

  function toggle() {
    set(!seniorMode.value)
  }

  /** 启动时初始化：URL ?senior=1 优先，其次读本地偏好 */
  function init() {
    if (initialized) return
    initialized = true
    const query = new URLSearchParams(window.location.search)
    const forced = query.get('senior') === '1'
    let stored = false
    try {
      stored = window.localStorage.getItem(STORAGE_KEY) === 'on'
    } catch {
      stored = false
    }
    const on = forced || stored
    seniorMode.value = on
    apply(on)
  }

  return { seniorMode, toasts, init, set, toggle, notify }
})
