import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import FamilyView from './FamilyView.vue'
import { resetCsrf } from '@/api/client'

function reply(body?: unknown, status = 200) {
  return {
    ok: status < 400,
    status,
    text: async () => (body === undefined ? '' : JSON.stringify(body)),
    json: async () => body,
  } as Response
}

describe('家庭档案与邀请', () => {
  beforeEach(() => resetCsrf())
  afterEach(() => vi.unstubAllGlobals())

  it('创建档案前要求授权声明，提交后选择新档案', async () => {
    let created = false
    const fetch = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/auth/csrf')) return reply({ token: 'token' })
      if (url.endsWith('/elders') && init?.method === 'POST') {
        created = true
        return reply({ id: 1, fullName: '林阿姨', birthDate: null })
      }
      if (url.endsWith('/elders'))
        return reply(created ? [{ id: 1, fullName: '林阿姨', birthDate: null }] : [])
      if (url.endsWith('/permissions')) return reply({ canViewService: true, canManageFamily: true })
      return reply([])
    })
    vi.stubGlobal('fetch', fetch)
    const wrapper = mount(FamilyView, { global: { plugins: [createPinia()], stubs: { teleport: true } } })
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('新增服务对象'))!
      .trigger('click')
    await wrapper.get('#elder-name').setValue('林阿姨')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '创建档案')!
      .trigger('click')
    expect(fetch.mock.calls.some(([url, init]) => url.endsWith('/elders') && init?.method === 'POST')).toBe(
      false,
    )
    await wrapper.get('#elder-authorization').setValue(true)
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '创建档案')!
      .trigger('click')
    await flushPromises()
    const creation = fetch.mock.calls.find(
      ([url, init]) => url.endsWith('/elders') && init?.method === 'POST',
    )!
    expect(JSON.parse(String(creation[1]?.body))).toEqual({
      fullName: '林阿姨',
      birthDate: null,
      declaresAuthorization: true,
    })
    expect(wrapper.text()).toContain('林阿姨')
    wrapper.unmount()
  })

  it('没有档案时也能查看并接受邀请，成功后刷新档案', async () => {
    let accepted = false
    const fetch = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/auth/csrf')) return reply({ token: 'token' })
      if (url.endsWith('/family/invitations/7/accept')) {
        accepted = true
        return reply(undefined, 204)
      }
      if (url.endsWith('/family/invitations'))
        return reply(
          accepted
            ? []
            : [
                {
                  id: 7,
                  elder_id: 1,
                  elder_name: '林阿姨',
                  expires_at: '2030-01-01T12:00:00',
                  can_view_service: 1,
                  can_book: 1,
                },
              ],
        )
      if (url.endsWith('/elders') && init?.method === 'GET')
        return reply(accepted ? [{ id: 1, fullName: '林阿姨', birthDate: null }] : [])
      if (url.endsWith('/permissions')) return reply({ canViewService: true })
      return reply([])
    })
    vi.stubGlobal('fetch', fetch)
    const wrapper = mount(FamilyView, { global: { plugins: [createPinia()], stubs: { teleport: true } } })
    await flushPromises()
    expect(wrapper.text()).toContain('林阿姨')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '接受邀请')!
      .trigger('click')
    await flushPromises()
    expect(fetch.mock.calls.some(([url]) => url.endsWith('/family/invitations/7/accept'))).toBe(true)
    expect(wrapper.text()).toContain('林阿姨')
    wrapper.unmount()
  })

  it('邀请已失效时保留提示，不显示接受成功', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url.endsWith('/auth/csrf')) return reply({ token: 'token' })
        if (url.endsWith('/family/invitations/7/accept'))
          return reply({ code: 'INVITATION_EXPIRED', message: '邀请已过期' }, 409)
        if (url.endsWith('/family/invitations'))
          return reply([
            {
              id: 7,
              elder_id: 1,
              elder_name: '林阿姨',
              expires_at: '2030-01-01T12:00:00',
              can_view_service: 1,
            },
          ])
        return reply([])
      }),
    )
    const wrapper = mount(FamilyView, { global: { plugins: [createPinia()], stubs: { teleport: true } } })
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '接受邀请')!
      .trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('邀请已过期')
    expect(wrapper.text()).not.toContain('已接受邀请')
    wrapper.unmount()
  })

  it('有管理授权时修改姓名保留已有出生日期并刷新档案', async () => {
    let fullName = '林阿姨'
    const fetch = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/auth/csrf')) return reply({ token: 'token' })
      if (url.endsWith('/elders/1') && init?.method === 'PUT') {
        fullName = '林女士'
        return reply({ id: 1, fullName, birthDate: '1950-02-03' })
      }
      if (url.endsWith('/elders')) return reply([{ id: 1, fullName, birthDate: '1950-02-03' }])
      if (url.endsWith('/permissions')) return reply({ canViewService: true, canManageFamily: true })
      return reply([])
    })
    vi.stubGlobal('fetch', fetch)
    const wrapper = mount(FamilyView, { global: { plugins: [createPinia()], stubs: { teleport: true } } })
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '修改资料')!
      .trigger('click')
    expect((wrapper.get('#edit-elder-birth-date').element as HTMLInputElement).value).toBe('1950-02-03')
    await wrapper.get('#edit-elder-name').setValue('林女士')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '保存资料')!
      .trigger('click')
    await flushPromises()
    const call = fetch.mock.calls.find(([url, init]) => url.endsWith('/elders/1') && init?.method === 'PUT')!
    expect(JSON.parse(String(call[1]?.body))).toEqual({ fullName: '林女士', birthDate: '1950-02-03' })
    expect(wrapper.text()).toContain('林女士')
    wrapper.unmount()
  })

  it('无管理权限时隐藏修改入口；权限被撤销后提交显示后端错误', async () => {
    let canManage = false
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url.endsWith('/auth/csrf')) return reply({ token: 'token' })
        if (url.endsWith('/elders/1') && init?.method === 'PUT')
          return reply({ message: '没有管理家庭授权的权限' }, 403)
        if (url.endsWith('/elders')) return reply([{ id: 1, fullName: '林阿姨', birthDate: null }])
        if (url.endsWith('/permissions')) return reply({ canViewService: true, canManageFamily: canManage })
        return reply([])
      }),
    )
    const wrapper = mount(FamilyView, { global: { plugins: [createPinia()], stubs: { teleport: true } } })
    await flushPromises()
    expect(wrapper.findAll('button').some((b) => b.text() === '修改资料')).toBe(false)
    canManage = true
    await wrapper.find('button.elder').trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '修改资料')!
      .trigger('click')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '保存资料')!
      .trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('没有管理家庭授权的权限')
    expect(wrapper.text()).toContain('林阿姨')
    wrapper.unmount()
  })

  it('切换档案后旧请求晚到时不展示其他档案的管理入口', async () => {
    let resolveFirst!: (response: Response) => void
    let firstPermissionCalls = 0
    const firstPermission = new Promise<Response>((resolve) => {
      resolveFirst = resolve
    })
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url.endsWith('/elders'))
          return reply([
            { id: 1, fullName: '林阿姨', birthDate: null },
            { id: 2, fullName: '张阿姨', birthDate: null },
          ])
        if (url.endsWith('/elders/1/permissions')) {
          firstPermissionCalls += 1
          return firstPermissionCalls === 1
            ? reply({ canViewService: true, canManageFamily: true })
            : firstPermission
        }
        if (url.endsWith('/elders/2/permissions'))
          return reply({ canViewService: true, canManageFamily: false })
        return reply([])
      }),
    )
    const wrapper = mount(FamilyView, { global: { plugins: [createPinia()], stubs: { teleport: true } } })
    await flushPromises()
    await wrapper.findAll('button.elder')[0].trigger('click')
    await flushPromises()
    await wrapper.findAll('button.elder')[1].trigger('click')
    await flushPromises()
    resolveFirst(reply({ canViewService: true, canManageFamily: true }))
    await flushPromises()
    expect(wrapper.findAll('button').some((b) => b.text() === '修改资料')).toBe(false)
    wrapper.unmount()
  })

  it('有家庭管理权限时可维护老人服务偏好', async () => {
    let preferences = ['助浴']
    const fetch = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/auth/csrf')) return reply({ token: 'token' })
      if (url.endsWith('/elders')) return reply([{ id: 1, fullName: '林阿姨', birthDate: null }])
      if (url.endsWith('/elders/1/permissions')) return reply({ canViewService: true, canManageFamily: true })
      if (url.endsWith('/elders/1/preferences') && init?.method === 'POST') {
        preferences.push(JSON.parse(String(init.body)).tag)
        return reply(undefined, 204)
      }
      if (decodeURIComponent(url).endsWith('/elders/1/preferences/助浴') && init?.method === 'DELETE') {
        preferences = preferences.filter((tag) => tag !== '助浴')
        return reply(undefined, 204)
      }
      if (url.endsWith('/elders/1/preferences')) return reply(preferences)
      return reply([])
    })
    vi.stubGlobal('fetch', fetch)
    const wrapper = mount(FamilyView, { global: { plugins: [createPinia()], stubs: { teleport: true } } })
    await flushPromises()
    expect(wrapper.text()).toContain('服务偏好')
    expect(wrapper.text()).toContain('助浴')
    await wrapper.get('#preference-tag').setValue('陪诊')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '添加偏好')!
      .trigger('click')
    await flushPromises()
    expect(
      fetch.mock.calls.some(
        ([url, init]) => url.endsWith('/elders/1/preferences') && init?.method === 'POST',
      ),
    ).toBe(true)
    expect(wrapper.text()).toContain('陪诊')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '移除助浴')!
      .trigger('click')
    await flushPromises()
    expect(
      fetch.mock.calls.some(
        ([url, init]) =>
          decodeURIComponent(url).endsWith('/elders/1/preferences/助浴') && init?.method === 'DELETE',
      ),
    ).toBe(true)
    expect(wrapper.findAll('button').some((button) => button.text() === '移除助浴')).toBe(false)
    wrapper.unmount()
  })
})
