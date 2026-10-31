<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppPageHeader from '../../components/AppPageHeader.vue'
import { errorMessage, notificationTarget, platformApi, requireRole, showFailure } from '../../api/platform.js'
import { formatDateTime } from '../../utils/datetime.js'
import { unread } from '../../store/unread.js'

const loading=ref(true)
const failure=ref('')
const items=ref([])
const busy=ref(false)
async function load() {
  loading.value=true
  failure.value=''
  try {
    if (!await requireRole(['USER','WORKER'])) { loading.value=false; return }
    const page=await platformApi.notifications()
    items.value=page.items || []
    unread.value=Number(page.unread) || 0
  }
  catch(error) { failure.value=errorMessage(error,'消息加载失败') }
  finally { loading.value=false }
}
// 点击消息：标记已读 + 跳转到对应业务页。
// 原来的实现只标记已读，已读消息还会在入口直接 return，所以点击后毫无反应。
async function open(item) {
  const target = notificationTarget(item)
  if (!target) return showFailure(new Error('这条通知没有可打开的内容'))
  if (!item.read_at) {
    busy.value = true
    try { await platformApi.readNotification(item.id); item.read_at = new Date().toISOString(); unread.value = Math.max(0, unread.value - 1) }
    catch (error) { /* 标记失败不阻断查看内容 */ }
    finally { busy.value = false }
  }
  uni.navigateTo({ url: target, fail: () => showFailure(new Error('页面打开失败，请稍后重试')) })
}
async function readAll() {
  const pending = items.value.filter((entry) => !entry.read_at)
  if (!pending.length) return
  busy.value = true
  let failed = 0
  // 逐条标记并各自兜底：一条失败不影响其余消息，最后统一提示结果。
  for (const item of pending) {
    try { await platformApi.readNotification(item.id); item.read_at = new Date().toISOString() }
    catch (error) { failed += 1 }
  }
  busy.value = false
  unread.value = items.value.filter((entry) => !entry.read_at).length
  if (failed) showFailure(new Error(`${failed} 条消息未能标记已读，请稍后重试`))
}
onShow(load)
</script>

<template>
  <view class="page">
    <AppPageHeader title="消息通知" />
    <view class="content">
      <view class="toolbar"><text class="title">平台消息</text><button class="link" :disabled="busy" @click="readAll">全部标为已读</button></view>
      <view v-if="loading" class="state">正在读取消息…</view>
      <view v-else-if="failure" class="state error"><text>{{ failure }}</text><button class="secondary" :disabled="busy" @click="load">重新加载</button></view>
      <view v-else-if="!items.length" class="state">目前没有消息。</view>
      <view v-else class="list">
        <view v-for="item in items" :key="item.id" class="card" :class="{ unread:!item.read_at }" @click="open(item)">
          <view class="row"><text class="item-title">{{ item.title }}</text><text v-if="!item.read_at" class="badge">未读</text></view>
          <text class="body">{{ item.body }}</text>
          <text class="date">{{ formatDateTime(item.created_at) }}</text>
          <text class="hint">{{ item.read_at ? '点击查看相关内容 ›' : '点击查看并标记已读 ›' }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.page { min-height:100vh; background:#f5f8fb; color:#19364d; font-size:16px; line-height:1.5; }
.content { padding:18px; }
.toolbar,.row { display:flex; justify-content:space-between; align-items:center; gap:10px; }
.toolbar { margin-bottom:12px; }
.title,.item-title { font-weight:700; }
.title { font-size:19px; }
.link { width:auto; margin:0; min-height:40px; padding:0; background:transparent; color:#125fa8; font-size:14px; }
.link::after { border:0; }
.list { display:flex; flex-direction:column; gap:9px; }
.card,.state { padding:15px; border-radius:12px; background:#fff; }
.unread { border-left:4px solid #c2703a; background:#fffaf5; }
.error { color:#a34135; }
.state button { min-height:44px; margin-top:10px; padding:0 14px; border-radius:9px; background:#eaf3fc; color:#104977; font-size:15px; }
.state button::after { border:0; }
.badge { color:#a35c23; font-size:13px; }
.body,.date,.hint { display:block; margin-top:7px; color:#687b89; font-size:14px; }
.hint { color:#125fa8; }
</style>
