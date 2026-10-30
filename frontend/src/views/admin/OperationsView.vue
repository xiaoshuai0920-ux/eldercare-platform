<script setup lang="ts">
  import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import PageHeader from '@/components/ui/PageHeader.vue'
  import AppButton from '@/components/ui/AppButton.vue'
  import AppIcon from '@/components/ui/AppIcon.vue'
  import AppTabs from '@/components/ui/AppTabs.vue'
  import AppInput from '@/components/ui/AppInput.vue'
  import AppField from '@/components/ui/AppField.vue'
  import AppTextarea from '@/components/ui/AppTextarea.vue'
  import AppSelect from '@/components/ui/AppSelect.vue'
  import AppTag from '@/components/ui/AppTag.vue'
  import StatusTag from '@/components/ui/StatusTag.vue'
  import DataTable, { type TableColumn } from '@/components/ui/DataTable.vue'
  import EmptyState from '@/components/ui/EmptyState.vue'
  import AppDrawer from '@/components/ui/AppDrawer.vue'
  import AppDialog from '@/components/ui/AppDialog.vue'
  import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
  import ErrorBanner from '@/components/ui/ErrorBanner.vue'
  import PaginationBar from '@/components/ui/PaginationBar.vue'
  import WorkerSchedule from './WorkerSchedule.vue'
  import { useAdminStore } from '@/stores/admin'
  import { ORDER_STATUS } from '@/constants/status'
  import { formatDateTime, formatFullDate, money } from '@/utils/format'
  import { useUiStore } from '@/stores/ui'
  import { adminApi, catalogApi, userApi } from '@/api/endpoints'
  import type { Area, CatalogItem } from '@/api/types'
  import type { CatalogItemInput } from '@/api/endpoints'

  const route = useRoute()
  const router = useRouter()
  const admin = useAdminStore()
  const ui = useUiStore()

  const STATUS_LABEL: Record<string, string> = Object.fromEntries(
    Object.entries(ORDER_STATUS).map(([key, meta]) => [key, meta.label]),
  )

  const tabs = computed(() => [
    { id: 'orders', label: '订单与派单', count: admin.orders.total },
    { id: 'users', label: '用户账号', count: users.value.length },
    { id: 'catalog', label: '服务目录', count: admin.catalogItems.length },
    { id: 'workers', label: '服务人员', count: admin.workers.total },
    { id: 'exceptions', label: '异常与争议', count: admin.incidents.total + admin.disputes.total },
    { id: 'reviews', label: '评价治理', count: admin.reviews.total },
    { id: 'coordination', label: '家庭协调', count: admin.claims.total + admin.familyDisputes.total },
  ])

  const PANEL_IDS = [
    'orders',
    'users',
    'catalog',
    'workers',
    'exceptions',
    'reviews',
    'coordination',
  ] as const
  const requestedPanel = typeof route.query.panel === 'string' ? route.query.panel : 'orders'
  const active = ref<string>(
    PANEL_IDS.includes(requestedPanel as (typeof PANEL_IDS)[number]) ? requestedPanel : 'orders',
  )

  watch(active, (panel) => router.replace({ query: { ...route.query, panel } }))
  watch(
    () => route.query.panel,
    (panel) => {
      if (
        typeof panel === 'string' &&
        panel !== active.value &&
        PANEL_IDS.includes(panel as (typeof PANEL_IDS)[number])
      )
        active.value = panel
    },
  )

  const keyword = ref('')
  const statusFilter = ref('ALL')
  const users = ref<Awaited<ReturnType<typeof adminApi.users>>>([])

  async function loadUsers() {
    try {
      users.value = await adminApi.users()
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '用户账号加载失败', 'danger')
    }
  }

  async function toggleUserState(user: (typeof users.value)[number]) {
    const enable = user.status === 'DISABLED'
    const reason = window.prompt(enable ? '填写启用原因' : '填写停用原因')?.trim()
    if (!reason) return
    try {
      await adminApi.setUserState(user.id, enable, reason)
      await loadUsers()
      ui.notify(enable ? '用户账号已启用' : '用户账号已停用', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '账号状态更新失败', 'danger')
    }
  }

  const statusOptions = [
    { value: 'ALL', label: '全部状态' },
    ...Object.entries(ORDER_STATUS).map(([value, meta]) => ({ value, label: meta.label })),
  ]

  /** 筛选交给后端：翻页时条件不丢，也不会只过滤当前页 */
  let keywordTimer: number | undefined
  function applyOrderFilters() {
    admin.setOrderFilters({ status: statusFilter.value, keyword: keyword.value })
  }
  watch(keyword, () => {
    window.clearTimeout(keywordTimer)
    keywordTimer = window.setTimeout(applyOrderFilters, 300)
  })
  watch(statusFilter, applyOrderFilters)
  onBeforeUnmount(() => window.clearTimeout(keywordTimer))

  const orderColumns: TableColumn[] = [
    { key: 'id', label: '订单号' },
    { key: 'elder_name', label: '老人' },
    { key: 'item_name_snapshot', label: '项目' },
    { key: 'start_at', label: '预约时间' },
    { key: 'status', label: '状态', className: 'col-status', headerClass: 'col-status' },
    { key: 'payable_amount', label: '金额', align: 'right' },
    { key: 'actions', label: '操作', className: 'col-actions', headerClass: 'col-actions' },
  ]

  const catalogColumns: TableColumn[] = [
    { key: 'name', label: '项目' },
    { key: 'category', label: '分类' },
    { key: 'pricing_model', label: '计费' },
    { key: 'unit_price', label: '单价', align: 'right' },
    { key: 'min_minutes', label: '起约时长' },
    { key: 'active', label: '状态', className: 'col-status', headerClass: 'col-status' },
    { key: 'actions', label: '操作' },
  ]

  const catalogEditorOpen = ref(false)
  const catalogSaving = ref(false)
  const catalogEditingId = ref<number | null>(null)
  const catalogForm = ref<CatalogItemInput>(emptyCatalogForm())
  const coverageOpen = ref(false)
  const coverageBusy = ref(false)
  const coverageItem = ref<CatalogItem | null>(null)
  const streetOptions = ref<{ id: number; full_name: string }[]>([])
  const coveredStreetIds = ref<number[]>([])
  const tagsOpen = ref(false)
  const tagsBusy = ref(false)
  const tagItem = ref<CatalogItem | null>(null)
  const itemTags = ref<string[]>([])
  const tagInput = ref('')
  const areaDialog = ref(false)
  const areaBusy = ref(false)
  const areaLevel = ref<'CITY' | 'DISTRICT' | 'STREET'>('CITY')
  const areaName = ref('')
  const areaParentId = ref<number | ''>('')
  const areaOptions = ref<Area[]>([])
  const parentAreas = computed(() =>
    areaOptions.value.filter((area) => area.level === (areaLevel.value === 'DISTRICT' ? 'CITY' : 'DISTRICT')),
  )

  function emptyCatalogForm(): CatalogItemInput {
    return {
      category: 'CLEANING',
      name: '',
      description: '',
      pricingModel: 'HOURLY',
      unitPrice: 40,
      minMinutes: 60,
      maxMinutes: 240,
      availableFrom: '08:00',
      availableTo: '18:00',
      advanceMinutes: 120,
      ruleText: '',
    }
  }

  function editCatalogItem(item?: CatalogItem) {
    catalogEditingId.value = item?.id ?? null
    catalogForm.value = item
      ? {
          category: item.category,
          name: item.name,
          description: item.description,
          pricingModel: item.pricing_model,
          unitPrice: item.unit_price,
          minMinutes: item.min_minutes,
          maxMinutes: item.max_minutes ?? 240,
          availableFrom: (item.available_from ?? '08:00').slice(0, 5),
          availableTo: (item.available_to ?? '18:00').slice(0, 5),
          advanceMinutes: item.advance_minutes ?? 120,
          ruleText: item.rule_text,
        }
      : emptyCatalogForm()
    catalogEditorOpen.value = true
  }

  async function saveCatalogItem() {
    catalogSaving.value = true
    const ok =
      catalogEditingId.value === null
        ? await admin.createCatalogItem(catalogForm.value)
        : await admin.updateCatalogItem(catalogEditingId.value, catalogForm.value)
    catalogSaving.value = false
    if (ok) {
      catalogEditorOpen.value = false
      ui.notify(
        catalogEditingId.value === null ? '服务项目已创建，当前为下架状态' : '服务项目已保存',
        'success',
      )
    } else ui.notify(admin.errors.catalog ?? '保存失败', 'danger')
  }

  async function toggleCatalogItem(item: CatalogItem) {
    const ok = await admin.setCatalogItemPublished(item.id, !item.active)
    ui.notify(
      ok ? (item.active ? '服务项目已下架' : '服务项目已上架') : (admin.errors.catalog ?? '状态更新失败'),
      ok ? 'success' : 'danger',
    )
  }

  async function editCoverage(item: CatalogItem) {
    coverageItem.value = item
    coverageOpen.value = true
    coverageBusy.value = true
    try {
      const [areas, current] = await Promise.all([catalogApi.areas(), catalogApi.itemAreas(item.id)])
      streetOptions.value = areas
        .filter((area) => area.level === 'STREET')
        .map(({ id, full_name }) => ({ id, full_name }))
      coveredStreetIds.value = current.map((area) => area.id)
    } catch (error) {
      coverageOpen.value = false
      ui.notify(error instanceof Error ? error.message : '覆盖街道加载失败', 'danger')
    } finally {
      coverageBusy.value = false
    }
  }

  async function saveCoverage() {
    const item = coverageItem.value
    if (!item) return
    const current = await catalogApi.itemAreas(item.id)
    const previous = new Set(current.map((area) => area.id))
    const next = new Set(coveredStreetIds.value)
    if (item.active && next.size === 0) {
      ui.notify('已上架项目至少保留一个覆盖街道', 'danger')
      return
    }
    coverageBusy.value = true
    try {
      for (const id of next) if (!previous.has(id)) await catalogApi.setItemArea(item.id, id, true)
      for (const id of previous) if (!next.has(id)) await catalogApi.setItemArea(item.id, id, false)
      coverageOpen.value = false
      ui.notify('覆盖街道已保存', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '覆盖街道保存失败', 'danger')
      await editCoverage(item)
    } finally {
      coverageBusy.value = false
    }
  }

  async function editTags(item: CatalogItem) {
    tagItem.value = item
    tagsOpen.value = true
    tagInput.value = ''
    tagsBusy.value = true
    try {
      itemTags.value = await catalogApi.itemTags(item.id)
    } catch (error) {
      itemTags.value = []
      ui.notify(error instanceof Error ? error.message : '服务标签加载失败', 'danger')
    } finally {
      tagsBusy.value = false
    }
  }

  async function saveItemTag() {
    if (!tagItem.value || !tagInput.value.trim()) return
    tagsBusy.value = true
    try {
      await catalogApi.addItemTag(tagItem.value.id, tagInput.value.trim())
      itemTags.value = await catalogApi.itemTags(tagItem.value.id)
      tagInput.value = ''
      ui.notify('推荐标签已添加', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '推荐标签添加失败', 'danger')
    } finally {
      tagsBusy.value = false
    }
  }

  async function removeItemTag(tag: string) {
    if (!tagItem.value) return
    tagsBusy.value = true
    try {
      await catalogApi.removeItemTag(tagItem.value.id, tag)
      itemTags.value = await catalogApi.itemTags(tagItem.value.id)
      ui.notify('推荐标签已移除', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '推荐标签移除失败', 'danger')
    } finally {
      tagsBusy.value = false
    }
  }

  async function openAreaDialog() {
    areaDialog.value = true
    areaLevel.value = 'CITY'
    areaName.value = ''
    areaParentId.value = ''
    try {
      areaOptions.value = await catalogApi.areas()
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '区域列表加载失败', 'danger')
    }
  }

  async function createArea() {
    if (!areaName.value.trim() || (areaLevel.value !== 'CITY' && areaParentId.value === '')) return
    areaBusy.value = true
    try {
      await catalogApi.createArea({
        level: areaLevel.value,
        parentId: areaLevel.value === 'CITY' ? null : Number(areaParentId.value),
        name: areaName.value.trim(),
      })
      areaOptions.value = await catalogApi.areas()
      areaDialog.value = false
      ui.notify('服务区域已新增', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '服务区域新增失败', 'danger')
    } finally {
      areaBusy.value = false
    }
  }

  const workerColumns: TableColumn[] = [
    { key: 'display_name', label: '姓名' },
    { key: 'username', label: '账号' },
    { key: 'status', label: '账号状态', className: 'col-status', headerClass: 'col-status' },
    { key: 'accepting', label: '接单状态', className: 'col-status', headerClass: 'col-status' },
    { key: 'actions', label: '操作' },
  ]

  const scheduleWorker = ref<{ id: number; name: string } | null>(null)
  const workerDialog = ref(false)
  const workerSaving = ref(false)
  const workerForm = ref({ username: '', name: '', password: '', email: '' })
  const capabilityWorker = ref<{ id: number; name: string } | null>(null)
  const capabilityRows = ref<
    { item_id: number; street_id: number; item_name: string; street_name: string; active: boolean }[]
  >([])
  const capabilityItems = ref<CatalogItem[]>([])
  const capabilityStreets = ref<{ id: number; full_name: string }[]>([])
  const capabilityForm = ref({ itemId: 0, streetId: 0 })
  const capabilityOpen = ref(false)

  async function createWorker() {
    workerSaving.value = true
    try {
      await adminApi.createWorker(workerForm.value)
      workerDialog.value = false
      workerForm.value = { username: '', name: '', password: '', email: '' }
      await admin.loadWorkers(1)
      ui.notify('服务人员账号已创建', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '创建服务人员失败', 'danger')
    } finally {
      workerSaving.value = false
    }
  }

  async function changeWorkerState(worker: { id: number; status: string; accepting: boolean }) {
    const action = worker.status === 'DISABLED' ? 'enable' : worker.accepting ? 'pause' : 'disable'
    const reason = window
      .prompt(action === 'enable' ? '填写启用原因' : action === 'pause' ? '填写暂停接单原因' : '填写停用原因')
      ?.trim()
    if (!reason) return
    try {
      await adminApi.setWorkerState(worker.id, action, reason)
      await admin.loadWorkers()
      ui.notify('人员状态已更新', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '人员状态更新失败', 'danger')
    }
  }

  async function openCapabilities(worker: { id: number; display_name: string }) {
    capabilityWorker.value = { id: worker.id, name: worker.display_name }
    capabilityOpen.value = true
    try {
      const [rows, areas, items] = await Promise.all([
        adminApi.workerCapabilities(worker.id),
        catalogApi.areas(),
        catalogApi.adminItems(),
      ])
      capabilityRows.value = rows
      capabilityStreets.value = areas
        .filter((area) => area.level === 'STREET')
        .map(({ id, full_name }) => ({ id, full_name }))
      capabilityItems.value = items
      capabilityForm.value = { itemId: items[0]?.id ?? 0, streetId: 0 }
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '人员能力加载失败', 'danger')
    }
  }

  async function grantCapability() {
    if (!capabilityWorker.value || !capabilityForm.value.itemId || !capabilityForm.value.streetId) return
    try {
      await adminApi.addWorkerCapability(
        capabilityWorker.value.id,
        capabilityForm.value.itemId,
        capabilityForm.value.streetId,
      )
      await openCapabilities({ id: capabilityWorker.value.id, display_name: capabilityWorker.value.name })
      ui.notify('服务能力已配置', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '服务能力配置失败', 'danger')
    }
  }

  async function decideLeave(id: number, approve: boolean) {
    try {
      await adminApi.decideLeave(id, approve)
      await admin.loadAll()
      ui.notify(approve ? '请假已批准' : '请假已驳回', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '请假处理失败', 'danger')
    }
  }

  async function resolveIncident(id: number) {
    const reason = window.prompt('填写服务中断处理结果')?.trim()
    if (!reason) return
    try {
      await adminApi.resolveIncident(id, reason)
      await admin.loadIncidents()
      ui.notify('服务中断记录已处理', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '服务中断处理失败', 'danger')
    }
  }

  async function scheduleSupplement(orderId: number) {
    const defaultStart = new Date(Date.now() + 24 * 60 * 60 * 1000)
    defaultStart.setHours(9, 0, 0, 0)
    const localDateTime = (date: Date) => {
      const pad = (value: number) => String(value).padStart(2, '0')
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
    }
    const startAt = window
      .prompt('输入补服务开始时间（YYYY-MM-DDTHH:mm）', localDateTime(defaultStart))
      ?.trim()
    if (!startAt) return
    const reason = window.prompt('填写补服务安排说明')?.trim()
    if (!reason) return
    try {
      await adminApi.scheduleSupplement(orderId, startAt, reason)
      await admin.openDetail(orderId)
      await admin.loadIncidents()
      ui.notify('补服务已安排，订单已重新进入派单', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '补服务安排失败', 'danger')
    }
  }

  async function proposeFeeAdjustment(orderId: number) {
    const rawAmount = window.prompt('输入核实后的结算金额（元）')?.trim()
    if (rawAmount === undefined || !/^\d+(\.\d{1,2})?$/.test(rawAmount)) {
      if (rawAmount !== undefined) ui.notify('金额须为非负数，最多两位小数', 'danger')
      return
    }
    const reason = window.prompt('填写异常费用核实说明')?.trim()
    if (!reason) return
    try {
      await adminApi.proposeFeeAdjustment(orderId, Number(rawAmount), reason)
      await admin.openDetail(orderId)
      ui.notify('费用方案已提交，等待有权限的家属确认', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '费用方案提交失败', 'danger')
    }
  }

  async function recordOfflineSettlement(orderId: number) {
    if (!window.confirm(`确认将订单 ${orderId} 登记为线下结算完成？此操作成功后不可撤销。`)) return
    try {
      await userApi.recordOfflineSettlement(orderId)
      await admin.openDetail(orderId)
      ui.notify('线下结算已登记', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '线下结算登记失败', 'danger')
    }
  }

  async function reviewIdentityClaim(id: number, decision: 'approve' | 'reject' | 'revoke') {
    const reason = window.prompt('填写核验处理说明')?.trim()
    if (!reason) return
    try {
      await adminApi.decideIdentityClaim(id, decision, reason)
      await admin.loadClaims()
      ui.notify('身份核验申请已处理', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '身份核验处理失败', 'danger')
    }
  }

  async function resolveCoordinationDispute(id: number, familyDispute: boolean) {
    const reason = window.prompt('填写协调处理结果')?.trim()
    if (!reason) return
    try {
      if (familyDispute) await adminApi.resolveFamilyDispute(id, reason)
      else await adminApi.resolveOrderDispute(id, reason)
      await Promise.all([admin.loadFamilyDisputes(), admin.loadDisputes()])
      ui.notify('争议已处理', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '争议处理失败', 'danger')
    }
  }

  const detailOpen = computed({
    get: () => admin.detail !== null || admin.detailLoading,
    set: (open: boolean) => {
      if (!open) admin.closeDetail()
    },
  })

  const dispatchOpen = ref(false)
  const dispatchOrderId = ref<number | null>(null)
  const pickedWorker = ref<number | null>(null)
  const dispatching = ref(false)
  const handoverOpen = ref(false)
  const handoverBusy = ref(false)
  const handoverAction = ref('')
  const handoverTarget = ref<number | null>(null)
  const handoverReason = ref('')
  const handoverRows = ref<Awaited<ReturnType<typeof adminApi.handovers>>>([])
  const handoverCandidates = ref<Awaited<ReturnType<typeof adminApi.handoverCandidates>>>([])
  const handoverError = ref('')

  const HANDOVER_ACTION_LABEL: Record<string, string> = {
    CANCEL: '取消订单',
    CONFIRM: '确认服务完成',
    REVIEW: '提交评价',
    DISPUTE: '提交订单争议',
    FEE_CONFIRM: '确认费用调整',
    PAYMENT: '结算操作',
  }

  function availableHandoverActions(status: string) {
    if (['WAITING_ASSIGNMENT', 'WAITING_ACCEPTANCE', 'WAITING_SERVICE'].includes(status)) return ['CANCEL']
    if (status === 'WAITING_CONFIRMATION') return ['CONFIRM']
    if (status === 'COMPLETED') return ['REVIEW', 'DISPUTE', 'PAYMENT']
    if (status === 'INTERRUPTED') return ['FEE_CONFIRM']
    if (status === 'ABNORMAL_CLOSED') return ['PAYMENT']
    return []
  }

  async function loadHandoverCandidates() {
    if (!admin.detail || !handoverAction.value) return
    handoverTarget.value = null
    handoverError.value = ''
    try {
      handoverCandidates.value = await adminApi.handoverCandidates(
        admin.detail.order.id,
        handoverAction.value,
      )
      handoverTarget.value = handoverCandidates.value[0]?.account_id ?? null
    } catch (error) {
      handoverError.value = error instanceof Error ? error.message : '家庭成员加载失败'
      handoverCandidates.value = []
    }
  }

  async function openHandovers() {
    if (!admin.detail) return
    handoverOpen.value = true
    handoverAction.value = availableHandoverActions(admin.detail.order.status)[0] ?? ''
    handoverReason.value = ''
    handoverError.value = ''
    try {
      handoverRows.value = await adminApi.handovers(admin.detail.order.id)
      await loadHandoverCandidates()
    } catch (error) {
      handoverError.value = error instanceof Error ? error.message : '操作交接信息加载失败'
    }
  }

  async function saveHandover() {
    if (!admin.detail || !handoverAction.value || !handoverTarget.value || !handoverReason.value.trim())
      return
    handoverBusy.value = true
    try {
      await adminApi.createHandover(admin.detail.order.id, {
        action: handoverAction.value,
        targetAccountId: handoverTarget.value,
        reason: handoverReason.value.trim(),
      })
      handoverRows.value = await adminApi.handovers(admin.detail.order.id)
      handoverReason.value = ''
      ui.notify('订单操作已交接', 'success')
    } catch (error) {
      handoverError.value = error instanceof Error ? error.message : '操作交接失败'
    } finally {
      handoverBusy.value = false
    }
  }

  async function revokeHandover(id: number) {
    if (!admin.detail) return
    handoverBusy.value = true
    try {
      await adminApi.revokeHandover(admin.detail.order.id, id)
      handoverRows.value = await adminApi.handovers(admin.detail.order.id)
      ui.notify('操作交接已撤销', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '撤销交接失败', 'danger')
    } finally {
      handoverBusy.value = false
    }
  }

  const selectedCandidate = computed(
    () => admin.candidates.find((item) => item.id === pickedWorker.value) ?? null,
  )

  onMounted(() => {
    void admin.loadAll()
    void loadUsers()
  })

  function openDetail(id: number) {
    dispatchOpen.value = false
    admin.openDetail(id)
  }

  async function openDispatch(id: number) {
    dispatchOrderId.value = id
    pickedWorker.value = null
    dispatchOpen.value = true
    await admin.loadCandidates(id)
    pickedWorker.value = admin.candidates[0]?.id ?? null
  }

  async function doAssign() {
    if (!dispatchOrderId.value || !pickedWorker.value) return
    dispatching.value = true
    const ok = await admin.assign(dispatchOrderId.value, pickedWorker.value)
    dispatching.value = false
    if (ok) {
      dispatchOpen.value = false
      ui.notify(`已派单给${selectedCandidate.value?.display_name ?? '服务人员'}`)
    } else {
      ui.notify(admin.errors.assign ?? '派单失败', 'danger')
    }
  }

  async function toggleReview(id: number, hidden: boolean) {
    const ok = await admin.setReviewVisibility(id, hidden)
    ui.notify(
      ok ? (hidden ? '评价已隐藏' : '评价已恢复') : (admin.errors.reviews ?? '操作失败'),
      ok ? 'success' : 'danger',
    )
  }

  function categoryLabel(category: string): string {
    return category === 'MEAL'
      ? '助餐'
      : category === 'CLEANING'
        ? '保洁'
        : category === 'ESCORT'
          ? '陪诊'
          : category
  }
