import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSessionStore } from './session'
import { resetCsrf } from '../api/client'

function reply(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => (body === undefined ? '' : JSON.stringify(body)),
    json: async () => body,
  } as unknown as Response
}

describe('session store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    resetCsrf()
  })

  it('登录时提交账号字段并保存会话用户', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(reply({ token: 'csrf' }))
      .mockResolvedValueOnce(reply({ id: 2, username: 'family01', name: '陈晓梅', role: 'USER' }))
    vi.stubGlobal('fetch', fetchMock)

    const store = useSessionStore()
    await store.login('family01', '123456')

    expect(store.user?.username).toBe('family01')
    expect(store.user?.role).toBe('USER')
    expect(store.name).toBe('陈晓梅')
    expect(store.ready).toBe(true)
    const payload = JSON.parse(String(fetchMock.mock.calls[1][1].body))
    expect(payload).toEqual({ username: 'family01', password: '123456' })
  })

  it('未登录时恢复会话返回空用户而不是抛错', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(reply({ code: 'UNAUTHENTICATED', message: '请先登录' }, 401)),
    )

    const store = useSessionStore()
    await store.load()

    expect(store.user).toBeNull()
    expect(store.ready).toBe(true)
  })

  it('登录失败时记录后端文案并向上抛出', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(reply({ token: 'csrf' }))
      .mockResolvedValueOnce(reply({ code: 'BAD_CREDENTIALS', message: '账号或密码错误' }, 401))
    vi.stubGlobal('fetch', fetchMock)

    const store = useSessionStore()
    await expect(store.login('family01', 'wrong')).rejects.toThrow('账号或密码错误')
    expect(store.error).toBe('账号或密码错误')
    expect(store.user).toBeNull()
  })

  it('退出后清空用户且不抛错（会话过期时）', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reply({ message: '请先登录' }, 401)))

    const store = useSessionStore()
    store.clear()
    await store.logout()

    expect(store.user).toBeNull()
    expect(store.ready).toBe(true)
  })
})
