<script setup lang="ts">
  import AppButton from '@/components/ui/AppButton.vue'
  import AppIcon from '@/components/ui/AppIcon.vue'
  import { useRouter } from 'vue-router'

  const router = useRouter()

  const paths = [
    { label: '用户端首页', to: '/user' },
    { label: '服务运营', to: '/admin/operations' },
    { label: '登录', to: '/login' },
  ]
</script>

<template>
  <div
    class="wrap"
    style="
      max-width: 720px;
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding-block: var(--sp-9);
    "
  >
    <div
      class="card card--pad"
      style="text-align: center; display: grid; gap: var(--sp-4); justify-items: center"
    >
      <span class="empty__icon" aria-hidden="true"><AppIcon name="alert" /></span>
      <div>
        <h1 class="section-title" style="font-size: var(--fs-30)">页面不存在</h1>
        <p class="muted" style="margin-top: var(--sp-2); font-size: var(--fs-14)">
          地址可能已经变更，或该页面还没有实现。
        </p>
      </div>
      <div class="row" style="justify-content: center">
        <AppButton @click="router.push('/')">返回工作台</AppButton>
        <RouterLink v-for="path in paths" :key="path.to" class="linkbtn" :to="path.to">{{
          path.label
        }}</RouterLink>
      </div>
    </div>
  </div>
</template>
