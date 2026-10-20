<template>
  <div :class="['workspace', role === 'USER' ? 'user-workspace' : 'staff-workspace']">
    <header v-if="role === 'USER'" class="topbar">
      <div class="content-width topbar-inner">
        <div class="brand"><BrandMark /><strong>居家养老</strong></div>
        <nav aria-label="用户导航">
          <router-link to="/user">首页</router-link>
          <router-link to="/user/services">服务预约</router-link>
          <router-link to="/user/orders">我的订单</router-link>
          <router-link to="/user/reminders">生活提醒</router-link>
          <router-link to="/user/family">家庭管理</router-link>
          <router-link to="/user/notifications">消息</router-link>
        </nav>
        <div class="account-actions">
          <SeniorToggle />
          <span class="account-name">{{ name }}</span>
          <button class="text-button" @click="signOut">退出</button>
        </div>
      </div>
    </header>
    <aside v-else class="sidebar">
      <div class="sidebar-brand"><BrandMark /><strong>居家养老<br />智能生活服务平台</strong></div>
      <nav :aria-label="role === 'WORKER' ? '服务人员导航' : '管理员导航'">
        <router-link :to="role === 'WORKER' ? '/worker' : '/admin'">工作台</router-link>
        <router-link v-if="role === 'ADMIN'" to="/admin/operations">服务运营</router-link>
        <router-link :to="role === 'WORKER' ? '/worker/notifications' : '/admin/notifications'">消息</router-link>
      </nav>
      <div class="sidebar-bottom">
        <SeniorToggle />
        <button class="text-button" @click="signOut">退出登录</button>
      </div>
    </aside>
    <div class="workspace-main">
      <header v-if="role !== 'USER'" class="staff-header">
        <strong>{{ role === 'WORKER' ? '服务人员端' : '管理员端' }}</strong>
        <span class="account-name">{{ name }}</span>
      </header>
      <main :class="role === 'USER' ? 'content-width page-content' : 'page-content'"><slot /></main>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import BrandMark from '../components/BrandMark.vue'
import SeniorToggle from '../components/common/SeniorToggle.vue'
import { useSessionStore } from '../stores/session'

defineProps<{ role: 'USER' | 'WORKER' | 'ADMIN' }>()
const router = useRouter()
const session = useSessionStore()
const name = computed(() => session.user?.name || '')
async function signOut() { await session.logout(); await router.replace('/login') }
</script>
<style scoped>
.workspace { min-height: 100vh; background: var(--color-bg); }
.topbar { background: rgba(255, 255, 255, .94); border-bottom: 1px solid var(--color-line); }
.topbar-inner { min-height: 72px; display: flex; align-items: center; gap: 32px; }
.brand { display: flex; align-items: center; gap: 9px; font-size: 21px; white-space: nowrap; }
.topbar nav { display: flex; gap: 10px; flex-wrap: wrap; }
.topbar nav a { font-weight: 650; color: var(--color-ink-soft); padding: 24px 8px; border-bottom: 3px solid transparent; white-space: nowrap; }
.topbar nav a:hover { color: var(--color-primary); }
.topbar nav a.router-link-exact-active { color: var(--color-primary); border-bottom-color: var(--color-primary); }
.account-actions { margin-left: auto; display: flex; align-items: center; gap: 16px; }
.account-name { color: var(--color-ink-soft); font-weight: 600; }
.staff-workspace { display: grid; grid-template-columns: 232px 1fr; }
.sidebar { background: var(--color-sidebar); color: #fff; min-height: 100vh; padding: 28px 16px; display: flex; flex-direction: column; }
.sidebar-brand { display: flex; align-items: center; gap: 10px; font-size: 16px; line-height: 1.35; margin-bottom: 44px; }
.sidebar-brand :deep(.brand-mark) { color: #8fe3c2; }
.sidebar nav a { display: block; color: #d8eee4; padding: 13px 16px; border-radius: var(--radius-md); font-weight: 650; }
.sidebar nav a:hover { background: rgba(255, 255, 255, .08); }
.sidebar nav a.router-link-exact-active { background: var(--color-sidebar-active); color: #fff; }
.sidebar-bottom { margin-top: auto; display: grid; gap: 10px; justify-items: start; }
.sidebar-bottom .text-button { color: #c7e7db; text-align: left; padding: 8px 4px; }
.workspace-main { min-width: 0; }
.staff-header { height: 68px; background: var(--color-surface); border-bottom: 1px solid var(--color-line); display: flex; align-items: center; justify-content: space-between; padding: 0 36px; }
.page-content { padding-top: 40px; padding-bottom: 64px; }
.staff-workspace .page-content { width: min(100% - 72px, var(--page-max-staff)); margin-inline: auto; }
@media (max-width: 850px) {
  .staff-workspace { grid-template-columns: 1fr; }
  .sidebar { min-height: auto; padding: 18px; }
  .sidebar-brand { margin-bottom: 16px; }
  .sidebar-bottom { display: flex; }
  .topbar-inner { gap: 16px; flex-wrap: wrap; height: auto; padding: 12px 0; }
  .account-actions { gap: 8px; }
  .staff-workspace .page-content { width: min(100% - 32px, var(--page-max-staff)); }
}
@media (max-width: 1150px) {
  .topbar-inner { flex-wrap: wrap; padding-block: 12px; gap: 12px 28px; }
  .topbar nav { order: 3; width: 100%; }
  .topbar nav a { padding: 10px 5px; }
}
</style>