</script>

<template>
  <PageHeader title="派单工作台" description="先核对订单，再选择符合条件的服务人员。">
    <template #actions>
      <span class="chip"
        >待派单 <span class="num">{{ admin.dashboard?.waitingAssignment ?? 0 }}</span></span
      >
      <span class="chip"
        >待处理异常 <span class="num">{{ admin.incidents.total + admin.disputes.total }}</span></span
      >
      <AppButton variant="secondary" @click="admin.loadAll"><AppIcon name="refresh" />刷新数据</AppButton>
    </template>
  </PageHeader>

  <ErrorBanner
    v-if="admin.errors.orders"
    :title="admin.errors.orders"
    description="订单列表加载失败，请稍后重试。"
    style="margin-top: var(--sp-5)"
  />

  <div class="card" style="margin-top: var(--sp-6)">
    <AppTabs v-model="active" :tabs="tabs" aria-label="服务运营页签" padded />

    <!-- 订单与派单 -->
    <div
      v-show="active === 'orders'"
      id="panel-orders"
      role="tabpanel"
      aria-labelledby="tab-orders"
      class="dispatch-layout"
    >
      <div class="dispatch-list">
        <div class="toolbar">
          <div class="input-affix" style="flex: 1 1 260px">
            <AppIcon name="search" size="18px" />
            <label class="visually-hidden" for="order-search">搜索订单号或老人姓名</label>
            <AppInput id="order-search" v-model="keyword" type="search" placeholder="搜索订单号 / 老人姓名" />
          </div>
          <label class="visually-hidden" for="f-status">订单状态</label>
          <AppSelect id="f-status" v-model="statusFilter">
            <option v-for="option in statusOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </AppSelect>
          <span class="toolbar__count"
            >共 <span class="num">{{ admin.orders.total }}</span> 条</span
          >
        </div>

        <div v-if="admin.loading" style="padding: var(--sp-6)"><SkeletonBlock :rows="5" /></div>
        <DataTable
          v-else
          :columns="orderColumns"
          :rows="admin.orders.items"
          max-height="none"
          caption="订单列表"
          empty-title="没有符合条件的订单"
          empty-desc="调整筛选条件或清空搜索词后再试。"
        >
          <template #cell-id="{ value }"
            ><span class="num">{{ value }}</span></template
          >
          <template #cell-start_at="{ value }"
            ><span class="num">{{ formatDateTime(String(value)) }}</span></template
          >
          <template #cell-status="{ value }"><StatusTag :value="String(value)" /></template>
          <template #cell-payable_amount="{ value }"
            ><span class="num">{{ money(Number(value)) }}</span></template
          >
          <template #cell-actions="{ row }">
            <div class="row">
              <AppButton
                v-if="row.status === 'WAITING_ASSIGNMENT'"
                compact
                :disabled="admin.candidatesLoading"
                @click="openDispatch(row.id)"
                >派单</AppButton
              >
              <button
                class="linkbtn linkbtn--muted"
                type="button"
                :disabled="admin.candidatesLoading"
                @click="openDetail(row.id)"
              >
                详情
              </button>
            </div>
          </template>
        </DataTable>
        <PaginationBar
          :page="admin.orders.page"
          :total="admin.orders.total"
          :size="admin.orders.size"
          :disabled="admin.loading"
          @change="admin.loadOrders"
        />
      </div>
      <aside class="dispatch-panel" aria-label="人员安排">
        <header class="dispatch-panel__head">
          <h2>选择合适的服务人员</h2>
          <p>按服务资格、覆盖区域和时间筛选</p>
        </header>
        <div class="dispatch-panel__body">
          <p v-if="!dispatchOpen" class="dispatch-panel__help">选择待派单订单，查看可安排的人员。</p>
          <template v-else>
            <p class="dispatch-panel__order">订单 {{ dispatchOrderId }}</p>
            <div v-if="admin.candidatesLoading"><SkeletonBlock :rows="2" /></div>
            <div v-else-if="admin.errors.candidates" role="alert">
              <p>{{ admin.errors.candidates }}</p>
              <AppButton variant="secondary" @click="dispatchOrderId && openDispatch(dispatchOrderId)"
                >重新加载</AppButton
              >
            </div>
            <template v-else>
              <div v-if="admin.candidates.length" class="grid" style="gap: var(--sp-3)">
                <button
                  v-for="candidate in admin.candidates"
                  :key="candidate.id"
                  class="candidate"
                  :class="{ 'candidate--picked': pickedWorker === candidate.id }"
                  type="button"
                  :aria-pressed="pickedWorker === candidate.id"
                  style="cursor: pointer"
                  @click="pickedWorker = candidate.id"
                >
                  <span style="text-align: left">
                    <span class="strong">{{ candidate.display_name }}</span>
                    <span class="candidate__why">资格、覆盖区域和时间均符合</span>
                  </span>
                  <AppTag tone="success">可派</AppTag>
                </button>
              </div>

              <div v-else class="banner banner--warm" role="note">
                <AppIcon name="alert" />
                <div class="banner__body">
                  <p class="banner__title">没有符合条件的候选</p>
                  <p class="banner__desc">
                    可能原因：时间不在任何人员排班内、项目资格或街道未覆盖、该时段有人请假或已被占用。请核对后改派或联系家属改期。
                  </p>
                </div>
              </div>
            </template>

            <p class="hint">派单后等待服务人员接单确认。</p>
            <AppButton
              :disabled="!pickedWorker || admin.candidatesLoading"
              :loading="dispatching"
              @click="doAssign"
            >
              {{ selectedCandidate ? `确认派单给${selectedCandidate.display_name}` : '确认派单' }}
            </AppButton>
            <AppButton variant="ghost" @click="dispatchOpen = false">取消选择</AppButton>
          </template>
        </div>
      </aside>
    </div>

    <!-- 服务目录 -->
    <div v-show="active === 'users'" id="panel-users" role="tabpanel" aria-labelledby="tab-users">
      <div class="toolbar">
        <span class="toolbar__count" style="margin-left: 0">普通用户账号 · 停用后该账号无法继续登录</span>
        <AppButton variant="secondary" @click="loadUsers">刷新账号</AppButton>
      </div>
      <DataTable
        :columns="[
          { key: 'username', label: '登录账号' },
          { key: 'display_name', label: '姓名' },
          { key: 'email', label: '联系邮箱' },
          { key: 'created_at', label: '注册时间' },
          { key: 'status', label: '状态' },
          { key: 'actions', label: '操作' },
        ]"
        :rows="users"
        max-height="none"
        caption="普通用户账号"
      >
        <template #cell-created_at="{ value }">{{ formatDateTime(String(value)) }}</template>
        <template #cell-status="{ value }">
          <AppTag :tone="value === 'ACTIVE' ? 'success' : 'neutral'">{{
            value === 'ACTIVE' ? '正常' : '已停用'
          }}</AppTag>
        </template>
        <template #cell-actions="{ row }">
          <AppButton variant="ghost" compact @click="toggleUserState(row)">{{
            row.status === 'DISABLED' ? '启用' : '停用'
          }}</AppButton>
        </template>
      </DataTable>
    </div>

    <!-- 服务目录 -->
    <div v-show="active === 'catalog'" id="panel-catalog" role="tabpanel" aria-labelledby="tab-catalog">
      <div class="toolbar">
        <span class="toolbar__count" style="margin-left: 0"
          >共 <span class="num">{{ admin.catalogItems.length }}</span> 个项目 · 上架与定价由运营维护</span
        >
        <div class="row">
          <AppButton variant="secondary" @click="openAreaDialog">新增区域</AppButton>
          <AppButton @click="editCatalogItem()">新增服务项目</AppButton>
        </div>
      </div>
      <DataTable :columns="catalogColumns" :rows="admin.catalogItems" max-height="none" caption="服务目录">
        <template #cell-category="{ value }">{{ categoryLabel(String(value)) }}</template>
        <template #cell-pricing_model="{ value }">{{ value === 'HOURLY' ? '按小时' : '按次' }}</template>
        <template #cell-unit_price="{ value }"
          ><span class="num">{{ money(Number(value)) }}</span></template
        >
        <template #cell-min_minutes="{ value }"
          ><span class="num">{{ value }} 分钟</span></template
        >
        <template #cell-active="{ value }"
          ><AppTag :tone="value ? 'success' : 'neutral'">{{ value ? '已上架' : '已下架' }}</AppTag></template
        >
        <template #cell-actions="{ row }">
          <div class="row-actions">
            <AppButton variant="secondary" compact @click="editCatalogItem(row)">编辑</AppButton>
            <AppButton variant="ghost" compact @click="editCoverage(row)">覆盖街道</AppButton>
            <AppButton variant="ghost" compact @click="editTags(row)">推荐标签</AppButton>
            <AppButton variant="ghost" compact @click="toggleCatalogItem(row)">{{
              row.active ? '下架' : '上架'
            }}</AppButton>
          </div>
        </template>
      </DataTable>
    </div>

    <AppDialog
      v-model="catalogEditorOpen"
      :title="catalogEditingId ? '编辑服务项目' : '新增服务项目'"
      subtitle="价格和服务规则只影响新预约，历史订单保留预约时快照。"
      wide
    >
      <form class="catalog-form" @submit.prevent="saveCatalogItem">
        <label
          >服务类别<select v-model="catalogForm.category" class="input">
            <option value="MEAL">助餐</option>
            <option value="CLEANING">保洁</option>
            <option value="ESCORT">陪诊</option>
          </select></label
        >
        <label
          >项目名称<input v-model.trim="catalogForm.name" class="input" required maxlength="100"
        /></label>
        <label
          >计费方式<select v-model="catalogForm.pricingModel" class="input">
            <option value="FIXED">按次</option>
            <option value="HOURLY">按小时</option>
          </select></label
        >
        <label
          >单价（元）<input
            v-model.number="catalogForm.unitPrice"
            class="input"
            type="number"
            min="0"
            step="0.01"
            required
        /></label>
        <label
          >最短时长（分钟）<input
            v-model.number="catalogForm.minMinutes"
            class="input"
            type="number"
            min="1"
            max="1440"
            required
        /></label>
        <label
          >最长时长（分钟）<input
            v-model.number="catalogForm.maxMinutes"
            class="input"
            type="number"
            min="1"
            max="1440"
            required
        /></label>
        <label
          >可预约开始<input v-model="catalogForm.availableFrom" class="input" type="time" required
        /></label>
        <label
          >可预约结束<input v-model="catalogForm.availableTo" class="input" type="time" required
        /></label>
        <label
          >提前预约（分钟）<input
            v-model.number="catalogForm.advanceMinutes"
            class="input"
            type="number"
            min="0"
            required
        /></label>
        <label class="catalog-form__wide"
          >服务说明<textarea
            v-model.trim="catalogForm.description"
            class="input"
            maxlength="1000"
            required
            rows="2"
          />
        </label>
        <label class="catalog-form__wide"
          >规则说明<textarea v-model.trim="catalogForm.ruleText" class="input" required rows="3" />
        </label>
        <div class="catalog-form__actions">
          <AppButton type="button" variant="ghost" @click="catalogEditorOpen = false">取消</AppButton
          ><AppButton type="submit" :loading="catalogSaving">保存</AppButton>
        </div>
      </form>
    </AppDialog>

    <AppDialog
      v-model="coverageOpen"
      :title="`${coverageItem?.name ?? '服务项目'} · 覆盖街道`"
      subtitle="请选择该项目可预约的街道。"
    >
      <p v-if="coverageBusy" class="muted">正在读取或保存街道…</p>
      <div v-else class="coverage-list">
        <label v-for="street in streetOptions" :key="street.id" class="coverage-option">
          <input v-model="coveredStreetIds" type="checkbox" :value="street.id" />
          {{ street.full_name }}
        </label>
        <EmptyState v-if="!streetOptions.length" title="暂无可选街道" description="请先配置街道区域。" />
      </div>
      <template #footer>
        <AppButton variant="ghost" @click="coverageOpen = false">取消</AppButton>
        <AppButton :loading="coverageBusy" :disabled="!streetOptions.length" @click="saveCoverage"
          >保存覆盖范围</AppButton
        >
      </template>
    </AppDialog>

    <AppDialog
      v-model="tagsOpen"
      :title="`${tagItem?.name ?? '服务项目'} · 推荐标签`"
      subtitle="标签用于匹配老人服务偏好并生成推荐理由。"
    >
      <p v-if="tagsBusy" class="muted">正在读取或保存标签…</p>
      <template v-else>
        <div v-if="itemTags.length" class="row" style="flex-wrap: wrap; gap: var(--sp-2)">
          <AppTag v-for="tag in itemTags" :key="tag" tone="neutral">
            {{ tag }}
            <AppButton variant="ghost" compact :disabled="tagsBusy" @click="removeItemTag(tag)"
              >移除</AppButton
            >
          </AppTag>
        </div>
        <p v-else class="muted">该项目尚未配置推荐标签。</p>
        <form class="row" style="margin-top: var(--sp-4)" @submit.prevent="saveItemTag">
          <AppInput
            v-model="tagInput"
            maxlength="60"
            aria-label="推荐标签"
            placeholder="例如：助浴、行动不便"
          />
          <AppButton type="submit" :loading="tagsBusy" :disabled="!tagInput.trim()">添加标签</AppButton>
        </form>
      </template>
    </AppDialog>

    <AppDialog v-model="areaDialog" title="新增服务区域" subtitle="区域用于服务覆盖与预约地址选择。">
      <p class="muted">仅支持新增城市、区县或街道；区域层级由服务端校验。</p>
      <AppField label="区域层级" html-for="area-level">
        <AppSelect id="area-level" v-model="areaLevel">
          <option value="CITY">城市</option>
          <option value="DISTRICT">区县</option>
          <option value="STREET">街道</option>
        </AppSelect>
      </AppField>
      <AppField v-if="areaLevel !== 'CITY'" label="上级区域" html-for="area-parent" required>
        <AppSelect id="area-parent" v-model.number="areaParentId">
          <option value="" disabled>请选择上级区域</option>
          <option v-for="parent in parentAreas" :key="parent.id" :value="parent.id">
            {{ parent.full_name }}
          </option>
        </AppSelect>
      </AppField>
      <AppField label="区域名称" html-for="area-name" required>
        <AppInput id="area-name" v-model="areaName" maxlength="80" />
      </AppField>
      <template #footer>
        <AppButton variant="ghost" @click="areaDialog = false">取消</AppButton>
        <AppButton
          :loading="areaBusy"
          :disabled="!areaName.trim() || (areaLevel !== 'CITY' && areaParentId === '')"
          @click="createArea"
          >新增区域</AppButton
        >
      </template>
    </AppDialog>

    <!-- 服务人员 -->
    <div v-show="active === 'workers'" id="panel-workers" role="tabpanel" aria-labelledby="tab-workers">
      <div class="toolbar">
        <span class="toolbar__count" style="margin-left: 0"
          >共 <span class="num">{{ admin.workers.total }}</span> 人 · 查看并新增工作时段</span
        >
        <AppButton @click="workerDialog = true">新增服务人员</AppButton>
      </div>
      <DataTable :columns="workerColumns" :rows="admin.workers.items" max-height="none" caption="服务人员">
        <template #cell-status="{ value }">
          <AppTag :tone="value === 'ACTIVE' ? 'success' : value === 'DISABLED' ? 'neutral' : 'action'">
            {{ value === 'ACTIVE' ? '正常' : value === 'DISABLED' ? '已停用' : String(value) }}
          </AppTag>
        </template>
        <template #cell-accepting="{ value }">
          <AppTag :tone="value ? 'progress' : 'neutral'">{{ value ? '接单中' : '已暂停' }}</AppTag>
        </template>
        <template #cell-actions="{ row }">
          <AppButton
            variant="secondary"
            compact
            @click="scheduleWorker = { id: row.id, name: row.display_name }"
            >查看排班</AppButton
          >
          <AppButton variant="ghost" compact @click="openCapabilities(row)">服务能力</AppButton>
          <AppButton variant="ghost" compact @click="changeWorkerState(row)">{{
            row.status === 'DISABLED' ? '启用' : row.accepting ? '暂停接单' : '停用'
          }}</AppButton>
        </template>
      </DataTable>
      <WorkerSchedule
        v-if="scheduleWorker"
        :key="scheduleWorker.id"
        :worker-id="scheduleWorker.id"
        :worker-name="scheduleWorker.name"
        @close="scheduleWorker = null"
      />
      <PaginationBar
        :page="admin.workers.page"
        :total="admin.workers.total"
        :size="admin.workers.size"
        @change="admin.loadWorkers"
      />
      <section class="card card--pad" style="margin-top: var(--sp-5)">
        <h2 class="section-title">请假审批</h2>
        <EmptyState
          v-if="!admin.leaves.length"
          title="暂无请假申请"
          description="服务人员提交的请假会显示在这里。"
        />
        <div v-else class="list">
          <article v-for="leave in admin.leaves" :key="leave.id" class="list__item">
            <div class="list__body">
              <p class="list__title">
                {{ leave.worker_name }} ·
                {{
                  leave.status === 'PENDING' ? '待审批' : leave.status === 'APPROVED' ? '已批准' : '已驳回'
                }}
              </p>
              <p class="list__desc">
                {{ formatDateTime(leave.start_at) }} 至 {{ formatDateTime(leave.end_at) }} ·
                {{ leave.reason }}
              </p>
            </div>
            <div v-if="leave.status === 'PENDING'" class="row-actions">
              <AppButton compact @click="decideLeave(leave.id, true)">批准</AppButton>
              <AppButton variant="danger" compact @click="decideLeave(leave.id, false)">驳回</AppButton>
            </div>
          </article>
        </div>
      </section>
    </div>

    <AppDialog
      v-model="capabilityOpen"
      :title="`${capabilityWorker?.name ?? '服务人员'} · 服务能力`"
      subtitle="可承接范围必须属于服务项目已覆盖的街道。"
    >
      <ul class="capability-list">
        <li v-for="cap in capabilityRows" :key="`${cap.item_id}-${cap.street_id}`">
          {{ cap.item_name }} · {{ cap.street_name }}{{ cap.active ? '' : ' · 已停用' }}
        </li>
      </ul>
      <form class="catalog-form" @submit.prevent="grantCapability">
        <label
          >服务项目<select v-model.number="capabilityForm.itemId" class="input">
            <option v-for="item in capabilityItems" :key="item.id" :value="item.id">{{ item.name }}</option>
          </select></label
        >
        <label
          >服务街道<select v-model.number="capabilityForm.streetId" class="input">
            <option :value="0" disabled>请选择街道</option>
            <option v-for="street in capabilityStreets" :key="street.id" :value="street.id">
              {{ street.full_name }}
            </option>
          </select></label
        >
        <div class="catalog-form__actions">
          <AppButton type="submit" :disabled="!capabilityForm.streetId">添加服务能力</AppButton>
        </div>
      </form>
    </AppDialog>

    <AppDialog
      v-model="workerDialog"
      title="新增服务人员"
      subtitle="新账号将立即启用，初始接单状态由后端默认规则设置。"
    >
      <form class="catalog-form" @submit.prevent="createWorker">
        <label
          >登录账号<input
            v-model.trim="workerForm.username"
            class="input"
            required
            minlength="4"
            maxlength="32"
            pattern="[a-z0-9_]+"
        /></label>
        <label>姓名<input v-model.trim="workerForm.name" class="input" required maxlength="80" /></label>
        <label
          >初始密码<input
            v-model="workerForm.password"
            class="input"
            type="password"
            required
            minlength="6"
            maxlength="72"
        /></label>
        <label
          >邮箱（可选）<input v-model.trim="workerForm.email" class="input" type="email" maxlength="254"
        /></label>
        <div class="catalog-form__actions">
          <AppButton type="button" variant="ghost" @click="workerDialog = false">取消</AppButton
          ><AppButton type="submit" :loading="workerSaving">创建账号</AppButton>
        </div>
      </form>
    </AppDialog>

    <!-- 异常与争议 -->
    <div
      v-show="active === 'exceptions'"
      id="panel-exceptions"
      role="tabpanel"
      aria-labelledby="tab-exceptions"
    >
      <template v-if="admin.incidents.total || admin.disputes.total">
        <div class="toolbar">
          <span class="toolbar__count" style="margin-left: 0"
            >服务中断 · 共 <span class="num">{{ admin.incidents.total }}</span> 条</span
          >
        </div>
        <div v-if="!admin.incidents.total" style="padding: var(--sp-6)">
          <EmptyState title="没有待处理的服务中断" description="服务人员上报中断后会出现在这里。" />
        </div>
        <div v-else class="list">
          <article
            v-for="item in admin.incidents.items"
            :key="`inc-${item.id}`"
            class="list__item"
            style="padding-inline: var(--sp-6)"
          >
            <span
              class="list__icon"
              aria-hidden="true"
              style="background: var(--c-danger-soft); color: var(--c-danger-ink)"
              ><AppIcon name="alert"
            /></span>
            <div class="list__body">
              <p class="list__title">
                服务中断 · 订单 {{ item.order_id }}<StatusTag :value="item.order_status" />
              </p>
              <p class="list__desc">{{ item.reason }}</p>
              <p class="list__desc num">
                {{ item.elder_name }} · {{ item.item_name_snapshot }} · {{ money(item.payable_amount) }} ·
                {{ formatFullDate(item.created_at) }}
              </p>
            </div>
            <div class="list__end">
              <AppButton variant="secondary" compact @click="openDetail(item.order_id)">查看订单</AppButton>
              <AppButton v-if="item.status === 'OPEN'" compact @click="resolveIncident(item.id)"
                >记录处理结果</AppButton
              >
            </div>
          </article>
        </div>
        <PaginationBar
          :page="admin.incidents.page"
          :total="admin.incidents.total"
          :size="admin.incidents.size"
          @change="admin.loadIncidents"
        />

        <div class="toolbar">
          <span class="toolbar__count" style="margin-left: 0"
            >订单争议 · 共 <span class="num">{{ admin.disputes.total }}</span> 条</span
          >
        </div>
        <div v-if="!admin.disputes.total" style="padding: var(--sp-6)">
          <EmptyState title="没有待处理的订单争议" description="家属发起争议后会出现在这里。" />
        </div>
        <div v-else class="list">
          <article
            v-for="item in admin.disputes.items"
            :key="`dis-${item.id}`"
            class="list__item"
            style="padding-inline: var(--sp-6)"
          >
            <span
              class="list__icon"
              aria-hidden="true"
              style="background: var(--c-warm-soft); color: var(--c-warm-ink)"
              ><AppIcon name="clock"
            /></span>
            <div class="list__body">
              <p class="list__title">
                订单争议 · 订单 {{ item.order_id
                }}<AppTag :tone="item.status === 'OPEN' ? 'action' : 'success'">{{
                  item.status === 'OPEN' ? '待协调' : '已处理'
                }}</AppTag>
              </p>
              <p class="list__desc">{{ item.reason }}</p>
              <p class="list__desc num">{{ item.opened_by_name }} · {{ formatFullDate(item.created_at) }}</p>
            </div>
            <div class="list__end">
              <AppButton variant="secondary" compact @click="openDetail(item.order_id)">查看订单</AppButton>
              <AppButton
                v-if="item.status === 'OPEN'"
                compact
                @click="resolveCoordinationDispute(item.id, false)"
                >记录处理结果</AppButton
              >
            </div>
          </article>
        </div>
        <PaginationBar
          :page="admin.disputes.page"
          :total="admin.disputes.total"
          :size="admin.disputes.size"
          @change="admin.loadDisputes"
        />
      </template>
      <div v-else style="padding: var(--sp-6)">
        <EmptyState title="没有待处理的异常与争议" description="服务中断与费用争议会出现在这里。" />
      </div>
    </div>

    <!-- 评价治理 -->
    <div v-show="active === 'reviews'" id="panel-reviews" role="tabpanel" aria-labelledby="tab-reviews">
      <div class="toolbar">
        <span class="toolbar__count" style="margin-left: 0"
          >共 <span class="num">{{ admin.reviews.total }}</span> 条评价 · 隐藏后仅管理员可见</span
        >
      </div>
      <div v-if="!admin.reviews.total" style="padding: var(--sp-6)">
        <EmptyState title="暂无评价" description="家属确认完成后可提交评价。" />
      </div>
      <div v-else class="list">
        <article
          v-for="item in admin.reviews.items"
          :key="item.id"
          class="list__item"
          style="padding-inline: var(--sp-6)"
        >
          <span
            class="list__icon"
            aria-hidden="true"
            :style="
              item.visibility === 'HIDDEN'
                ? 'background: var(--c-warm-soft); color: var(--c-warm-ink)'
                : undefined
            "
            ><AppIcon name="star"
          /></span>
          <div class="list__body">
            <p class="list__title">
              {{ item.stars }} 星 · 订单 {{ item.order_id }}
              <AppTag :tone="item.visibility === 'HIDDEN' ? 'action' : 'success'">{{
                item.visibility === 'HIDDEN' ? '已隐藏' : '可见'
              }}</AppTag>
            </p>
            <p class="list__desc">{{ item.content }}</p>
            <p class="list__desc num">{{ item.author_name }} · {{ formatFullDate(item.created_at) }}</p>
          </div>
          <div class="list__end">
            <AppButton
              variant="secondary"
              compact
              @click="toggleReview(item.id, item.visibility !== 'HIDDEN')"
            >
              {{ item.visibility === 'HIDDEN' ? '恢复展示' : '隐藏' }}
            </AppButton>
          </div>
        </article>
      </div>
      <PaginationBar
        :page="admin.reviews.page"
        :total="admin.reviews.total"
        :size="admin.reviews.size"
        @change="admin.loadReviews"
      />
    </div>

    <!-- 家庭协调 -->
    <div
      v-show="active === 'coordination'"
      id="panel-coordination"
      role="tabpanel"
      aria-labelledby="tab-coordination"
    >
      <template v-if="admin.claims.total || admin.familyDisputes.total">
        <div class="toolbar">
          <span class="toolbar__count" style="margin-left: 0"
            >身份核验 · 共 <span class="num">{{ admin.claims.total }}</span> 条</span
          >
        </div>
        <div v-if="!admin.claims.total" style="padding: var(--sp-6)">
          <EmptyState title="没有身份核验申请" description="家属申请核验与档案关系后会出现在这里。" />
        </div>
        <div v-else class="list">
          <article
            v-for="item in admin.claims.items"
            :key="`claim-${item.id}`"
            class="list__item"
            style="padding-inline: var(--sp-6)"
          >
            <span class="list__icon" aria-hidden="true"><AppIcon name="shield" /></span>
            <div class="list__body">
              <p class="list__title">
                身份核验 · {{ item.elder_name
                }}<AppTag :tone="item.status === 'PENDING' ? 'action' : 'success'">{{
                  item.status === 'PENDING' ? '待核验' : item.status === 'APPROVED' ? '已通过' : '已拒绝'
                }}</AppTag>
              </p>
              <p class="list__desc">
                {{ item.account_name }} 申请核验与该档案的关系{{
                  item.review_note ? ` · ${item.review_note}` : ''
                }}
              </p>
              <p class="list__desc num">{{ formatFullDate(item.requested_at) }}</p>
            </div>
            <div v-if="item.status === 'PENDING'" class="row-actions">
              <AppButton compact @click="reviewIdentityClaim(item.id, 'approve')">通过</AppButton>
              <AppButton variant="danger" compact @click="reviewIdentityClaim(item.id, 'reject')"
                >拒绝</AppButton
              >
            </div>
            <div v-else-if="item.status === 'APPROVED'" class="list__end">
              <AppButton variant="ghost" compact @click="reviewIdentityClaim(item.id, 'revoke')"
                >撤销核验</AppButton
              >
            </div>
          </article>
        </div>
        <PaginationBar
          :page="admin.claims.page"
          :total="admin.claims.total"
          :size="admin.claims.size"
          @change="admin.loadClaims"
        />

        <div class="toolbar">
          <span class="toolbar__count" style="margin-left: 0"
            >家庭争议 · 共 <span class="num">{{ admin.familyDisputes.total }}</span> 条</span
          >
        </div>
        <div v-if="!admin.familyDisputes.total" style="padding: var(--sp-6)">
          <EmptyState title="没有家庭争议" description="家庭成员对授权或档案有异议时会出现在这里。" />
        </div>
        <div v-else class="list">
          <article
            v-for="item in admin.familyDisputes.items"
            :key="`fd-${item.id}`"
            class="list__item"
            style="padding-inline: var(--sp-6)"
          >
            <span class="list__icon" aria-hidden="true"><AppIcon name="users" /></span>
            <div class="list__body">
              <p class="list__title">
                家庭争议 · {{ item.elder_name
                }}<AppTag :tone="item.status === 'OPEN' ? 'action' : 'success'">{{
                  item.status === 'OPEN' ? '待协调' : '已处理'
                }}</AppTag>
              </p>
              <p class="list__desc">{{ item.reason }}</p>
              <p class="list__desc num">{{ item.reporter_name }} · {{ formatFullDate(item.reported_at) }}</p>
            </div>
            <div v-if="item.status === 'OPEN'" class="list__end">
              <AppButton compact @click="resolveCoordinationDispute(item.id, true)">记录处理结果</AppButton>
            </div>
          </article>
        </div>
        <PaginationBar
          :page="admin.familyDisputes.page"
          :total="admin.familyDisputes.total"
          :size="admin.familyDisputes.size"
          @change="admin.loadFamilyDisputes"
        />
      </template>
      <div v-else style="padding: var(--sp-6)">
        <EmptyState title="暂无家庭协调事项" description="身份核验申请与家庭争议会出现在这里。" />
      </div>
    </div>
  </div>

  <!-- 订单详情 -->
  <AppDrawer
    v-model="detailOpen"
    :title="admin.detail ? admin.detail.order.item_name_snapshot : '订单详情'"
    :subtitle="
      admin.detail ? `${formatFullDate(admin.detail.order.start_at)} · ${admin.detail.order.elder_name}` : ''
    "
  >
    <template #eyebrow
      ><p v-if="admin.detail" class="order-card__id num">订单号 {{ admin.detail.order.id }}</p></template
    >

    <div v-if="admin.detailLoading"><SkeletonBlock :rows="5" /></div>
    <template v-else-if="admin.detail">
      <div class="row" style="justify-content: space-between">
        <StatusTag :value="admin.detail.order.status" />
        <span class="order-card__amount num" style="margin: 0; font-size: var(--fs-24)">{{
          money(admin.detail.order.payable_amount)
        }}</span>
      </div>

      <dl style="margin: 0">
        <div class="detail-row">
          <dt>老人</dt>
          <dd>{{ admin.detail.order.elder_name }}</dd>
        </div>
        <div class="detail-row">
          <dt>下单账号</dt>
          <dd>{{ admin.detail.order.creator_name }}</dd>
        </div>
        <div class="detail-row">
          <dt>服务时间</dt>
          <dd class="num">
            {{ formatFullDate(admin.detail.order.start_at) }} –
            {{ formatFullDate(admin.detail.order.end_at).slice(11) }}
          </dd>
        </div>
        <div class="detail-row">
          <dt>服务地址</dt>
          <dd>{{ admin.detail.order.street_name_snapshot }} · {{ admin.detail.order.address_snapshot }}</dd>
        </div>
        <div class="detail-row">
          <dt>项目规则</dt>
          <dd>{{ admin.detail.order.rule_snapshot }}</dd>
        </div>
        <div class="detail-row">
          <dt>派单记录</dt>
          <dd>{{ admin.detail.assignments.length }} 条</dd>
        </div>
      </dl>

      <section>
        <div class="section__head">
          <h3 class="section-title" style="font-size: var(--fs-16)">操作交接</h3>
          <AppButton variant="secondary" compact @click="openHandovers">管理交接</AppButton>
        </div>
        <p class="muted" style="font-size: var(--fs-13)">
          创建人账号停用或失去对应家庭权限时，可将这笔订单的指定操作交给仍有权限的家庭成员。
        </p>
      </section>

      <section>
        <h3 class="section-title" style="font-size: var(--fs-16); margin-bottom: var(--sp-4)">订单时间轴</h3>
        <div class="timeline">
          <div
            v-for="(event, index) in admin.detail.events"
            :key="index"
            class="tl"
            :class="
              event.new_status === 'INTERRUPTED'
                ? 'tl--danger'
                : index === admin.detail.events.length - 1
                  ? 'tl--active'
                  : 'tl--done'
            "
          >
            <div class="tl__time num">{{ formatDateTime(event.created_at) }}</div>
            <div class="tl__rail"><span class="tl__dot" /></div>
            <div class="tl__body">
              <p class="tl__title">
                {{ event.action }} · {{ STATUS_LABEL[event.new_status ?? ''] ?? event.new_status ?? '—' }}
              </p>
              <p class="tl__desc">{{ event.note ?? '—' }} · 操作人 #{{ event.actor_id }}</p>
            </div>
          </div>
        </div>
      </section>

      <section v-if="admin.detail.assignments.length">
        <h3 class="section-title" style="font-size: var(--fs-16); margin-bottom: var(--sp-3)">
          派单与服务人员
        </h3>
        <dl style="margin: 0">
          <div v-for="item in admin.detail.assignments" :key="item.id" class="detail-row">
            <dt>{{ item.worker_name }}</dt>
            <dd>
              {{ item.status }} · 派单于 {{ formatDateTime(item.assigned_at)
              }}{{ item.rejection_reason ? ` · 拒单原因：${item.rejection_reason}` : '' }}
            </dd>
          </div>
        </dl>
      </section>
    </template>

    <template #footer>
      <AppButton variant="secondary" @click="admin.closeDetail">关闭</AppButton>
      <AppButton
        v-if="admin.detail?.order.status === 'WAITING_ASSIGNMENT'"
        @click="admin.detail && openDispatch(admin.detail.order.id)"
        >去派单</AppButton
      >
      <AppButton
        v-if="admin.detail?.order.status === 'COMPLETED'"
        variant="secondary"
        @click="recordOfflineSettlement(admin.detail.order.id)"
        >登记线下结算</AppButton
      >
      <template v-if="admin.detail?.order.status === 'INTERRUPTED'">
        <AppButton variant="secondary" @click="scheduleSupplement(admin.detail.order.id)"
          >安排补服务</AppButton
        >
        <AppButton @click="proposeFeeAdjustment(admin.detail.order.id)">提出费用方案</AppButton>
      </template>
    </template>
  </AppDrawer>

  <AppDialog
    v-model="handoverOpen"
    title="订单操作交接"
    subtitle="仅能交接给该老人档案下仍具备对应操作权限的有效账号。"
    wide
  >
    <p v-if="handoverError" class="banner banner--error" role="alert">{{ handoverError }}</p>
    <section v-if="handoverRows.length">
      <h3 class="section-title" style="font-size: var(--fs-16)">交接记录</h3>
      <div v-for="item in handoverRows" :key="item.id" class="list__item">
        <div class="list__body">
          <p class="list__title">
            {{ HANDOVER_ACTION_LABEL[item.action] ?? item.action }} → {{ item.target_name }}
          </p>
          <p class="list__desc">{{ item.status === 'ACTIVE' ? '生效中' : '已撤销' }} · {{ item.reason }}</p>
        </div>
        <AppButton
          v-if="item.status === 'ACTIVE'"
          variant="ghost"
          compact
          :disabled="handoverBusy"
          @click="revokeHandover(item.id)"
        >
          撤销
        </AppButton>
      </div>
    </section>
    <p v-else class="muted">当前订单没有交接记录。</p>
    <div
      v-if="admin.detail && availableHandoverActions(admin.detail.order.status).length"
      class="catalog-form"
      style="margin-top: var(--sp-5)"
    >
      <label
        >交接操作
        <select v-model="handoverAction" @change="loadHandoverCandidates">
          <option
            v-for="action in availableHandoverActions(admin.detail.order.status)"
            :key="action"
            :value="action"
          >
            {{ HANDOVER_ACTION_LABEL[action] }}
          </option>
        </select>
      </label>
      <label
        >接收家庭成员
        <select v-model.number="handoverTarget" :disabled="!handoverCandidates.length">
          <option v-for="person in handoverCandidates" :key="person.account_id" :value="person.account_id">
            {{ person.display_name }}（{{ person.username }}）
          </option>
        </select>
      </label>
      <label class="catalog-form__wide"
        >交接原因
        <AppTextarea
          v-model="handoverReason"
          maxlength="500"
          placeholder="说明创建人为何无法继续处理该操作"
        />
      </label>
      <div class="catalog-form__actions">
        <AppButton
          :loading="handoverBusy"
          :disabled="!handoverTarget || !handoverReason.trim()"
          @click="saveHandover"
          >确认交接</AppButton
        >
      </div>
      <p v-if="!handoverCandidates.length && !handoverError" class="muted catalog-form__wide">
        没有可接收此操作的家庭成员。
      </p>
    </div>
  </AppDialog>
