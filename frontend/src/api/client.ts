import type { ApiError } from '../types/api'

let csrfToken: string | undefined

async function ensureCsrf(): Promise<string> {
  if (csrfToken) return csrfToken
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' })
  if (!response.ok) throw new Error('无法获取安全令牌，请刷新页面重试')
  const data = await response.json() as { token: string }
  csrfToken = data.token
  return csrfToken
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) headers.set('X-CSRF-TOKEN', await ensureCsrf())
  const response = await fetch(`/api${path}`, { ...init, method, headers, credentials: 'same-origin' })
  if (response.status === 204) return undefined as T
  const data = await response.json().catch(() => undefined)
  if (!response.ok) {
    if (response.status === 403) csrfToken = undefined
    const error = data as ApiError | undefined
    throw new Error(error?.message || `请求失败 (${response.status})`)
  }
  return data as T
}

export function resetCsrf() { csrfToken = undefined }
