import { request, resetSession, setUnauthorizedHandler } from './request.js'

export const platformApi = {
  me: () => request('/auth/me'),
  login: (username, password) => request('/auth/login', 'POST', { username, password }),
  register: (payload) => request('/auth/register', 'POST', payload),
  logout: () => request('/auth/logout', 'POST'),
  userDashboard: () => request('/user/dashboard'),
  workerDashboard: () => request('/worker/dashboard'),
  elders: () => request('/elders'),
  invitations: () => request('/family/invitations'),
  respondInvitation: (id, accept) => request(`/family/invitations/${id}/${accept ? 'accept' : 'reject'}`, 'POST'),
  grants: (elderId) => request(`/elders/${elderId}/grants`),
  revokeGrant: (elderId, accountId) => request(`/elders/${elderId}/grants/${accountId}/revoke`, 'POST'),
  invite: (elderId, payload) => request(`/elders/${elderId}/invitations`, 'POST', payload),
  createElder: (payload) => request('/elders', 'POST', payload),
  updateElder: (elderId, payload) => request(`/elders/${elderId}`, 'PUT', payload),
  catalog: (streetId) => request(`/catalog/items${streetId ? `?streetId=${streetId}` : ''}`),
  areas: () => request('/catalog/areas'),
  recommendations: (elderId, streetId) => request(`/catalog/recommendations?elderId=${elderId}&streetId=${streetId}`),
  createOrder: (payload, key) => request('/orders', 'POST', payload, { 'Idempotency-Key': key }),
  orders: (page = 1, size = 10, group) => request(`/orders?page=${page}&size=${size}${group ? `&group=${group}` : ''}`),
  order: (id) => request(`/orders/${id}`),
  confirmOrder: (id) => request(`/orders/${id}/confirm`, 'POST'),
  cancelOrder: (id) => request(`/orders/${id}/cancel`, 'POST'),
  // 未评价订单的 GET /review 返回 404 REVIEW_NOT_FOUND，这是“还没评价”的正常业务状态，
  // 不能当成加载失败：调用方需要它返回 null 才能继续读取同一订单的结算信息。
  review: async (id) => {
    try {
      return await request(`/orders/${id}/review`)
    } catch (error) {
      if (error?.code === 'REVIEW_NOT_FOUND' || error?.statusCode === 404) return null
      throw error
    }
  },
  submitReview: (id, payload) => request(`/orders/${id}/review`, 'POST', payload),
  settlement: (id) => request(`/orders/${id}/settlement`),
  createPaymentAttempt: (id) => request(`/orders/${id}/settlement/payment-attempts`, 'POST'),
  syncSettlement: (id) => request(`/orders/${id}/settlement/sync`, 'POST'),
  reminders: () => request('/reminders'),
  createReminder: (payload) => request('/reminders', 'POST', payload),
  updateReminder: (id, payload) => request(`/reminders/${id}`, 'PUT', payload),
  stopReminder: (id) => request(`/reminders/${id}/stop`, 'POST'),
  resumeReminder: (id, nextFireAt) => request(`/reminders/${id}/resume`, 'POST', { nextFireAt }),
  notifications: () => request('/notifications?page=1&size=50'),
  unreadCount: () => request('/notifications?page=1&size=1').then((data) => data.unread || 0),
  readNotification: (id) => request(`/notifications/${id}/read`, 'POST'),
  workerTasks: () => request('/worker/tasks'),
  workerTask: (id) => request(`/worker/tasks/${id}`),
  setAccepting: (accepting) => request('/worker/accepting', 'POST', { accepting }),
  workerAction: (id, action, payload) => request(`/orders/${id}/${action}`, 'POST', payload),
  interruptOrder: (id, reason) => request(`/orders/${id}/interrupt`, 'POST', { reason }),
  requestLeave: (payload) => request('/worker/leaves', 'POST', payload),
  resetSession,
}

let navigating = false
let navigationTarget
let pendingNavigation

