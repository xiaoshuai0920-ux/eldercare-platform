<script setup>
import { ref } from 'vue'
import { activateFocusedButton } from '../../accessibility.js'
import { navigate, platformApi } from '../../api/platform.js'

const username = ref('')
const password = ref('')
const busy = ref(false)
const message = ref('')
async function login() {
  if (!username.value.trim() || !password.value) {
    message.value = '请输入用户名和密码。'
    return
  }
  busy.value = true
  message.value = ''
  try {
    const user = await platformApi.login(username.value.trim(), password.value)
    password.value = ''
    if (user.role === 'ADMIN') {
      message.value = '管理员请使用 PC Web 工作台。'
      await platformApi.logout()
      return
    }
    message.value = `已登录：${user.name}`
    navigate(user.role === 'WORKER' ? '/pages/worker/index' : '/pages/dashboard/index')
  } catch (error) {
    message.value = error instanceof Error ? error.message : '登录失败，请稍后重试。'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <view class="login-page" @keydown="activateFocusedButton">
    <view class="title">登录账号</view>
    <view class="note">使用平台账号登录，业务数据由平台服务提供。</view>
    <view class="label">用户名</view><input v-model="username" class="input" placeholder="请输入用户名" autocomplete="username" />
    <view class="label">密码</view><input v-model="password" class="input" password placeholder="请输入密码" />
    <view v-if="message" class="message">{{ message }}</view>
    <button role="button" tabindex="0" class="submit" :disabled="busy" @click="login">{{ busy ? '正在登录' : '登录' }}</button>
  </view>
</template>

<style scoped>
.login-page { padding: 32px 22px; color: #19364d; }
.title { font-size: 25px; font-weight: 700; }
.note { margin-top: 8px; color: #586c7c; }
.label { margin: 22px 0 8px; font-weight: 600; }
.input { min-height: 48px; padding: 0 12px; background: white; border: 1px solid #c4d5e2; border-radius: 8px; }
.message { margin-top: 16px; color: #104977; }
.submit { margin-top: 24px; background: #125fa8; color: white; }
.back { margin-top: 12px; background: #eaf3fc; color: #104977; }
</style>
