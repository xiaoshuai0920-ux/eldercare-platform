<script setup lang="ts">
  import { computed, onMounted } from 'vue'
  import { RouterLink } from 'vue-router'
  import AppIcon from '@/components/ui/AppIcon.vue'
  import AppButton from '@/components/ui/AppButton.vue'
  import StatusTag from '@/components/ui/StatusTag.vue'
  import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
  import ErrorBanner from '@/components/ui/ErrorBanner.vue'
  import EmptyState from '@/components/ui/EmptyState.vue'
  import { useCatalogStore } from '@/stores/catalog'
  import { useFamilyStore } from '@/stores/family'
  import { useSessionStore } from '@/stores/session'
  import { useUserDashboardStore } from '@/stores/dashboard'
  import { formatDateTime, initial } from '@/utils/format'

  const catalog = useCatalogStore()
  const family = useFamilyStore()
  const session = useSessionStore()
  const dashboard = useUserDashboardStore()

  const ICONS: Record<string, 'meal' | 'broom' | 'bag'> = { MEAL: 'meal', CLEANING: 'broom', ESCORT: 'bag' }
  const featured = computed(() => catalog.items.slice(0, 3))
  const loading = computed(() => dashboard.loading || catalog.loading || family.loading)

  const statusLine = computed(() => {
    if (!dashboard.data) return '正在读取您的服务安排…'
    const { waiting, active } = dashboard.data
    if (active > 0) return `当前有 ${active} 项服务正在进行，${waiting} 项待服务。`
    if (waiting > 0) return `当前有 ${waiting} 项服务待开始。`
    return '当前没有待服务安排，可以看看常用服务。'
  })

  onMounted(() => {
    dashboard.load()
    catalog.load()
    family.load()
  })

  function retry() {
    dashboard.load()
    catalog.load()
    family.load()
  }
</script>

