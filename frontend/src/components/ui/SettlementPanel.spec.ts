import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { userApi } from '@/api/endpoints'
import SettlementPanel from './SettlementPanel.vue'

const processing = {
  settlement: { status: 'PROCESSING' as const, amount: 80, method: 'SIMULATED', version: 1 },
  attempts: [{ attempt_ref: 'demo-ref', amount: 80, settlement_version: 1, remote_status: 'PROCESSING' }],
}

const waiting = {
  settlement: { status: 'WAITING_METHOD' as const, amount: 80, method: null, version: 1 },
  attempts: [],
}

describe('模拟结算', () => {
  beforeEach(() => {
    // jsdom 未实现 window.open，默认按“被拦截”处理，避免噪音输出
    vi.spyOn(window, 'open').mockReturnValue(null)
  })
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
    vi.spyOn(userApi, 'settlement').mockResolvedValueOnce(waiting).mockRejectedValueOnce(new Error('network'))
    vi.spyOn(userApi, 'createPaymentAttempt').mockRejectedValue(new Error('timeout'))
    const wrapper = mount(SettlementPanel, { props: { orderId: 7 } })
    await flushPromises()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('结果尚未确认')
    expect(wrapper.text()).not.toContain('发起模拟支付')
  })

  it('发起成功后在新标签打开已预填尝试信息的控制台', async () => {
    const opened = { location: { href: '' } } as unknown as Window
    vi.mocked(window.open).mockReturnValue(opened)
    vi.spyOn(userApi, 'settlement').mockResolvedValueOnce(waiting).mockResolvedValueOnce(processing)
    vi.spyOn(userApi, 'createPaymentAttempt').mockResolvedValue({
      attemptRef: 'demo-ref',
      orderRef: 7,
      settlementVersion: 1,
      amount: 80,
    })

    const wrapper = mount(SettlementPanel, { props: { orderId: 7 } })
    await flushPromises()
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(window.open).toHaveBeenCalledTimes(1)
    expect(opened.location.href).toContain('/console.html?v=demo-ref')
    expect(opened.location.href).toContain('#ref=demo-ref')
    expect(opened.location.href).toContain('order=7')
    expect(opened.location.href).toContain('amount=80.00')
    expect(wrapper.text()).toContain('已在新的标签页打开模拟支付控制台')
  })

  it('新标签被拦截时提示改用页面内链接', async () => {
    vi.spyOn(userApi, 'settlement').mockResolvedValueOnce(waiting).mockResolvedValueOnce(processing)
    vi.spyOn(userApi, 'createPaymentAttempt').mockResolvedValue({
      attemptRef: 'demo-ref',
      orderRef: 7,
      settlementVersion: 1,
      amount: 80,
    })

    const wrapper = mount(SettlementPanel, { props: { orderId: 7 } })
    await flushPromises()
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('浏览器拦截了新标签页')
    expect(wrapper.find('a[href*="console.html?v=demo-ref"]').exists()).toBe(true)
  })
})
