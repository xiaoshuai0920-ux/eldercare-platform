<script setup>
// 系统导航栏已显示页面名，页内再放一次会形成两层重复标题；这里只保留品牌条。
// title 仅用于无障碍标签。
defineProps({ title: { type: String, required: true } })
</script>

<template>
  <view class="app-page-header" role="banner" :aria-label="title">
    <view class="brand">
      <image class="brand-mark" src="/static/eldercare-logo-mark.png" mode="aspectFit" />
      <text>居家养老</text>
    </view>
  </view>
</template>

<style scoped>
.app-page-header { display:flex; align-items:center; min-height:56px; padding:8px 20px; background:#fff; border-bottom:1px solid #e2e9ee; }
.brand { display:flex; align-items:center; gap:8px; color:#143b59; font-size:16px; font-weight:700; white-space:nowrap; }
.brand-mark { width:32px; height:32px; }
</style>
