export interface ApiError {
  code: string
  message: string
  details?: Record<string, string>
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface SessionUser {
  id: number
  name: string
  role: 'USER' | 'WORKER' | 'ADMIN'
}
