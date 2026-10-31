<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppPageHeader from '../../components/AppPageHeader.vue'
import { errorMessage, navigate, platformApi, requireRole, showFailure, localDateTime } from '../../api/platform.js'
import { formatDateTime } from '../../utils/datetime.js'
import { unread, refreshUnread } from '../../store/unread.js'

const loading=ref(true)
const failure=ref('')
const dashboard=ref(null)
const tasks=ref([])
const busy=ref(false)
const leaveOpen=ref(false)
const leaveStartDate=ref('')
const leaveStartTime=ref('09:00')
const leaveEndDate=ref('')
const leaveEndTime=ref('18:00')
const leaveReason=ref('')
const labels={WAITING_ACCEPTANCE:'待确认接单',WAITING_SERVICE:'待服务',IN_SERVICE:'服务中',WAITING_CONFIRMATION:'待用户确认',COMPLETED:'已完成',CANCELLED:'已取消',CLOSED_EXCEPTION:'异常结案'}
// 服务端要求请假时段在未来，日期选择器先挡住已经过去的日子
const today=(()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`})()
// 每笔任务的原因分开保存：共用同一个字段时，给 A 填写的原因会串到 B 的输入框并被提交。
const reasons=ref({})
function reasonOf(task) { return reasons.value[task.order_id] || '' }
function setReason(orderId, event) { reasons.value[orderId] = event?.detail?.value ?? '' }
const detail=ref(null)
const detailBusy=ref(false)
async function openTask(task) {
  detailBusy.value=true
  try { detail.value=await platformApi.workerTask(task.order_id) }
  catch(error) { showFailure(error) }
  finally { detailBusy.value=false }
}
async function load() {
  loading.value=true
  failure.value=''
  detail.value=null
  try {
    if (!await requireRole(['WORKER'])) { loading.value=false; return }
    ;[dashboard.value,tasks.value]=await Promise.all([platformApi.workerDashboard(),platformApi.workerTasks()])
  }
  catch(error) { failure.value=errorMessage(error,'任务加载失败') }
  finally { loading.value=false }
}
async function toggleAccepting() {
  busy.value=true
  try { await platformApi.setAccepting(!dashboard.value.accepting); await load() }
  catch(error) { showFailure(error) }
  finally { busy.value=false }
}
async function act(task, action) {
  const reason=reasonOf(task).trim()
  if (['reject','interrupt'].includes(action) && !reason) return showFailure(new Error('请填写原因'))
  busy.value=true
  try {
    if (action === 'reject') await platformApi.workerAction(task.order_id,'reject',{ reason })
    else if (action === 'interrupt') await platformApi.interruptOrder(task.order_id,reason)
    else await platformApi.workerAction(task.order_id,action)
    delete reasons.value[task.order_id]
    await load(); uni.showToast({ title:'任务已更新', icon:'success' })
  } catch(error) { showFailure(error) }
  finally { busy.value=false }
}
// 提交完成记录后订单转待用户确认，服务人员无法撤回，先确认一次。
function complete(task) {
  uni.showModal({
    title: '提交完成记录',
    content: '提交后订单进入待用户确认，不能再修改。',
    success: (result) => { if (result.confirm) act(task, 'submit-completion') },
  })
}
async function submitLeave() {
  if (!leaveStartDate.value || !leaveEndDate.value || !leaveReason.value.trim()) return showFailure(new Error('请填写请假起止时间和原因'))
  // 结束时间不晚于开始时间会被服务端拒绝，先在本地拦住并说明原因。
  if (new Date(`${leaveEndDate.value}T${leaveEndTime.value}:00`).getTime() <= new Date(`${leaveStartDate.value}T${leaveStartTime.value}:00`).getTime()) {
    return showFailure(new Error('结束时间必须晚于开始时间'))
  }
  busy.value=true
  try {
    await platformApi.requestLeave({ startAt:localDateTime(leaveStartDate.value,leaveStartTime.value), endAt:localDateTime(leaveEndDate.value,leaveEndTime.value), reason:leaveReason.value.trim() })
    leaveOpen.value=false; leaveReason.value=''; uni.showToast({ title:'请假申请已提交', icon:'success' })
  } catch(error) { showFailure(error) }
  finally { busy.value=false }
}
async function logout() {
  try { await platformApi.logout() } catch(error) { showFailure(error) }
  finally { platformApi.resetSession(); navigate('/pages/login/index') }
}
onShow(() => { load(); refreshUnread() })
</script>

<template>
  <view class="page">
    <AppPageHeader title="服务工作台" />
    <view class="content">
      <view v-if="loading" class="state">正在读取本人任务…</view>
      <view v-else-if="failure" class="state error"><text>{{ failure }}</text><button class="secondary" :disabled="busy" @click="load">重新加载</button></view>
      <template v-else>
        <view v-if="dashboard" class="panel">
          <view class="row"><view><text class="title">服务安排</text><text class="muted">仅显示当前账号已分派任务</text></view><button class="toggle" :disabled="busy" @click="toggleAccepting">{{ dashboard.accepting?'暂停接单':'恢复接单' }}</button></view>
          <view class="metrics"><text>待处理 {{ dashboard.pending }}</text><text>服务中 {{ dashboard.active }}</text></view>
        </view>
        <view v-if="!tasks.length" class="state">当前没有分派给您的服务任务。</view>
        <view v-for="task in tasks" :key="task.order_id" class="task">
          <view class="row"><text class="title">{{ task.item_name_snapshot }}</text><text class="status">{{ labels[task.status] || task.status }}</text></view>
          <text class="muted">{{ formatDateTime(task.start_at) }} – {{ formatDateTime(task.end_at) }}</text>
          <text class="muted">{{ task.street_name_snapshot || '服务地址以订单详情为准' }}</text>
          <button class="text-button" :disabled="detailBusy" @click="openTask(task)">查看服务信息</button>
          <view v-if="['WAITING_ACCEPTANCE','IN_SERVICE'].includes(task.status)" class="field">
            <textarea v-if="task.status === 'WAITING_ACCEPTANCE'" :value="reasonOf(task)" class="input reason" maxlength="300" placeholder="拒绝接单时填写原因（可留空以接受）" @input="setReason(task.order_id, $event)" />
            <textarea v-if="task.status === 'IN_SERVICE'" :value="reasonOf(task)" class="input reason" maxlength="300" placeholder="中断服务时填写原因" @input="setReason(task.order_id, $event)" />
          </view>
          <view class="actions">
            <button v-if="task.status === 'WAITING_ACCEPTANCE'" class="secondary" :disabled="busy" @click="act(task,'reject')">拒绝接单</button>
            <button v-if="task.status === 'WAITING_ACCEPTANCE'" class="primary" :disabled="busy" @click="act(task,'accept')">确认接单</button>
            <button v-if="task.status === 'WAITING_SERVICE'" class="primary" :disabled="busy" @click="act(task,'start')">开始服务</button>
            <button v-if="task.status === 'IN_SERVICE'" class="secondary" :disabled="busy" @click="act(task,'interrupt')">报告中断</button>
            <button v-if="task.status === 'IN_SERVICE'" class="primary" :disabled="busy" @click="complete(task)">提交完成记录</button>
          </view>
        </view>
        <view v-if="detail" class="panel detail-panel">
          <view class="detail-head"><text class="title">服务信息</text><text class="text-button" @click="detail=null">收起</text></view>
          <view class="detail-row"><text>服务对象</text><text>{{ detail.elder_name || '—' }}</text></view>
          <view class="detail-row"><text>服务项目</text><text>{{ detail.item_name_snapshot }}</text></view>
          <view class="detail-row"><text>服务时间</text><text>{{ formatDateTime(detail.start_at) }} – {{ formatDateTime(detail.end_at) }}</text></view>
          <view class="detail-row"><text>服务街道</text><text>{{ detail.street_name_snapshot || '—' }}</text></view>
          <template v-if="detail.address_snapshot">
            <view class="detail-row"><text>详细地址</text><text class="wrap">{{ detail.address_snapshot }}</text></view>
            <view class="detail-row"><text>联系电话</text><text>{{ detail.contact_phone || '未填写' }}</text></view>
          </template>
          <text v-else class="muted">接单后可见完整地址与联系电话。</text>
        </view>
        <view v-if="leaveOpen" class="panel form">
          <text class="title">请假申请</text>
          <text class="field-label">开始日期 / 时间</text><picker mode="date" :start="today" @change="leaveStartDate=$event.detail.value"><view class="picker">{{ leaveStartDate || '选择开始日期' }}</view></picker><picker mode="time" :value="leaveStartTime" @change="leaveStartTime=$event.detail.value"><view class="picker">{{ leaveStartTime }}</view></picker>
          <text class="field-label">结束日期 / 时间</text><picker mode="date" :start="today" @change="leaveEndDate=$event.detail.value"><view class="picker">{{ leaveEndDate || '选择结束日期' }}</view></picker><picker mode="time" :value="leaveEndTime" @change="leaveEndTime=$event.detail.value"><view class="picker">{{ leaveEndTime }}</view></picker>
          <textarea v-model="leaveReason" class="input reason" maxlength="300" placeholder="请填写请假原因" />
          <button class="primary" :disabled="busy" @click="submitLeave">提交申请</button>
        </view>
        <view class="footer-actions"><button class="secondary" @click="leaveOpen=!leaveOpen">申请请假</button><button class="secondary" @click="uni.navigateTo({url:'/pages/messages/index'})">消息通知<text v-if="unread" class="cnt">{{ unread > 99 ? '99+' : unread }}</text></button><button class="logout" @click="logout">退出登录</button></view>
      </template>
    </view>
  </view>
</template>

<style scoped>
.page { min-height:100vh; display:flex; flex-direction:column; background:#f5f8fb; color:#19364d; font-size:16px; line-height:1.5; }
.content { flex:1; display:flex; flex-direction:column; gap:12px; padding:16px 18px 30px; padding-bottom:calc(16px + env(safe-area-inset-bottom)); }
.panel,.task,.state { padding:16px; border-radius:13px; background:#fff; }
.row { display:flex; align-items:center; justify-content:space-between; gap:10px; }
.title { display:block; font-size:18px; font-weight:700; }
.muted { display:block; margin-top:6px; color:#687b89; font-size:14px; }
.status { color:#125fa8; font-size:14px; }
.metrics { display:flex; gap:24px; margin-top:14px; color:#586c7c; font-size:14px; }
.toggle { width:auto; margin:0; min-height:42px; padding:0 12px; border-radius:8px; background:#eaf3fc; color:#104977; font-size:14px; }
.field { margin-top:9px; }
.input,.picker { width:100%; min-height:45px; padding:10px; box-sizing:border-box; border:1px solid #cbd9e4; border-radius:8px; background:#fff; }
.reason { min-height:68px; }
.actions,.footer-actions { display:flex; flex-wrap:wrap; gap:8px; }
.actions { margin-top:10px; }
.footer-actions { margin-top:auto; padding-top:10px; }
button { min-height:44px; padding:0 12px; border-radius:8px; font-size:14px; }
.actions button,.footer-actions button { width:auto; margin:0; }
.footer-actions .logout { margin-left:auto; }
button::after { border:0; }
.primary { background:#125fa8; color:#fff; }
.secondary { background:#eaf3fc; color:#104977; }
.logout { color:#687b89; background:transparent; }
.cnt { margin-left:6px; padding:1px 7px; border-radius:9px; background:#c2402e; color:#fff; font-size:12px; }
.error { color:#a34135; }
.form { display:flex; flex-direction:column; gap:8px; }
.field-label { margin-top:8px; font-size:14px; font-weight:600; }
.text-button { width:auto; margin:6px 0 0; padding:0; min-height:0; background:transparent; color:#125fa8; font-size:14px; }
.detail-head { display:flex; align-items:center; justify-content:space-between; gap:10px; margin-bottom:6px; }
.detail-panel { display:flex; flex-direction:column; gap:6px; }
.detail-row { display:flex; justify-content:space-between; gap:12px; padding:8px 0; border-bottom:1px solid #e4ebf0; font-size:14px; }
.detail-row > text:first-child { flex-shrink:0; color:#687b89; }
.detail-row > text:last-child { text-align:right; }
.wrap { overflow-wrap:anywhere; }

</style>
