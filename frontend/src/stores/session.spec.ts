import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSessionStore } from './session'

function reply(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response
}

describe('session store', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('登录时提交账号字段并保存会话用户', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply({ token: 'csrf' }))
      .mockResolvedValueOnce(reply({ id: 2, username: 'family01', name: '陈晓梅', role: 'USER' }))
    vi.stubGlobal('fetch', fetchMock)

    const store = useSessionStore()
    await store.login('family01', '123456')

    expect(store.user?.username).toBe('family01')
    expect(store.user?.role).toBe('USER')
    expect(store.loaded).toBe(true)
    const payload = JSON.parse(String(fetchMock.mock.calls[1][1].body))
    expect(payload).toEqual({ username: 'family01', password: '123456' })
  })

  it('未登录时刷新返回空用户而不是抛错', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reply({ code: 'UNAUTHENTICATED', message: '请先登录' }, 401)))

    const store = useSessionStore()
    await store.refresh()

    expect(store.user).toBeNull()
    expect(store.loaded).toBe(true)
  })

  it('退出后清空用户', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(reply({ token: 'csrf' }))
      .mockResolvedValueOnce(reply({ id: 2, username: 'family01', name: '陈晓梅', role: 'USER' }))
      .mockResolvedValueOnce({ ok: true, status: 204, json: async () => undefined } as unknown as Response)
    vi.stubGlobal('fetch', fetchMock)

    const store = useSessionStore()
    await store.login('family01', '123456')
    await store.logout()

    expect(store.user).toBeNull()
  })
})
