<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue'
  import PageHeader from '@/components/ui/PageHeader.vue'
  import AppButton from '@/components/ui/AppButton.vue'
  import AppIcon from '@/components/ui/AppIcon.vue'
  import AppSwitch from '@/components/ui/AppSwitch.vue'
  import StatusTag from '@/components/ui/StatusTag.vue'
  import AppDrawer from '@/components/ui/AppDrawer.vue'
  import AppDialog from '@/components/ui/AppDialog.vue'
  import AppField from '@/components/ui/AppField.vue'
  import AppSelect from '@/components/ui/AppSelect.vue'
  import AppTextarea from '@/components/ui/AppTextarea.vue'
  import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
  import ErrorBanner from '@/components/ui/ErrorBanner.vue'
  import EmptyState from '@/components/ui/EmptyState.vue'
  import { useTaskStore } from '@/stores/tasks'
  import { useUiStore } from '@/stores/ui'
  import { formatFullDate } from '@/utils/format'

  const tasks = useTaskStore()
  const ui = useUiStore()

  const rejectOpen = ref(false)
  const interruptOpen = ref(false)
  const targetId = ref<number | null>(null)
  const reason = ref('')
  const note = ref('')
  const acknowledged = ref(false)
  const busy = ref(false)

  const detailOpen = computed({
    get: () => tasks.detail !== null,
    set: (open: boolean) => {
      if (!open) tasks.closeTask()
    },
  })

  const STATUS_LABEL: Record<string, string> = {
    WAITING_ACCEPTANCE: '待接单',
    WAITING_SERVICE: '待服务',
    IN_SERVICE: '服务中',
    WAITING_CONFIRMATION: '待用户确认',
    COMPLETED: '已完成',
    INTERRUPTED: '服务中断',
  }

  onMounted(() => tasks.load())

  async function toggleAccepting(value: boolean) {
    const ok = await tasks.setAccepting(value)
    ui.notify(
      ok ? (value ? '已开启接单，将接收新派单' : '已暂停接单，不再接收新派单') : tasks.error,
      ok ? 'success' : 'danger',
    )
  }

  async function act(action: 'accept' | 'start' | 'submit', orderId: number) {
    const ok = await tasks.act(action, orderId)
    ui.notify(
      ok
        ? action === 'accept'
          ? '已接单'
          : action === 'start'
            ? '已开始服务'
            : '已提交完成，等待家属确认'
        : tasks.error,
      ok ? 'success' : 'danger',
    )
  }

  function askReject(orderId: number) {
    targetId.value = orderId
    reason.value = '时间冲突，无法按时到达'
    note.value = ''
    rejectOpen.value = true
  }

  async function doReject() {
    if (!targetId.value) return
    busy.value = true
    const ok = await tasks.reject(
      targetId.value,
      note.value.trim() ? `${reason.value}（${note.value.trim()}）` : reason.value,
    )
    busy.value = false
    rejectOpen.value = false
    ui.notify(ok ? '已拒绝派单' : tasks.error, ok ? 'default' : 'danger')
  }

  function askInterrupt(orderId: number) {
    targetId.value = orderId
    reason.value = '老人身体不适，已通知家属'
    note.value = ''
    acknowledged.value = false
    interruptOpen.value = true
  }

  async function doInterrupt() {
    if (!targetId.value) return
    busy.value = true
    const ok = await tasks.interrupt(
      targetId.value,
      note.value.trim() ? `${reason.value}：${note.value.trim()}` : reason.value,
    )
    busy.value = false
    interruptOpen.value = false
    ui.notify(ok ? '已上报中断，进入异常协调' : tasks.error, ok ? 'success' : 'danger')
  }
</script>

