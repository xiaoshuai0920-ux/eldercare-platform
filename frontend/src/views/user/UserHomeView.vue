<template>
  <WorkspaceLayout role="USER">
    <section class="welcome"><p class="eyebrow">您的居家服务</p><h1 class="page-title">欢迎回来，{{ session.user?.name }}。</h1><p class="muted">选择服务对象，查看适合的上门生活服务。</p></section>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <section class="categories" aria-label="服务分类">
      <router-link v-for="item in categories" :key="item.title" to="/user/services" class="surface category"><span class="category-icon">{{ item.icon }}</span><h2>{{ item.title }}</h2><p>{{ item.description }}</p><span class="browse">浏览服务 →</span></router-link>
    </section>
    <section class="lower-grid"><div class="surface block"><div class="block-head"><h2>我的订单</h2><router-link to="/user/orders">查看全部 →</router-link></div><div v-if="dashboard" class="numbers"><span><strong>{{ dashboard.waiting }}</strong>等待服务</span><span><strong>{{ dashboard.active }}</strong>进行中</span><span><strong>{{ dashboard.completed }}</strong>已完成</span></div><div v-for="order in dashboard?.recentOrders || []" :key="order.id" class="recent"><strong>{{ order.item_name_snapshot }}</strong><span>{{ statusName(order.status) }}</span></div><div v-if="!dashboard?.recentOrders?.length && !error" class="empty">暂无订单。您可以先浏览服务项目。</div></div><div class="surface block"><h2>家庭照护</h2><p class="muted">管理服务对象和家庭授权，确保每次预约有明确的权限来源。</p><router-link class="primary family-link" to="/user/family">查看家庭档案</router-link></div></section>
  </WorkspaceLayout>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import WorkspaceLayout from '../../layouts/WorkspaceLayout.vue'
import { useSessionStore } from '../../stores/session'
import { api } from '../../api/client'

const session = useSessionStore()
const error = ref('')
type RecentOrder = { id:number; item_name_snapshot:string; status:string }
type Dashboard = {waiting:number; active:number; completed:number; recentOrders:RecentOrder[]}
const dashboard = ref<Dashboard | null>(null)
const categories = [
  { title: '助餐', icon: '餐', description: '上门协助备餐与用餐' },
  { title: '保洁', icon: '洁', description: '让居家环境舒适整洁' },
  { title: '陪诊', icon: '陪', description: '陪同就医与流程协助' },
]
const labels:Record<string,string>={WAITING_ASSIGNMENT:'待派单',WAITING_ACCEPTANCE:'待接单',WAITING_SERVICE:'待服务',IN_SERVICE:'服务中',WAITING_CONFIRMATION:'待确认',COMPLETED:'已完成',CANCELLED:'已取消',INTERRUPTED:'服务中断',ABNORMAL_CLOSED:'异常结案'}
function statusName(value:string){return labels[value]||value}
onMounted(async () => { try { dashboard.value = await api<Dashboard>('/user/dashboard') } catch (reason) { error.value = reason instanceof Error ? reason.message : '数据加载失败' } })
</script>
<style scoped>
.welcome { padding: 26px 0 42px; }.welcome .muted { font-size: 18px; margin-top: 14px; }
.categories { display: grid; grid-template-columns: repeat(3, 1fr); gap: 20px; }.category { min-height: 220px; padding: 30px; }.category h2 { font-size: 25px; margin: 18px 0 8px; }.category p { color: #6b7c72; }.category-icon { display: grid; place-items: center; width: 58px; height: 58px; border-radius: 18px; background: #e4f5ec; color: #117b5c; font-size: 21px; font-weight: 700; }.browse{color:#0e765b;font-weight:700}
.lower-grid { display: grid; grid-template-columns: 1.2fr .8fr; gap: 20px; margin-top: 26px; }.block { padding: 28px; }.block h2 { margin: 0 0 8px; }.block .empty { margin-top: 28px; }.block-head{display:flex;justify-content:space-between;align-items:center}.block-head a{color:#0e765b;font-weight:700}.numbers{display:flex;gap:28px;margin:26px 0}.numbers span{display:grid;color:#6b7c72}.numbers strong{font-size:28px;color:#18382f}.recent{display:flex;justify-content:space-between;padding:12px 0;border-top:1px solid #edf1ee}.family-link{display:inline-block;margin-top:22px}.error { color: #b34939; }
@media (max-width: 800px) { .categories, .lower-grid { grid-template-columns: 1fr; } }
</style>
