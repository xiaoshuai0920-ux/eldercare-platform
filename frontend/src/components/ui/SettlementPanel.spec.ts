import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { userApi } from '@/api/endpoints'
import SettlementPanel from './SettlementPanel.vue'

const processing = {
  settlement: { status: 'PROCESSING' as const, amount: 80, method: 'SIMULATED', version: 1 },
  attempts: [{ attempt_ref: 'demo-ref', amount: 80, settlement_version: 1, remote_status: 'PROCESSING' }],
}

describe('模拟结算', () => {
  afterEach(() => vi.restoreAllMocks())

  it('同步查询失败时保留处理中，并可重新查询', async () => {
    vi.spyOn(userApi, 'settlement').mockResolvedValue(processing)
    vi.spyOn(userApi, 'syncSettlement').mockRejectedValue(new Error('network'))
    const wrapper = mount(SettlementPanel, { props: { orderId: 7 } })
    await flushPromises()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('处理中')
    expect(wrapper.text()).toContain('查询未完成')
    expect(wrapper.text()).not.toContain('支付失败')
  })

  it('发起请求结果不明且重新加载失败时，不提供重复发起按钮', async () => {
    vi.spyOn(userApi, 'settlement')
      .mockResolvedValueOnce({
        settlement: { status: 'WAITING_METHOD', amount: 80, method: null, version: 1 },
        attempts: [],
      })
      .mockRejectedValueOnce(new Error('network'))
    vi.spyOn(userApi, 'createPaymentAttempt').mockRejectedValue(new Error('timeout'))
    const wrapper = mount(SettlementPanel, { props: { orderId: 7 } })
    await flushPromises()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('结果尚未确认')
    expect(wrapper.text()).not.toContain('发起模拟支付')
  })
})
