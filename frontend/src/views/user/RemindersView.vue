<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue'
  import PageHeader from '@/components/ui/PageHeader.vue'
  import AppButton from '@/components/ui/AppButton.vue'
  import AppIcon from '@/components/ui/AppIcon.vue'
  import AppField from '@/components/ui/AppField.vue'
  import AppInput from '@/components/ui/AppInput.vue'
  import AppSelect from '@/components/ui/AppSelect.vue'
  import AppTag from '@/components/ui/AppTag.vue'
  import AppDialog from '@/components/ui/AppDialog.vue'
  import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
  import ErrorBanner from '@/components/ui/ErrorBanner.vue'
  import EmptyState from '@/components/ui/EmptyState.vue'
  import { useReminderStore } from '@/stores/reminders'
  import { useFamilyStore } from '@/stores/family'
  import { useUiStore } from '@/stores/ui'
  import type { ReminderRow } from '@/api/types'
  import { formatFullDate, localDateTime, nowMs } from '@/utils/format'

  const reminders = useReminderStore()
  const family = useFamilyStore()
  const ui = useUiStore()

  const createOpen = ref(false)
  const editOpen = ref(false)
  const editId = ref<number | null>(null)
  const editForm = reactive({ title: '', recurrence: 'DAILY', nextFireAt: '' })
  const submitting = ref(false)
  // 默认取下一个整点，避免默认值早于当前时间
  const form = reactive({ elderId: 0, title: '', recurrence: 'DAILY', firstAt: nextHour() })

  /** 下一个整点的本地时间串（YYYY-MM-DDTHH:mm） */
  function nextHour(): string {
    const base = new Date()
    base.setMinutes(0, 0, 0)
    base.setHours(base.getHours() + 1)
    const pad = (value: number) => String(value).padStart(2, '0')
    return `${base.getFullYear()}-${pad(base.getMonth() + 1)}-${pad(base.getDate())}T${pad(base.getHours())}:00`
  }

  /** datetime-local 的最小可选值：当前时刻 */
  const minDateTime = localDateTime()
  const error = ref('')

  const activeCount = computed(() => reminders.list.filter((row) => row.status === 'ACTIVE').length)

  onMounted(() => {
    reminders.load()
    family.load()
  })

  function openCreate() {
    form.elderId = family.elders[0]?.id ?? 0
    form.title = ''
    form.firstAt = nextHour()
    error.value = ''
    createOpen.value = true
  }

  function openEdit(item: ReminderRow) {
    editId.value = item.id
    editForm.title = item.title
    editForm.recurrence = item.recurrence
    editForm.nextFireAt = (item.next_fire_at ?? '').replace(' ', 'T')
    error.value = ''
    editOpen.value = true
  }

  async function submitEdit() {
    const timestamp = new Date(editForm.nextFireAt).getTime()
    if (!editForm.title.trim()) {
      error.value = '请填写提醒标题'
      return
    }
    if (!Number.isFinite(timestamp) || timestamp <= nowMs()) {
      error.value = '下次提醒时间必须晚于当前时间'
      return
    }
    if (editId.value === null) return
    submitting.value = true
    const ok = await reminders.update(editId.value, {
      title: editForm.title.trim(),
      recurrence: editForm.recurrence,
      nextFireAt: editForm.nextFireAt.length === 16 ? `${editForm.nextFireAt}:00` : editForm.nextFireAt,
    })
    submitting.value = false
    if (ok) {
      editOpen.value = false
      ui.notify('提醒已修改')
    } else error.value = reminders.error
  }

  async function submit() {
    if (!form.elderId || !form.title.trim()) {
      error.value = '请选择服务对象并填写提醒标题'
      return
    }
    if (new Date(form.firstAt).getTime() < nowMs()) {
      error.value = '首次提醒时间不能早于当前时间'
      return
    }
    submitting.value = true
    const ok = await reminders.create({
      elderId: form.elderId,
      title: form.title.trim(),
      recurrence: form.recurrence,
      firstAt: form.firstAt.length === 16 ? `${form.firstAt}:00` : form.firstAt,
    })
    submitting.value = false
    if (ok) {
      createOpen.value = false
      ui.notify('提醒已创建')
    } else {
      error.value = reminders.error
    }
  }

  async function stop(id: number) {
    const ok = await reminders.stop(id)
    ui.notify(ok ? '提醒已停用' : reminders.error, ok ? 'default' : 'danger')
  }
</script>