<template>
  <section class="welcome">
    <h1 class="welcome__title">{{ session.name || '您好' }}，欢迎回来</h1>
    <p class="home-intro">生活里的小事，一起安排好。</p>
    <div class="home-current">
      <p>您当前的服务</p>
      <h2>{{ dashboard.error ? '暂时无法读取服务安排' : statusLine }}</h2>
      <RouterLink class="home-current__link" to="/user/orders">查看我的订单 →</RouterLink>
    </div>
  </section>

  <ErrorBanner
    v-if="dashboard.error"
    :title="dashboard.error"
    description="请检查后端服务是否已启动，或稍后重试。"
    tone="error"
    style="margin-bottom: var(--sp-6)"
  >
    <template #action>
      <AppButton variant="secondary" compact style="margin-left: auto" @click="retry">重新加载</AppButton>
    </template>
  </ErrorBanner>

  <section aria-labelledby="svc-title">
    <div class="section__head">
      <div>
        <h2 id="svc-title" class="section-title">常用服务</h2>
        <p class="section__desc">价格以下单时后端校验为准，提交后由管理员人工派单。</p>
      </div>
      <RouterLink class="link-arrow" to="/user/services">全部服务项目 →</RouterLink>
    </div>

    <div v-if="catalog.loading" class="card card--pad"><SkeletonBlock :rows="3" /></div>
    <div v-else class="grid grid--3 home-services">
      <article v-for="item in featured" :key="item.id" class="card card--hover svc-card">
        <span class="svc-card__icon" aria-hidden="true"
          ><AppIcon :name="ICONS[item.category] ?? 'bag'"
        /></span>
        <p class="svc-card__tag">{{ catalog.categoryLabel(item.category) }}</p>
        <h3 class="svc-card__title">{{ item.name }}</h3>
        <p class="svc-card__desc">{{ item.description }}</p>
        <div class="svc-card__foot">
          <p class="price">
            <span class="num">¥{{ item.unit_price }}</span>
            <span class="price__unit">{{ catalog.priceUnit(item) }}</span>
          </p>
          <AppButton variant="secondary" to="/user/services">查看详情</AppButton>
        </div>
      </article>
      <EmptyState
        v-if="!featured.length"
        title="暂无可预约的服务项目"
        description="服务目录尚未上架，请联系运营人员。"
      />
    </div>
  </section>

  <div class="home-summary">
    <section class="card card--pad" aria-labelledby="myorders-title">
      <div class="section__head">
        <h2 id="myorders-title" class="section-title">我的订单</h2>
        <RouterLink class="link-arrow" to="/user/orders">查看全部 →</RouterLink>
      </div>

      <div v-if="dashboard.data" class="counts">
        <div class="count">
          <span class="count__value num">{{ dashboard.data?.waiting ?? 0 }}</span>
          <span class="count__label">待服务</span>
        </div>
        <div class="count">
          <span class="count__value num">{{ dashboard.data?.active ?? 0 }}</span>
          <span class="count__label">进行中</span>
        </div>
        <div class="count">
          <span class="count__value num">{{ dashboard.data?.completed ?? 0 }}</span>
          <span class="count__label">已完成</span>
        </div>
      </div>

      <div v-if="loading" style="margin-top: var(--sp-4)"><SkeletonBlock :rows="3" /></div>
      <div v-else-if="!dashboard.data?.recentOrders.length" style="margin-top: var(--sp-4)">
        <EmptyState title="暂无订单" description="选择一项服务，提交后会进入人工派单。" />
      </div>
      <div v-else style="margin-top: var(--sp-5)">
        <div v-for="order in dashboard.data.recentOrders" :key="order.id" class="mini-order">
          <div class="mini-order__main">
            <p class="mini-order__title">{{ order.item_name_snapshot }}</p>
            <p class="mini-order__meta num">
              {{ formatDateTime(order.start_at) }} · ¥{{ order.payable_amount.toFixed(2) }}
            </p>
          </div>
          <div class="mini-order__end">
            <StatusTag :value="order.status" />
            <RouterLink class="link-arrow" to="/user/orders">详情</RouterLink>
          </div>
        </div>
      </div>
    </section>

    <section class="card card--pad" aria-labelledby="family-title">
      <h2 id="family-title" class="section-title">家庭照护</h2>
      <p class="muted" style="margin-top: var(--sp-2); font-size: var(--fs-14)">
        为家人建立服务档案，管理可代办的家庭授权。每次预约都会记录权限来源。
      </p>

      <ul style="display: grid; gap: var(--sp-3); margin-top: var(--sp-5)">
        <li v-for="elder in family.elders.slice(0, 3)" :key="elder.id" class="row">
          <span class="avatar" aria-hidden="true">{{ initial(elder.fullName) }}</span>
          <span style="display: grid">
            <span class="strong">{{ elder.fullName }}</span>
            <span class="muted" style="font-size: var(--fs-13)">
              {{ family.ageOf(elder.birthDate) ?? '—' }} 岁 · {{ elder.birthDate ?? '未填写生日' }}
            </span>
          </span>
        </li>
      </ul>
      <p
        v-if="!family.elders.length && !family.loading"
        class="muted"
        style="margin-top: var(--sp-4); font-size: var(--fs-13)"
      >
        还没有服务对象档案。
      </p>

      <AppButton variant="secondary" block style="margin-top: var(--sp-6)" to="/user/family"
        >查看家庭档案</AppButton
      >
      <p class="muted" style="font-size: var(--fs-12); margin-top: var(--sp-3)">
        共 {{ family.elders.length }} 位服务对象。
      </p>
    </section>
  </div>

  <div class="banner banner--info" style="margin-top: var(--sp-7)" role="note">
    <AppIcon name="info" />
    <div class="banner__body">
      <p class="banner__title">预约提交后会发生什么</p>
      <p class="banner__desc">
        系统按服务能力与街道覆盖匹配服务人员，由管理员确认派单；接单结果会在「消息」中通知您。
      </p>
    </div>
    <RouterLink class="linkbtn" to="/user/services" style="margin-left: auto">去预约</RouterLink>
  </div>
</template>

<style scoped>
  .welcome__title {
    font-size: var(--fs-30);
  }
  .home-intro {
    margin: var(--sp-2) 0 var(--sp-5);
    color: var(--c-ink-muted);
  }
  .home-current {
    padding: var(--sp-5);
    border-radius: var(--r-card);
    background: var(--c-primary-50);
  }
  .home-current > p {
    margin: 0 0 var(--sp-2);
    color: var(--c-primary-700);
    font-size: var(--fs-13);
  }
  .home-current h2 {
    margin: 0;
    font-size: var(--fs-18);
  }
  .home-current__link {
    display: inline-flex;
    margin-top: var(--sp-3);
    min-height: var(--tap-min);
    align-items: center;
    color: var(--c-primary-700);
    font-weight: var(--fw-semi);
  }
  .home-summary {
    display: grid;
    grid-template-columns: minmax(0, 1.18fr) minmax(0, 0.82fr);
    gap: var(--sp-5);
    margin-top: var(--sp-7);
  }
  @media (max-width: 767px) {
    .welcome__title {
      font-size: var(--fs-24);
    }
    .home-summary {
      grid-template-columns: minmax(0, 1fr);
    }
    .home-services {
      grid-template-columns: minmax(0, 1fr);
    }
  }
</style>
