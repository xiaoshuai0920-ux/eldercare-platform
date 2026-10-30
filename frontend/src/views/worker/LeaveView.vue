<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue'
  import PageHeader from '@/components/ui/PageHeader.vue'
  import AppButton from '@/components/ui/AppButton.vue'
  import AppIcon from '@/components/ui/AppIcon.vue'
  import AppField from '@/components/ui/AppField.vue'
  import AppInput from '@/components/ui/AppInput.vue'
  import AppTextarea from '@/components/ui/AppTextarea.vue'
  import StatusTag from '@/components/ui/StatusTag.vue'
  import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
  import EmptyState from '@/components/ui/EmptyState.vue'
  import { useTaskStore } from '@/stores/tasks'
  import { useUiStore } from '@/stores/ui'
  import { addDays, formatFullDate, localDateTime, todayIso } from '@/utils/format'

  const tasks = useTaskStore()
  const ui = useUiStore()

  // 默认明天 08:00–17:00，不再写死演示日期
  const tomorrow = addDays(todayIso(), 1)
  const form = reactive({ start: `${tomorrow}T08:00`, end: `${tomorrow}T17:00`, reason: '' })

  /** 请假开始时间的最小可选值：当前时刻 */
  const minDateTime = localDateTime()
  const errors = reactive({ start: '', end: '', reason: '' })
  const submitting = ref(false)

  const records = computed(() => [...tasks.leaves].sort((a, b) => (a.start_at < b.start_at ? 1 : -1)))

  onMounted(() => tasks.load())

  function validate(): boolean {
    errors.start = !form.start
      ? '请选择开始时间'
      : new Date(form.start).getTime() < Date.now()
        ? '开始时间不能早于当前时间'
        : ''
    errors.end = !form.end ? '请选择结束时间' : form.end <= form.start ? '结束时间需晚于开始时间' : ''
    errors.reason = form.reason.trim() ? '' : '请填写请假原因'
    return !errors.start && !errors.end && !errors.reason
  }

  async function submit() {
    if (!validate()) return
    submitting.value = true
    const ok = await tasks.requestLeave({
      startAt: form.start.length === 16 ? `${form.start}:00` : form.start,
      endAt: form.end.length === 16 ? `${form.end}:00` : form.end,
      reason: form.reason.trim(),
    })
    submitting.value = false
    if (ok) {
      form.reason = ''
      ui.notify('请假申请已提交，等待审批')
    } else {
      ui.notify(tasks.error, 'danger')
    }
  }
</script>

<template>
  <PageHeader
    eyebrow="请假"
    title="先安排好人手，再离开。"
    description="请假由运营人员审批；审批中的申请会在列表中标注状态。"
  >
    <template #actions>
      <AppButton variant="secondary" to="/worker">返回工作台</AppButton>
    </template>
  </PageHeader>

  <div
    class="grid"
    style="
      grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
      margin-top: var(--sp-6);
      align-items: start;
    "
  >
    <section class="card card--pad" aria-labelledby="leave-form-title">
      <h2 id="leave-form-title" class="section-title">提交请假申请</h2>
      <p class="section__desc">提交后写入后端，审批结果会通过站内消息通知。</p>

      <form
        class="stack"
        style="margin-top: var(--sp-5); gap: var(--sp-4)"
        novalidate
        @submit.prevent="submit"
      >
        <div class="grid grid--2" style="gap: var(--sp-4)">
          <AppField label="开始时间" html-for="leave-start" required :error="errors.start">
            <AppInput
              id="leave-start"
              v-model="form.start"
              type="datetime-local"
              :min="minDateTime"
              :invalid="!!errors.start"
            />
          </AppField>
          <AppField
            label="结束时间"
            html-for="leave-end"
            required
            help="结束时间需晚于开始时间。"
            :error="errors.end"
          >
            <AppInput id="leave-end" v-model="form.end" type="datetime-local" :invalid="!!errors.end" />
          </AppField>
        </div>

        <AppField label="请假原因" html-for="leave-reason" required :error="errors.reason">
          <AppTextarea
            id="leave-reason"
            v-model="form.reason"
            placeholder="请简要说明原因，便于调度安排替班。"
          />
        </AppField>

        <div class="banner banner--warm" role="note">
          <AppIcon name="alert" />
          <div class="banner__body">
            <p class="banner__title">提交后的影响</p>
            <p class="banner__desc">审批通过后，该时段不会再有新派单；已存在的派单需要运营人员改派。</p>
          </div>
        </div>

        <AppButton type="submit" :loading="submitting">提交申请</AppButton>
      </form>
    </section>

    <section class="card" aria-labelledby="leave-list-title" style="overflow: hidden">
      <div class="section__head" style="padding: var(--sp-5) var(--sp-6) 0; margin-bottom: 0">
        <div>
          <h2 id="leave-list-title" class="section-title">请假记录</h2>
          <p class="section__desc">共 {{ records.length }} 条。</p>
        </div>
      </div>

      <div v-if="tasks.loading" style="padding: var(--sp-6)"><SkeletonBlock :rows="3" /></div>
      <div v-else-if="!records.length" style="padding: var(--sp-6)">
        <EmptyState title="还没有请假记录" description="提交后会出现在这里，并同步给运营人员。" />
      </div>
      <div v-else class="list" style="margin-top: var(--sp-4)">
        <article v-for="record in records" :key="record.id" class="list__item">
          <span class="list__icon" aria-hidden="true"><AppIcon name="calendar" /></span>
          <div class="list__body">
            <p class="list__title">请假 <StatusTag :value="record.status" dict="leave" /></p>
            <p class="list__desc num">
              {{ formatFullDate(record.start_at) }} – {{ formatFullDate(record.end_at).slice(11) }}
            </p>
            <p class="list__desc">
              原因：{{ record.reason
              }}{{ record.decided_at ? ` · 审批于 ${formatFullDate(record.decided_at)}` : '' }}
            </p>
          </div>
        </article>
      </div>

      <div class="banner banner--info" style="margin: var(--sp-5) var(--sp-6) var(--sp-6)" role="note">
        <AppIcon name="info" />
        <div class="banner__body">
          <p class="banner__title">审批中和已批准都会占用时段</p>
          <p class="banner__desc">派单候选会排除请假中的服务人员，因此提交请假后可能不再收到该时段派单。</p>
        </div>
      </div>
    </section>
  </div>
</template>
