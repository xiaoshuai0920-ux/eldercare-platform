<script setup lang="ts">
  import { computed, onMounted } from 'vue'
  import PageHeader from '@/components/ui/PageHeader.vue'
  import AppButton from '@/components/ui/AppButton.vue'
  import AppIcon from '@/components/ui/AppIcon.vue'
  import StatCard from '@/components/ui/StatCard.vue'
  import StatusTag from '@/components/ui/StatusTag.vue'
  import DataTable, { type TableColumn } from '@/components/ui/DataTable.vue'
  import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
  import ErrorBanner from '@/components/ui/ErrorBanner.vue'
  import EmptyState from '@/components/ui/EmptyState.vue'
  import { useAdminStore } from '@/stores/admin'
  import { formatDateTime, money } from '@/utils/format'

  const admin = useAdminStore()

  const columns: TableColumn[] = [
    { key: 'id', label: '订单号' },
    { key: 'elder_name', label: '老人' },
    { key: 'item_name_snapshot', label: '项目' },
    { key: 'start_at', label: '预约时间' },
    { key: 'status', label: '状态', className: 'col-status', headerClass: 'col-status' },
    { key: 'payable_amount', label: '金额', align: 'right' },
  ]

  const recentRows = computed(() => admin.dashboard?.recentOrders ?? [])

  const PRIORITY: Record<string, { label: string; tone: 'danger' | 'action' | 'neutral' }> = {
    HIGH: { label: '高优先级', tone: 'danger' },
    ACTION: { label: '待处理', tone: 'action' },
    NORMAL: { label: '普通', tone: 'neutral' },
  }

  onMounted(() => admin.loadOverview())
</script>

<template>
  <PageHeader
    eyebrow="运营概览"
    title="让每项服务都有回应。"
    description="统计、待办与列表均来自管理端接口，无趋势推算。异常优先于数量。"
  >
    <template #actions>
      <span class="chip"
        >有效服务人员 <span class="num">{{ admin.dashboard?.activeWorkers ?? 0 }}</span> 人</span
      >
      <AppButton to="/admin/operations">前往服务运营</AppButton>
    </template>
  </PageHeader>

  <ErrorBanner
    v-if="admin.errors.dashboard"
    :title="admin.errors.dashboard"
    description="管理端数据加载失败，请稍后重试。"
    style="margin-top: var(--sp-5)"
  >
    <template #action>
      <AppButton variant="secondary" compact style="margin-left: auto" @click="admin.loadOverview"
        >重新加载</AppButton
      >
    </template>
  </ErrorBanner>

  <div v-if="admin.loading" class="card card--pad" style="margin-top: var(--sp-6)">
    <SkeletonBlock :rows="4" />
  </div>

  <div v-else class="grid grid--4" style="margin-top: var(--sp-6)">
    <StatCard
      label="待派单"
      :value="admin.dashboard?.waitingAssignment ?? 0"
      hint="需要指派服务人员"
      tone="action"
    />
    <StatCard label="服务中" :value="admin.dashboard?.inService ?? 0" hint="正在履约的订单" />
    <StatCard
      label="待确认"
      :value="admin.dashboard?.waitingConfirmation ?? 0"
      hint="等待家属确认完成"
      tone="action"
    />
    <StatCard label="已完成" :value="admin.dashboard?.completed ?? 0" hint="历史累计" />
  </div>

  <div
    class="grid"
    style="grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); margin-top: var(--sp-6); align-items: start"
  >
    <section class="card panel" aria-labelledby="todo-title">
      <div class="section__head" style="padding: var(--sp-5) var(--sp-6) 0; margin-bottom: var(--sp-3)">
        <div>
          <h2 id="todo-title" class="section-title">高优先级待办</h2>
          <p class="section__desc">由 /admin/todos 实时计算，不另存状态。</p>
        </div>
        <span class="tag tag--action">待你处理</span>
      </div>

      <div class="panel__body" style="padding-top: 0; display: grid; gap: var(--sp-2)">
        <article v-for="todo in admin.todos" :key="todo.orderId" class="todo">
          <div class="todo__main">
            <p class="todo__title">
              订单 {{ todo.orderId }} · {{ todo.title }}
              <span class="tag" :class="`tag--${PRIORITY[todo.priority]?.tone ?? 'action'}`">{{
                PRIORITY[todo.priority]?.label ?? todo.priority
              }}</span>
            </p>
            <p class="todo__reason">{{ todo.reason }}</p>
          </div>
          <div class="todo__end">
            <AppButton
              variant="secondary"
              compact
              :to="{ path: '/admin/operations', query: { panel: 'orders' } }"
              >去处理</AppButton
            >
          </div>
        </article>

        <EmptyState
          v-if="!admin.todos.length"
          title="当前没有高优先级待办"
          description="出现中断、超时未确认或临近未派单时会自动出现在这里。"
        />
      </div>

      <div class="banner banner--info" style="margin: 0 var(--sp-6) var(--sp-5)" role="note">
        <AppIcon name="info" />
        <div class="banner__body">
          <p class="banner__title">待办随状态变化</p>
          <p class="banner__desc">订单派单、确认或结案后，对应对办会从列表中消失。</p>
        </div>
      </div>
    </section>

    <section class="card panel" aria-labelledby="recent-title">
      <div class="section__head" style="padding: var(--sp-5) var(--sp-6) 0; margin-bottom: var(--sp-3)">
        <div>
          <h2 id="recent-title" class="section-title">最新订单</h2>
          <p class="section__desc">来自 /admin/dashboard 的最近订单。</p>
        </div>
        <RouterLink class="link-arrow" :to="{ path: '/admin/operations', query: { panel: 'orders' } }"
          >查看全部 →</RouterLink
        >
      </div>

      <DataTable
        :columns="columns"
        :rows="recentRows"
        max-height="none"
        caption="最新订单"
        empty-title="暂无订单"
        empty-desc="有新的预约后会出现在这里。"
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
      </DataTable>
    </section>
  </div>
</template>
