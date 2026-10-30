<script setup>
defineProps({ title: { type: String, required: true } })
</script>

<template>
  <view class="app-page-header">
    <view class="brand">
      <image class="brand-mark" src="/static/eldercare-logo-mark.png" mode="aspectFit" />
      <text>居家养老</text>
    </view>
    <text class="page-title">{{ title }}</text>
  </view>
</template>

<style scoped>
.app-page-header { display:flex; align-items:center; justify-content:space-between; gap:12px; min-height:60px; padding:8px 20px; background:#fff; border-bottom:1px solid #e2e9ee; }
.brand { display:flex; align-items:center; gap:8px; color:#143b59; font-size:16px; font-weight:700; white-space:nowrap; }
.brand-mark { width:34px; height:34px; }
.page-title { color:#19364d; font-size:18px; font-weight:700; text-align:right; }
</style>
