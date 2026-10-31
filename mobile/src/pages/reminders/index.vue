<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppPageHeader from '../../components/AppPageHeader.vue'
import { errorMessage, platformApi, requireRole, showFailure, localDateTime } from '../../api/platform.js'
import { formatDateTime } from '../../utils/datetime.js'

const loading = ref(true)
const failure = ref('')
const reminders = ref([])
const elders = ref([])
const busy = ref(false)
const formOpen = ref(false)
const editing = ref(null)
const resuming = ref(null)
const elderIndex = ref(0)
const title = ref('')
const recurrenceIndex = ref(1)
const date = ref('')
const time = ref('09:00')
const recurrenceValues = ['ONCE','DAILY','WEEKLY']
const recurrenceLabels = ['仅一次','每天','每周']
// STOPPED 是用户主动停止、可恢复；FINISHED 是一次性提醒已投递，属于终态，不能用同一个“已停止”显示。
const statusLabels = { ACTIVE:'进行中', STOPPED:'已停止', FINISHED:'已完成' }
function dateString(offset) {
  const d = new Date(); d.setDate(d.getDate() + offset)
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
}
function defaultDate() { return dateString(1) }
// 服务端只接受未来时间，日期选择器直接挡住已经过去的日子。
const today = dateString(0)
async function load() {
  loading.value = true
  failure.value = ''
  try {
    if (!await requireRole(['USER'])) { loading.value = false; return }
    ;[reminders.value, elders.value] = await Promise.all([platformApi.reminders(), platformApi.elders()])
  }
  catch (error) { failure.value = errorMessage(error, '提醒加载失败') }
  finally { loading.value = false }
}
function create() { editing.value=null; resuming.value=null; title.value=''; recurrenceIndex.value=1; date.value=defaultDate(); time.value='09:00'; formOpen.value=true }
function edit(item) {
  editing.value=item; resuming.value=null; title.value=item.title; recurrenceIndex.value=Math.max(0,recurrenceValues.indexOf(item.recurrence))
  const next=item.next_fire_at || ''; date.value=next.slice(0,10) || defaultDate(); time.value=next.slice(11,16) || '09:00'; formOpen.value=true
}
// 重新开启只选新的提醒时间：标题与周期沿用原提醒，服务端也只接受这两个字段中的一个时间。
function resume(item) {
  resuming.value=item; editing.value=null; title.value=item.title; date.value=defaultDate(); time.value='09:00'; formOpen.value=true
}
function cancelForm() { formOpen.value=false; editing.value=null; resuming.value=null }
async function save() {
  if (!title.value.trim() || !date.value || !time.value) return showFailure(new Error('请完整填写提醒内容和时间'))
  if (!editing.value && !resuming.value && !elders.value[elderIndex.value]) return showFailure(new Error('请先建立老人档案'))
  if (new Date(`${date.value}T${time.value}:00`).getTime() <= Date.now()) return showFailure(new Error('提醒时间需晚于当前时间'))
  busy.value=true
  const recurrence=recurrenceValues[recurrenceIndex.value]
  try {
    if (resuming.value) await platformApi.resumeReminder(resuming.value.id, localDateTime(date.value,time.value))
    else if (editing.value) await platformApi.updateReminder(editing.value.id, { title:title.value.trim(), recurrence, nextFireAt:localDateTime(date.value,time.value) })
    else await platformApi.createReminder({ elderId:elders.value[elderIndex.value].id, title:title.value.trim(), recurrence, firstAt:localDateTime(date.value,time.value) })
    const done = resuming.value ? '提醒已重新开启' : editing.value ? '提醒已更新' : '提醒已创建'
    formOpen.value=false; editing.value=null; resuming.value=null
    await load(); uni.showToast({ title:done, icon:'success' })
  } catch (error) { showFailure(error) }
  finally { busy.value=false }
}
function stop(item) {
  uni.showModal({ title:'停止提醒', content:`确定停止“${item.title}”吗？`, success:async (result)=>{
    if (!result.confirm) return
    busy.value=true
    try { await platformApi.stopReminder(item.id); await load(); uni.showToast({ title:'提醒已停止', icon:'success' }) }
    catch (error) { showFailure(error) }
    finally { busy.value=false }
  } })
}
onShow(load)
</script>

