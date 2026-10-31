<script setup>
import { ref } from 'vue'
import { navigate, platformApi } from '../../api/platform.js'

const username = ref('')
const password = ref('')
const confirm = ref('')
const busy = ref(false)
const message = ref('')

function validUsername(value) { return /^[A-Za-z0-9_]{4,32}$/.test(value) }

async function submit() {
  message.value = ''
  const usernameValue = username.value.trim()
  if (!validUsername(usernameValue)) { message.value = '用户名需为 4–32 位字母、数字或下划线。'; return }
  if (!password.value || password.value.length < 6) { message.value = '密码至少 6 位。'; return }
  if (password.value !== confirm.value) { message.value = '两次输入的密码不一致。'; return }
  busy.value = true
  try {
    await platformApi.register({ username: usernameValue, password: password.value })
    uni.showToast({ title: '注册成功，请登录', icon: 'success' })
    navigate('/pages/login/index')
  } catch (error) {
    message.value = error instanceof Error ? error.message : '注册失败，请稍后重试。'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <view class="register-page">
    <view class="title">创建账号</view>
    <view class="note">注册后即可为自己或家人预约服务。</view>
    <view class="label">用户名</view><input v-model="username" class="input" maxlength="32" placeholder="4–32 位字母、数字或下划线" />
    <view class="label">密码</view><input v-model="password" class="input" password placeholder="至少 6 位" />
    <view class="label">确认密码</view><input v-model="confirm" class="input" password placeholder="再次输入密码" />
    <view v-if="message" class="message">{{ message }}</view>
    <button class="submit" :disabled="busy" @click="submit">{{ busy ? '正在注册' : '注册' }}</button>
    <button class="back" @click="navigate('/pages/login/index')">返回登录</button>
  </view>
</template>

<style scoped>
.register-page { min-height:100vh; padding:32px 22px 40px; box-sizing:border-box; color:#19364d; background:#f5f8fb; }
.title { font-size:25px; font-weight:700; }
.note { margin-top:8px; color:#586c7c; }
.label { margin:20px 0 8px; font-weight:600; }
.input { min-height:48px; padding:0 12px; box-sizing:border-box; background:#fff; border:1px solid #c4d5e2; border-radius:8px; }
.message { margin-top:16px; color:#a34135; }
.submit { margin-top:24px; background:#125fa8; color:#fff; }
.back { margin-top:12px; background:#eaf3fc; color:#104977; }
</style>
