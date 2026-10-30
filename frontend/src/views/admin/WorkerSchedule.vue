<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { api, ApiError } from '@/api/client'
  import AppButton from '@/components/ui/AppButton.vue'

  const props = defineProps<{ workerId: number; workerName: string }>()
  const emit = defineEmits<{ close: [] }>()
  interface Schedule {
    id: number
    weekday: number
    start_time: string
    end_time: string
  }
  const schedules = ref<Schedule[]>([])
  const loading = ref(false)
  const submitting = ref(false)
  const error = ref('')
  const loadError = ref('')
  const notice = ref('')
  const editingId = ref<number | null>(null)
  const weekday = ref(1)
  const startTime = ref('08:00')
  const endTime = ref('17:00')
  const weekdays = ['一', '二', '三', '四', '五', '六', '日']
  const path = `/admin/workers/${props.workerId}/schedules`

  async function load() {
    loading.value = true
    loadError.value = ''
    try {
      schedules.value = await api.get<Schedule[]>(path)
    } catch (reason) {
      loadError.value = reason instanceof ApiError ? reason.message : '排班加载失败'
    } finally {
      loading.value = false
    }
  }

  async function submit() {
    if (loading.value || loadError.value || submitting.value) return
    error.value = ''
    notice.value = ''
    if (!startTime.value || !endTime.value || startTime.value >= endTime.value) {
      error.value = '结束时间必须晚于开始时间'
      return
    }
    submitting.value = true
    try {
      const payload = {
        weekday: weekday.value,
        startTime: startTime.value,
        endTime: endTime.value,
      }
      if (editingId.value === null) {
        await api.post<void>(path, payload)
        notice.value = '排班已新增'
      } else {
        await api.put<void>(path + '/' + editingId.value, payload)
        notice.value = '排班已更新'
        editingId.value = null
      }
      await load()
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '排班保存失败'
    } finally {
      submitting.value = false
    }
  }

  function edit(item: Schedule) {
    editingId.value = item.id
    weekday.value = item.weekday
    startTime.value = item.start_time.slice(0, 5)
    endTime.value = item.end_time.slice(0, 5)
    error.value = ''
    notice.value = ''
  }

  function cancelEdit() {
    editingId.value = null
    weekday.value = 1
    startTime.value = '08:00'
    endTime.value = '17:00'
    error.value = ''
  }

  async function remove(item: Schedule) {
    const label =
      '确定删除周' +
      weekdays[item.weekday - 1] +
      ' ' +
      item.start_time.slice(0, 5) +
      '—' +
      item.end_time.slice(0, 5) +
      ' 的排班吗？'
    if (submitting.value || !window.confirm(label)) return
    submitting.value = true
    error.value = ''
    notice.value = ''
    try {
      await api.delete<void>(path + '/' + item.id)
      if (editingId.value === item.id) cancelEdit()
      notice.value = '排班已删除'
      await load()
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '排班删除失败'
    } finally {
      submitting.value = false
    }
  }

  onMounted(load)
</script>

<template>
  <section class="card card--pad" style="margin: var(--sp-5)" aria-labelledby="schedule-title">
    <div class="section__head">
      <div>
        <h2 id="schedule-title" class="section-title">{{ workerName }} 的排班</h2>
        <p class="section__desc">按星期维护可服务时段；派单还需检查服务资格、请假与占用。</p>
      </div>
      <AppButton variant="ghost" compact @click="emit('close')">关闭</AppButton>
    </div>
    <p v-if="loadError" role="alert">
      {{ loadError }} <AppButton variant="ghost" compact @click="load">重新加载</AppButton>
    </p>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="loading">正在加载排班…</p>
    <ul v-else-if="!loadError && schedules.length">
      <li v-for="item in schedules" :key="item.id" class="schedule-row">
        <span
          >周{{ weekdays[item.weekday - 1] }} {{ item.start_time.slice(0, 5) }}—{{
            item.end_time.slice(0, 5)
          }}</span
        >
        <span class="schedule-actions">
          <AppButton
            variant="ghost"
            compact
            :disabled="submitting"
            :aria-label="'修改周' + weekdays[item.weekday - 1] + '排班'"
            @click="edit(item)"
            >修改</AppButton
          >
          <AppButton
            variant="danger"
            compact
            :disabled="submitting"
            :aria-label="'删除周' + weekdays[item.weekday - 1] + '排班'"
            @click="remove(item)"
            >删除</AppButton
          >
        </span>
      </li>
    </ul>
    <p v-else-if="!loadError">暂无排班。</p>
    <form @submit.prevent="submit">
      <div class="grid grid--3" style="gap: var(--sp-4); margin-top: var(--sp-4)">
        <label
          >星期
          <select id="schedule-day" v-model.number="weekday" class="input">
            <option v-for="(label, index) in weekdays" :key="label" :value="index + 1">周{{ label }}</option>
          </select></label
        >
        <label
          >开始时间 <input id="schedule-start" v-model="startTime" class="input" type="time" required
        /></label>
        <label
          >结束时间 <input id="schedule-end" v-model="endTime" class="input" type="time" required
        /></label>
      </div>
      <AppButton
        type="submit"
        :loading="submitting"
        :disabled="loading || Boolean(loadError)"
        style="margin-top: var(--sp-4)"
        >{{ editingId === null ? '新增时段' : '保存修改' }}</AppButton
      >
      <AppButton
        v-if="editingId !== null"
        variant="ghost"
        :disabled="submitting"
        style="margin: var(--sp-4) 0 0 var(--sp-2)"
        @click="cancelEdit"
        >取消编辑</AppButton
      >
    </form>
  </section>
</template>

<style scoped>
  .schedule-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-3);
    padding: var(--sp-3) 0;
    border-bottom: 1px solid var(--c-line);
  }
  .schedule-actions {
    display: flex;
    flex: 0 0 auto;
    gap: var(--sp-2);
  }
  @media (max-width: 480px) {
    .schedule-row {
      align-items: flex-start;
      flex-direction: column;
    }
  }
</style>
