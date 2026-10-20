<template>
  <WorkspaceLayout role="ADMIN">
    <PageHeader eyebrow="运营概览" title="让每项服务都有回应。" description="统计与列表均来自当前业务数据库。">
      <template #actions>
        <span class="worker-chip">有效服务人员 {{ dashboard?.activeWorkers ?? '—' }}</span>
        <router-link class="link" to="/admin/operations">前往服务运营 →</router-link>
      </template>
    </PageHeader>

    <ErrorBanner :message="error" />

    <div class="metrics">
      <StatCard v-for="metric in metrics" :key="metric.label" :label="metric.label" :value="metric.value" />
    </div>

    <SectionCard class="panel" title="高优先级待办" description="按订单与人员当前状态实时计算，不另存状态。">
      <ul v-if="todos.length" class="todo-list">
        <li v-for="todo in todos" :key="todo.orderId">
          <div>
            <strong>订单 #{{ todo.orderId }} · {{ todo.title }}</strong>
            <p class="muted">{{ todo.reason }}</p>
          </div>
          <StatusTag :value="todo.priority" :dict="PRIORITY" />
        </li>
      </ul>
      <EmptyState v-else-if="!error" title="当前没有高优先级待办。" description="出现中断、超时未确认或临近未派单时会自动出现在这里。" />
    </SectionCard>

    <SectionCard class="panel" title="最新订单">
      <template #actions>
        <router-link class="link" to="/admin/operations">查看全部 →</router-link>
      </template>
      <div v-if="dashboard?.recentOrders?.length" class="order-list">
        <div v-for="order in dashboard.recentOrders" :key="order.id" class="order-row">
          <span class="cell-strong">{{ order.item_name_snapshot }}</span>
          <span class="muted">{{ order.elder_name }}</span>
          <StatusTag :value="order.status" :dict="ORDER_STATUS" />
          <span class="muted time">{{ formatDateTime(order.start_at) }}</span>
        </div>
      </div>
      <EmptyState v-else-if="!error" title="暂无订单数据。" />
    </SectionCard>
  </WorkspaceLayout>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import WorkspaceLayout from '../../layouts/WorkspaceLayout.vue'
import PageHeader from '../../components/common/PageHeader.vue'
import StatCard from '../../components/common/StatCard.vue'
import StatusTag from '../../components/common/StatusTag.vue'
import SectionCard from '../../components/common/SectionCard.vue'
import EmptyState from '../../components/common/EmptyState.vue'
import ErrorBanner from '../../components/common/ErrorBanner.vue'
import { formatDateTime } from '../../utils/format'
import { ORDER_STATUS, PRIORITY } from '../../constants/status'
import { api } from '../../api/client'

type Recent = { id: number; item_name_snapshot: string; elder_name: string; status: string; start_at: string }
type Dashboard = {
  waitingAssignment: number
  waitingConfirmation: number
  inService: number
  completed: number
  activeWorkers: number
  recentOrders: Recent[]
}
type Todo = { orderId: number; title: string; reason: string; priority: string }

const dashboard = ref<Dashboard | null>(null)
const todos = ref<Todo[]>([])
const error = ref('')

const metrics = computed(() => [
  { label: '待派单', value: dashboard.value?.waitingAssignment ?? '—' },
  { label: '服务中', value: dashboard.value?.inService ?? '—' },
  { label: '待确认', value: dashboard.value?.waitingConfirmation ?? '—' },
  { label: '已完成', value: dashboard.value?.completed ?? '—' },
])

onMounted(async () => {
  try {
    const [stats, todoList] = await Promise.all([api<Dashboard>('/admin/dashboard'), api<Todo[]>('/admin/todos')])
    dashboard.value = stats
    todos.value = todoList
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '统计加载失败'
  }
})
</script>
<style scoped>
.metrics { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: var(--space-4); margin: var(--space-6) 0; }
.panel { margin-bottom: var(--space-4); }
.worker-chip { padding: 6px 14px; border-radius: var(--radius-pill); background: var(--color-primary-soft); color: var(--color-primary); font-size: var(--text-sm); font-weight: 650; }
.link { color: var(--color-primary); font-weight: 650; font-size: var(--text-sm); }
.todo-list { list-style: none; margin: 0; padding: 0; }
.todo-list li { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-4); padding: var(--space-4) 0; border-top: 1px solid var(--color-line); }
.todo-list li:first-child { border-top: 0; padding-top: 0; }
.todo-list p { margin: var(--space-1) 0 0; font-size: var(--text-sm); }
.order-list { display: grid; }
.order-row { display: grid; grid-template-columns: minmax(0, 2fr) 1fr 110px 170px; align-items: center; gap: var(--space-4); min-height: var(--row-compact); border-top: 1px solid var(--color-line); }
.order-row:first-child { border-top: 0; }
.cell-strong { font-weight: 650; }
@media (max-width: 760px) {
  .order-row { grid-template-columns: 1fr 1fr; padding: var(--space-3) 0; }
  .time { grid-column: 1 / -1; }
}
</style>
