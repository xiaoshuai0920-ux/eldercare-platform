<script setup lang="ts">
  import { computed } from 'vue'
  import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
  import SeniorToggle from '@/components/ui/SeniorToggle.vue'
  import AppIcon from '@/components/ui/AppIcon.vue'
  import AppButton from '@/components/ui/AppButton.vue'
  import type { IconName } from '@/components/ui/icons'
  import { useSessionStore } from '@/stores/session'
  import { today } from '@/utils/format'
  import horizontalLogo from '@/assets/eldercare-logo-horizontal.png'
  import logoMark from '@/assets/eldercare-logo-mark.png'

  const props = defineProps<{ role: 'WORKER' | 'ADMIN' }>()

  interface NavItem {
    label: string
    icon: IconName
    to: string
    panel?: string
    /** 仅在无 panel 查询时高亮 */
    baseOnly?: boolean
  }
  interface NavGroup {
    label: string
    items: NavItem[]
  }

  const route = useRoute()
  const router = useRouter()
  const session = useSessionStore()

  const groups = computed<NavGroup[]>(() => {
    if (props.role === 'WORKER') {
      return [
        {
          label: '今日',
          items: [
            { label: '工作台', icon: 'home', to: '/worker', baseOnly: true },
            { label: '请假', icon: 'calendar', to: '/worker/leave' },
          ],
        },
        { label: '系统', items: [{ label: '消息', icon: 'message', to: '/worker/messages' }] },
      ]
    }
    return [
      {
        label: '运营',
        items: [
          { label: '工作台', icon: 'home', to: '/admin', baseOnly: true },
          { label: '派单工作台', icon: 'list', to: '/admin/operations', baseOnly: true },
        ],
      },
      {
        label: '资源',
        items: [
          { label: '服务目录', icon: 'folder', to: '/admin/operations', panel: 'catalog' },
          { label: '服务人员', icon: 'users', to: '/admin/operations', panel: 'workers' },
        ],
      },
      { label: '系统', items: [{ label: '消息', icon: 'message', to: '/admin/messages' }] },
    ]
  })

  const roleLabel = computed(() => (props.role === 'WORKER' ? '服务人员端' : '管理端'))
  const sectionLabel = computed(() => (route.meta.section as string | undefined) ?? '')

  function isActive(item: NavItem): boolean {
    const panel = typeof route.query.panel === 'string' ? route.query.panel : ''
    const onPath = route.path === item.to || (item.to !== '/admin' && route.path.startsWith(item.to))
    if (!onPath) return false
    if (item.panel) return panel === item.panel
    if (item.baseOnly) return panel === '' || panel === 'orders'
    return true
  }

  function signOut() {
    session.logout()
    router.push('/login')
  }
</script>

<template>
  <a class="skip" href="#main">跳到主内容</a>
  <div class="shell" :class="role === 'ADMIN' ? 'shell--admin' : 'shell--worker'">
    <aside class="sidebar">
      <div v-if="role === 'ADMIN'" class="sidebar__brand sidebar__brand--admin">
        <picture
          ><source media="(max-width: 1023px)" :srcset="logoMark" />
          <img :src="horizontalLogo" alt="居家养老智能生活服务平台"
        /></picture>
        <small>管理工作台</small>
      </div>
      <div v-else class="sidebar__brand">
        <img class="worker-brand-logo" :src="logoMark" alt="" />
        <span>
          <strong>居家养老</strong>
          <small>{{ roleLabel }}</small>
        </span>
      </div>

      <nav class="sidenav" :aria-label="`${roleLabel}导航`">
        <template v-for="group in groups" :key="group.label">
          <p class="sidenav__group">{{ group.label }}</p>
          <RouterLink
            v-for="item in group.items"
            :key="item.label"
            :to="item.panel ? { path: item.to, query: { panel: item.panel } } : item.to"
            :aria-current="isActive(item) ? 'page' : undefined"
          >
            <AppIcon :name="item.icon" />
            <span>{{ item.label }}</span>
          </RouterLink>
        </template>
      </nav>

      <div class="sidebar__foot">
        <SeniorToggle variant="onDark" />
        <AppButton
          variant="ghost"
          compact
          style="color: var(--c-sidebar-ink); justify-content: flex-start"
          @click="signOut"
        >
          <AppIcon name="logout" />
          <span>退出登录</span>
        </AppButton>
      </div>
    </aside>

    <div class="main">
      <header class="staffbar">
        <span class="staffbar__title">{{ roleLabel }}{{ sectionLabel ? ` · ${sectionLabel}` : '' }}</span>
        <div class="row" style="gap: var(--sp-4)">
          <span class="muted" style="font-size: var(--fs-13)">{{ today() }}</span>
          <span class="account">{{ session.name || (role === 'WORKER' ? '陈志强' : '王强') }}</span>
          <span class="avatar" aria-hidden="true" style="width: 34px; height: 34px; font-size: var(--fs-13)">
            {{ (session.name || (role === 'WORKER' ? '陈志强' : '王强')).slice(0, 1) }}
          </span>
        </div>
      </header>

      <main id="main" tabindex="-1" class="wrap wrap--staff page-body">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
  .sidebar__brand--admin {
    display: block;
  }
  .sidebar__brand--admin img {
    display: block;
    width: 100%;
    max-width: 164px;
    height: auto;
    filter: brightness(0) invert(1);
  }
  .sidebar__brand--admin small {
    margin-top: 8px;
  }
  .worker-brand-logo {
    width: 36px;
    height: 36px;
    object-fit: contain;
    filter: brightness(0) invert(1);
  }
  @media (max-width: 1023px) {
    .sidebar__brand--admin img {
      width: 48px;
      max-width: none;
    }
    .sidebar__brand--admin small {
      display: none;
    }
  }
  @media (max-width: 767px) {
    .shell--worker {
      display: block;
    }
    .shell--worker .sidebar {
      position: static;
      width: 100%;
      height: auto;
      padding: var(--sp-3) var(--sp-4);
      align-items: stretch;
      gap: var(--sp-2);
    }
    .shell--worker .sidebar__brand {
      justify-content: flex-start;
    }
    .shell--worker .sidebar__brand span {
      display: block;
    }
    .shell--worker .sidenav {
      display: flex;
      flex-wrap: wrap;
    }
    .shell--worker .sidenav a {
      width: auto;
      padding: 0 var(--sp-3);
    }
    .shell--worker .sidenav a span {
      display: inline;
    }
    .shell--worker .sidebar__foot {
      display: flex;
      justify-content: space-between;
    }
    .shell--worker .sidebar__foot .switch__label,
    .shell--worker .sidebar__foot .btn span {
      display: inline;
    }
    .shell--worker .sidebar__foot .btn {
      width: auto;
      padding: 0 var(--sp-3);
    }
  }
</style>
