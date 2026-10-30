import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { resetCsrf } from '@/api/client'
import WorkerSchedule from './WorkerSchedule.vue'

function reply(body?: unknown, status = 200) {
  return {
    ok: status < 400,
    status,
    text: async () => (body === undefined ? '' : JSON.stringify(body)),
    json: async () => body,
  } as Response
}

describe('人员排班', () => {
  beforeEach(() => resetCsrf())
  afterEach(() => vi.unstubAllGlobals())

  it('显示既有排班并新增工作时段', async () => {
    let added = false
    const fetch = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/auth/csrf')) return reply({ token: 'token' })
      if (url.endsWith('/admin/workers/3/schedules') && init?.method === 'POST') {
        added = true
        return reply(undefined, 204)
      }
      if (url.endsWith('/admin/workers/3/schedules'))
        return reply(added ? [{ id: 1, weekday: 1, start_time: '08:00:00', end_time: '12:00:00' }] : [])
      return reply([])
    })
    vi.stubGlobal('fetch', fetch)
    const wrapper = mount(WorkerSchedule, { props: { workerId: 3, workerName: '周师傅' } })
    await flushPromises()
    await wrapper.get('#schedule-day').setValue('1')
    await wrapper.get('#schedule-start').setValue('08:00')
    await wrapper.get('#schedule-end').setValue('12:00')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const call = fetch.mock.calls.find(
      ([url, init]) => url.endsWith('/schedules') && init?.method === 'POST',
    )!
    expect(JSON.parse(String(call[1]?.body))).toEqual({ weekday: 1, startTime: '08:00', endTime: '12:00' })
    expect(wrapper.text()).toContain('08:00')
    wrapper.unmount()
  })

  it('拒绝无效时段并显示后端冲突错误', async () => {
    const fetch = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/auth/csrf')) return reply({ token: 'token' })
      if (url.endsWith('/schedules') && init?.method === 'POST')
        return reply({ message: '工作时段重叠' }, 409)
      return reply([])
    })
    vi.stubGlobal('fetch', fetch)
    const wrapper = mount(WorkerSchedule, { props: { workerId: 3, workerName: '周师傅' } })
    await flushPromises()
    await wrapper.get('#schedule-start').setValue('12:00')
    await wrapper.get('#schedule-end').setValue('08:00')
    await wrapper.get('form').trigger('submit')
    expect(wrapper.text()).toContain('结束时间必须晚于开始时间')
    expect(fetch.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
    await wrapper.get('#schedule-end').setValue('16:00')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('工作时段重叠')
    wrapper.unmount()
  })

  it('编辑既有时段并提交 PUT', async () => {
    const rows = [{ id: 8, weekday: 1, start_time: '08:00:00', end_time: '12:00:00' }]
    const fetch = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/auth/csrf')) return reply({ token: 'token' })
      if (url.endsWith('/schedules/8') && init?.method === 'PUT') {
        rows[0] = { ...rows[0], weekday: 2, start_time: '09:00:00', end_time: '13:00:00' }
        return reply(undefined, 204)
      }
      if (url.endsWith('/schedules')) return reply(rows)
      return reply(undefined, 404)
    })
    vi.stubGlobal('fetch', fetch)
    const wrapper = mount(WorkerSchedule, { props: { workerId: 3, workerName: '周师傅' } })
    await flushPromises()
    await wrapper.get('[aria-label="修改周一排班"]').trigger('click')
    await wrapper.get('#schedule-day').setValue('2')
    await wrapper.get('#schedule-start').setValue('09:00')
    await wrapper.get('#schedule-end').setValue('13:00')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const call = fetch.mock.calls.find(
      ([url, init]) => url.endsWith('/schedules/8') && init?.method === 'PUT',
    )!
    expect(JSON.parse(String(call[1]?.body))).toEqual({ weekday: 2, startTime: '09:00', endTime: '13:00' })
    expect(wrapper.text()).toContain('周二 09:00—13:00')
    wrapper.unmount()
  })

  it('确认后删除已有时段并刷新列表', async () => {
    let exists = true
    const fetch = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.endsWith('/auth/csrf')) return reply({ token: 'token' })
      if (url.endsWith('/schedules/8') && init?.method === 'DELETE') {
        exists = false
        return reply(undefined, 204)
      }
      if (url.endsWith('/schedules'))
        return reply(exists ? [{ id: 8, weekday: 1, start_time: '08:00:00', end_time: '12:00:00' }] : [])
      return reply(undefined, 404)
    })
    vi.stubGlobal('fetch', fetch)
    vi.stubGlobal(
      'confirm',
      vi.fn(() => true),
    )
    const wrapper = mount(WorkerSchedule, { props: { workerId: 3, workerName: '周师傅' } })
    await flushPromises()
    await wrapper.get('[aria-label="删除周一排班"]').trigger('click')
    await flushPromises()
    expect(
      fetch.mock.calls.some(([url, init]) => url.endsWith('/schedules/8') && init?.method === 'DELETE'),
    ).toBe(true)
    expect(wrapper.text()).toContain('暂无排班。')
    wrapper.unmount()
  })
})
