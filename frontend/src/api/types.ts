/** 后端 DTO：字段名与接口返回保持一致（snake_case 原样保留） */
export type Role = 'USER' | 'WORKER' | 'ADMIN'

export interface SessionUser {
  id: number
  username: string
  name: string
  role: Role
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface OrderStatusRow {
  id: number
  status: string
  start_at: string
  end_at: string
  item_name_snapshot: string
  payable_amount: number
}

/** GET /orders 列表行 */
export interface UserOrderRow extends OrderStatusRow {
  elder_id: number
  category_snapshot: string
}

export interface UserOrderPage extends Page<UserOrderRow> {
  counts: { total: number; pending: number; active: number; upcoming: number; done: number }
}

/** GET /orders/{id} */
export interface UserOrderDetail extends UserOrderRow {
  creator_id: number
  item_id: number
  rule_snapshot: string
  pricing_model_snapshot: string
  unit_price_snapshot: number
  duration_minutes: number
  original_amount: number
  street_id: number
  street_name_snapshot: string
  address_snapshot: string
  created_at: string
  updated_at: string
}

export interface SettlementView {
  settlement: {
    status: 'WAITING_METHOD' | 'PROCESSING' | 'SETTLED' | 'FAILED'
    amount: number
    method: string | null
    version: number
  }
  attempts: { attempt_ref: string; amount: number; settlement_version: number; remote_status: string }[]
}

/** GET /admin/orders 列表行 */
export interface AdminOrderRow extends OrderStatusRow {
  street_name_snapshot: string
  elder_name: string
}

export interface AdminOrderEvent {
  action: string
  old_status: string | null
  new_status: string | null
  note: string | null
  created_at: string
  actor_id: number
}

export interface AdminAssignment {
  id: number
  worker_id: number
  worker_name: string
  status: string
  assigned_at: string
  responded_at: string | null
  rejection_reason: string | null
}

export interface AdminOrderDetail {
  order: UserOrderDetail & { elder_name: string; creator_name: string }
  events: AdminOrderEvent[]
  assignments: AdminAssignment[]
  fulfillments: Record<string, unknown>[]
  supplements: Record<string, unknown>[]
  incidents: { id: number; reason: string; status: string; created_at: string }[]
  feeAdjustments: Record<string, unknown>[]
  disputes: { id: number; reason: string; status: string; created_at: string }[]
}

export interface Candidate {
  id: number
  display_name: string
}

export interface ElderRow {
  id: number
  fullName: string
  birthDate: string | null
}

export interface FamilyInvitation {
  id: number
  elder_id: number
  elder_name: string
  expires_at: string
  can_view_service: number | boolean
  can_book: number | boolean
  can_cancel: number | boolean
  can_confirm: number | boolean
  can_review: number | boolean
  can_confirm_fee: number | boolean
  can_manage_reminder: number | boolean
  can_manage_family: number | boolean
}

export interface Permissions {
  canViewService: boolean
  canBook: boolean
  canManageReminder: boolean
  canCancel: boolean
  canConfirm: boolean
  canReview: boolean
  canConfirmFee: boolean
  canManageFamily: boolean
}

export interface Area {
  id: number
  parent_id: number | null
  level: string
  name: string
  full_name: string
}

export interface CatalogItem {
  id: number
  category: string
  name: string
  description: string
  pricing_model: string
  unit_price: number
  min_minutes: number
  max_minutes?: number
  available_from?: string
  available_to?: string
  advance_minutes?: number
  rule_text: string
  active?: boolean
}

export interface DashboardUser {
  waiting: number
  active: number
  completed: number
  recentOrders: {
    id: number
    item_name_snapshot: string
    start_at: string
    status: string
    payable_amount: number
  }[]
}

export interface DashboardAdmin {
  waitingAssignment: number
  waitingConfirmation: number
  inService: number
  completed: number
  activeWorkers: number
  recentOrders: {
    id: number
    item_name_snapshot: string
    start_at: string
    status: string
    payable_amount: number
    elder_name: string
  }[]
}

export interface AdminTodo {
  orderId: number
  title: string
  reason: string
  priority: string
}

export interface WorkerTaskRow {
  order_id: number
  item_name_snapshot: string
  start_at: string
  end_at: string
  street_name_snapshot?: string
  status: string
  assignment_status: string
}

export interface WorkerTaskDetail extends WorkerTaskRow {
  category_snapshot: string
  rule_snapshot: string
  duration_minutes: number
  address_snapshot: string
  elder_name: string
  assigned_at?: string
}

export interface DashboardWorker {
  accepting: boolean
  pending: number
  active: number
  tasks: WorkerTaskRow[]
}

export interface LeaveRow {
  id: number
  start_at: string
  end_at: string
  reason: string
  status: string
  decided_at?: string | null
  worker_name?: string
}

export interface NotificationRow {
  id: number
  title: string
  body: string
  target_path: string
  priority: string
  read_at: string | null
  created_at: string
}

export interface ReminderRow {
  id: number
  elder_id: number
  elder_name: string
  title: string
  recurrence: string
  next_fire_at: string | null
  status: string
}

export interface AdminWorkerRow {
  id: number
  username: string
  email: string | null
  display_name: string
  status: string
  accepting: boolean
}

export interface IncidentRow {
  id: number
  order_id: number
  reason: string
  status: string
  created_at: string
  order_status: string
  item_name_snapshot: string
  payable_amount: number
  elder_name: string
}

export interface DisputeRow {
  id: number
  order_id: number
  reason: string
  status: string
  created_at: string
  opened_by_name: string
}

export interface ReviewRow {
  id: number
  order_id: number
  stars: number
  content: string
  visibility: string
  created_at: string
  author_name: string
}

export interface IdentityClaimRow {
  id: number
  elder_id: number
  elder_name: string
  account_id: number
  account_name: string
  status: string
  requested_at: string
  review_note: string | null
  reviewed_at: string | null
}

export interface FamilyDisputeRow {
  id: number
  elder_id: number
  elder_name: string
  reporter_id: number
  reporter_name: string
  reason: string
  status: string
  reported_at: string
  resolution: string | null
  resolved_at: string | null
}
