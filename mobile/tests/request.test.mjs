import test from 'node:test'
import assert from 'node:assert/strict'
import { request, resetSession } from '../src/api/request.js'

test('uses the renewed session after login and obtains a fresh CSRF token', async () => {
  resetSession()
  const requests = []
  const responses = [
    { statusCode: 200, data: { token: 'before' }, cookies: ['JSESSIONID=old; Path=/'] },
    { statusCode: 200, data: { role: 'USER' }, cookies: ['JSESSIONID=new; Path=/'] },
    { statusCode: 200, data: { token: 'after' }, cookies: [] },
    { statusCode: 204, data: null, cookies: [] },
  ]
  globalThis.uni = { request(options) { requests.push(options); options.success(responses.shift()) } }
  await request('/auth/login', 'POST', { username: 'demo', password: 'demo' })
  await request('/orders/1/confirm', 'POST')
  assert.equal(requests[1].header.Cookie, 'JSESSIONID=old')
  assert.equal(requests[1].header['X-CSRF-TOKEN'], 'before')
  assert.equal(requests[2].header.Cookie, 'JSESSIONID=new')
  assert.equal(requests[3].header['X-CSRF-TOKEN'], 'after')
})

test('a 401 clears the session so it is not sent again', async () => {
  resetSession()
  const requests = []
  const responses = [
    { statusCode: 200, data: { token: 't' }, cookies: ['JSESSIONID=old; Path=/'] },
    { statusCode: 401, data: { message: '会话已过期' }, cookies: [] },
    { statusCode: 200, data: {}, cookies: [] },
  ]
  globalThis.uni = { request(options) { requests.push(options); options.success(responses.shift()) } }
  await request('/auth/csrf')
  await assert.rejects(request('/auth/me'), /会话已过期/)
  await request('/auth/csrf')
  assert.equal(requests[2].header.Cookie, undefined)
})
