import test from 'node:test'
import assert from 'node:assert/strict'
import { platformApi } from '../src/api/platform.js'
import { resetSession } from '../src/api/request.js'

test('updates a reminder through the shared PUT API', async () => {
  resetSession()
  const requests = []
  const responses = [
    { statusCode: 200, data: { token: 'csrf-test' }, cookies: [] },
    { statusCode: 204, data: null, cookies: [] },
  ]
  globalThis.uni = { request(options) { requests.push(options); options.success(responses.shift()) } }
  const payload = { title: '复诊提醒', recurrence: 'DAILY', nextFireAt: '2026-11-02T09:00:00' }

  await platformApi.updateReminder(101, payload)

  assert.equal(requests[1].url.endsWith('/api/reminders/101'), true)
  assert.equal(requests[1].method, 'PUT')
  assert.deepEqual(requests[1].data, payload)
  assert.equal(requests[1].header['X-CSRF-TOKEN'], 'csrf-test')
})

test('revokes a family grant through the existing backend action', async () => {
  resetSession()
  const requests = []
  const responses = [
    { statusCode: 200, data: { token: 'csrf-test' }, cookies: [] },
    { statusCode: 204, data: null, cookies: [] },
  ]
  globalThis.uni = { request(options) { requests.push(options); options.success(responses.shift()) } }

  await platformApi.revokeGrant(10, 20)

  assert.equal(requests[1].url.endsWith('/api/elders/10/grants/20/revoke'), true)
  assert.equal(requests[1].method, 'POST')
  assert.equal(requests[1].header['X-CSRF-TOKEN'], 'csrf-test')
})

test('updates an elder profile through the existing PUT API', async () => {
  resetSession()
  const requests = []
  const responses = [
    { statusCode: 200, data: { token: 'csrf-test' }, cookies: [] },
    { statusCode: 200, data: { id: 10, fullName: '林阿姨', birthDate: '1940-01-01' }, cookies: [] },
  ]
  globalThis.uni = { request(options) { requests.push(options); options.success(responses.shift()) } }
  const payload = { fullName: '林阿姨', birthDate: '1940-01-01' }

  await platformApi.updateElder(10, payload)

  assert.equal(requests[1].url.endsWith('/api/elders/10'), true)
  assert.equal(requests[1].method, 'PUT')
  assert.deepEqual(requests[1].data, payload)
  assert.equal(requests[1].header['X-CSRF-TOKEN'], 'csrf-test')
})
