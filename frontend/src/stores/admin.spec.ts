import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { adminApi, catalogApi } from '@/api/endpoints'
import { useAdminStore } from './admin'

describe('管理端派单候选', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('查询失败时显示错误，并在重新查询成功后清除错误', async () => {
    const request = vi
      .spyOn(adminApi, 'candidates')
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce([{ id: 7, display_name: '示例人员' }])
    const admin = useAdminStore()

    await admin.loadCandidates(1)
    expect(admin.errors.candidates).toBeTruthy()
    expect(admin.candidates).toEqual([])

    await admin.loadCandidates(1)
    expect(request).toHaveBeenCalledTimes(2)
    expect(admin.errors.candidates).toBeUndefined()
    expect(admin.candidates[0]?.id).toBe(7)
  })
})

describe('管理端服务目录操作', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('创建服务项目成功后刷新目录列表', async () => {
    const create = vi.spyOn(catalogApi, 'createAdminItem').mockResolvedValue({ id: 12 })
    const list = vi.spyOn(catalogApi, 'adminItems').mockResolvedValue([
      {
        id: 12,
        category: 'CLEANING',
        name: '上门保洁',
        description: '',
        pricing_model: 'HOURLY',
        unit_price: 40,
        min_minutes: 60,
        rule_text: '',
      },
    ])
    const admin = useAdminStore()

    await expect(admin.createCatalogItem({} as never)).resolves.toBe(true)
    expect(create).toHaveBeenCalledOnce()
    expect(list).toHaveBeenCalledOnce()
    expect(admin.catalogItems[0]?.id).toBe(12)
  })
})
