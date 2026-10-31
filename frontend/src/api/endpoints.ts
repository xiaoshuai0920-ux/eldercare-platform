import { api } from './client'
import type {
  AdminOrderDetail,
  AdminOrderRow,
  AdminTodo,
  AdminWorkerRow,
  Area,
  Candidate,
  CatalogItem,
  DashboardAdmin,
  DashboardUser,
  DashboardWorker,
  DisputeRow,
  ElderRow,
  FamilyInvitation,
  FamilyDisputeRow,
  IdentityClaimRow,
  IncidentRow,
  LeaveRow,
  NotificationRow,
  Page,
  Permissions,
  ReminderRow,
  ReviewRow,
  SessionUser,
  SettlementView,
  UserOrderDetail,
  UserOrderPage,
  WorkerTaskDetail,
  WorkerTaskRow,
} from './types'

/** 认证 */
export const authApi = {
  csrf: () => api.get<{ token: string }>('/auth/csrf', undefined, true),
  me: () => api.get<SessionUser>('/auth/me', undefined, true),
  login: (username: string, password: string) => api.post<SessionUser>('/auth/login', { username, password }),
  logout: () => api.post<void>('/auth/logout'),
  register: (payload: { username: string; name: string; password: string; email?: string }) =>
    api.post<SessionUser>('/auth/register', payload),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.post<void>('/auth/password', { currentPassword, newPassword }),
}

/** 用户端 */
export const userApi = {
  dashboard: () => api.get<DashboardUser>('/user/dashboard'),
  elders: () => api.get<ElderRow[]>('/elders'),
  createElder: (payload: { fullName: string; birthDate: string | null; declaresAuthorization: boolean }) =>
    api.post<ElderRow>('/elders', payload),
  updateElder: (id: number, payload: { fullName: string; birthDate: string | null }) =>
    api.put<ElderRow>(`/elders/${id}`, payload),
  invitations: () => api.get<FamilyInvitation[]>('/family/invitations'),
  respondInvitation: (id: number, accept: boolean) =>
    api.post<void>(`/family/invitations/${id}/${accept ? 'accept' : 'reject'}`),
  permissions: (elderId: number) => api.get<Permissions>(`/elders/${elderId}/permissions`),
  preferences: (elderId: number) => api.get<string[]>(`/elders/${elderId}/preferences`),
  addPreference: (elderId: number, tag: string) => api.post<void>(`/elders/${elderId}/preferences`, { tag }),
  removePreference: (elderId: number, tag: string) =>
    api.delete<void>(`/elders/${elderId}/preferences/${encodeURIComponent(tag)}`),
  claims: (elderId: number) => api.get<IdentityClaimRow[]>(`/elders/${elderId}/identity-claims`),
  familyDisputes: (elderId: number) => api.get<FamilyDisputeRow[]>(`/elders/${elderId}/family-disputes`),
  grants: (elderId: number) => api.get<Record<string, unknown>[]>(`/elders/${elderId}/grants`),
  invite: (elderId: number, payload: { username: string; permissions: Record<string, boolean> }) =>
    api.post<{ id: number }>(`/elders/${elderId}/invitations`, payload),
  revokeGrant: (elderId: number, accountId: number) =>
    api.post<void>(`/elders/${elderId}/grants/${accountId}/revoke`, undefined),
  createClaim: (elderId: number) => api.post<void>(`/elders/${elderId}/identity-claims`),
  createDispute: (elderId: number, reason: string) =>
    api.post<void>(`/elders/${elderId}/family-disputes`, { reason }),
  orders: (page = 1, size = 10, group = 'ALL') =>
    api.get<UserOrderPage>('/orders', { page, size, group: group === 'ALL' ? undefined : group }),
  order: (id: number) => api.get<UserOrderDetail>(`/orders/${id}`),
  createOrder: (payload: {
    elderId: number
    itemId: number
    streetId: number
    address: string
    startAt: string
    durationMinutes: number
  }) => api.post<{ id: number }>('/orders', payload, { 'Idempotency-Key': crypto.randomUUID() }),
  cancel: (id: number) => api.post<void>(`/orders/${id}/cancel`),
  confirm: (id: number) => api.post<void>(`/orders/${id}/confirm`),
  review: (id: number) => api.get<ReviewRow | null>(`/orders/${id}/review`),
  submitReview: (id: number, payload: { stars: number; content: string }) =>
    api.post<void>(`/orders/${id}/review`, payload),
  createOrderDispute: (id: number, reason: string) => api.post<void>(`/orders/${id}/disputes`, { reason }),
  feeAdjustments: (id: number) => api.get<Record<string, unknown>[]>(`/orders/${id}/fee-adjustments`),
  decideFeeAdjustment: (orderId: number, feeId: number, approve: boolean) =>
    api.post<void>(`/orders/${orderId}/fee-adjustments/${feeId}/${approve ? 'approve' : 'reject'}`),
  settlement: (id: number) => api.get<SettlementView>(`/orders/${id}/settlement`),
  createPaymentAttempt: (id: number) =>
    api.post<{ attemptRef: string; orderRef: number; settlementVersion: number; amount: number }>(
      `/orders/${id}/settlement/payment-attempts`,
    ),
  syncSettlement: (id: number) => api.post<SettlementView>(`/orders/${id}/settlement/sync`),
  recordOfflineSettlement: (id: number) => api.post<SettlementView>(`/orders/${id}/settlement/offline`),
  notifications: (page = 1, size = 20, kind?: string) =>
    api.get<Page<NotificationRow>>('/notifications', { page, size, kind }),
  readNotification: (id: number) => api.post<void>(`/notifications/${id}/read`),
  reminders: () => api.get<ReminderRow[]>('/reminders'),
  createReminder: (payload: { elderId: number; title: string; recurrence: string; firstAt: string }) =>
    api.post<void>('/reminders', payload),
  updateReminder: (id: number, payload: { title: string; recurrence: string; nextFireAt: string }) =>
    api.put<void>(`/reminders/${id}`, payload),
  stopReminder: (id: number) => api.post<void>(`/reminders/${id}/stop`),
}

