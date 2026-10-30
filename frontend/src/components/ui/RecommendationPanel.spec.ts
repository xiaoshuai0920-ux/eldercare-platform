import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { catalogApi } from '@/api/endpoints'
import RecommendationPanel from './RecommendationPanel.vue'

describe('服务推荐', () => {
  afterEach(() => vi.restoreAllMocks())
  it('选择老人和街道后显示后端理由，切换街道清除旧结果', async () => {
    const request = vi
      .spyOn(catalogApi, 'recommendations')
      .mockResolvedValue([{ id: 1, name: '保洁', reason: '符合偏好：保洁' }])
    const wrapper = mount(RecommendationPanel, {
      props: {
        elders: [{ id: 3, fullName: '林阿姨', birthDate: null }],
        areas: [{ id: 4, parent_id: null, level: 'STREET', name: '街道', full_name: '示例街道' }],
        items: [],
      },
    })
    const selects = wrapper.findAll('select')
    await selects[0]!.setValue('3')
    await selects[1]!.setValue('4')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(request).toHaveBeenCalledWith(3, 4)
    expect(wrapper.text()).toContain('符合偏好：保洁')
    await selects[1]!.setValue('0')
    expect(wrapper.text()).not.toContain('符合偏好：保洁')
  })
})
