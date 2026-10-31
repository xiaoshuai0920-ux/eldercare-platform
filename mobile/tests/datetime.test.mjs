import test from 'node:test'
import assert from 'node:assert/strict'
import { formatDateTime } from '../src/utils/datetime.js'

const thisYear = new Date().getFullYear()

test('当年时间省略年份，保留到分钟', () => {
  assert.equal(formatDateTime(`${thisYear}-03-09T08:05:00`), '03-09 08:05')
  assert.equal(formatDateTime(`${thisYear}-03-09 08:05:00.123`), '03-09 08:05')
})

test('跨年时间保留年份，避免把往年当成今年', () => {
  assert.equal(formatDateTime('2019-12-31T23:59:59'), '2019-12-31 23:59')
})

test('毫秒时间戳按本地时间格式化', () => {
  const stamp = new Date(thisYear, 0, 2, 7, 8).getTime()
  assert.equal(formatDateTime(stamp), '01-02 07:08')
})

test('空值与无法识别的字符串原样返回，不抛异常', () => {
  assert.equal(formatDateTime(null), '')
  assert.equal(formatDateTime(''), '')
  assert.equal(formatDateTime('待定'), '待定')
})