<template>
  <view class="page">
    <AppPageHeader title="生活提醒" />
    <view class="content">
      <view class="intro"><text>记住重要的生活安排</text><button class="primary add" @click="create">新增提醒</button></view>
      <view v-if="loading" class="state">正在读取提醒…</view>
      <view v-else-if="failure" class="state error"><text>{{ failure }}</text><button class="secondary" :disabled="busy" @click="load">重新加载</button></view>
      <view v-else-if="!reminders.length" class="state">目前没有提醒，创建后会按时间进入平台消息。</view>
      <view v-else class="list">
        <view v-for="item in reminders" :key="item.id" class="card">
          <view class="row"><text class="title">{{ item.title }}</text><text class="status" :class="(item.status || '').toLowerCase()">{{ statusLabels[item.status] || item.status }}</text></view>
          <text class="muted">{{ item.elder_name }} · {{ recurrenceLabels[recurrenceValues.indexOf(item.recurrence)] || item.recurrence }}</text>
          <text class="muted">{{ item.status === 'ACTIVE' ? '下次提醒：' + formatDateTime(item.next_fire_at) : item.status === 'STOPPED' ? '已停止提醒，可随时重新开启' : '一次性提醒已完成，如需再次提醒请新增' }}</text>
          <view v-if="item.status !== 'FINISHED'" class="actions">
            <template v-if="item.status === 'ACTIVE'">
              <button class="secondary" :disabled="busy" @click="edit(item)">修改</button>
              <button class="danger" :disabled="busy" @click="stop(item)">停止</button>
            </template>
            <button v-else-if="item.status === 'STOPPED'" class="primary" :disabled="busy" @click="resume(item)">重新开启</button>
          </view>
        </view>
      </view>
      <view v-if="formOpen" class="form-card">
        <text class="title">{{ resuming ? '重新开启提醒' : editing ? '修改提醒' : '新增提醒' }}</text>
        <picker v-if="!editing && !resuming && elders.length" :range="elders" range-key="fullName" :value="elderIndex" @change="elderIndex=Number($event.detail.value)"><view class="picker">{{ elders[elderIndex]?.fullName || '选择老人' }}　⌄</view></picker>
        <text v-if="resuming" class="muted">{{ resuming.title }} · 只能调整下次提醒时间</text>
        <input v-else v-model="title" maxlength="100" class="input" placeholder="例如：复诊时间提醒" />
        <picker v-if="!resuming" :range="recurrenceLabels" :value="recurrenceIndex" @change="recurrenceIndex=Number($event.detail.value)"><view class="picker">{{ recurrenceLabels[recurrenceIndex] }}　⌄</view></picker>
        <picker mode="date" :start="today" :value="date" @change="date=$event.detail.value"><view class="picker">{{ date || '选择日期' }}</view></picker>
        <picker mode="time" :value="time" @change="time=$event.detail.value"><view class="picker">{{ time || '选择时间' }}</view></picker>
        <view class="actions"><button class="secondary" @click="cancelForm">取消</button><button class="primary" :disabled="busy" @click="save">{{ resuming ? '重新开启' : '保存' }}</button></view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.page { min-height:100vh; background:#f5f8fb; color:#19364d; font-size:16px; line-height:1.5; }
.content { padding:18px; padding-bottom:30px; }
.intro,.row { display:flex; justify-content:space-between; align-items:center; gap:10px; }
.intro { margin-bottom:14px; font-size:19px; font-weight:700; }
.list { display:flex; flex-direction:column; gap:10px; }
.card,.state,.form-card { padding:16px; border-radius:13px; background:#fff; }
.form-card { display:flex; flex-direction:column; gap:10px; margin-top:14px; }
.title { font-size:17px; font-weight:700; }
.status { color:#125fa8; font-size:14px; }
.status.stopped { color:#8a5a20; }
.status.finished { color:#687b89; }
.muted { display:block; margin-top:6px; color:#687b89; font-size:14px; }
.actions { display:flex; gap:8px; margin-top:8px; }
button { width:auto; margin:0; min-height:44px; padding:0 13px; border-radius:8px; font-size:15px; }
button::after { border:0; }
.primary { background:#125fa8; color:#fff; }
.secondary { background:#eaf3fc; color:#104977; }
.danger { background:#f9eae7; color:#a34135; }
.error { color:#a34135; }
.add { width:auto; margin:0; font-size:14px; }
.input,.picker { min-height:46px; padding:10px; box-sizing:border-box; border:1px solid #cbd9e4; border-radius:8px; background:#fff; }
</style>