/** 服务目录 */
export const catalogApi = {
  items: () => api.get<CatalogItem[]>('/catalog/items'),
  adminItems: () => api.get<CatalogItem[]>('/catalog/admin/items'),
  createAdminItem: (payload: CatalogItemInput) => api.post<{ id: number }>('/catalog/admin/items', payload),
  updateAdminItem: (id: number, payload: CatalogItemInput) =>
    api.put<void>(`/catalog/admin/items/${id}`, payload),
  publishAdminItem: (id: number) => api.post<void>(`/catalog/admin/items/${id}/publish`),
  unpublishAdminItem: (id: number) => api.post<void>(`/catalog/admin/items/${id}/unpublish`),
  areas: () => api.get<Area[]>('/catalog/areas'),
  createArea: (payload: { level: 'CITY' | 'DISTRICT' | 'STREET'; parentId: number | null; name: string }) =>
    api.post<void>('/catalog/admin/areas', payload),
  recommendations: (elderId: number, streetId: number) =>
    api.get<{ id: number; name: string; reason: string }[]>('/catalog/recommendations', {
      elderId,
      streetId,
    }),
  /** 管理端：包含未上架项目，必须使用 admin 路由 */
  itemAreas: (itemId: number) =>
    api.get<{ id: number; full_name: string }[]>(`/catalog/admin/items/${itemId}/areas`),
  /** 用户端：仅已上架项目的可服务街道，走公开路由，避免普通账号被 admin 鉴权拒绝 */
  publicItemAreas: (itemId: number) =>
    api.get<{ id: number; full_name: string }[]>(`/catalog/items/${itemId}/areas`),
  setItemArea: (itemId: number, streetId: number, covered: boolean) =>
    covered
      ? api.post<void>(`/catalog/admin/items/${itemId}/areas/${streetId}`)
      : api.delete<void>(`/catalog/admin/items/${itemId}/areas/${streetId}`),
  itemTags: (itemId: number) => api.get<string[]>(`/catalog/admin/items/${itemId}/tags`),
  addItemTag: (itemId: number, tag: string) => api.post<void>(`/catalog/admin/items/${itemId}/tags`, { tag }),
  removeItemTag: (itemId: number, tag: string) =>
    api.delete<void>(`/catalog/admin/items/${itemId}/tags/${encodeURIComponent(tag)}`),
}

export interface CatalogItemInput {
  category: string
  name: string
  description: string
  pricingModel: string
  unitPrice: number
  minMinutes: number
  maxMinutes: number
  availableFrom: string
  availableTo: string
  advanceMinutes: number
  ruleText: string
}

/** 服务人员端 */
export const workerApi = {
  dashboard: () => api.get<DashboardWorker>('/worker/dashboard'),
  tasks: () => api.get<WorkerTaskRow[]>('/worker/tasks'),
  task: (orderId: number) => api.get<WorkerTaskDetail>(`/worker/tasks/${orderId}`),
  leaves: () => api.get<LeaveRow[]>('/worker/leaves'),
  setAccepting: (accepting: boolean) => api.post<void>('/worker/accepting', { accepting }),
  accept: (orderId: number) => api.post<void>(`/orders/${orderId}/accept`),
  reject: (orderId: number, reason: string) => api.post<void>(`/orders/${orderId}/reject`, { reason }),
  start: (orderId: number) => api.post<void>(`/orders/${orderId}/start`),
  submit: (orderId: number) => api.post<void>(`/orders/${orderId}/submit-completion`),
  interrupt: (orderId: number, reason: string) => api.post<void>(`/orders/${orderId}/interrupt`, { reason }),
  requestLeave: (payload: { startAt: string; endAt: string; reason: string }) =>
    api.post<void>('/worker/leaves', payload),
}