// 首屏的 onShow 触发跳转时，微信路由控制器还没走完 appLaunch，
// 此时用 uni.redirectTo 会出现 “routeTo redirectTo timeout”，页面一直不进 ready。
// 统一改用 uni.reLaunch 重置页面栈，并加守卫避免重复跳转。
export function navigate(url) {
  if (navigating) {
    if (url !== navigationTarget) pendingNavigation = url
    return
  }
  navigating = true
  navigationTarget = url
  const release = () => { setTimeout(() => {
    navigating = false
    navigationTarget = undefined
    const next = pendingNavigation
    pendingNavigation = undefined
    if (next) navigate(next)
  }, 300) }
  uni.reLaunch({ url, complete: release })
}

const homePages = { USER: '/pages/dashboard/index', WORKER: '/pages/worker/index' }

// 业务操作中途会话失效时，请求层通知这里统一回登录页；跳转去重仍由 navigate 控制。
setUnauthorizedHandler(() => {
  platformApi.resetSession()
  navigate('/pages/login/index')
})

export async function requireRole(roles) {
  try {
    const user = await platformApi.me()
    if (roles.includes(user.role)) return user
    if (user.role === 'ADMIN') {
      // 管理员没有小程序业务页，提示后回登录页，否则页面只会留下一个空白骨架。
      uni.showModal({
        title: '请使用管理端',
        content: '管理员业务请通过 PC Web 工作台处理。',
        showCancel: false,
        success: () => navigate('/pages/login/index'),
      })
      return null
    }
    const target = homePages[user.role]
    if (!target) {
      // 角色缺失或无法识别时不能回跳首页，否则会和首页的校验互相跳转形成死循环。
      showFailure(new Error('账号角色无法识别，请重新登录。'))
      navigate('/pages/login/index')
      return null
    }
    navigate(target)
    return null
  } catch (error) {
    if (error?.statusCode === 401) {
      platformApi.resetSession()
      navigate('/pages/login/index')
      return null
    }
    // 网络或服务端异常不能当成“未登录/无数据”处理，向上抛出让页面显示错误状态和重试入口。
    throw error
  }
}

export function errorMessage(error, fallback = '加载失败，请稍后重试') {
  return error instanceof Error && error.message ? error.message : fallback
}

// 通知里的 target_path 是 PC 端路径（/user/orders、/user/reminders、/worker…），
// 小程序没有对应路由，必须显式映射；不映射就等于点击后什么都不会发生。
const notificationPages = [
  { page: '/pages/orders/index', match: (path) => path === '/user/orders' || path.startsWith('/user/orders/') },
  { page: '/pages/reminders/index', match: (path) => path === '/user/reminders' || path.startsWith('/user/reminders/') },
  { page: '/pages/family/index', match: (path) => path === '/user/family' || path.startsWith('/user/family/') },
  { page: '/pages/worker/index', match: (path) => path === '/worker' || path.startsWith('/worker/') },
]

/**
 * 把一条消息转换成小程序可跳转的地址。
 * 订单类消息标题固定为「订单 #<id> · <项目>」，据此下钻到该订单详情，而不是只停在列表。
 * 管理员路径在移动端没有页面，返回 null 由调用方提示。
 */
export function notificationTarget(notification) {
  const path = notification?.target_path || ''
  if (!path || path.startsWith('/admin')) return null
  const matched = notificationPages.find((entry) => entry.match(path))
  if (!matched) return null
  const orderId = Number(/#(\d+)/.exec(notification?.title || '')?.[1])
  if (matched.page === '/pages/orders/index' && Number.isFinite(orderId) && orderId > 0) {
    return `${matched.page}?orderId=${orderId}`
  }
  return matched.page
}

export function showFailure(error) {
  uni.showToast({ title: errorMessage(error, '操作失败，请稍后重试'), icon: 'none', duration: 3000 })
}

export function localDateTime(date, time) {
  return `${date}T${time}:00`
}

export function idempotencyKey() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
}
