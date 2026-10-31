import test from 'node:test'
import assert from 'node:assert/strict'
import { requireRole } from '../src/api/platform.js'
import { resetSession } from '../src/api/request.js'

// navigate() 内置 300ms 去重守卫，便于表单重复点击和首屏重复 onShow 只触发一次跳转。
const settled = () => new Promise((resolve) => setTimeout(resolve, 330))

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
    showModal(options) { calls.push({ showModal: options.title }) },
    showToast(options) { calls.push({ showToast: options.title }) },
  }
  return calls
}

test('an expired session navigates to the login page once instead of redirecting twice', async () => {
  resetSession()
  const calls = mountUni([{ statusCode: 401, data: { message: '会话已过期' }, cookies: [] }])

  const user = await requireRole(['USER'])

  assert.equal(user, null)
  assert.deepEqual(calls.filter((call) => call.reLaunch), [{ reLaunch: '/pages/login/index' }])
  await settled()
})

test('a worker opening a user page is sent to the worker workbench', async () => {
  resetSession()
  const calls = mountUni([{ statusCode: 200, data: { role: 'WORKER', name: '服务人员' }, cookies: [] }])

  const user = await requireRole(['USER'])

  assert.equal(user, null)
  assert.deepEqual(calls.filter((call) => call.reLaunch), [{ reLaunch: '/pages/worker/index' }])
  await settled()
})

test('an unrecognisable role is sent back to login so the home page cannot bounce in a loop', async () => {
  resetSession()
  const calls = mountUni([{ statusCode: 200, data: { name: '无角色账号' }, cookies: [] }])

  const user = await requireRole(['USER'])

  assert.equal(user, null)
  assert.deepEqual(calls.filter((call) => call.reLaunch), [{ reLaunch: '/pages/login/index' }])
  assert.equal(calls.some((call) => call.showToast), true)
  await settled()
})

test('an allowed role returns the account without touching the router', async () => {
  resetSession()
  const calls = mountUni([{ statusCode: 200, data: { role: 'USER', name: '林阿姨女儿' }, cookies: [] }])

  const user = await requireRole(['USER'])

  assert.equal(user.name, '林阿姨女儿')
  assert.equal(calls.some((call) => call.reLaunch), false)
})

test('a session expiry during navigation eventually opens login', async () => {
  resetSession()
  const calls = []
  let finishFirst
  globalThis.uni = {
    request(options) { options.success({ statusCode: 401, data: { message: '会话已过期' }, cookies: [] }) },
    reLaunch(options) {
      calls.push(options.url)
      if (!finishFirst) finishFirst = options.complete
      else options.complete?.()
    },
  }
  const { navigate } = await import('../src/api/platform.js')
  navigate('/pages/dashboard/index')
  await requireRole(['USER'])
  finishFirst()
  await settled()
  assert.deepEqual(calls, ['/pages/dashboard/index', '/pages/login/index'])
  await settled()
})
