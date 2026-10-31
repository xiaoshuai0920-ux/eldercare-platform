<script setup lang="ts">
  import { computed, onMounted, ref, watch } from 'vue'
  import PageHeader from '@/components/ui/PageHeader.vue'
  import AppButton from '@/components/ui/AppButton.vue'
  import AppSegmented from '@/components/ui/AppSegmented.vue'
  import StatusTag from '@/components/ui/StatusTag.vue'
  import AppDrawer from '@/components/ui/AppDrawer.vue'
  import AppDialog from '@/components/ui/AppDialog.vue'
  import AppIcon from '@/components/ui/AppIcon.vue'
  import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
  import ErrorBanner from '@/components/ui/ErrorBanner.vue'
  import EmptyState from '@/components/ui/EmptyState.vue'
  import AppField from '@/components/ui/AppField.vue'
  import AppSelect from '@/components/ui/AppSelect.vue'
  import AppTextarea from '@/components/ui/AppTextarea.vue'
  import SettlementPanel from '@/components/ui/SettlementPanel.vue'
  import PaginationBar from '@/components/ui/PaginationBar.vue'
  import { userApi } from '@/api/endpoints'
  import { ApiError } from '@/api/client'
  import { useOrderStore, type FilterKey } from '@/stores/orders'
  import { useFamilyStore } from '@/stores/family'
  import { useUiStore } from '@/stores/ui'
  import { formatFullDate, money } from '@/utils/format'

  const orders = useOrderStore()
  const family = useFamilyStore()
  const ui = useUiStore()

  const filter = ref<FilterKey>('ALL')
  const filterOptions = computed(() => [
    { value: 'ALL' as FilterKey, label: `全部 ${orders.counts.total}` },
    { value: 'action' as FilterKey, label: `待你处理 ${orders.counts.pending}` },
    { value: 'active' as FilterKey, label: `进行中 ${orders.counts.active}` },
    { value: 'upcoming' as FilterKey, label: `待服务 ${orders.counts.upcoming}` },
    { value: 'done' as FilterKey, label: `已结束 ${orders.counts.done}` },
  ])

  watch(filter, (value) => orders.load(1, value))

  const rows = computed(() => orders.group(filter.value))

  const GROUP_LABEL: Record<string, string> = {
    action: '待你处理',
    active: '进行中',
    upcoming: '待服务',
    done: '已完成 / 已结束',
  }

  const grouped = computed(() => {
    const keys: FilterKey[] =
      filter.value === 'ALL' ? ['action', 'active', 'upcoming', 'done'] : [filter.value]
    return keys
      .map((key) => ({ key, label: GROUP_LABEL[key] ?? key, items: orders.group(key) }))
      .filter((group) => group.items.length > 0)
  })

  const confirmOpen = ref(false)
  const cancelOpen = ref(false)
  const busy = ref(false)
  const targetId = ref<number | null>(null)
  const feeAdjustments = ref<Record<string, unknown>[]>([])
  const feeLoading = ref(false)

  const detailOpen = computed({
    get: () => orders.detail !== null,
    set: (open: boolean) => {
      if (!open) orders.closeDetail()
    },
  })

  function elderName(elderId: number): string {
    return family.elders.find((elder) => elder.id === elderId)?.fullName ?? `#${elderId}`
  }

  async function openDetail(id: number) {
    feeAdjustments.value = []
    orders.openDetail(id)
    feeLoading.value = true
    try {
      feeAdjustments.value = await userApi.feeAdjustments(id)
    } catch {
      feeAdjustments.value = []
    } finally {
      feeLoading.value = false
    }
  }

  /** 按档案补齐权限：订单卡片上的确认/取消/评价按钮必须与后端授权一致，避免点了才报 403 */
  async function refreshPermissions() {
    const ids = Array.from(new Set(orders.list.map((row) => row.elder_id)))
    await Promise.all(ids.map((id) => family.ensurePermissions(id)))
  }

  watch(() => orders.list, refreshPermissions, { immediate: true })

  function permsOf(elderId: number) {
    return family.permsOf(elderId)
  }

  /** 权限未知（未加载/加载失败）时返回 false，按钮先不出现，避免误导 */
  function allowed(elderId: number, key: 'canConfirm' | 'canCancel' | 'canReview'): boolean {
    return Boolean(permsOf(elderId)?.[key])
  }

  /** 403 权限类错误给出可执行的说明，而不是「请稍后重试」 */
  function actionMessage(fallback: string): string {
    const denied =
      orders.actionErrorCode === 'FAMILY_PERMISSION_REQUIRED' ||
      orders.actionErrorCode === 'ORDER_ACTION_FORBIDDEN'
    if (denied) {
      return '当前账号在该服务对象档案上没有此操作的授权。请让档案管理者在「家庭管理」中授予对应权限后再试。'
    }
    return orders.actionError || fallback
  }

  async function startOrderDispute(id: number) {
    const reason = window.prompt('请简要说明对本次服务的异议')?.trim()
    if (!reason) return
    busy.value = true
    try {
      await userApi.createOrderDispute(id, reason)
      ui.notify('争议已提交，平台将协助处理', 'success')
      await openDetail(id)
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '争议提交失败', 'danger')
    } finally {
      busy.value = false
    }
  }

  async function decideFee(feeId: number, approve: boolean) {
    const id = orders.detail?.id
    if (!id) return
    if (
      !window.confirm(
        approve ? '确认接受该异常费用方案？确认后将按提案金额结案。' : '确认拒绝该异常费用方案？',
      )
    )
      return
    busy.value = true
    try {
      await userApi.decideFeeAdjustment(id, feeId, approve)
      await openDetail(id)
      ui.notify(approve ? '已确认费用方案' : '已拒绝费用方案', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '费用处理失败', 'danger')
    } finally {
      busy.value = false
    }
  }

  function askConfirm(id: number) {
    targetId.value = id
    confirmOpen.value = true
  }

  async function doConfirm() {
    if (!targetId.value) return
    busy.value = true
    const ok = await orders.confirm(targetId.value)
    busy.value = false
    if (ok) {
      confirmOpen.value = false
      ui.notify('已确认完成', 'success')
      return
    }
    ui.notify(actionMessage('确认失败，请稍后重试'), 'danger')
  }

  function askCancel(id: number) {
    targetId.value = id
    cancelOpen.value = true
  }

  async function doCancel() {
    if (!targetId.value) return
    busy.value = true
    const ok = await orders.cancel(targetId.value)
    busy.value = false
    if (ok) {
      cancelOpen.value = false
      ui.notify('订单已取消')
      return
    }
    ui.notify(actionMessage('取消失败，请稍后重试'), 'danger')
  }

  // ---- 评价 ----
  const reviewOpen = ref(false)
  const reviewTarget = ref<number | null>(null)
  const reviewStars = ref(5)
  const reviewContent = ref('')
  const existingReview = ref<{ stars: number; content: string | null } | null>(null)
  const reviewBusy = ref(false)
  const reviewError = ref('')
  const reviewed = ref<Record<number, boolean>>({})

  async function openReview(id: number) {
    reviewTarget.value = id
    reviewError.value = ''
    reviewStars.value = 5
    reviewContent.value = ''
    existingReview.value = null
    try {
      const found = await userApi.review(id)
      if (found) existingReview.value = { stars: found.stars, content: found.content }
      reviewed.value[id] = Boolean(found)
    } catch {
      /* 未评价时后端返回空，不阻断打开弹层 */
    }
    reviewOpen.value = true
  }

  async function submitReview() {
    if (!reviewTarget.value) return
    reviewBusy.value = true
    reviewError.value = ''
    try {
      await userApi.submitReview(reviewTarget.value, {
        stars: reviewStars.value,
        content: reviewContent.value.trim(),
      })
      reviewed.value[reviewTarget.value] = true
      reviewOpen.value = false
      ui.notify('评价已提交，感谢您的反馈')
      await orders.load()
    } catch (reason) {
      reviewError.value = reason instanceof ApiError ? reason.message : '评价提交失败，请稍后重试'
    } finally {
      reviewBusy.value = false
    }
  }

  function statusAllowsCancel(status: string): boolean {
    return ['WAITING_ASSIGNMENT', 'WAITING_ACCEPTANCE', 'WAITING_SERVICE'].includes(status)
  }

  onMounted(() => {
    orders.load(1, 'ALL')
    family.load()
  })
