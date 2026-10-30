<script setup lang="ts">
  import { ref } from 'vue'
  import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
  import logoMark from '@/assets/eldercare-logo-mark.png'
  import SeniorToggle from '@/components/ui/SeniorToggle.vue'
  import AppButton from '@/components/ui/AppButton.vue'
  import AppDialog from '@/components/ui/AppDialog.vue'
  import AppInput from '@/components/ui/AppInput.vue'
  import { authApi } from '@/api/endpoints'
  import { useSessionStore } from '@/stores/session'
  import { useUiStore } from '@/stores/ui'

  interface NavItem {
    to: string
    label: string
  }

  const NAV: NavItem[] = [
    { to: '/user', label: '首页' },
    { to: '/user/services', label: '服务预约' },
    { to: '/user/orders', label: '我的订单' },
    { to: '/user/reminders', label: '生活提醒' },
    { to: '/user/family', label: '家庭管理' },
    { to: '/user/messages', label: '消息' },
  ]

  const route = useRoute()
  const router = useRouter()
  const session = useSessionStore()
  const ui = useUiStore()
  const passwordOpen = ref(false)
  const passwordBusy = ref(false)
  const currentPassword = ref('')
  const newPassword = ref('')
  const confirmPassword = ref('')

  async function changePassword() {
    if (newPassword.value !== confirmPassword.value) {
      ui.notify('两次输入的新密码不一致', 'danger')
      return
    }
    passwordBusy.value = true
    try {
      await authApi.changePassword(currentPassword.value, newPassword.value)
      passwordOpen.value = false
      currentPassword.value = ''
      newPassword.value = ''
      confirmPassword.value = ''
      ui.notify('密码已修改', 'success')
    } catch (error) {
      ui.notify(error instanceof Error ? error.message : '密码修改失败', 'danger')
    } finally {
      passwordBusy.value = false
    }
  }

  /** 当前分区高亮：/user/orders 时「我的订单」高亮 */
  function isActive(to: string): boolean {
    return to === '/user' ? route.path === '/user' : route.path.startsWith(to)
  }

  function signOut() {
    session.logout()
    router.push('/login')
  }
</script>

<template>
  <a class="skip" href="#main">跳到主内容</a>
  <header class="topbar">
    <div class="topbar__inner">
      <RouterLink class="brand" to="/user">
        <img class="user-brand-logo" :src="logoMark" alt="" />
        <span class="brand__text">居家养老</span>
      </RouterLink>
      <nav class="usernav" aria-label="用户导航">
        <RouterLink
          v-for="item in NAV"
          :key="item.to"
          :to="item.to"
          :aria-current="isActive(item.to) ? 'page' : undefined"
        >
          {{ item.label }}
        </RouterLink>
      </nav>
      <div class="topbar__end">
        <SeniorToggle />
        <span class="account">{{ session.name || '用户' }}</span>
        <AppButton variant="ghost" compact @click="passwordOpen = true">修改密码</AppButton>
        <AppButton variant="ghost" compact @click="signOut">退出</AppButton>
      </div>
    </div>
  </header>

  <AppDialog v-model="passwordOpen" title="修改登录密码" subtitle="请输入当前密码与新的登录密码（6–72 位）。">
    <form class="password-form" @submit.prevent="changePassword">
      <label
        >当前密码<AppInput v-model="currentPassword" type="password" required autocomplete="current-password"
      /></label>
      <label
        >新密码<AppInput
          v-model="newPassword"
          type="password"
          required
          minlength="6"
          maxlength="72"
          autocomplete="new-password"
      /></label>
      <label
        >确认新密码<AppInput
          v-model="confirmPassword"
          type="password"
          required
          minlength="6"
          maxlength="72"
          autocomplete="new-password"
      /></label>
      <div class="password-actions">
        <AppButton type="button" variant="ghost" @click="passwordOpen = false">取消</AppButton
        ><AppButton type="submit" :loading="passwordBusy">保存密码</AppButton>
      </div>
    </form>
  </AppDialog>

  <main id="main" tabindex="-1" class="wrap wrap--user user-main">
    <RouterView />
  </main>
</template>

<style scoped>
  .password-form {
    display: grid;
    gap: var(--sp-4);
  }
  .password-form label {
    display: grid;
    gap: var(--sp-2);
  }
  .password-actions {
    display: flex;
    justify-content: flex-end;
    gap: var(--sp-3);
  }
  .user-brand-logo {
    width: 38px;
    height: 38px;
    object-fit: contain;
  }
  @media (max-width: 767px) {
    .topbar__inner {
      padding: var(--sp-3) var(--sp-4);
    }
    .topbar__end {
      margin-left: auto;
    }
    .topbar__end .account {
      display: none;
    }
    .usernav {
      flex-wrap: nowrap;
      overflow-x: auto;
    }
    .usernav a {
      flex: 0 0 auto;
    }
  }
</style>
