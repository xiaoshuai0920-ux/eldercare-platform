<script setup>
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import AppPageHeader from '../../components/AppPageHeader.vue'
import AppBottomNav from '../../components/AppBottomNav.vue'
import { errorMessage, platformApi, requireRole, showFailure } from '../../api/platform.js'

const loading = ref(true)
const failure = ref('')
const orders = ref([])
const selected = ref(null)
const review = ref(null)
const settlement = ref(null)
const reviewStars = ref(5)
const reviewContent = ref('')
const busy = ref(false)
const labels = {
  WAITING_ASSIGNMENT:'待派单', WAITING_ACCEPTANCE:'待接单', WAITING_SERVICE:'待服务',
  IN_SERVICE:'服务中', WAITING_CONFIRMATION:'待确认', COMPLETED:'已完成',
  CANCELLED:'已取消', CLOSED_EXCEPTION:'异常结案',
}
async function load() {
  loading.value = true; failure.value = ''
  try {
    if (!await requireRole(['USER'])) { loading.value = false; return }
    const page = await platformApi.orders()
    orders.value = page.items || []
  }
  catch (error) { failure.value = errorMessage(error, '订单读取失败') }
  finally { loading.value = false }
}
async function openDetail(order) {
  busy.value = true
  try {
    selected.value = await platformApi.order(order.id)
    review.value = null; settlement.value = null
    if (order.status === 'COMPLETED') {
      ;[review.value, settlement.value] = await Promise.all([platformApi.review(order.id), platformApi.settlement(order.id)])
    }
  } catch (error) { showFailure(error) }
  finally { busy.value = false }
}
async function act(order, action) {
  busy.value = true
  try {
    if (action === 'confirm') await platformApi.confirmOrder(order.id)
    else await platformApi.cancelOrder(order.id)
    await load()
    if (selected.value?.id === order.id) await openDetail(order)
  } catch (error) { showFailure(error) }
  finally { busy.value = false }
}
async function submitReview() {
  if (!selected.value || !reviewContent.value.trim()) return showFailure(new Error('请填写服务评价'))
  busy.value = true
  try {
    await platformApi.submitReview(selected.value.id, { stars: reviewStars.value, content: reviewContent.value.trim() })
    reviewContent.value = ''
    review.value = await platformApi.review(selected.value.id)
    uni.showToast({ title: '评价已提交', icon: 'success' })
  } catch (error) { showFailure(error) }
  finally { busy.value = false }
}
async function createPayment() {
  if (!selected.value) return
  busy.value = true
  try {
    const attempt = await platformApi.createPaymentAttempt(selected.value.id)
    uni.showModal({ title: '模拟支付已发起', content: `流水号：${attempt.attemptRef}\n请由本机模拟支付服务处理后，再刷新结算状态。`, showCancel: false })
    settlement.value = await platformApi.settlement(selected.value.id)
  } catch (error) { showFailure(error) }
  finally { busy.value = false }
}
async function syncSettlement() {
  if (!selected.value) return
  busy.value = true
  try { settlement.value = await platformApi.syncSettlement(selected.value.id) }
  catch (error) { showFailure(error) }
  finally { busy.value = false }
}
onShow(load)
</script>

