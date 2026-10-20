<template>
  <AuthLayout>
    <p class="eyebrow">加入我们</p>
    <h2 class="auth-title">创建账号</h2>
    <p class="muted auth-subtitle">注册后可预约服务，档案权限需另行授权。</p>
    <el-form @submit.prevent="submit" label-position="top" class="auth-form">
      <el-form-item label="账号">
        <el-input v-model="username" autocomplete="username" size="large" placeholder="4–32 位小写字母、数字或下划线" />
      </el-form-item>
      <el-form-item label="称呼"><el-input v-model="name" autocomplete="name" size="large" placeholder="请输入您的称呼" /></el-form-item>
      <el-form-item label="邮箱（选填）"><el-input v-model="email" type="email" autocomplete="email" size="large" placeholder="仅用于联系，不参与登录" /></el-form-item>
      <el-form-item label="密码"><el-input v-model="password" type="password" autocomplete="new-password" show-password size="large" placeholder="至少 6 位" /></el-form-item>
      <el-form-item label="确认密码"><el-input v-model="confirmPassword" type="password" autocomplete="new-password" show-password size="large" placeholder="再次输入密码" /></el-form-item>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <button class="primary submit" type="submit" :disabled="loading">{{ loading ? '注册中…' : '注册并继续' }}</button>
    </el-form>
    <p class="auth-switch">已有账号？ <router-link to="/login">返回登录</router-link></p>
  </AuthLayout>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AuthLayout from '../../layouts/AuthLayout.vue'
import { api } from '../../api/client'

const router = useRouter()
const username = ref('')
const name = ref('')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const error = ref('')
const loading = ref(false)
const usernamePattern = /^[a-z0-9_]{4,32}$/
async function submit() {
  error.value = ''
  const account = username.value.trim().toLowerCase()
  if (!usernamePattern.test(account)) { error.value = '账号须为 4–32 位小写字母、数字或下划线'; return }
  if (!name.value.trim()) { error.value = '请填写称呼'; return }
  if (password.value.length < 6) { error.value = '密码至少 6 位'; return }
  if (password.value !== confirmPassword.value) { error.value = '两次输入的密码不一致'; return }
  loading.value = true
  try {
    await api('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username: account, name: name.value.trim(), email: email.value.trim(), password: password.value }),
    })
    await router.replace('/login')
  } catch (reason) { error.value = reason instanceof Error ? reason.message : '注册失败' }
  finally { loading.value = false }
}
</script>
<style scoped>
.auth-title { font-size: 42px; margin: 10px 0 8px; letter-spacing: -.04em; }
.auth-subtitle { margin: 0 0 30px; line-height: 1.7; }
.auth-form :deep(.el-form-item) { margin-bottom: 16px; }
.auth-form :deep(.el-form-item__label) { font-weight: 650; color: #234b3e; }
.auth-form :deep(.el-input__wrapper) { min-height: 46px; border-radius: 12px; }
.submit { width: 100%; margin-top: 12px; min-height: 50px; }
.form-error { color: #bc4536; }
.auth-switch { text-align: center; margin-top: 24px; color: #758078; }
.auth-switch a { color: #0b7b5e; font-weight: 700; }
@media (max-height: 720px) and (min-width: 851px) { .auth-title { font-size: 32px; margin: 4px 0; } .auth-subtitle { margin-bottom: 8px; line-height: 1.4; } .auth-form :deep(.el-form-item) { margin-bottom: 0; } .auth-form :deep(.el-form-item__label) { padding-bottom: 0; line-height: 20px; } .auth-form :deep(.el-input__wrapper) { min-height: 44px; } .submit { min-height: 44px; margin-top: 4px; } .auth-switch { margin-top: 8px; } }
</style>