</script>

<template>
  <PageHeader
    eyebrow="我的订单"
    title="按状态查看，先处理待你确认的。"
    description="服务完成后需要您确认，确认即表示认可本次服务。列表与详情均来自订单接口。"
  />

  <div class="row" style="margin-top: var(--sp-6); justify-content: space-between">
    <AppSegmented v-model="filter" :options="filterOptions" aria-label="订单状态筛选" />
    <AppButton variant="ghost" compact @click="orders.load()">刷新</AppButton>
  </div>

  <ErrorBanner
    v-if="orders.error"
    :title="orders.error"
    description="订单数据加载失败，请稍后重试。"
    style="margin-top: var(--sp-4)"
  >
    <template #action>
      <AppButton variant="secondary" compact style="margin-left: auto" @click="orders.load()"
        >重新加载</AppButton
      >
    </template>
  </ErrorBanner>

  <div v-if="orders.loading" class="card card--pad" style="margin-top: var(--sp-5)">
    <SkeletonBlock :rows="5" />
  </div>

  <template v-else>
    <template v-for="group in grouped" :key="group.key">
      <div class="group-head">
        <h2>{{ group.label }}</h2>
        <span class="count-chip num">本页 {{ group.items.length }}</span>
        <span v-if="group.key === 'action'" class="tag tag--action">需要确认</span>
      </div>

      <div class="grid" style="gap: var(--sp-4)">
        <article
          v-for="order in group.items"
          :key="order.id"
          class="card order-card"
          :style="group.key === 'action' ? 'border-left: 3px solid var(--c-warm)' : undefined"
        >
          <div class="order-card__head">
            <div>
              <p class="order-card__id num">订单号 {{ order.id }}</p>
              <h3 class="order-card__title">{{ order.item_name_snapshot }}</h3>
            </div>
            <StatusTag :value="order.status" />
          </div>

          <div class="order-card__meta">
            <div class="meta-item">
              <span class="meta-item__label">预约时间</span
              ><span class="meta-item__value num">{{ formatFullDate(order.start_at) }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-item__label">服务对象</span
              ><span class="meta-item__value">{{ elderName(order.elder_id) }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-item__label">服务类别</span
              ><span class="meta-item__value">{{ order.category_snapshot }}</span>
            </div>
          </div>

          <div class="order-card__foot">
            <span class="order-card__amount num">{{ money(order.payable_amount) }}</span>
            <span
              v-if="
                order.status === 'WAITING_CONFIRMATION' &&
                permsOf(order.elder_id) &&
                !allowed(order.elder_id, 'canConfirm')
              "
              class="chip"
              title="该档案未授予当前账号「确认完成」权限"
              >无确认权限</span
            >
            <AppButton
              v-if="statusAllowsCancel(order.status) && allowed(order.elder_id, 'canCancel')"
              variant="ghost"
              compact
              @click="askCancel(order.id)"
              >取消订单</AppButton
            >
            <AppButton variant="secondary" @click="openDetail(order.id)">查看详情</AppButton>
            <AppButton
              v-if="order.status === 'WAITING_CONFIRMATION' && allowed(order.elder_id, 'canConfirm')"
              @click="askConfirm(order.id)"
              >确认完成</AppButton
            >
            <AppButton
              v-if="order.status === 'COMPLETED' && allowed(order.elder_id, 'canReview')"
              variant="ghost"
              compact
              @click="openReview(order.id)"
              >{{ reviewed[order.id] ? '查看评价' : '评价服务' }}</AppButton
            >
          </div>
        </article>
      </div>
    </template>

    <EmptyState
      v-if="!rows.length"
      title="这个状态下没有订单"
      description="切换筛选条件，或到「服务预约」提交一次新的服务需求。"
    />
    <PaginationBar
      :page="orders.page"
      :total="orders.total"
      :size="orders.size"
      :disabled="orders.loading"
      @change="orders.load"
    />
  </template>

  <AppDrawer
    v-model="detailOpen"
    :title="orders.detail ? orders.detail.item_name_snapshot : '订单详情'"
    :subtitle="orders.detail ? formatFullDate(orders.detail.start_at) : ''"
  >
    <template #eyebrow
      ><p v-if="orders.detail" class="order-card__id num">订单号 {{ orders.detail.id }}</p></template
    >

    <div v-if="orders.detailLoading"><SkeletonBlock :rows="4" /></div>
    <p v-else-if="!orders.detail" class="muted">{{ orders.actionError || '订单详情加载失败，请稍后重试' }}</p>
    <template v-else-if="orders.detail">
      <div class="row" style="justify-content: space-between">
        <StatusTag :value="orders.detail.status" />
        <span class="order-card__amount num" style="margin: 0; font-size: var(--fs-24)">{{
          money(orders.detail.payable_amount)
        }}</span>
      </div>
      <dl style="margin: 0">
        <div class="detail-row">
          <dt>服务对象</dt>
          <dd>{{ elderName(orders.detail.elder_id) }}</dd>
        </div>
        <div class="detail-row">
          <dt>服务时间</dt>
          <dd class="num">
            {{ formatFullDate(orders.detail.start_at) }} –
            {{ formatFullDate(orders.detail.end_at).slice(11) }}
          </dd>
        </div>
        <div class="detail-row">
          <dt>服务地址</dt>
          <dd>{{ orders.detail.street_name_snapshot }} · {{ orders.detail.address_snapshot }}</dd>
        </div>
        <div class="detail-row">
          <dt>服务时长</dt>
          <dd class="num">{{ orders.detail.duration_minutes }} 分钟</dd>
        </div>
        <div class="detail-row">
          <dt>项目规则</dt>
          <dd>{{ orders.detail.rule_snapshot }}</dd>
        </div>
        <div class="detail-row">
          <dt>原始金额</dt>
          <dd class="num">{{ money(orders.detail.original_amount) }}</dd>
        </div>
        <div class="detail-row">
          <dt>创建时间</dt>
          <dd class="num">{{ formatFullDate(orders.detail.created_at) }}</dd>
        </div>
      </dl>
      <SettlementPanel
        v-if="orders.detail.status === 'COMPLETED'"
        :key="orders.detail.id"
        :order-id="orders.detail.id"
      />
      <section
        v-if="orders.detail.status === 'INTERRUPTED'"
        class="settlement-panel"
        aria-label="异常费用处理"
      >
        <h2>异常费用核实</h2>
        <p v-if="feeLoading" class="muted">正在读取费用提案…</p>
        <p v-else-if="!feeAdjustments.length" class="muted">管理员尚未提交费用提案。</p>
        <article v-for="fee in feeAdjustments" :key="String(fee.id)" class="list__item">
          <div class="list__body">
            <p class="list__title">
              提案金额：{{ money(Number(fee.proposed_amount)) }} ·
              {{ fee.status === 'PENDING' ? '待确认' : fee.status === 'APPROVED' ? '已接受' : '已拒绝' }}
            </p>
            <p class="list__desc">{{ fee.reason }}</p>
          </div>
          <div v-if="fee.status === 'PENDING'" class="row-actions">
            <AppButton compact :disabled="busy" @click="decideFee(Number(fee.id), true)">接受</AppButton>
            <AppButton variant="danger" compact :disabled="busy" @click="decideFee(Number(fee.id), false)"
              >拒绝</AppButton
            >
          </div>
        </article>
      </section>
      <div class="banner banner--info" role="note">
        <AppIcon name="info" />
        <div class="banner__body">
          <p class="banner__title">确认前请核对</p>
          <p class="banner__desc">确认后订单进入结算；如对服务有异议，可在订单中发起争议。</p>
        </div>
      </div>
    </template>

    <template #footer>
      <AppButton variant="secondary" @click="orders.closeDetail">关闭</AppButton>
      <AppButton
        v-if="
          orders.detail &&
          orders.detail.status === 'WAITING_CONFIRMATION' &&
          allowed(orders.detail.elder_id, 'canConfirm')
        "
        @click="askConfirm(orders.detail.id)"
        >确认完成</AppButton
      >
      <AppButton
        v-if="orders.detail && orders.detail.status === 'COMPLETED'"
        variant="secondary"
        @click="startOrderDispute(orders.detail.id)"
        >发起争议</AppButton
      >
    </template>
  </AppDrawer>

  <AppDialog
    v-model="confirmOpen"
    title="确认本次服务已完成？"
    subtitle="确认后订单进入结算，不能再修改服务记录。"
  >
    <p class="muted" style="font-size: var(--fs-14)">
      确认即表示认可本次服务。如需说明，请联系平台或在订单中发起争议。
    </p>
    <template #footer>
      <AppButton variant="ghost" @click="confirmOpen = false">再看看</AppButton>
      <AppButton :loading="busy" @click="doConfirm">确认完成</AppButton>
    </template>
  </AppDialog>

  <AppDialog
    v-model="cancelOpen"
    title="取消这次预约？"
    subtitle="取消后订单不再派单，已接单的服务人员会收到通知。"
  >
    <div class="banner banner--warm" role="note">
      <AppIcon name="alert" />
      <div class="banner__body">
        <p class="banner__title">操作后果</p>
        <p class="banner__desc">取消不可撤销，如需同一时段服务请重新提交预约。</p>
      </div>
    </div>
    <template #footer>
      <AppButton variant="ghost" @click="cancelOpen = false">保留订单</AppButton>
      <AppButton variant="danger" :loading="busy" @click="doCancel">确认取消</AppButton>
    </template>
  </AppDialog>

  <AppDialog
    v-model="reviewOpen"
    title="评价本次服务"
    subtitle="评价用于运营改进；提交后不可修改，如需更正请联系平台。"
  >
    <div v-if="reviewError" class="banner banner--error" role="alert">
      <AppIcon name="alert" />
      <div class="banner__body">
        <p class="banner__title">提交失败</p>
        <p class="banner__desc">{{ reviewError }}</p>
      </div>
    </div>

    <div v-if="existingReview" class="banner banner--info" role="note">
      <AppIcon name="info" />
      <div class="banner__body">
        <p class="banner__title">该订单已评价 · {{ existingReview.stars }} 星</p>
        <p class="banner__desc">{{ existingReview.content || '未填写文字说明' }}</p>
      </div>
    </div>

    <template v-else>
      <AppField label="评分" html-for="rv-stars" required>
        <AppSelect id="rv-stars" v-model.number="reviewStars">
          <option :value="5">5 星 · 非常满意</option>
          <option :value="4">4 星 · 满意</option>
          <option :value="3">3 星 · 一般</option>
          <option :value="2">2 星 · 不满意</option>
          <option :value="1">1 星 · 很不满意</option>
        </AppSelect>
      </AppField>
      <AppField label="文字说明" html-for="rv-content" help="可留空，最多 1000 字。">
        <AppTextarea
          id="rv-content"
          v-model="reviewContent"
          maxlength="1000"
          placeholder="例如：准时到达，餐食温度合适，老人反馈很好。"
        />
      </AppField>
    </template>

    <template #footer>
      <AppButton variant="ghost" @click="reviewOpen = false">关闭</AppButton>
      <AppButton v-if="!existingReview" :loading="reviewBusy" @click="submitReview">提交评价</AppButton>
    </template>
  </AppDialog>
</template>