<template>
  <PageHeader
    eyebrow="生活提醒"
    title="把该记住的事，交给提醒。"
    description="提醒由后端按时间触发并写入消息。停用后不再触发，历史记录保留。"
  >
    <template #actions>
      <AppButton @click="openCreate"><AppIcon name="plus" />新增提醒</AppButton>
    </template>
  </PageHeader>

  <div class="row" style="margin-top: var(--sp-6); justify-content: space-between">
    <span class="chip">共 {{ reminders.list.length }} 条 · 启用 {{ activeCount }} 条</span>
    <AppButton variant="ghost" compact @click="reminders.load">刷新</AppButton>
  </div>

  <ErrorBanner
    v-if="reminders.error"
    :title="reminders.error"
    description="提醒加载失败，请稍后重试。"
    style="margin-top: var(--sp-4)"
  >
    <template #action>
      <AppButton variant="secondary" compact style="margin-left: auto" @click="reminders.load"
        >重新加载</AppButton
      >
    </template>
  </ErrorBanner>

  <section class="card" style="margin-top: var(--sp-4); overflow: hidden" aria-label="提醒列表">
    <div v-if="reminders.loading" style="padding: var(--sp-6)"><SkeletonBlock :rows="3" /></div>
    <div v-else-if="!reminders.list.length" style="padding: var(--sp-6)">
      <EmptyState title="还没有提醒" description="为用药、复诊或缴费设一个提醒，到点会通过站内消息通知。">
        <AppButton variant="secondary" @click="openCreate">新增提醒</AppButton>
      </EmptyState>
    </div>
    <div v-else class="list">
      <article
        v-for="item in reminders.list"
        :key="item.id"
        class="list__item"
        :style="item.status === 'ACTIVE' ? undefined : 'background: var(--c-surface-sunken)'"
      >
        <span class="list__icon" aria-hidden="true"><AppIcon name="clock" /></span>
        <div class="list__body">
          <p class="list__title">
            {{ item.title }}
            <AppTag :tone="item.status === 'ACTIVE' ? 'success' : 'neutral'">{{
              item.status === 'ACTIVE' ? '启用中' : '已停用'
            }}</AppTag>
          </p>
          <p class="list__desc">
            {{ item.elder_name }} ·
            {{
              item.recurrence === 'DAILY'
                ? '每天'
                : item.recurrence === 'WEEKLY'
                  ? '每周'
                  : item.recurrence === 'ONCE'
                    ? '仅一次'
                    : item.recurrence
            }}
          </p>
          <p class="list__desc num">
            下次提醒：{{ item.next_fire_at ? formatFullDate(item.next_fire_at) : '已停止' }}
          </p>
        </div>
        <div class="list__end">
          <button v-if="item.status === 'ACTIVE'" class="linkbtn" type="button" @click="openEdit(item)">
            修改
          </button>
          <button v-if="item.status === 'ACTIVE'" class="linkbtn" type="button" @click="stop(item.id)">
            停用
          </button>
          <span v-else class="muted" style="font-size: var(--fs-12)">已停止</span>
        </div>
      </article>
    </div>
  </section>

  <AppDialog v-model="editOpen" title="修改生活提醒" subtitle="修改下次触发时间和重复方式。">
    <div v-if="error" class="banner banner--error" role="alert">{{ error }}</div>
    <AppField label="提醒标题" html-for="edit-title" required
      ><AppInput id="edit-title" v-model="editForm.title" maxlength="100"
    /></AppField>
    <div class="grid grid--2" style="gap: var(--sp-4)">
      <AppField label="重复方式" html-for="edit-recur" required
        ><AppSelect id="edit-recur" v-model="editForm.recurrence"
          ><option value="ONCE">仅一次</option>
          <option value="DAILY">每天</option>
          <option value="WEEKLY">每周</option></AppSelect
        ></AppField
      >
      <AppField label="下次提醒时间" html-for="edit-next" required
        ><AppInput
          id="edit-next"
          v-model="editForm.nextFireAt"
          type="datetime-local"
          step="0.001"
          :min="minDateTime"
      /></AppField>
    </div>
    <template #footer
      ><AppButton variant="ghost" @click="editOpen = false">取消</AppButton
      ><AppButton :loading="submitting" @click="submitEdit">保存修改</AppButton></template
    >
  </AppDialog>

  <AppDialog v-model="createOpen" title="新增生活提醒" subtitle="提交后由后端按时间触发通知。">
    <div v-if="error" class="banner banner--error" role="alert">
      <AppIcon name="alert" />
      <div class="banner__body">
        <p class="banner__title">无法提交</p>
        <p class="banner__desc">{{ error }}</p>
      </div>
    </div>
    <AppField label="服务对象" html-for="rm-elder" required>
      <AppSelect id="rm-elder" v-model.number="form.elderId">
        <option v-for="elder in family.elders" :key="elder.id" :value="elder.id">{{ elder.fullName }}</option>
      </AppSelect>
    </AppField>
    <AppField label="提醒标题" html-for="rm-title" required>
      <AppInput id="rm-title" v-model="form.title" placeholder="例如：每日午餐用药提醒" />
    </AppField>
    <div class="grid grid--2" style="gap: var(--sp-4)">
      <AppField label="重复方式" html-for="rm-recur" required>
        <AppSelect id="rm-recur" v-model="form.recurrence">
          <option value="DAILY">每天</option>
          <option value="WEEKLY">每周</option>
        </AppSelect>
      </AppField>
      <AppField label="首次提醒时间" html-for="rm-first" required>
        <AppInput id="rm-first" v-model="form.firstAt" type="datetime-local" :min="minDateTime" />
      </AppField>
    </div>
    <template #footer>
      <AppButton variant="ghost" @click="createOpen = false">取消</AppButton>
      <AppButton :loading="submitting" @click="submit">创建提醒</AppButton>
    </template>
  </AppDialog>
</template>
