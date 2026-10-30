<script setup>
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import AppPageHeader from '../../components/AppPageHeader.vue'
import AppBottomNav from '../../components/AppBottomNav.vue'
import { errorMessage, navigate, platformApi, requireRole, showFailure } from '../../api/platform.js'

const user = ref(null)
const dashboard = ref(null)
const loading = ref(true)
const failure = ref('')
const statusLabels = {
  WAITING_ASSIGNMENT: '待派单', WAITING_ACCEPTANCE: '待接单', WAITING_SERVICE: '待服务',
  IN_SERVICE: '服务中', WAITING_CONFIRMATION: '待确认', COMPLETED: '已完成',
  CANCELLED: '已取消', CLOSED_EXCEPTION: '异常结案',
}

async function load() {
  loading.value = true
  failure.value = ''
  let current
  try { current = await requireRole(['USER']) }
  catch (error) { failure.value = errorMessage(error, '首页数据加载失败'); loading.value = false; return }
  if (!current) { loading.value = false; return }
  user.value = current
  try { dashboard.value = await platformApi.userDashboard() }
  catch (error) { failure.value = errorMessage(error, '首页数据加载失败') }
  finally { loading.value = false }
}
function open(path) { uni.navigateTo({ url: path }) }
async function logout() {
  try { await platformApi.logout() }
  catch (error) { showFailure(error) }
  finally { platformApi.resetSession(); navigate('/pages/login/index') }
}
onShow(load)
</script>

<template>
  <view class="page">
    <AppPageHeader title="首页" />
    <view class="content">
      <view class="welcome">
        <text class="eyebrow">居家服务安排</text>
        <text class="greeting">{{ user?.name || '您好' }}，欢迎回来</text>
        <text class="muted">服务信息来自平台账户与业务数据。</text>
      </view>
      <view v-if="loading" class="state">正在读取账户和服务信息…</view>
      <view v-else-if="failure" class="state error"><text>{{ failure }}</text><button class="secondary" @click="load">重新加载</button></view>
      <template v-else-if="dashboard">
        <view class="stats">
          <view class="stat"><text class="number">{{ dashboard.waiting }}</text><text>待安排</text></view>
          <view class="stat"><text class="number">{{ dashboard.active }}</text><text>服务中</text></view>
          <view class="stat"><text class="number">{{ dashboard.completed }}</text><text>已完成</text></view>
        </view>
        <view class="section-head"><text>最近订单</text><text class="link" @click="open('/pages/orders/index')">全部订单 ›</text></view>
        <view v-if="dashboard.recentOrders?.length" class="card-list">
          <view v-for="order in dashboard.recentOrders" :key="order.id" class="card">
            <view class="row"><text class="card-title">{{ order.item_name_snapshot }}</text><text class="status">{{ statusLabels[order.status] || order.status }}</text></view>
            <text class="muted">{{ order.start_at }} · ¥{{ Number(order.payable_amount).toFixed(2) }}</text>
          </view>
        </view>
        <view v-else class="empty">还没有订单。预约服务后，订单会显示在这里。</view>
        <view class="section-head"><text>常用功能</text></view>
        <view class="actions">
          <button class="primary" @click="open('/pages/booking/index')">预约服务</button>
          <button class="secondary" @click="open('/pages/family/index')">家庭授权</button>
          <button class="secondary" @click="open('/pages/reminders/index')">生活提醒</button>
          <button class="secondary" @click="open('/pages/messages/index')">消息通知</button>
        </view>
        <button class="logout" @click="logout">退出登录</button>
      </template>
    </view>
    <AppBottomNav active="home" />
  </view>
</template>

<style scoped>
.page { min-height:100vh; padding-bottom:calc(72px + env(safe-area-inset-bottom)); box-sizing:border-box; background:#f5f8fb; color:#19364d; font-size:17px; line-height:1.55; }
.content { padding:18px 18px 30px; }
.welcome { display:flex; flex-direction:column; gap:5px; padding:22px 20px; border-radius:16px; background:#eaf3fc; }
.eyebrow,.muted { color:#586c7c; font-size:14px; }
.greeting { font-size:25px; font-weight:700; }
.stats { display:flex; gap:10px; margin-top:16px; }
.stat { display:flex; flex:1; flex-direction:column; align-items:center; gap:3px; padding:13px 5px; border-radius:12px; background:#fff; color:#586c7c; font-size:13px; }
.number { color:#125fa8; font-size:21px; font-weight:700; }
.section-head { display:flex; justify-content:space-between; align-items:center; margin:24px 2px 10px; font-size:18px; font-weight:700; }
.link { color:#125fa8; font-size:14px; font-weight:500; }
.card-list { display:flex; flex-direction:column; gap:9px; }
.card { display:flex; flex-direction:column; gap:8px; padding:15px; border-radius:12px; background:#fff; }
.row { display:flex; justify-content:space-between; gap:12px; }
.card-title { font-weight:700; }
.status { flex-shrink:0; color:#125fa8; font-size:14px; }
.empty,.state { padding:20px; border-radius:12px; background:#fff; color:#586c7c; }
.error { color:#a34135; }
.actions { display:grid; grid-template-columns:1fr 1fr; gap:10px; }
button { min-height:48px; border-radius:9px; font-size:16px; }
button::after { border:0; }
.primary { background:#125fa8; color:#fff; }
.secondary { background:#eaf3fc; color:#104977; }
.logout { margin:20px 0; padding:0 16px; background:transparent; color:#667b8c; }
</style>
