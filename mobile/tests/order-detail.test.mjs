import test from 'node:test'
import assert from 'node:assert/strict'
import { platformApi } from '../src/api/platform.js'
import { resetSession } from '../src/api/request.js'

function mountUni(responses) {
  const calls = []
  globalThis.uni = {
    request(options) {
      calls.push(options)
      const next = responses.shift()
      options.success(next || { statusCode: 200, data: {}, cookies: [] })
    },
    reLaunch(options) {
      calls.push({ reLaunch: options.url })
      options.complete && options.complete({})
    },
    showModal() {},
    showToast() {},
    pageScrollTo() {},
  }
  return calls
}

test('未评价订单的评价接口返回 null，不再阻断同一订单的结算读取', async () => {
  resetSession()
  const calls = mountUni([
    { statusCode: 404, data: { code: 'REVIEW_NOT_FOUND', message: '尚未评价' }, cookies: [] },
    { statusCode: 200, data: { settlement: { status: 'WAITING_METHOD', amount: 60 }, attempts: [] }, cookies: [] },
  ])

  assert.equal(await platformApi.review(62), null)
  const view = await platformApi.settlement(62)
  assert.equal(view.settlement.status, 'WAITING_METHOD')
  assert.equal(calls.length, 2)
})

test('评价接口的其他失败仍然向上抛出，不会被当成“未评价”', async () => {
  resetSession()
  mountUni([{ statusCode: 403, data: { code: 'PERMISSION_DENIED', message: '没有查看权限' }, cookies: [] }])

  await assert.rejects(() => platformApi.review(1), /没有查看权限/)
})

test('业务操作中会话失效会跳转登录页，且只跳一次', async () => {
  resetSession()
  const calls = mountUni([
    { statusCode: 200, data: { token: 'csrf-after-expiry' }, cookies: [] },
    { statusCode: 401, data: { message: '会话已过期' }, cookies: [] },
  ])

  await assert.rejects(() => platformApi.confirmOrder(1), /会话已过期/)
  assert.deepEqual(calls.filter((call) => call.reLaunch), [{ reLaunch: '/pages/login/index' }])
  await new Promise((resolve) => setTimeout(resolve, 330))
})