<template>
  <view class="page">
    <AppPageHeader title="我的订单" />
    <view class="content">
      <view v-if="loading" class="state">正在读取平台订单…</view>
      <view v-else-if="failure" class="state error"><text>{{ failure }}</text><button class="secondary" @click="load">重新加载</button></view>
      <view v-else-if="!orders.length" class="state">还没有订单，预约后可在这里查看进度。</view>
      <view v-else class="list">
        <view v-for="order in orders" :key="order.id" class="card">
          <view class="row"><text class="title">{{ order.item_name_snapshot }}</text><text class="status">{{ labels[order.status] || order.status }}</text></view>
          <text class="muted">{{ order.start_at }}</text>
          <view class="row bottom"><text>¥{{ Number(order.payable_amount).toFixed(2) }}</text><button class="link-button" @click="openDetail(order)">查看详情</button></view>
          <view class="actions">
            <button v-if="order.status === 'WAITING_CONFIRMATION'" class="primary" :disabled="busy" @click="act(order, 'confirm')">确认服务完成</button>
            <button v-if="['WAITING_ASSIGNMENT','WAITING_ACCEPTANCE','WAITING_SERVICE'].includes(order.status)" class="secondary" :disabled="busy" @click="uni.showModal({ title:'取消预约', content:'确定取消这笔预约吗？', success: (res) => res.confirm && act(order, 'cancel') })">取消预约</button>
          </view>
        </view>
      </view>
      <view v-if="selected" class="detail-card">
        <view class="detail-head"><text class="title">订单详情</text><text class="link-button" @click="selected = null">收起</text></view>
        <view class="detail"><text>服务对象</text><text>{{ selected.elder_name || selected.elder_id }}</text></view>
        <view class="detail"><text>项目</text><text>{{ selected.item_name_snapshot }}</text></view>
        <view class="detail"><text>时间</text><text>{{ selected.start_at }} – {{ selected.end_at }}</text></view>
        <view class="detail"><text>地址</text><text class="address">{{ selected.address_snapshot }}</text></view>
        <view v-if="selected.status === 'COMPLETED'" class="extra">
          <text class="subtitle">服务评价</text>
          <view v-if="review" class="muted">已评价 · {{ review.stars }} 星 · {{ review.content }}</view>
          <view v-else class="review-form"><picker :range="[1,2,3,4,5]" @change="reviewStars = Number($event.detail.value) + 1"><view class="picker">{{ reviewStars }} 星　⌄</view></picker><textarea v-model="reviewContent" class="textarea" maxlength="500" placeholder="请简要评价本次服务" /><button class="primary" :disabled="busy" @click="submitReview">提交评价</button></view>
          <text class="subtitle">结算状态</text>
          <view v-if="settlement" class="muted">{{ settlement.settlement.status }} · ¥{{ Number(settlement.settlement.amount).toFixed(2) }}</view>
          <view class="actions"><button v-if="settlement?.settlement.status === 'WAITING_METHOD' || settlement?.settlement.status === 'FAILED'" class="primary" :disabled="busy" @click="createPayment">发起模拟支付</button><button v-if="settlement?.settlement.status === 'PROCESSING'" class="secondary" :disabled="busy" @click="syncSettlement">刷新结算状态</button></view>
          <text class="hint">模拟支付结果由服务端核对；网络查询失败不能作为支付失败依据。此处不处理真实资金。</text>
        </view>
      </view>
    </view>
    <AppBottomNav active="orders" />
  </view>
</template>

<style scoped>
.page { min-height:100vh; padding-bottom:calc(72px + env(safe-area-inset-bottom)); box-sizing:border-box; background:#f5f8fb; color:#19364d; font-size:16px; line-height:1.55; }
.content { padding:16px 18px 28px; }
.list { display:flex; flex-direction:column; gap:10px; }
.card,.detail-card,.state { padding:16px; border-radius:13px; background:#fff; }
.row,.detail-head { display:flex; justify-content:space-between; align-items:center; gap:12px; }
.title { font-size:18px; font-weight:700; }
.status { flex-shrink:0; color:#125fa8; font-size:14px; }
.muted,.hint { display:block; margin-top:7px; color:#687b89; font-size:14px; }
.bottom { margin-top:9px; }
.actions { display:flex; flex-wrap:wrap; gap:8px; margin-top:10px; }
button { min-height:44px; padding:0 13px; border-radius:9px; font-size:15px; }
button::after { border:0; }
.primary { background:#125fa8; color:#fff; }
.secondary { background:#eaf3fc; color:#104977; }
.link-button { padding:0; background:transparent; color:#125fa8; }
.detail-card { margin-top:14px; }
.detail { display:flex; justify-content:space-between; gap:12px; padding:11px 0; border-bottom:1px solid #e4ebf0; font-size:14px; }
.detail > text:first-child { flex-shrink:0; color:#687b89; }
.address { max-width:70%; text-align:right; overflow-wrap:anywhere; }
.extra { display:flex; flex-direction:column; gap:8px; padding-top:16px; }
.subtitle { margin-top:8px; font-weight:700; }
.review-form { display:flex; flex-direction:column; gap:9px; }
.picker,.textarea { width:100%; min-height:46px; padding:10px; box-sizing:border-box; border:1px solid #cbd9e4; border-radius:8px; background:#fff; }
.textarea { min-height:92px; }
.error { color:#a34135; }
</style>
