<script setup>
import { onLoad, onReachBottom, onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import AppPageHeader from '../../components/AppPageHeader.vue'
import AppBottomNav from '../../components/AppBottomNav.vue'
import { errorMessage, platformApi, requireRole, showFailure } from '../../api/platform.js'
import { formatDateTime } from '../../utils/datetime.js'

const loading = ref(true)
const failure = ref('')
const orders = ref([])
const total = ref(0)
const elderNames = ref({})
const selected = ref(null)
const review = ref(null)
const settlement = ref(null)
const settlementFailure = ref('')
const reviewDenied = ref(false)
const reviewStars = ref(5)
const reviewContent = ref('')
const busy = ref(false)
// 分页与筛选：每页 10 条，触底加载更多；切换筛选整体重载。
const pageSize = 10
const pageNo = ref(1)
const group = ref('')
const loadingMore = ref(false)
const moreFailed = ref(false)
const noMore = ref(false)
const filters = [
  { label:'全部', value:'' },
  { label:'待处理', value:'action' },
  { label:'服务中', value:'active' },
  { label:'待服务', value:'upcoming' },
  { label:'已完成', value:'done' },
]
const labels = {
  WAITING_ASSIGNMENT:'待派单', WAITING_ACCEPTANCE:'待接单', WAITING_SERVICE:'待服务',
  IN_SERVICE:'服务中', WAITING_CONFIRMATION:'待确认', COMPLETED:'已完成',
  CANCELLED:'已取消', CLOSED_EXCEPTION:'异常结案',
}
const settlementLabels = { WAITING_METHOD:'待选择结算方式', PROCESSING:'结算处理中', SETTLED:'已结算', FAILED:'结算失败' }
// 从消息通知进入时带上 orderId，列表加载完成后直接展开这笔订单，而不是停在列表顶部。
let pendingOrderId = 0
onLoad((query) => {
  const id = Number(query?.orderId)
  pendingOrderId = Number.isInteger(id) && id > 0 ? id : 0
})
async function load(reset = true) {
  if (reset) { pageNo.value = 1; noMore.value = false }
  loading.value = reset
  failure.value = ''
  try {
    if (!await requireRole(['USER'])) { loading.value = false; return }
    const [page, elderList] = await Promise.all([
      platformApi.orders(pageNo.value, pageSize, group.value || undefined),
      reset ? platformApi.elders().catch(() => []) : Promise.resolve(null),
    ])
    const items = page.items || []
    orders.value = reset ? items : orders.value.concat(items)
    total.value = Number(page.total) || orders.value.length
    noMore.value = orders.value.length >= total.value
    if (reset && elderList) elderNames.value = Object.fromEntries((elderList || []).map((elder) => [elder.id, elder.fullName]))
    if (pendingOrderId) {
      const id = pendingOrderId
      pendingOrderId = 0
      await openDetail({ id })
    }
  }
  catch (error) { if (reset) failure.value = errorMessage(error, '订单读取失败'); else { moreFailed.value = true } }
  finally { loading.value = false }
}
async function loadMore() {
  if (loadingMore.value || noMore.value) return
  loadingMore.value = true; moreFailed.value = false
  pageNo.value += 1
  try {
    const page = await platformApi.orders(pageNo.value, pageSize, group.value || undefined)
    orders.value = orders.value.concat(page.items || [])
    total.value = Number(page.total) || orders.value.length
    noMore.value = orders.value.length >= total.value
  } catch (error) { moreFailed.value = true; pageNo.value -= 1 }
  finally { loadingMore.value = false }
}
function changeFilter(value) { group.value = value; load(true) }
onReachBottom(loadMore)
async function openDetail(order) {
  busy.value = true
  try {
    const detail = await platformApi.order(order.id)
    selected.value = detail
    review.value = null; settlement.value = null; settlementFailure.value = ''; reviewDenied.value = false
    // 用服务端返回的最新状态判断，避免列表里的旧状态挡住评价和结算入口。
    if (detail.status === 'COMPLETED') {
      review.value = await platformApi.review(detail.id)
      try { settlement.value = await platformApi.settlement(detail.id) }
      catch (error) { settlementFailure.value = errorMessage(error, '结算信息读取失败') }
    }
    // 详情卡固定渲染在列表上方，打开后回到页首就能看到；不依赖 selector 测量，小程序与 H5 表现一致。
    uni.pageScrollTo({ scrollTop: 0, duration: 200, fail: () => {} })
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
function confirmCompletion(order) {
  // 确认完成会生成待结算且不能撤回，必须先让用户确认一次。
  uni.showModal({
    title: '确认服务完成',
    content: '确认后将生成待结算，操作不可撤销。',
    success: (result) => { if (result.confirm) act(order, 'confirm') },
  })
}
function cancelBooking(order) {
  uni.showModal({
    title: '取消预约',
    content: '确定取消这笔预约吗？',
    success: (result) => { if (result.confirm) act(order, 'cancel') },
  })
}
async function submitReview() {
  if (!selected.value || !reviewContent.value.trim()) return showFailure(new Error('请填写服务评价'))
  busy.value = true
  try {
    await platformApi.submitReview(selected.value.id, { stars: reviewStars.value, content: reviewContent.value.trim() })
    reviewContent.value = ''
    review.value = await platformApi.review(selected.value.id)
    uni.showToast({ title: '评价已提交', icon: 'success' })
  } catch (error) {
    // 重复评价会返回 409，此时把已存在的评价读回来，页面不会停在“可重复提交”的状态。
    if (error?.code === 'REVIEW_EXISTS') { review.value = await platformApi.review(selected.value.id); showFailure(new Error('该订单已评价')) }
    // 没有该档案的评价授权时收起表单，避免用户对着一个必然失败的表单反复点击。
    else if (error?.statusCode === 403) { reviewDenied.value = true; showFailure(error) }
    else showFailure(error)
  }
  finally { busy.value = false }
}
async function createPayment() {
  if (!selected.value) return
  busy.value = true
  try {
    const attempt = await platformApi.createPaymentAttempt(selected.value.id)
    uni.showModal({ title: '模拟支付已发起', content: `流水号：${attempt.attemptRef}\n请由本机模拟支付服务处理后，再刷新结算状态。`, showCancel: false })
    // 重新读取结算视图失败只影响这次展示，不能把“查询失败”当成“支付失败”。
    try { settlement.value = await platformApi.settlement(selected.value.id); settlementFailure.value = '' }
    catch (error) { settlementFailure.value = errorMessage(error, '结算信息读取失败') }
  } catch (error) { showFailure(error) }
  finally { busy.value = false }
}
async function syncSettlement() {
  if (!selected.value) return
  busy.value = true
  try { settlement.value = await platformApi.syncSettlement(selected.value.id); settlementFailure.value = '' }
  catch (error) { showFailure(error) }
  finally { busy.value = false }
}
onShow(load)
</script>

<template>
  <view class="page">
    <AppPageHeader title="我的订单" />
    <view class="content">
      <view class="filters">
        <view v-for="f in filters" :key="f.value" class="filter" :class="{ on: group === f.value }" @click="changeFilter(f.value)">{{ f.label }}</view>
      </view>
      <view v-if="loading" class="state">正在读取平台订单…</view>
      <view v-else-if="failure" class="state error"><text>{{ failure }}</text><button class="secondary" @click="load">重新加载</button></view>
      <template v-else>
      <view v-if="selected" id="order-detail" class="detail-card">
        <view class="detail-head"><text class="title">订单详情</text><text class="link-button" @click="selected = null">收起</text></view>
        <view class="detail"><text>服务对象</text><text>{{ elderNames[selected.elder_id] || '档案 ' + selected.elder_id }}</text></view>
        <view class="detail"><text>项目</text><text>{{ selected.item_name_snapshot }}</text></view>
        <view class="detail"><text>时间</text><text>{{ formatDateTime(selected.start_at) }} – {{ formatDateTime(selected.end_at) }}</text></view>
        <view class="detail"><text>地址</text><text class="address">{{ selected.address_snapshot }}</text></view>
        <view v-if="selected.status === 'COMPLETED'" class="extra">
          <text class="subtitle">服务评价</text>
          <view v-if="review" class="muted">已评价 · {{ review.stars }} 星 · {{ review.content }}</view>
          <view v-else-if="reviewDenied" class="muted">当前账号没有该档案的评价权限；如需评价，请让档案管理员调整授权。</view>
          <view v-else class="review-form"><picker :range="[1,2,3,4,5]" @change="reviewStars = Number($event.detail.value) + 1"><view class="picker">{{ reviewStars }} 星　⌄</view></picker><textarea v-model="reviewContent" class="textarea" maxlength="500" placeholder="请简要评价本次服务" /><button class="primary" :disabled="busy" @click="submitReview">提交评价</button></view>
          <text class="subtitle">结算状态</text>
          <view v-if="settlementFailure" class="settle-failure"><text>{{ settlementFailure }}</text><button class="link-button" :disabled="busy" @click="openDetail(selected)">重新读取</button></view>
          <view v-else-if="settlement" class="muted">{{ settlementLabels[settlement.settlement.status] || settlement.settlement.status }} · ¥{{ Number(settlement.settlement.amount).toFixed(2) }}</view>
          <view v-else class="muted">正在读取结算信息…</view>
          <view class="actions"><button v-if="settlement?.settlement.status === 'WAITING_METHOD' || settlement?.settlement.status === 'FAILED'" class="primary" :disabled="busy" @click="createPayment">发起模拟支付</button><button v-if="settlement?.settlement.status === 'PROCESSING'" class="secondary" :disabled="busy" @click="syncSettlement">刷新结算状态</button></view>
          <text class="hint">模拟支付结果由服务端核对；网络查询失败不能作为支付失败依据。此处不处理真实资金。</text>
        </view>
      </view>
      <view v-if="!orders.length" class="state">还没有订单，预约后可在这里查看进度。</view>
      <view v-else class="list">
        <view v-for="order in orders" :key="order.id" class="card" :class="{ active: selected && selected.id === order.id }">
          <view class="row"><text class="title">{{ order.item_name_snapshot }}</text><text class="status">{{ labels[order.status] || order.status }}</text></view>
          <text class="muted">{{ formatDateTime(order.start_at) }}</text>
          <view class="row bottom"><text>¥{{ Number(order.payable_amount).toFixed(2) }}</text><button class="link-button" @click="openDetail(order)">查看详情</button></view>
          <view class="actions">
            <button v-if="order.status === 'WAITING_CONFIRMATION'" class="primary" :disabled="busy" @click="confirmCompletion(order)">确认服务完成</button>
            <button v-if="['WAITING_ASSIGNMENT','WAITING_ACCEPTANCE','WAITING_SERVICE'].includes(order.status)" class="secondary" :disabled="busy" @click="cancelBooking(order)">取消预约</button>
          </view>
        </view>
      </view>
      <view v-if="loadingMore" class="paging">正在加载更多…</view>
      <view v-else-if="moreFailed" class="paging"><text>加载失败</text><button class="link-button" @click="loadMore">重试</button></view>
      <view v-else-if="noMore && orders.length" class="paging">没有更多订单了</view>
      </template>
    </view>
    <AppBottomNav active="orders" />
  </view>
</template>

<style scoped>
.page { min-height:100vh; padding-bottom:calc(72px + env(safe-area-inset-bottom)); box-sizing:border-box; background:#f5f8fb; color:#19364d; font-size:16px; line-height:1.55; }
.content { padding:16px 18px 28px; }
.list { display:flex; flex-direction:column; gap:10px; }
.card,.detail-card,.state { padding:16px; border-radius:13px; background:#fff; }
/* 详情卡置顶后，用它标出当前展开的是哪一笔，避免连点多个“查看详情”时对不上 */
.card.active { box-shadow:inset 0 0 0 2px #125fa8; }
.paging { display:flex; justify-content:center; align-items:center; gap:8px; margin-top:12px; color:#687b89; font-size:13px; }
.filters { display:flex; gap:8px; margin-bottom:12px; overflow-x:auto; }
.filter { flex:0 0 auto; padding:7px 15px; border-radius:16px; background:#fff; color:#586c7c; font-size:14px; }
.filter.on { background:#125fa8; color:#fff; font-weight:600; }
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
/* 小程序 button 默认 display:block + 左右 auto 外边距，在 space-between 行内会被居中撑开，
   这里显式收回宽度和外边距，让“查看详情/收起/重新读取”贴住行的右端。 */
.link-button { width:auto; margin:0; padding:0; background:transparent; color:#125fa8; }
.settle-failure { display:flex; justify-content:space-between; align-items:center; gap:8px; margin-top:7px; color:#a34135; font-size:14px; }
.detail-card { margin:0 0 12px; }
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
