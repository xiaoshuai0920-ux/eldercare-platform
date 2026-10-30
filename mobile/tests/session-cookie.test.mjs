import test from 'node:test'
import assert from 'node:assert/strict'
import { sessionCookie } from '../src/api/session-cookie.js'

test('picks the latest JSESSIONID while ignoring cookie attributes and unrelated cookies', () => {
  assert.equal(sessionCookie(['theme=light; Path=/', 'JSESSIONID=first; HttpOnly', 'JSESSIONID=second; Path=/']), 'JSESSIONID=second')
})

test('returns null when the server expires the session cookie', () => {
  assert.equal(sessionCookie(['JSESSIONID=; Max-Age=0; Path=/']), null)
  assert.equal(sessionCookie(['JSESSIONID=stale; Max-Age=0; Path=/']), null)
})

test('returns undefined when no session cookie was supplied', () => {
  assert.equal(sessionCookie(['theme=light; Path=/']), undefined)
})
