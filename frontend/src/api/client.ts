/** 后端错误结构：{ code, message, details } */
export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly details: Record<string, string>

  constructor(message: string, code: string, status: number, details: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.details = details
  }
}

export interface ApiErrorBody {
  code?: string
  message?: string
  details?: Record<string, string>
}

const BASE = import.meta.env.VITE_API_BASE ?? '/api'
const TIMEOUT = 10_000

let csrfToken: string | undefined
let unauthorizedHandler: (() => void) | null = null

/** 由 main.ts 注入：清理会话并跳转登录 */
export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler
}

export function resetCsrf() {
  csrfToken = undefined
}

async function ensureCsrf(): Promise<string> {
  if (csrfToken) return csrfToken
  const response = await fetch(`${BASE}/auth/csrf`, { credentials: 'same-origin' })
  if (!response.ok)
    throw new ApiError('无法获取安全令牌，请刷新页面重试', 'CSRF_UNAVAILABLE', response.status)
  const data = (await response.json()) as { token: string }
  csrfToken = data.token
  return csrfToken
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  query?: Record<string, string | number | boolean | undefined | null>
  headers?: Record<string, string>
  /** 登录/探活等请求不需要触发 401 跳转 */
  skipAuthRedirect?: boolean
  signal?: AbortSignal
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const url = `${BASE}${path}`
  if (!query) return url
  const search = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  const qs = search.toString()
  return qs ? `${url}?${qs}` : url
}

function toApiError(status: number, payload: unknown): ApiError {
  const body = (payload ?? {}) as ApiErrorBody
  const message =
    body.message ||
    (status === 401
      ? '登录已过期，请重新登录'
      : status === 403
        ? '没有权限执行此操作'
        : status === 404
          ? '数据不存在或已被删除'
          : status >= 500
            ? '服务暂时不可用，请稍后重试'
            : `请求失败（${status}）`)
  return new ApiError(message, body.code ?? `HTTP_${status}`, status, body.details ?? {})
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body !== undefined) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) headers.set('X-CSRF-TOKEN', await ensureCsrf())

  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), TIMEOUT)
  const abortOuter = () => controller.abort()
  options.signal?.addEventListener('abort', abortOuter)

  let response: Response
  try {
    response = await fetch(buildUrl(path, options.query), {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: 'same-origin',
      signal: controller.signal,
    })
  } catch (error) {
    if ((error as Error).name === 'AbortError') {
      throw new ApiError('请求超时，请检查网络后重试', 'TIMEOUT', 0)
    }
    throw new ApiError('网络连接失败，请检查网络或后端服务是否已启动', 'NETWORK_ERROR', 0)
  } finally {
    window.clearTimeout(timer)
    options.signal?.removeEventListener('abort', abortOuter)
  }

  if (response.status === 204) return undefined as T

  const text = await response.text()
  let payload: unknown
  try {
    payload = text ? JSON.parse(text) : undefined
  } catch {
    payload = undefined
  }

  if (response.status === 401) {
    resetCsrf()
    if (!options.skipAuthRedirect) unauthorizedHandler?.()
    throw toApiError(401, payload)
  }

  if (response.status === 403) {
    resetCsrf()
    if (!options.skipAuthRedirect) throw toApiError(403, payload)
  }

  if (!response.ok) throw toApiError(response.status, payload)

  return payload as T
}

export const api = {
  get: <T>(path: string, query?: RequestOptions['query'], skipAuthRedirect = false) =>
    request<T>(path, { method: 'GET', query, skipAuthRedirect }),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    request<T>(path, { method: 'POST', body, headers }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