<template>
  <PageHeader title="今天的服务安排" description="按任务进度逐项处理，接单后请留意预约时间。">
    <template #actions>
      <AppButton variant="secondary" to="/worker/leave">申请请假</AppButton>
    </template>
  </PageHeader>

  <div class="worker-overview">
    <section class="duty" :class="tasks.accepting ? 'duty--on' : 'duty--off'" aria-labelledby="duty-title">
      <AppSwitch
        :model-value="tasks.accepting"
        size="lg"
        label-on="接单中"
        label-off="暂停接单"
        @update:model-value="toggleAccepting"
      />
      <div class="duty__text">
        <p id="duty-title" class="duty__state">{{ tasks.accepting ? '可接收新派单' : '暂停接收新派单' }}</p>
        <p class="duty__hint">暂停后调度不会再向您派单，已接任务不受影响。</p>
      </div>
    </section>

    <p v-if="!tasks.loading && !tasks.error" class="worker-summary">
      待接单 {{ tasks.pending }} 项 · 进行中 {{ tasks.running }} 项
    </p>
  </div>

  <ErrorBanner
    v-if="tasks.error"
    :title="tasks.error"
    description="任务加载失败，请稍后重试。"
    style="margin-top: var(--sp-5)"
  >
    <template #action>
      <AppButton variant="secondary" compact style="margin-left: auto" @click="tasks.load"
        >重新加载</AppButton
      >
    </template>
  </ErrorBanner>

  <section style="margin-top: var(--sp-6)" aria-labelledby="agenda-title">
    <div class="section__head">
      <div>
        <h2 id="agenda-title" class="section-title">今日任务流</h2>
        <p class="section__desc">按预约开始时间排序，时间均为到达时间。</p>
      </div>
      <span class="chip"
        ><span class="num">{{ tasks.tasks.length }}</span> 项安排</span
      >
    </div>

    <div v-if="tasks.loading" class="card card--pad"><SkeletonBlock :rows="3" /></div>
    <div v-else-if="!tasks.tasks.length" class="card card--pad">
      <EmptyState title="当前暂无有效任务" description="保持接单中，调度派单后会出现在这里。" />
    </div>
    <div v-else class="agenda">
      <article
        v-for="task in tasks.tasks"
        :key="task.order_id"
        class="agenda__item"
        :class="{ 'agenda__item--now': task.status === 'IN_SERVICE' }"
      >
        <div>
          <p class="agenda__time num">{{ formatFullDate(task.start_at).slice(11) }}</p>
          <p class="muted" style="font-size: var(--fs-12)">{{ STATUS_LABEL[task.status] ?? task.status }}</p>
        </div>
        <div class="agenda__meta">
          <p class="agenda__title">{{ task.item_name_snapshot }} · 订单 {{ task.order_id }}</p>
          <p class="agenda__sub">{{ task.street_name_snapshot ?? '—' }}</p>
          <p class="agenda__sub num">
            {{ formatFullDate(task.start_at) }} – {{ formatFullDate(task.end_at).slice(11) }}
          </p>
        </div>
        <div class="agenda__actions">
          <StatusTag :value="task.status" />
          <template v-if="task.status === 'WAITING_ACCEPTANCE'">
            <AppButton @click="act('accept', task.order_id)">接单</AppButton>
            <AppButton variant="ghost" compact @click="askReject(task.order_id)">拒单</AppButton>
          </template>
          <template v-else-if="task.status === 'WAITING_SERVICE'">
            <AppButton variant="secondary" @click="act('start', task.order_id)">开始服务</AppButton>
            <button class="linkbtn linkbtn--muted" type="button" @click="tasks.openTask(task.order_id)">
              详情
            </button>
          </template>
          <template v-else-if="task.status === 'IN_SERVICE'">
            <AppButton @click="act('submit', task.order_id)">提交完成</AppButton>
            <button class="linkbtn linkbtn--danger" type="button" @click="askInterrupt(task.order_id)">
              上报中断
            </button>
          </template>
          <template v-else>
            <button class="linkbtn linkbtn--muted" type="button" @click="tasks.openTask(task.order_id)">
              详情
            </button>
          </template>
        </div>
      </article>
    </div>
  </section>

  <AppDrawer
    v-model="detailOpen"
    :title="tasks.detail ? tasks.detail.item_name_snapshot : '任务详情'"
    :subtitle="
      tasks.detail
        ? `${formatFullDate(tasks.detail.start_at)} – ${formatFullDate(tasks.detail.end_at).slice(11)}`
        : ''
    "
  >
    <template #eyebrow
      ><p v-if="tasks.detail" class="order-card__id num">订单号 {{ tasks.detail.order_id }}</p></template
    >
    <div v-if="tasks.busy"><SkeletonBlock :rows="4" /></div>
    <dl v-else-if="tasks.detail" style="margin: 0">
      <div class="detail-row">
        <dt>服务对象</dt>
        <dd>{{ tasks.detail.elder_name }}</dd>
      </div>
      <div class="detail-row">
        <dt>服务项目</dt>
        <dd>{{ tasks.detail.item_name_snapshot }}</dd>
      </div>
      <div class="detail-row">
        <dt>服务地址</dt>
        <dd>{{ tasks.detail.street_name_snapshot }} · {{ tasks.detail.address_snapshot }}</dd>
      </div>
      <div class="detail-row">
        <dt>服务时长</dt>
        <dd class="num">{{ tasks.detail.duration_minutes }} 分钟</dd>
      </div>
      <div class="detail-row">
        <dt>项目规则</dt>
        <dd>{{ tasks.detail.rule_snapshot }}</dd>
      </div>
      <div class="detail-row">
        <dt>接单状态</dt>
        <dd>{{ tasks.detail.assignment_status }}</dd>
      </div>
    </dl>
    <template #footer>
      <AppButton variant="secondary" @click="tasks.closeTask">关闭</AppButton>
      <AppButton
        v-if="tasks.detail && tasks.detail.status === 'IN_SERVICE'"
        @click="act('submit', tasks.detail.order_id)"
        >提交完成</AppButton
      >
    </template>
  </AppDrawer>

  <AppDialog
    v-model="rejectOpen"
    title="拒绝这次派单？"
    subtitle="拒单后订单退回调度，由运营人员重新匹配人员。"
  >
    <div class="banner banner--warm" role="note">
      <AppIcon name="alert" />
      <div class="banner__body">
        <p class="banner__title">操作后果</p>
        <p class="banner__desc">该订单会回到「待派单」，由管理员重新安排人员。</p>
      </div>
    </div>
    <AppField label="拒单原因" html-for="reject-reason" required>
      <AppSelect id="reject-reason" v-model="reason">
        <option>时间冲突，无法按时到达</option>
        <option>地址超出我的服务范围</option>
        <option>不具备该项目所需资格</option>
        <option>身体不适</option>
      </AppSelect>
    </AppField>
    <AppField label="补充说明" html-for="reject-note">
      <AppTextarea id="reject-note" v-model="note" placeholder="例如：前一单未结束，预计迟到 40 分钟。" />
    </AppField>
    <template #footer>
      <AppButton variant="ghost" @click="rejectOpen = false">返回</AppButton>
      <AppButton variant="danger" :loading="busy" @click="doReject">确认拒单</AppButton>
    </template>
  </AppDialog>

  <AppDialog v-model="interruptOpen" title="上报服务中断" subtitle="提交后订单进入异常协调，由运营人员跟进。">
    <AppField label="中断原因" html-for="interrupt-reason" required>
      <AppSelect id="interrupt-reason" v-model="reason">
        <option>老人身体不适，已通知家属</option>
        <option>无法进入住所</option>
        <option>现场环境存在安全风险</option>
        <option>其他</option>
      </AppSelect>
    </AppField>
    <AppField label="情况说明" html-for="interrupt-note" required help="说明会同步给运营人员与家属。">
      <AppTextarea
        id="interrupt-note"
        v-model="note"
        placeholder="请说明发生时间、当前状态与已采取的措施。"
      />
    </AppField>
    <label class="check">
      <input v-model="acknowledged" type="checkbox" />
      <span>我已确认老人当前状态安全，并已通知家属或联系人。</span>
    </label>
    <template #footer>
      <AppButton variant="ghost" @click="interruptOpen = false">取消</AppButton>
      <AppButton
        variant="danger"
        :disabled="!acknowledged || !note.trim()"
        :loading="busy"
        @click="doInterrupt"
        >提交中断</AppButton
      >
    </template>
  </AppDialog>
</template>

<style scoped>
  .worker-overview {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
    gap: var(--sp-5);
    margin-top: var(--sp-6);
  }
  .worker-summary {
    color: var(--c-ink-soft);
  }
  @media (max-width: 767px) {
    .worker-overview {
      grid-template-columns: minmax(0, 1fr);
      gap: var(--sp-2);
    }
  }
</style>
