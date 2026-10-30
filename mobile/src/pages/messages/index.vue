<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppPageHeader from '../../components/AppPageHeader.vue'
import { errorMessage, platformApi, requireRole, showFailure } from '../../api/platform.js'

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
  }
  catch(error) { failure.value=errorMessage(error,'消息加载失败') }
  finally { loading.value=false }
}
async function read(item) {
  if (item.read_at) return
  busy.value=true
  try { await platformApi.readNotification(item.id); item.read_at=new Date().toISOString() }
  catch(error) { showFailure(error) }
  finally { busy.value=false }
}
async function readAll() {
  for (const item of items.value.filter((entry)=>!entry.read_at)) await read(item)
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
        <view v-for="item in items" :key="item.id" class="card" :class="{ unread:!item.read_at }" @click="read(item)">
          <view class="row"><text class="item-title">{{ item.title }}</text><text v-if="!item.read_at" class="badge">未读</text></view>
          <text class="body">{{ item.body }}</text>
          <text class="date">{{ item.created_at }}</text>
          <text class="hint">{{ item.read_at ? '已读' : '点击标记为已读' }}</text>
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
.link { min-height:40px; padding:0; background:transparent; color:#125fa8; font-size:14px; }
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
