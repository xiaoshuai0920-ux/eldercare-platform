import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { useUiStore } from './stores/ui'
import { useSessionStore } from './stores/session'
import { onUnauthorized } from './api/client'
import '@/styles/tokens.css'
import '@/styles/app.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)

// 适老模式在挂载前应用，避免首屏闪烁
useUiStore().init()

// 401 统一处理：清会话并带回跳地址
onUnauthorized(() => {
  const session = useSessionStore()
  session.clear()
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.replace({ path: '/login', query: { redirect: current.fullPath } })
  }
})

/**
 * 渲染/生命周期异常的兜底：不让整页因为一个组件报错而变成白屏。
 * 仍然把原始错误打到控制台，便于定位；用户侧只看到一句可操作提示。
 */
let lastNoticeAt = 0
function reportError(context: string, error: unknown) {
  console.error(`[ui] ${context}`, error)
  const now = Date.now()
  // 同一次异常往往同时触发 errorHandler 与 unhandledrejection，3 秒内只提示一次
  if (now - lastNoticeAt < 3000) return
  lastNoticeAt = now
  useUiStore().notify('页面出现异常，请刷新后重试', 'danger')
}

app.config.errorHandler = (error, _instance, info) => reportError(info, error)

window.addEventListener('unhandledrejection', (event) => reportError('unhandledrejection', event.reason))

app.mount('#app')
