<template>
  <AuthLayout>
    <p class="eyebrow">欢迎回来</p>
    <h2 class="auth-title">登录您的账号</h2>
    <p class="muted auth-subtitle">继续管理服务、订单与生活提醒。</p>
    <el-form @submit.prevent="submit" label-position="top" class="auth-form">
      <el-form-item label="账号"><el-input v-model="username" autocomplete="username" size="large" placeholder="请输入登录账号" /></el-form-item>
      <el-form-item label="密码"><el-input v-model="password" type="password" autocomplete="current-password" show-password size="large" placeholder="请输入密码" /></el-form-item>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="primary submit" type="submit" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
    </el-form>
    <p class="auth-switch">还没有账号？ <router-link to="/register">创建普通用户账号</router-link></p>
  </AuthLayout>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AuthLayout from '../../layouts/AuthLayout.vue'
import { useSessionStore } from '../../stores/session'

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)
const router = useRouter()
const session = useSessionStore()
async function submit() {
  error.value = ''
  if (!username.value || !password.value) { error.value = '请输入账号和密码'; return }
  loading.value = true
  try { await session.login(username.value.trim(), password.value); await router.replace(`/${session.user?.role.toLowerCase() || 'user'}`) }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '登录失败' }
  finally { loading.value = false }
}
</script>
<style scoped>
.auth-title { font-size: 42px; margin: 10px 0 8px; letter-spacing: -.04em; }
.auth-subtitle { margin: 0 0 38px; }
.auth-form :deep(.el-form-item) { margin-bottom: 23px; }
.auth-form :deep(.el-form-item__label) { font-weight: 650; color: #234b3e; }
.auth-form :deep(.el-input__wrapper) { min-height: 48px; border-radius: 12px; }
.submit { width: 100%; margin-top: 8px; min-height: 50px; }
.form-error { color: #bc4536; margin: 0 0 16px; }
.auth-switch { text-align: center; margin-top: 26px; color: #758078; }
.auth-switch a { color: #0b7b5e; font-weight: 700; }
</style>
