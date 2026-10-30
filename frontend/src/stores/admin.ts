import { defineStore } from 'pinia'
import { ref } from 'vue'
import { adminApi, catalogApi, type CatalogItemInput } from '@/api/endpoints'
import { ApiError } from '@/api/client'
import type {
  AdminOrderDetail,
  AdminOrderRow,
  AdminTodo,
  AdminWorkerRow,
  Candidate,
  CatalogItem,
  DashboardAdmin,
  DisputeRow,
  FamilyDisputeRow,
  IdentityClaimRow,
  IncidentRow,
  LeaveRow,
  Page,
  ReviewRow,
} from '@/api/types'

/** 列表统一分页状态，字段与后端 PageResult 对齐 */
function emptyPage<T>(size = 20): Page<T> {
  return { items: [], page: 1, size, total: 0 }
}

export const useAdminStore = defineStore('admin', () => {
  const dashboard = ref<DashboardAdmin | null>(null)
  const todos = ref<AdminTodo[]>([])

  // 分页列表：页码与总数由后端返回，前端不再本地切片
  const orders = ref<Page<AdminOrderRow>>(emptyPage(10))
  const workers = ref<Page<AdminWorkerRow>>(emptyPage())
  const incidents = ref<Page<IncidentRow>>(emptyPage())
  const disputes = ref<Page<DisputeRow>>(emptyPage())
  const reviews = ref<Page<ReviewRow>>(emptyPage())
  const claims = ref<Page<IdentityClaimRow>>(emptyPage())
  const familyDisputes = ref<Page<FamilyDisputeRow>>(emptyPage())

  // 服务目录项目数量有限，保持一次性返回
  const catalogItems = ref<CatalogItem[]>([])
  const leaves = ref<LeaveRow[]>([])

  const detail = ref<AdminOrderDetail | null>(null)
  const candidates = ref<Candidate[]>([])
  const detailLoading = ref(false)
  const candidatesLoading = ref(false)

  const loading = ref(false)
  const error = ref('')

  /** 各分区的错误分别记录，单块失败不影响其他区块 */
  const errors = ref<Record<string, string>>({})

  /** 当前筛选条件，翻页与刷新时复用 */
  const orderFilters = ref({ status: 'ALL', keyword: '' })

  async function run<T>(key: string, task: () => Promise<T>, assign: (value: T) => void) {
    try {
      assign(await task())
      delete errors.value[key]
    } catch (reason) {
      errors.value[key] = reason instanceof ApiError ? reason.message : '加载失败'
    }
  }

  async function loadOverview() {
    loading.value = true
    error.value = ''
    await Promise.all([
      run('dashboard', adminApi.dashboard, (value) => (dashboard.value = value)),
      run('todos', adminApi.todos, (value) => (todos.value = value)),
    ])
    loading.value = false
  }

  async function loadOrders(page = orders.value.page) {
    await run(
      'orders',
      () =>
        adminApi.orders({
          page,
          size: orders.value.size,
          status: orderFilters.value.status,
          keyword: orderFilters.value.keyword,
        }),
      (value) => (orders.value = value),
    )
  }

  async function setOrderFilters(filters: { status: string; keyword: string }) {
    orderFilters.value = filters
    await loadOrders(1)
  }

  async function loadWorkers(page = workers.value.page) {
    await run(
      'workers',
      () => adminApi.workers(page, workers.value.size),
      (value) => (workers.value = value),
    )
  }

  async function loadIncidents(page = incidents.value.page) {
    await run(
      'incidents',
      () => adminApi.incidents(page, incidents.value.size),
      (value) => (incidents.value = value),
    )
  }

  async function loadDisputes(page = disputes.value.page) {
    await run(
      'disputes',
      () => adminApi.disputes(page, disputes.value.size),
      (value) => (disputes.value = value),
    )
  }

  async function loadReviews(page = reviews.value.page) {
    await run(
      'reviews',
      () => adminApi.reviews(page, reviews.value.size),
      (value) => (reviews.value = value),
    )
  }

  async function loadClaims(page = claims.value.page) {
    await run(
      'claims',
      () => adminApi.claims(page, claims.value.size),
      (value) => (claims.value = value),
    )
  }

  async function loadFamilyDisputes(page = familyDisputes.value.page) {
    await run(
      'familyDisputes',
      () => adminApi.familyDisputes(page, familyDisputes.value.size),
      (value) => (familyDisputes.value = value),
    )
  }

  /** 进入服务运营页：概览 + 各列表第一页 */
  async function loadAll() {
    loading.value = true
    error.value = ''
    await Promise.all([
      loadOverview(),
      loadOrders(1),
      loadWorkers(1),
      loadIncidents(1),
      loadDisputes(1),
      loadReviews(1),
      loadClaims(1),
      loadFamilyDisputes(1),
      run('catalog', catalogApi.adminItems, (value) => (catalogItems.value = value)),
      run('leaves', adminApi.leaves, (value) => (leaves.value = value)),
    ])
    loading.value = false
  }

  async function openDetail(id: number) {
    detailLoading.value = true
    detail.value = null
    try {
      detail.value = await adminApi.order(id)
      await loadCandidates(id)
    } catch (reason) {
      errors.value.detail = reason instanceof ApiError ? reason.message : '订单详情加载失败'
    } finally {
      detailLoading.value = false
    }
  }

  function closeDetail() {
    detail.value = null
    candidates.value = []
  }

  async function loadCandidates(id: number) {
    candidatesLoading.value = true
    candidates.value = []
    delete errors.value.candidates
    try {
      candidates.value = await adminApi.candidates(id)
    } catch (reason) {
      errors.value.candidates = reason instanceof ApiError ? reason.message : '人员查询失败，请重试'
    } finally {
      candidatesLoading.value = false
    }
  }

  async function assign(orderId: number, workerId: number): Promise<boolean> {
    try {
      await adminApi.assign(orderId, workerId)
      await Promise.all([loadOrders(), loadOverview()])
      await openDetail(orderId)
      return true
    } catch (reason) {
      errors.value.assign = reason instanceof ApiError ? reason.message : '派单失败'
      return false
    }
  }

  async function setReviewVisibility(id: number, hidden: boolean): Promise<boolean> {
    try {
      await adminApi.reviewVisibility(id, hidden)
      await loadReviews()
      return true
    } catch (reason) {
      errors.value.reviews = reason instanceof ApiError ? reason.message : '评价处理失败'
      return false
    }
  }

  async function createCatalogItem(input: CatalogItemInput): Promise<boolean> {
    try {
      await catalogApi.createAdminItem(input)
      await run('catalog', catalogApi.adminItems, (value) => (catalogItems.value = value))
      return !errors.value.catalog
    } catch (reason) {
      errors.value.catalog = reason instanceof ApiError ? reason.message : '服务项目创建失败'
      return false
    }
  }

  async function updateCatalogItem(id: number, input: CatalogItemInput): Promise<boolean> {
    try {
      await catalogApi.updateAdminItem(id, input)
      await run('catalog', catalogApi.adminItems, (value) => (catalogItems.value = value))
      return !errors.value.catalog
    } catch (reason) {
      errors.value.catalog = reason instanceof ApiError ? reason.message : '服务项目更新失败'
      return false
    }
  }

  async function setCatalogItemPublished(id: number, published: boolean): Promise<boolean> {
    try {
      if (published) await catalogApi.publishAdminItem(id)
      else await catalogApi.unpublishAdminItem(id)
      await run('catalog', catalogApi.adminItems, (value) => (catalogItems.value = value))
      return !errors.value.catalog
    } catch (reason) {
      errors.value.catalog = reason instanceof ApiError ? reason.message : '服务项目状态更新失败'
      return false
    }
  }

  return {
    dashboard,
    todos,
    orders,
    workers,
    catalogItems,
    incidents,
    disputes,
    reviews,
    claims,
    familyDisputes,
    leaves,
    detail,
    candidates,
    detailLoading,
    candidatesLoading,
    loading,
    error,
    errors,
    orderFilters,
    loadOverview,
    loadAll,
    loadOrders,
    setOrderFilters,
    loadWorkers,
    loadIncidents,
    loadDisputes,
    loadReviews,
    loadClaims,
    loadFamilyDisputes,
    openDetail,
    closeDetail,
    loadCandidates,
    assign,
    setReviewVisibility,
    createCatalogItem,
    updateCatalogItem,
    setCatalogItemPublished,
  }
})
