import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, resetCsrf } from '../api/client'

function reply(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response
}

describe('api client', () => {
  beforeEach(() => resetCsrf())

  it('对 GET 请求不获取 CSRF 令牌', async () => {
    const fetchMock = vi.fn().mockResolvedValue(reply({ id: 1 }))
    vi.stubGlobal('fetch', fetchMock)

    await api('/auth/me')

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/auth/me')
    expect((init as RequestInit).method).toBe('GET')
    expect((init as RequestInit).credentials).toBe('same-origin')
  })

  it('写请求先取令牌并在后续请求复用', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply({ token: 'csrf-token-1' }))
      .mockResolvedValueOnce(reply({ id: 9 }))
      .mockResolvedValueOnce(reply({ id: 10 }))
    vi.stubGlobal('fetch', fetchMock)

    await api('/orders', { method: 'POST', body: '{}' })
    await api('/orders', { method: 'POST', body: '{}' })

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/csrf')
    const firstHeaders = fetchMock.mock.calls[1][1].headers as Headers
    expect(firstHeaders.get('X-CSRF-TOKEN')).toBe('csrf-token-1')
    expect(firstHeaders.get('Content-Type')).toBe('application/json')
  })

  it('把后端错误码文案抛给调用方', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply({ token: 't' }))
      .mockResolvedValueOnce(reply({ code: 'BAD_CREDENTIALS', message: '账号或密码错误' }, 401))
    vi.stubGlobal('fetch', fetchMock)

    await expect(api('/auth/login', { method: 'POST', body: '{}' })).rejects.toThrow('账号或密码错误')
  })

  it('收到 403 后清空令牌，下次写请求会重新获取', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply({ token: 'expired' }))
      .mockResolvedValueOnce(reply({ code: 'FORBIDDEN', message: '无权执行此操作' }, 403))
      .mockResolvedValueOnce(reply({ token: 'fresh' }))
      .mockResolvedValueOnce(reply({ id: 11 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(api('/orders/1/cancel', { method: 'POST' })).rejects.toThrow('无权执行此操作')
    await api('/orders/1/cancel', { method: 'POST' })

    expect(fetchMock.mock.calls[2][0]).toBe('/api/auth/csrf')
    const headers = fetchMock.mock.calls[3][1].headers as Headers
    expect(headers.get('X-CSRF-TOKEN')).toBe('fresh')
  })
})
