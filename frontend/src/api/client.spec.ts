import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, onUnauthorized, request, resetCsrf } from './client'

function reply(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => (body === undefined ? '' : JSON.stringify(body)),
    json: async () => body,
  } as unknown as Response
}

describe('api client', () => {
  beforeEach(() => resetCsrf())

  it('GET 请求不获取 CSRF 令牌，并带上同源凭据', async () => {
    const fetchMock = vi.fn().mockResolvedValue(reply({ id: 1 }))
    vi.stubGlobal('fetch', fetchMock)

    await request('/auth/me')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/auth/me')
    expect((init as RequestInit).method).toBe('GET')
    expect((init as RequestInit).credentials).toBe('same-origin')
  })

  it('写请求先取令牌，并在后续请求复用', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(reply({ token: 'csrf-token-1' }))
      .mockResolvedValueOnce(reply({ id: 9 }))
      .mockResolvedValueOnce(reply({ id: 10 }))
    vi.stubGlobal('fetch', fetchMock)

    await request('/orders', { method: 'POST', body: {} })
    await request('/orders', { method: 'POST', body: {} })

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/csrf')
    const firstHeaders = fetchMock.mock.calls[1][1].headers as Headers
    expect(firstHeaders.get('X-CSRF-TOKEN')).toBe('csrf-token-1')
    expect(firstHeaders.get('Content-Type')).toBe('application/json')
  })

  it('把后端错误文案与错误码抛给调用方', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(reply({ token: 't' }))
      .mockResolvedValueOnce(reply({ code: 'BAD_CREDENTIALS', message: '账号或密码错误' }, 401))
    vi.stubGlobal('fetch', fetchMock)
    onUnauthorized(vi.fn())

    await expect(request('/auth/login', { method: 'POST', body: {} })).rejects.toMatchObject({
      message: '账号或密码错误',
      code: 'BAD_CREDENTIALS',
      status: 401,
    })
  })

  it('收到 403 后清空令牌，下次写请求会重新获取', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(reply({ token: 'expired' }))
      .mockResolvedValueOnce(reply({ code: 'FORBIDDEN', message: '无权执行此操作' }, 403))
      .mockResolvedValueOnce(reply({ token: 'fresh' }))
      .mockResolvedValueOnce(reply({ id: 11 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(request('/orders/1/cancel', { method: 'POST' })).rejects.toThrow('无权执行此操作')
    await request('/orders/1/cancel', { method: 'POST' })

    expect(fetchMock.mock.calls[2][0]).toBe('/api/auth/csrf')
    const headers = fetchMock.mock.calls[3][1].headers as Headers
    expect(headers.get('X-CSRF-TOKEN')).toBe('fresh')
  })

  it('401 触发统一跳转回调', async () => {
    const handler = vi.fn()
    onUnauthorized(handler)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reply({ message: '登录已过期' }, 401)))

    await expect(request('/orders')).rejects.toThrow('登录已过期')
    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('网络异常归一为中文提示而不是原始网络错误', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    await expect(request('/orders')).rejects.toMatchObject({
      code: 'NETWORK_ERROR',
      message: '网络连接失败，请检查网络或后端服务是否已启动',
    })
  })

  it('错误类型统一为 ApiError', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reply({ message: '服务暂时不可用' }, 503)))

    await expect(request('/orders')).rejects.toBeInstanceOf(ApiError)
  })
})
