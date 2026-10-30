import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ORDER_STATUS } from '@/constants/status'
import { userApi } from '@/api/endpoints'
import { useOrderStore } from './orders'

describe('用户订单分组', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('待派单和待接单可在待服务中查看，只有待确认需要用户处理', () => {
    const store = useOrderStore()
    store.list = Object.keys(ORDER_STATUS).map((status, index) => ({
      id: index + 1,
      elder_id: 1,
      item_name_snapshot: '示例服务',
      category_snapshot: 'CLEAN',
      status,
      start_at: '2099-01-01T09:00:00',
      end_at: '2099-01-01T10:00:00',
      payable_amount: 80,
    }))

    expect(store.group('upcoming').map((row) => row.status)).toEqual([
      'WAITING_ASSIGNMENT',
      'WAITING_ACCEPTANCE',
      'WAITING_SERVICE',
    ])
    expect(store.group('action').map((row) => row.status)).toEqual(['WAITING_CONFIRMATION'])
    const visible = ['action', 'active', 'upcoming', 'done'] as const
    const ids = visible.flatMap((key) => store.group(key).map((row) => row.id))
    expect(ids.sort((a, b) => a - b)).toEqual(store.list.map((row) => row.id))
  })

  it('跨页筛选由服务端完成，计数基于全部授权订单', async () => {
    const request = vi.spyOn(userApi, 'orders').mockResolvedValue({
      items: [
        {
          id: 51,
          elder_id: 1,
          item_name_snapshot: '示例服务',
          category_snapshot: 'CLEAN',
          status: 'WAITING_ASSIGNMENT',
          start_at: '2099-01-01T09:00:00',
          end_at: '2099-01-01T10:00:00',
          payable_amount: 80,
        },
      ],
      page: 2,
      size: 10,
      total: 65,
      counts: { total: 70, pending: 2, active: 0, upcoming: 65, done: 3 },
    })
    const store = useOrderStore()
    await store.load(2, 'upcoming')
    expect(request).toHaveBeenCalledWith(2, 10, 'upcoming')
    expect(store.page).toBe(2)
    expect(store.total).toBe(65)
    expect(store.counts.total).toBe(70)
    expect(store.counts.upcoming).toBe(65)
    expect(store.group('upcoming')[0].id).toBe(51)
    request.mockRestore()
  })

  it('末页订单减少后回到仍存在的最后一页', async () => {
    const counts = { total: 10, pending: 0, active: 0, upcoming: 10, done: 0 }
    const request = vi
      .spyOn(userApi, 'orders')
      .mockResolvedValueOnce({ items: [], page: 2, size: 10, total: 10, counts })
      .mockResolvedValueOnce({ items: [], page: 1, size: 10, total: 10, counts })
    const store = useOrderStore()
    await store.load(2, 'upcoming')
    expect(request).toHaveBeenNthCalledWith(2, 1, 10, 'upcoming')
    expect(store.page).toBe(1)
    request.mockRestore()
  })

  it('较早的筛选请求晚返回时不能覆盖最新结果', async () => {
    const counts = { total: 1, pending: 1, active: 0, upcoming: 0, done: 0 }
    const response = { items: [], page: 1, size: 10, total: 1, counts }
    let finishOld!: (value: typeof response) => void
    const request = vi
      .spyOn(userApi, 'orders')
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            finishOld = resolve
          }),
      )
      .mockResolvedValueOnce(response)
    const store = useOrderStore()
    const old = store.load(1, 'upcoming')
    await store.load(1, 'action')
    finishOld({ ...response, total: 99 })
    await old
    expect(store.total).toBe(1)
    expect(store.counts.pending).toBe(1)
    request.mockRestore()
  })
})
