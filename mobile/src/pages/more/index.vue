<script setup>
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import AppPageHeader from '../../components/AppPageHeader.vue'
import AppBottomNav from '../../components/AppBottomNav.vue'
import { errorMessage, requireRole } from '../../api/platform.js'

const ready = ref(false)
const failure = ref('')
const entries = [
  { title:'家庭授权', detail:'管理老人档案、邀请与授权', url:'/pages/family/index' },
  { title:'生活提醒', detail:'创建或调整服务对象的提醒', url:'/pages/reminders/index' },
  { title:'消息通知', detail:'查看订单与平台消息', url:'/pages/messages/index' },
]
function open(url) { uni.navigateTo({ url }) }
async function load() {
  failure.value = ''
  try { ready.value = Boolean(await requireRole(['USER'])) }
  catch (error) { ready.value = false; failure.value = errorMessage(error, '页面加载失败') }
}
onShow(load)
</script>

<template>
  <view class="page">
    <AppPageHeader title="更多服务" />
    <view class="content" v-if="failure"><view class="card error"><text>{{ failure }}</text><button class="secondary" @click="load">重新加载</button></view></view>
    <view class="content" v-else-if="ready">
      <view class="intro"><text class="title">照护与提醒</text><text class="muted">按服务对象的有效授权管理信息。</text></view>
      <button v-for="entry in entries" :key="entry.title" class="menu-card" @click="open(entry.url)">
        <view><text class="menu-title">{{ entry.title }}</text><text class="muted">{{ entry.detail }}</text></view><text class="arrow">›</text>
      </button>
    </view>
    <AppBottomNav active="more" />
  </view>
</template>

<style scoped>
.page { min-height:100vh; padding-bottom:calc(72px + env(safe-area-inset-bottom)); box-sizing:border-box; background:#f5f8fb; color:#19364d; font-size:17px; }
.content { padding:22px 18px; }
.intro { display:flex; flex-direction:column; gap:6px; margin-bottom:16px; }
.title { font-size:23px; font-weight:700; }
.muted { display:block; margin-top:5px; color:#687b89; font-size:14px; }
.menu-card { display:flex; align-items:center; justify-content:space-between; width:100%; min-height:76px; margin:9px 0; padding:14px; border-radius:12px; background:#fff; text-align:left; }
.menu-card::after { border:0; }
.error { display:flex; flex-direction:column; gap:12px; padding:16px; border-radius:12px; background:#fff; color:#a34135; }
.error button { min-height:44px; border-radius:9px; background:#eaf3fc; color:#104977; font-size:15px; }
.error button::after { border:0; }
.menu-title { font-weight:700; }
.arrow { color:#125fa8; font-size:26px; }
</style>
