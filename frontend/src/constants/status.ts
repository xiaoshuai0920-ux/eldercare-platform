import type { OrderStatus, StatusMeta, StatusTone, Tone } from '@/types'

/** 状态中文名与色调的唯一来源：与后端 constants/status.ts 保持一致 */
export const ORDER_STATUS: Record<OrderStatus, StatusMeta> = {
  WAITING_ASSIGNMENT: { label: '待派单', tone: 'neutral' },
  WAITING_ACCEPTANCE: { label: '待接单', tone: 'action' },
  WAITING_SERVICE: { label: '待服务', tone: 'progress' },
  IN_SERVICE: { label: '服务中', tone: 'progress' },
  WAITING_CONFIRMATION: { label: '待确认', tone: 'action' },
  COMPLETED: { label: '已完成', tone: 'success' },
  CANCELLED: { label: '已取消', tone: 'neutral' },
  INTERRUPTED: { label: '服务中断', tone: 'danger' },
  ABNORMAL_CLOSED: { label: '异常结案', tone: 'danger' },
}

export const ASSIGNMENT_STATUS: Record<string, StatusMeta> = {
  PENDING: { label: '待接单', tone: 'action' },
  ACCEPTED: { label: '已接单', tone: 'progress' },
  REJECTED: { label: '已拒绝', tone: 'danger' },
  REPLACED: { label: '已改派', tone: 'neutral' },
  RELEASED: { label: '已释放', tone: 'neutral' },
}

export const ACCOUNT_STATUS: Record<string, StatusMeta> = {
  ACTIVE: { label: '正常', tone: 'success' },
  PENDING_ACTIVATION: { label: '待激活', tone: 'action' },
  DISABLED: { label: '已停用', tone: 'neutral' },
  CLOSED: { label: '已注销', tone: 'neutral' },
}

export const SETTLEMENT_STATUS: Record<string, StatusMeta> = {
  WAITING_METHOD: { label: '待选择方式', tone: 'action' },
  PROCESSING: { label: '处理中', tone: 'progress' },
  SETTLED: { label: '已结算', tone: 'success' },
  FAILED: { label: '结算失败', tone: 'danger' },
}

export const PRIORITY: Record<string, StatusMeta> = {
  NORMAL: { label: '普通', tone: 'neutral' },
  ACTION: { label: '待处理', tone: 'action' },
  HIGH: { label: '高优先级', tone: 'danger' },
}

export const LEAVE_STATUS: Record<string, StatusMeta> = {
  PENDING: { label: '待审批', tone: 'action' },
  APPROVED: { label: '已批准', tone: 'success' },
  REJECTED: { label: '已拒绝', tone: 'danger' },
}

export const AUTH_STATUS: Record<string, StatusMeta> = {
  ACTIVE: { label: '生效中', tone: 'success' },
  REVOKED: { label: '已撤回', tone: 'neutral' },
}

export const VERIFY_STATUS: Record<string, StatusMeta> = {
  VERIFIED: { label: '已核验', tone: 'success' },
  PENDING: { label: '待核验', tone: 'action' },
  REVOKED: { label: '已撤回', tone: 'neutral' },
}

export const ORDER_GROUP: Record<string, { label: string; tone: Tone }> = {
  action: { label: '待你处理', tone: 'action' },
  active: { label: '进行中', tone: 'progress' },
  upcoming: { label: '待服务', tone: 'progress' },
  done: { label: '已完成', tone: 'success' },
}

export function statusMeta(dict: Record<string, StatusMeta>, value: string): StatusMeta {
  return dict[value] ?? { label: value || '未知', tone: 'neutral' as StatusTone }
}
