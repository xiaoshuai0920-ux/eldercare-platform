export type StatusTone = 'neutral' | 'progress' | 'action' | 'success' | 'danger'

/** 标签色调：五档 tone + 稿中用于说明信息的 info */
export type Tone = StatusTone | 'info'

export interface StatusMeta {
  label: string
  tone: StatusTone
}

export type OrderStatus =
  | 'WAITING_ASSIGNMENT'
  | 'WAITING_ACCEPTANCE'
  | 'WAITING_SERVICE'
  | 'IN_SERVICE'
  | 'WAITING_CONFIRMATION'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'INTERRUPTED'
  | 'ABNORMAL_CLOSED'

export type ServiceCategory = 'MEAL' | 'CLEANING' | 'ESCORT'

export interface ServiceItem {
  id: number
  category: ServiceCategory
  name: string
  /** 分类下的具体形态，如「上门」「慢病」 */
  tagline: string
  description: string
  pricingModel: 'PER_TIME' | 'HOURLY'
  price: number
  minMinutes: number
  rule: string
  covered: string[]
}

export interface TimelineEvent {
  time: string
  title: string
  desc: string
  state: 'done' | 'active' | 'danger' | 'idle'
}

export interface Order {
  id: string
  elder: string
  elderAge: number
  item: string
  category: ServiceCategory
  street: string
  address: string
  startAt: string
  endAt: string
  durationMinutes: number
  worker: string | null
  amount: number
  status: OrderStatus
  /** 是否属于当前登录家属的订单（演示数据用） */
  mine?: boolean
  createdAt: string
  actualMinutes?: number
  confirmedAt?: string
  settled?: boolean
  timeline: TimelineEvent[]
}

export interface FamilyAuth {
  id: number
  name: string
  relation: string
  scope: string
  status: 'active' | 'revoked'
  since: string
}

export interface Elder {
  id: number
  name: string
  age: number
  gender: '男' | '女'
  relation: string
  street: string
  address: string
  phone: string
  verified: 'VERIFIED' | 'PENDING'
  verifiedAt: string
  verifiedBy: string
  diet: string
  mobility: string
  tags: string[]
  auths: FamilyAuth[]
  dispute: { count: number; orderId: string; result: string; handledAt: string } | null
}

export interface WorkerTask {
  orderId: string
  item: string
  elder: string
  street: string
  address: string
  startAt: string
  endAt: string
  durationMinutes: number
  actualMinutes?: number
  status: OrderStatus
  diet?: string
}

export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface LeaveRecord {
  id: number
  type: string
  range: string
  hours: string
  reason: string
  status: LeaveStatus
  submittedAt: string
  approver?: string
  note?: string
}

export interface NoticeMessage {
  id: number
  kind: 'order' | 'system'
  title: string
  desc: string
  time: string
  read: boolean
  tag?: { label: string; tone: Tone }
  action?: { label: string; to: string }
  orderId?: string
}

export interface Reminder {
  id: number
  icon: 'pill' | 'calendar' | 'card' | 'clock'
  title: string
  rule: string
  detail: string
  target: string
  channel: string
  enabled: boolean
  tag?: { label: string; tone: Tone }
}

export interface Candidate {
  id: number
  name: string
  quals: string
  streets: string
  slot: string
  load: string
  recommended?: boolean
  disabled?: boolean
  disabledReason?: string
}

export interface KpiItem {
  label: string
  value: string | number
  hint?: string
  tone?: 'default' | 'action' | 'danger'
}
