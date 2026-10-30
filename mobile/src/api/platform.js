import { request, resetSession } from './request.js'

export const platformApi = {
  me: () => request('/auth/me'),
  login: (username, password) => request('/auth/login', 'POST', { username, password }),
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
  orders: () => request('/orders?page=1&size=50'),
  order: (id) => request(`/orders/${id}`),
  confirmOrder: (id) => request(`/orders/${id}/confirm`, 'POST'),
  cancelOrder: (id) => request(`/orders/${id}/cancel`, 'POST'),
  review: (id) => request(`/orders/${id}/review`),
  submitReview: (id, payload) => request(`/orders/${id}/review`, 'POST', payload),
  settlement: (id) => request(`/orders/${id}/settlement`),
  createPaymentAttempt: (id) => request(`/orders/${id}/settlement/payment-attempts`, 'POST'),
  syncSettlement: (id) => request(`/orders/${id}/settlement/sync`, 'POST'),
  reminders: () => request('/reminders'),
  createReminder: (payload) => request('/reminders', 'POST', payload),
  updateReminder: (id, payload) => request(`/reminders/${id}`, 'PUT', payload),
  stopReminder: (id) => request(`/reminders/${id}/stop`, 'POST'),
  notifications: () => request('/notifications?page=1&size=50'),
  readNotification: (id) => request(`/notifications/${id}/read`, 'POST'),
  workerTasks: () => request('/worker/tasks'),
  setAccepting: (accepting) => request('/worker/accepting', 'POST', { accepting }),
  workerAction: (id, action, payload) => request(`/orders/${id}/${action}`, 'POST', payload),
  interruptOrder: (id, reason) => request(`/orders/${id}/interrupt`, 'POST', { reason }),
  requestLeave: (payload) => request('/worker/leaves', 'POST', payload),
  resetSession,
}

let navigating = false

// 首屏的 onShow 触发跳转时，微信路由控制器还没走完 appLaunch，
// 此时用 uni.redirectTo 会出现 “routeTo redirectTo timeout”，页面一直不进 ready。
// 统一改用 uni.reLaunch 重置页面栈，并加守卫避免重复跳转。
export function navigate(url) {
  if (navigating) return
  navigating = true
  const release = () => { setTimeout(() => { navigating = false }, 300) }
  uni.reLaunch({ url, complete: release, fail: release })
}

const homePages = { USER: '/pages/dashboard/index', WORKER: '/pages/worker/index' }

export async function requireRole(roles) {
  try {
    const user = await platformApi.me()
    if (roles.includes(user.role)) return user
    if (user.role === 'ADMIN') {
      uni.showModal({ title: '请使用管理端', content: '管理员业务请通过 PC Web 工作台处理。', showCancel: false })
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

export function showFailure(error) {
  uni.showToast({ title: errorMessage(error, '操作失败，请稍后重试'), icon: 'none', duration: 3000 })
}

export function localDateTime(date, time) {
  return `${date}T${time}:00`
}

export function idempotencyKey() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
}