/** 管理端 */
export const adminApi = {
  users: () =>
    api.get<
      {
        id: number
        username: string
        display_name: string
        email: string | null
        status: string
        created_at: string
      }[]
    >('/admin/users'),
  setUserState: (id: number, enable: boolean, reason: string) =>
    api.post<void>(`/admin/users/${id}/${enable ? 'enable' : 'disable'}`, { reason }),
  dashboard: () => api.get<DashboardAdmin>('/admin/dashboard'),
  todos: () => api.get<AdminTodo[]>('/admin/todos'),
  orders: (params: { page?: number; size?: number; status?: string; keyword?: string } = {}) =>
    api.get<Page<AdminOrderRow>>('/admin/orders', {
      page: params.page ?? 1,
      size: params.size ?? 20,
      status: params.status && params.status !== 'ALL' ? params.status : undefined,
      keyword: params.keyword?.trim() || undefined,
    }),
  order: (id: number) => api.get<AdminOrderDetail>(`/admin/orders/${id}`),
  candidates: (id: number) => api.get<Candidate[]>(`/admin/orders/${id}/candidates`),
  assign: (id: number, workerId: number) => api.post<void>(`/orders/${id}/assign`, { workerId }),
  workers: (page = 1, size = 20) => api.get<Page<AdminWorkerRow>>('/admin/workers', { page, size }),
  createWorker: (payload: { username: string; name: string; password: string; email?: string }) =>
    api.post<{ id: number }>('/admin/workers', payload),
  setWorkerState: (
    id: number,
    action: 'pause' | 'disable' | 'emergency-disable' | 'enable',
    reason: string,
  ) => api.post<void>(`/admin/workers/${id}/${action}`, { reason }),
  workerCapabilities: (id: number) =>
    api.get<
      {
        item_id: number
        street_id: number
        item_name: string
        street_name: string
        active: boolean
      }[]
    >(`/admin/workers/${id}/capabilities`),
  addWorkerCapability: (id: number, itemId: number, streetId: number) =>
    api.post<void>(`/admin/workers/${id}/capabilities`, { itemId, streetId }),
  leaves: () => api.get<LeaveRow[]>('/admin/leaves'),
  decideLeave: (id: number, approve: boolean) =>
    api.post<void>(`/admin/leaves/${id}/${approve ? 'approve' : 'reject'}`),
  incidents: (page = 1, size = 20) => api.get<Page<IncidentRow>>('/admin/incidents', { page, size }),
  resolveIncident: (id: number, reason: string) =>
    api.post<void>(`/admin/incidents/${id}/resolve`, { reason }),
  scheduleSupplement: (orderId: number, startAt: string, reason: string) =>
    api.post<void>(`/admin/orders/${orderId}/supplements`, { startAt, reason }),
  proposeFeeAdjustment: (orderId: number, amount: number, reason: string) =>
    api.post<void>(`/orders/${orderId}/fee-adjustments`, { amount, reason }),
  disputes: (page = 1, size = 20) => api.get<Page<DisputeRow>>('/admin/disputes', { page, size }),
  reviews: (page = 1, size = 20) => api.get<Page<ReviewRow>>('/admin/reviews', { page, size }),
  claims: (page = 1, size = 20) => api.get<Page<IdentityClaimRow>>('/admin/identity-claims', { page, size }),
  familyDisputes: (page = 1, size = 20) =>
    api.get<Page<FamilyDisputeRow>>('/admin/family-disputes', { page, size }),
  decideIdentityClaim: (id: number, decision: 'approve' | 'reject' | 'revoke', reason: string) =>
    api.post<void>(`/admin/identity-claims/${id}/${decision}`, { reason }),
  resolveFamilyDispute: (id: number, reason: string) =>
    api.post<void>(`/admin/family-disputes/${id}/resolve`, { reason }),
  resolveOrderDispute: (id: number, reason: string) =>
    api.post<void>(`/admin/disputes/${id}/resolve`, { reason }),
  handovers: (orderId: number) =>
    api.get<
      {
        id: number
        action: string
        target_account_id: number
        target_name: string
        reason: string
        status: string
        assigned_at: string
        revoked_at: string | null
      }[]
    >(`/admin/orders/${orderId}/handovers`),
  handoverCandidates: (orderId: number, action: string) =>
    api.get<{ account_id: number; username: string; display_name: string }[]>(
      `/admin/orders/${orderId}/handover-candidates`,
      { action },
    ),
  createHandover: (orderId: number, payload: { action: string; targetAccountId: number; reason: string }) =>
    api.post<void>(`/admin/orders/${orderId}/handovers`, payload),
  revokeHandover: (orderId: number, handoverId: number) =>
    api.post<void>(`/admin/orders/${orderId}/handovers/${handoverId}/revoke`),
  reviewVisibility: (id: number, hide: boolean) =>
    api.post<void>(`/admin/reviews/${id}/hide`, { hidden: hide }),
}