</template>

<style scoped>
  .catalog-form {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--sp-4);
  }
  .catalog-form label {
    display: grid;
    gap: var(--sp-2);
    color: var(--c-ink-muted);
    font-size: var(--fs-13);
  }
  .catalog-form__wide,
  .catalog-form__actions {
    grid-column: 1 / -1;
  }
  .catalog-form textarea {
    resize: vertical;
  }
  .catalog-form__actions {
    display: flex;
    justify-content: flex-end;
    gap: var(--sp-3);
    padding-top: var(--sp-2);
  }
  .row-actions {
    display: flex;
    gap: var(--sp-2);
  }
  @media (max-width: 680px) {
    .catalog-form {
      grid-template-columns: 1fr;
    }
    .catalog-form__wide,
    .catalog-form__actions {
      grid-column: auto;
    }
  }
  .dispatch-layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 290px;
    gap: var(--sp-5);
    padding: var(--sp-5);
    align-items: start;
  }
  .dispatch-list {
    min-width: 0;
  }
  .dispatch-panel {
    overflow: hidden;
    border-radius: var(--r-card);
    background: var(--c-surface-sunken);
  }
  .dispatch-panel__head {
    padding: var(--sp-5);
    background: var(--c-primary-50);
  }
  .dispatch-panel__head h2 {
    margin: 0;
    font-size: var(--fs-16);
  }
  .dispatch-panel__head p,
  .dispatch-panel__help {
    margin: var(--sp-2) 0 0;
    color: var(--c-ink-muted);
    font-size: var(--fs-13);
  }
  .dispatch-panel__body {
    display: grid;
    gap: var(--sp-3);
    padding: var(--sp-5);
  }
  .dispatch-panel__order {
    margin: 0;
    font-weight: var(--fw-semi);
  }
  .dispatch-panel .candidate {
    width: 100%;
    grid-template-columns: minmax(0, 1fr) auto;
  }
  .dispatch-panel .candidate__why {
    color: var(--c-success-ink);
    font-size: var(--fs-12);
  }
  @media (max-width: 1100px) {
    .dispatch-layout {
      grid-template-columns: minmax(0, 1fr);
    }
  }
</style>
