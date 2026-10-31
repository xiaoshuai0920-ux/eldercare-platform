import { sessionCookie } from './session-cookie.js'

let csrf
let cookie
const configuredBase = import.meta.env?.VITE_API_BASE_URL
let base = configuredBase || '/api'
// #ifdef MP-WEIXIN
base = configuredBase || 'http://127.0.0.1:8080/api'
// #endif

export function resetSession() {
  csrf = undefined
  cookie = undefined
}

export class ApiRequestError extends Error {
  constructor(message, statusCode, code) {
    super(message)
    this.name = 'ApiRequestError'
    this.statusCode = statusCode
    this.code = code
  }
}

// 业务操作过程中会话过期（401）时，页面只弹一句提示会把用户困在当前页。
// 由平台层注册一次跳登录的处理器；登录接口自身的 401 是密码错误，不能触发跳转。
let unauthorizedHandler
export function setUnauthorizedHandler(handler) { unauthorizedHandler = handler }

async function send(path, method, data, token, extraHeaders = {}) {
  const header = { Accept: 'application/json' }
  if (data !== undefined) header['Content-Type'] = 'application/json'
  if (token) header['X-CSRF-TOKEN'] = token
  Object.assign(header, extraHeaders)
  // #ifdef MP-WEIXIN
  if (cookie) header.Cookie = cookie
  // #endif
  let response
  try {
    response = await new Promise((resolve, reject) => uni.request({
      url: `${base}${path}`, method, data, header, timeout: 10000,
      success: resolve, fail: reject,
    }))
  } catch {
    throw new Error('网络连接失败，请检查服务是否启动。')
  }
  // #ifdef MP-WEIXIN
  const received = sessionCookie(response.cookies || [])
  if (received !== undefined) cookie = received || undefined
  // #endif
  if (response.statusCode === 401) {
    resetSession()
    if (path !== '/auth/login' && path !== '/auth/csrf' && unauthorizedHandler) unauthorizedHandler()
  }
  if (response.statusCode === 403) csrf = undefined
  if (response.statusCode < 200 || response.statusCode >= 300) {
    throw new ApiRequestError(response.data?.message || `请求失败（${response.statusCode}）`, response.statusCode, response.data?.code)
  }
  return response.data
}

export async function request(path, method = 'GET', data, headers = {}) {
  if (method !== 'GET' && !csrf) {
    const response = await send('/auth/csrf', 'GET')
    csrf = response.token
  }
  const result = await send(path, method, data, method === 'GET' ? undefined : csrf, headers)
  if (path === '/auth/login') csrf = undefined
  if (path === '/auth/logout') resetSession()
  return result
}
