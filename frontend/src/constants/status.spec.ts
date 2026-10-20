import { describe, expect, it } from 'vitest'
import { ACCOUNT_STATUS, ORDER_STATUS, PRIORITY, SETTLEMENT_STATUS, statusMeta } from './status'
import { formatAmount, formatDateTime, formatDuration } from '../utils/format'

describe('状态字典', () => {
  it('覆盖订单状态机的全部合法状态', () => {
    const statuses = ['WAITING_ASSIGNMENT', 'WAITING_ACCEPTANCE', 'WAITING_SERVICE', 'IN_SERVICE',
      'WAITING_CONFIRMATION', 'COMPLETED', 'CANCELLED', 'INTERRUPTED', 'ABNORMAL_CLOSED']
    for (const status of statuses) expect(ORDER_STATUS[status], status).toBeDefined()
  })

  it('覆盖账号、结算与优先级的关键状态', () => {
    expect(ACCOUNT_STATUS.ACTIVE.label).toBe('正常')
    expect(SETTLEMENT_STATUS.WAITING_METHOD.label).toBe('待选择方式')
    expect(PRIORITY.HIGH.tone).toBe('danger')
  })

  it('未知状态回退为原始值而不是空白', () => {
    expect(statusMeta(ORDER_STATUS, 'UNKNOWN_STATE')).toEqual({ label: 'UNKNOWN_STATE', tone: 'neutral' })
    expect(statusMeta(ORDER_STATUS, '')).toEqual({ label: '未知', tone: 'neutral' })
  })
})

describe('格式化工具', () => {
  it('金额固定两位小数并带人民币符号', () => {
    expect(formatAmount(45)).toBe('¥45.00')
    expect(formatAmount('180.5')).toBe('¥180.50')
    expect(formatAmount(null)).toBe('—')
  })

  it('时长按小时与分钟组合展示', () => {
    expect(formatDuration(45)).toBe('45 分钟')
    expect(formatDuration(120)).toBe('2 小时')
    expect(formatDuration(90)).toBe('1 小时 30 分钟')
    expect(formatDuration(0)).toBe('—')
  })

  it('非法时间返回占位符', () => {
    expect(formatDateTime(null)).toBe('—')
    expect(formatDateTime('not-a-date')).toBe('—')
    expect(formatDateTime('2026-10-20T09:00:00')).toContain('2026')
  })
})
