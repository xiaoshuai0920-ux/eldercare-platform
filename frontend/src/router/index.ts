import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '../stores/session'
const LoginView = () => import('../views/auth/LoginView.vue')
const RegisterView = () => import('../views/auth/RegisterView.vue')
const UserHomeView = () => import('../views/user/UserHomeView.vue')
const FamilyView = () => import('../views/user/FamilyView.vue')
const ServicesView = () => import('../views/user/ServicesView.vue')
const UserOrdersView = () => import('../views/user/UserOrdersView.vue')
const RemindersView = () => import('../views/user/RemindersView.vue')
const NotificationsView = () => import('../views/NotificationsView.vue')
const WorkerHomeView = () => import('../views/worker/WorkerHomeView.vue')
const AdminHomeView = () => import('../views/admin/AdminHomeView.vue')
const AdminOperationsView = () => import('../views/admin/AdminOperationsView.vue')

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/register', component: RegisterView, meta: { public: true } },
    { path: '/user', component: UserHomeView, meta: { role: 'USER' } },
    { path: '/user/family', component: FamilyView, meta: { role: 'USER' } },
    { path: '/user/services', component: ServicesView, meta: { role: 'USER' } },
    { path: '/user/orders', component: UserOrdersView, meta: { role: 'USER' } },
    { path: '/user/reminders', component: RemindersView, meta: { role: 'USER' } },
    { path: '/user/notifications', component: NotificationsView, meta: { role: 'USER' } },
    { path: '/worker', component: WorkerHomeView, meta: { role: 'WORKER' } },
    { path: '/worker/notifications', component: NotificationsView, meta: { role: 'WORKER' } },
    { path: '/admin', component: AdminHomeView, meta: { role: 'ADMIN' } },
    { path: '/admin/operations', component: AdminOperationsView, meta: { role: 'ADMIN' } },
    { path: '/admin/notifications', component: NotificationsView, meta: { role: 'ADMIN' } },
  ],
})

router.beforeEach(async to => {
  if (to.meta.public) return true
  const session = useSessionStore()
  if (!session.loaded) await session.refresh()
  if (!session.user) return '/login'
  if (session.user.role !== to.meta.role) return `/${session.user.role.toLowerCase()}`
  return true
})
