<template><AuthLayout><p class="eyebrow">服务人员激活</p><h2 class="auth-title">设置登录密码</h2><p class="muted">请输入管理员安全交付的一次性激活口令。</p><el-form label-position="top" @submit.prevent="activate"><el-form-item label="激活口令"><el-input v-model="token" autocomplete="off" /></el-form-item><el-form-item label="新密码"><el-input v-model="password" type="password" show-password autocomplete="new-password" /></el-form-item><p v-if="error" class="error" role="alert">{{ error }}</p><button class="primary submit" type="submit" :disabled="busy">激活账号</button></el-form><p><router-link to="/login">返回登录</router-link></p></AuthLayout></template>
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AuthLayout from '../../layouts/AuthLayout.vue'
import { api } from '../../api/client'
const router=useRouter(),token=ref(''),password=ref(''),error=ref(''),busy=ref(false)
async function activate(){if(!token.value.trim()||password.value.length<12){error.value='请填写口令和至少 12 位密码';return}busy.value=true;try{await api('/auth/activate-worker',{method:'POST',body:JSON.stringify({token:token.value.trim(),password:password.value})});await router.replace('/login')}catch(reason){error.value=reason instanceof Error?reason.message:'激活失败'}finally{busy.value=false}}
</script>
<style scoped>.auth-title{font-size:40px;margin:10px 0}.submit{width:100%;margin:18px 0}.error{color:#b34939}a{color:#0b7b5e;font-weight:700}</style>

