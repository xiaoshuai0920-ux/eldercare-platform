<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppPageHeader from '../../components/AppPageHeader.vue'
import { errorMessage, navigate, platformApi, requireRole, showFailure, localDateTime } from '../../api/platform.js'

const loading=ref(true)
const failure=ref('')
const dashboard=ref(null)
const tasks=ref([])
const busy=ref(false)
const reason=ref('')
const leaveOpen=ref(false)
const leaveStartDate=ref('')
const leaveStartTime=ref('09:00')
const leaveEndDate=ref('')
const leaveEndTime=ref('18:00')
const leaveReason=ref('')
const labels={WAITING_ACCEPTANCE:'待确认接单',WAITING_SERVICE:'待服务',IN_SERVICE:'服务中',WAITING_CONFIRMATION:'待用户确认',COMPLETED:'已完成',CANCELLED:'已取消',CLOSED_EXCEPTION:'异常结案'}
async function load() {
  loading.value=true
  failure.value=''
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
  if (['reject','interrupt'].includes(action) && !reason.value.trim()) return showFailure(new Error('请填写原因'))
  busy.value=true
  try {
    if (action === 'reject') await platformApi.workerAction(task.order_id,'reject',{ reason:reason.value.trim() })
    else if (action === 'interrupt') await platformApi.interruptOrder(task.order_id,reason.value.trim())
    else await platformApi.workerAction(task.order_id,action)
    reason.value=''; await load(); uni.showToast({ title:'任务已更新', icon:'success' })
  } catch(error) { showFailure(error) }
  finally { busy.value=false }
}
async function submitLeave() {
  if (!leaveStartDate.value || !leaveEndDate.value || !leaveReason.value.trim()) return showFailure(new Error('请填写请假起止时间和原因'))
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
onShow(load)
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
          <text class="muted">{{ task.start_at }} – {{ task.end_at }}</text>
          <text class="muted">{{ task.street_name_snapshot || '服务地址以订单详情为准' }}</text>
          <view v-if="['WAITING_ACCEPTANCE','IN_SERVICE'].includes(task.status)" class="field">
            <textarea v-if="task.status === 'WAITING_ACCEPTANCE'" v-model="reason" class="input reason" maxlength="300" placeholder="拒绝接单时填写原因（可留空以接受）" />
            <textarea v-if="task.status === 'IN_SERVICE'" v-model="reason" class="input reason" maxlength="300" placeholder="中断服务时填写原因" />
          </view>
          <view class="actions">
            <button v-if="task.status === 'WAITING_ACCEPTANCE'" class="secondary" :disabled="busy" @click="act(task,'reject')">拒绝接单</button>
            <button v-if="task.status === 'WAITING_ACCEPTANCE'" class="primary" :disabled="busy" @click="act(task,'accept')">确认接单</button>
            <button v-if="task.status === 'WAITING_SERVICE'" class="primary" :disabled="busy" @click="act(task,'start')">开始服务</button>
            <button v-if="task.status === 'IN_SERVICE'" class="secondary" :disabled="busy" @click="act(task,'interrupt')">报告中断</button>
            <button v-if="task.status === 'IN_SERVICE'" class="primary" :disabled="busy" @click="act(task,'submit-completion')">提交完成记录</button>
          </view>
        </view>
        <view class="footer-actions"><button class="secondary" @click="leaveOpen=!leaveOpen">申请请假</button><button class="secondary" @click="uni.navigateTo({url:'/pages/messages/index'})">消息通知</button><button class="logout" @click="logout">退出登录</button></view>
        <view v-if="leaveOpen" class="panel form">
          <text class="title">请假申请</text>
          <text class="field-label">开始日期 / 时间</text><picker mode="date" @change="leaveStartDate=$event.detail.value"><view class="picker">{{ leaveStartDate || '选择开始日期' }}</view></picker><picker mode="time" :value="leaveStartTime" @change="leaveStartTime=$event.detail.value"><view class="picker">{{ leaveStartTime }}</view></picker>
          <text class="field-label">结束日期 / 时间</text><picker mode="date" @change="leaveEndDate=$event.detail.value"><view class="picker">{{ leaveEndDate || '选择结束日期' }}</view></picker><picker mode="time" :value="leaveEndTime" @change="leaveEndTime=$event.detail.value"><view class="picker">{{ leaveEndTime }}</view></picker>
          <textarea v-model="leaveReason" class="input reason" maxlength="300" placeholder="请填写请假原因" />
          <button class="primary" :disabled="busy" @click="submitLeave">提交申请</button>
        </view>
      </template>
    </view>
  </view>
</template>

<style scoped>
.page { min-height:100vh; background:#f5f8fb; color:#19364d; font-size:16px; line-height:1.5; }
.content { display:flex; flex-direction:column; gap:12px; padding:16px 18px 30px; }
.panel,.task,.state { padding:16px; border-radius:13px; background:#fff; }
.row { display:flex; align-items:center; justify-content:space-between; gap:10px; }
.title { display:block; font-size:18px; font-weight:700; }
.muted { display:block; margin-top:6px; color:#687b89; font-size:14px; }
.status { color:#125fa8; font-size:14px; }
.metrics { display:flex; gap:24px; margin-top:14px; color:#586c7c; font-size:14px; }
.toggle { min-height:42px; padding:0 12px; border-radius:8px; background:#eaf3fc; color:#104977; font-size:14px; }
.field { margin-top:9px; }
.input,.picker { width:100%; min-height:45px; padding:10px; box-sizing:border-box; border:1px solid #cbd9e4; border-radius:8px; background:#fff; }
.reason { min-height:68px; }
.actions,.footer-actions { display:flex; flex-wrap:wrap; gap:8px; margin-top:10px; }
button { min-height:44px; padding:0 12px; border-radius:8px; font-size:14px; }
button::after { border:0; }
.primary { background:#125fa8; color:#fff; }
.secondary { background:#eaf3fc; color:#104977; }
.logout { color:#687b89; background:transparent; }
.error { color:#a34135; }
.form { display:flex; flex-direction:column; gap:8px; }
.field-label { margin-top:8px; font-size:14px; font-weight:600; }
</style>
