import { beforeEach, describe, expect, it, vi } from 'vitest'
import { resetCsrf } from './client'
import { adminApi, catalogApi } from './endpoints'

describe('catalog admin endpoints', () => {
  beforeEach(() => resetCsrf())

  it('loads coverage using the admin route for unpublished items', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => '[]',
    })
    vi.stubGlobal('fetch', fetchMock)

    await catalogApi.itemAreas(42)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/catalog/admin/items/42/areas')
  })

  it('records a service-incident resolution through the admin route', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => ({ token: 'csrf-token' }),
        text: async () => JSON.stringify({ token: 'csrf-token' }),
      })
      .mockResolvedValueOnce({ ok: true, status: 204, text: async () => '' })
    vi.stubGlobal('fetch', fetchMock)

    await adminApi.resolveIncident(7, '已联系家属并登记方案')

    expect(fetchMock.mock.calls[1][0]).toBe('/api/admin/incidents/7/resolve')
    expect((fetchMock.mock.calls[1][1] as RequestInit).method).toBe('POST')
    expect(((fetchMock.mock.calls[1][1] as RequestInit).headers as Headers).get('X-CSRF-TOKEN')).toBe(
      'csrf-token',
    )
  })

  it('schedules a supplement and proposes an incident fee through existing backend routes', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        json: async () => ({ token: 'csrf-token' }),
        text: async () => JSON.stringify({ token: 'csrf-token' }),
      })
      .mockResolvedValueOnce({ ok: true, status: 204, text: async () => '' })
      .mockResolvedValueOnce({ ok: true, status: 204, text: async () => '' })
    vi.stubGlobal('fetch', fetchMock)

    await adminApi.scheduleSupplement(18, '2026-11-01T09:00', '补足服务时长')
    await adminApi.proposeFeeAdjustment(18, 30, '按实际服务核实')

    expect(fetchMock.mock.calls[1][0]).toBe('/api/admin/orders/18/supplements')
    expect(JSON.parse((fetchMock.mock.calls[1][1] as RequestInit).body as string)).toEqual({
      startAt: '2026-11-01T09:00',
      reason: '补足服务时长',
    })
    expect(fetchMock.mock.calls[2][0]).toBe('/api/orders/18/fee-adjustments')
    expect(JSON.parse((fetchMock.mock.calls[2][1] as RequestInit).body as string)).toEqual({
      amount: 30,
      reason: '按实际服务核实',
    })
  })
})
