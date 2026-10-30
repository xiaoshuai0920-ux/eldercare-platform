<script setup>
import { navigate } from '../api/platform.js'

const props = defineProps({ active: { type: String, required: true } })
const entries = [
  { id: 'home', label: '首页', url: '/pages/dashboard/index' },
  { id: 'booking', label: '预约', url: '/pages/booking/index' },
  { id: 'orders', label: '订单', url: '/pages/orders/index' },
  { id: 'more', label: '更多', url: '/pages/more/index' },
]
function open(entry) {
  if (entry.id !== props.active) navigate(entry.url)
}
</script>

<template>
  <view class="bottom-nav" role="navigation" aria-label="主要导航">
    <button v-for="entry in entries" :key="entry.id" :class="{ active: entry.id === active }" @click="open(entry)">{{ entry.label }}</button>
  </view>
</template>

<style scoped>
.bottom-nav { position:fixed; z-index:5; right:0; bottom:0; left:0; display:flex; padding:6px 8px calc(6px + constant(safe-area-inset-bottom)); padding-bottom:calc(6px + env(safe-area-inset-bottom)); background:#fff; border-top:1px solid #dce5ec; box-shadow:0 -4px 16px rgba(27,58,80,.04); }
.bottom-nav button { flex:1; min-height:48px; padding:0; background:transparent; color:#667b8c; font-size:15px; }
.bottom-nav button::after { border:0; }
.bottom-nav button.active { color:#125fa8; font-weight:700; }
</style>
