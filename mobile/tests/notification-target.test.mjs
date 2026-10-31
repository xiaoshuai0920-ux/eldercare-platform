import test from 'node:test'
import assert from 'node:assert/strict'
import { notificationTarget } from '../src/api/platform.js'

test('订单消息跳转到订单页并带上具体订单号', () => {
  assert.equal(
    notificationTarget({ title: '订单 #52 · 居家保洁', target_path: '/user/orders' }),
    '/pages/orders/index?orderId=52',
  )
})

test('生活提醒消息跳转到提醒页，不误带订单号', () => {
  assert.equal(
    notificationTarget({ title: '生活提醒', target_path: '/user/reminders' }),
    '/pages/reminders/index',
  )
})

test('服务人员消息跳转到服务工作台', () => {
  assert.equal(
    notificationTarget({ title: '订单 #52 · 居家保洁', target_path: '/worker' }),
    '/pages/worker/index',
  )
})

test('家庭类消息跳转到家庭授权页', () => {
  assert.equal(
    notificationTarget({ title: '家庭授权邀请', target_path: '/user/family' }),
    '/pages/family/index',
  )
})

test('管理员路径在移动端没有页面，返回 null 交由调用方提示', () => {
  assert.equal(notificationTarget({ title: '订单 #9 · 上门助餐', target_path: '/admin/operations' }), null)
})

test('未知或缺失的 target_path 返回 null 而不是跳到错误页面', () => {
  assert.equal(notificationTarget({ title: '系统通知', target_path: '/unknown/path' }), null)
  assert.equal(notificationTarget({ title: '系统通知' }), null)
  assert.equal(notificationTarget(null), null)
})

test('订单消息标题没有编号时只打开订单列表', () => {
  assert.equal(notificationTarget({ title: '订单状态已更新', target_path: '/user/orders' }), '/pages/orders/index')
})
