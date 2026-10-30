import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import RemindersView from './RemindersView.vue'
import { resetCsrf } from '@/api/client'

function reply(body?: unknown, status = 200) {
  return {
    ok: status < 400,
    status,
    text: async () => (body === undefined ? '' : JSON.stringify(body)),
    json: async () => body,
  } as Response
}

const row = {
  id: 4,
  elder_id: 8,
  elder_name: '林阿姨',
  title: '散步',
  recurrence: 'WEEKLY',
  next_fire_at: '2099-04-05T08:30:00',
  status: 'ACTIVE',
}

describe('修改提醒', () => {
  beforeEach(() => resetCsrf())
  afterEach(() => vi.unstubAllGlobals())

  it('可编辑启用中的提醒，提交后按后端字段更新并刷新', async () => {
    let title = row.title
    const fetch = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/auth/csrf')) return reply({ token: 'token' })
      if (url.endsWith('/reminders/4') && init?.method === 'PUT') {
        title = '吃饭'
        return reply(undefined, 204)
      }
      if (url.endsWith('/reminders')) return reply([{ ...row, title }])
      return reply([])
    })
    vi.stubGlobal('fetch', fetch)
    const wrapper = mount(RemindersView, { global: { plugins: [createPinia()], stubs: { teleport: true } } })
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '修改')!
      .trigger('click')
    expect((wrapper.get('#edit-title').element as HTMLInputElement).value).toBe('散步')
    await wrapper.get('#edit-title').setValue('吃饭')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '保存修改')!
      .trigger('click')
    await flushPromises()
    const call = fetch.mock.calls.find(
      ([url, init]) => url.endsWith('/reminders/4') && init?.method === 'PUT',
    )!
    expect(JSON.parse(String(call[1]?.body))).toEqual({
      title: '吃饭',
      recurrence: 'WEEKLY',
      nextFireAt: '2099-04-05T08:30:00',
    })
    expect(wrapper.text()).toContain('吃饭')
    wrapper.unmount()
  })

  it('权限失效后显示后端错误，已停止的提醒不可编辑', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        if (url.endsWith('/auth/csrf')) return reply({ token: 'token' })
        if (url.endsWith('/reminders/4') && init?.method === 'PUT')
          return reply({ message: '没有管理提醒的权限' }, 403)
        if (url.endsWith('/reminders'))
          return reply([row, { ...row, id: 5, status: 'STOPPED', next_fire_at: null }])
        return reply([])
      }),
    )
    const wrapper = mount(RemindersView, { global: { plugins: [createPinia()], stubs: { teleport: true } } })
    await flushPromises()
    expect(wrapper.findAll('button').filter((b) => b.text() === '修改')).toHaveLength(1)
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '修改')!
      .trigger('click')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '保存修改')!
      .trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('没有管理提醒的权限')
    wrapper.unmount()
  })

  it('不提交过去的下次提醒时间', async () => {
    const fetch = vi.fn(async (url: string) => {
      if (url.endsWith('/reminders')) return reply([row])
      return reply([])
    })
    vi.stubGlobal('fetch', fetch)
    const wrapper = mount(RemindersView, { global: { plugins: [createPinia()], stubs: { teleport: true } } })
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '修改')!
      .trigger('click')
    await wrapper.get('#edit-next').setValue('2000-01-01T12:00')
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '保存修改')!
      .trigger('click')
    expect(wrapper.text()).toContain('下次提醒时间必须晚于当前时间')
    expect(fetch.mock.calls.some(([url]) => url.endsWith('/reminders/4'))).toBe(false)
    wrapper.unmount()
  })
})
