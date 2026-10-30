import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import type { Role } from '@/api/types'

// 迁移约定：旧版路由路径全部保留（含 /user|worker|admin/notifications），
// 设计稿索引与规范页移到 /dev 下，避免占用旧版 `/` → `/login` 的契约。
import NotFoundView from '@/views/NotFoundView.vue'
import LoginView from '@/views/auth/LoginView.vue'
import RegisterView from '@/views/auth/RegisterView.vue'
import UserLayout from '@/layouts/UserLayout.vue'
import StaffLayout from '@/layouts/StaffLayout.vue'
import UserHomeView from '@/views/user/HomeView.vue'
import ServicesView from '@/views/user/ServicesView.vue'
import UserOrdersView from '@/views/user/OrdersView.vue'
import FamilyView from '@/views/user/FamilyView.vue'
import RemindersView from '@/views/user/RemindersView.vue'
import MessagesView from '@/views/user/MessagesView.vue'
import WorkerHomeView from '@/views/worker/HomeView.vue'
import LeaveView from '@/views/worker/LeaveView.vue'
import AdminHomeView from '@/views/admin/HomeView.vue'
import OperationsView from '@/views/admin/OperationsView.vue'

const ROLE_HOME: Record<Role, string> = { USER: '/user', WORKER: '/worker', ADMIN: '/admin' }

export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    // 旧版契约：根路径直接进登录页
    { path: '/', redirect: '/login' },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/register', name: 'register', component: RegisterView },

    {
      path: '/user',
      component: UserLayout,
      meta: { role: 'USER' },
      children: [
        { path: '', name: 'user-home', component: UserHomeView },
        {
          path: 'services',
          name: 'user-services',
          component: ServicesView,
          meta: { role: 'USER', section: '服务预约' },
        },
        {
          path: 'orders',
          name: 'user-orders',
          component: UserOrdersView,
          meta: { role: 'USER', section: '我的订单' },
        },
        {
          path: 'family',
          name: 'user-family',
          component: FamilyView,
          meta: { role: 'USER', section: '家庭管理' },
        },
        {
          path: 'reminders',
          name: 'user-reminders',
          component: RemindersView,
          meta: { role: 'USER', section: '生活提醒' },
        },
        {
          path: 'messages',
          name: 'user-messages',
          component: MessagesView,
          meta: { role: 'USER', section: '消息' },
        },
        // 旧路径兼容：/user/notifications 曾是消息页
        { path: 'notifications', redirect: '/user/messages' },
      ],
    },
    {
      path: '/worker',
      component: StaffLayout,
      props: { role: 'WORKER' },
      meta: { role: 'WORKER' },
      children: [
        { path: '', name: 'worker-home', component: WorkerHomeView },
        {
          path: 'leave',
          name: 'worker-leave',
          component: LeaveView,
          meta: { role: 'WORKER', section: '请假' },
        },
        {
          path: 'messages',
          name: 'worker-messages',
          component: MessagesView,
          meta: { role: 'WORKER', section: '消息' },
        },
        { path: 'notifications', redirect: '/worker/messages' },
      ],
    },
    {
      path: '/admin',
      component: StaffLayout,
      props: { role: 'ADMIN' },
      meta: { role: 'ADMIN' },
      children: [
        { path: '', name: 'admin-home', component: AdminHomeView },
        {
          path: 'operations',
          name: 'admin-operations',
          component: OperationsView,
          meta: { role: 'ADMIN', section: '服务运营' },
        },
        {
          path: 'messages',
          name: 'admin-messages',
          component: MessagesView,
          meta: { role: 'ADMIN', section: '消息' },
        },
        { path: 'notifications', redirect: '/admin/messages' },
      ],
    },

    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView },
  ],
})

router.beforeEach(async (to) => {
  const session = useSessionStore()
  if (!session.ready) await session.load()

  // 已登录访问登录页，直接回到对应工作台
  if (to.name === 'login' && session.user) return ROLE_HOME[session.user.role]

  const required = to.meta.role as Role | undefined
  if (!required) return true
  if (!session.user) return { path: '/login', query: { redirect: to.fullPath } }
  if (session.user.role !== required) return ROLE_HOME[session.user.role]
  return true
})
