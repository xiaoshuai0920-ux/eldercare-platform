<script setup lang="ts">
  import BrandMark from '@/components/ui/BrandMark.vue'
  import SeniorToggle from '@/components/ui/SeniorToggle.vue'
  import AppButton from '@/components/ui/AppButton.vue'

  interface PageEntry {
    no: string
    name: string
    desc: string
    file: string
    to: string
    extra?: string
  }

  const groups: { title: string; note: string; pages: PageEntry[] }[] = [
    {
      title: '用户端',
      note: '低密度 · 大字号 · 少决策 ｜ 版心 1200',
      pages: [
        {
          no: '用户端 01',
          name: '首页',
          desc: '欢迎区 + 三张服务卡 + 订单概览 + 家庭照护入口',
          file: 'views/user/HomeView.vue',
          to: '/user',
        },
        {
          no: '用户端 02',
          name: '服务预约',
          desc: '筛选与卡片同屏；分步预约（选对象 → 选时段 → 确认金额）',
          file: 'views/user/ServicesView.vue',
          to: '/user/services',
        },
        {
          no: '用户端 03',
          name: '我的订单',
          desc: '按状态分组，每单最多两个主操作；详情为右侧抽屉',
          file: 'views/user/OrdersView.vue',
          to: '/user/orders',
        },
        {
          no: '用户端 04',
          name: '家庭管理',
          desc: '档案卡 → 单页三段（基本信息 / 家庭授权 / 核验与争议）',
          file: 'views/user/FamilyView.vue',
          to: '/user/family',
        },
        {
          no: '用户端 05',
          name: '生活提醒',
          desc: '用药 / 复诊 / 缴费提醒 + 接下来 7 天时间轴',
          file: 'views/user/RemindersView.vue',
          to: '/user/reminders',
        },
        {
          no: '用户端 06',
          name: '消息',
          desc: '按订单 / 系统筛选，未读用底色 + 圆点 + 文案三重编码',
          file: 'views/user/MessagesView.vue',
          to: '/user/messages',
        },
      ],
    },
    {
      title: '服务人员端',
      note: '中密度 · 任务聚焦 ｜ 侧栏 240 + 内容区',
      pages: [
        {
          no: '人员端 01',
          name: '工作台',
          desc: '接单开关 + 两个 KPI + 今日任务流（按开始时间排序）',
          file: 'views/worker/HomeView.vue',
          to: '/worker',
        },
        {
          no: '人员端 02',
          name: '请假',
          desc: '表单与记录同页；待审批可撤回，后果写在按钮上方',
          file: 'views/worker/LeaveView.vue',
          to: '/worker/leave',
        },
        {
          no: '共用 01',
          name: '登录',
          desc: '左故事右表单；autocomplete 规范、脚注 5.2:1',
          file: 'views/auth/LoginView.vue',
          to: '/login',
        },
        {
          no: '共用 02',
          name: '注册',
          desc: '新密码 / 确认密码成对出现，协议必须显式勾选',
          file: 'views/auth/RegisterView.vue',
          to: '/register',
        },
      ],
    },
    {
      title: '管理端',
      note: '高密度 · 表格化 · 异常优先 ｜ 版心 1440',
      pages: [
        {
          no: '管理端 01',
          name: '工作台',
          desc: '4 个 KPI + 高优先级待办（橙色置顶）+ 最新订单表',
          file: 'views/admin/HomeView.vue',
          to: '/admin',
        },
        {
          no: '管理端 02',
          name: '服务运营（七页签）',
          desc: '订单表 48px 行高 + 派单弹层（含「为什么可选」）+ 时间轴抽屉',
          file: 'views/admin/OperationsView.vue',
          to: '/admin/operations',
        },
      ],
    },
  ]

  const chips = [
    '暖白 #faf9f7',
    '行动绿 #0f7b5c',
    '待办橙 #c2703a',
    '圆角 14 / 10 / 999',
    '触达 44 / 48',
    '字号 12–38 九档',
  ]
</script>

<template>
  <header class="topbar">
    <div class="topbar__inner">
      <RouterLink class="brand" to="/">
        <BrandMark />
        <span class="brand__text">居家养老 · Vue 版</span>
      </RouterLink>
      <nav class="usernav" aria-label="索引导航">
        <a href="#g-user" aria-current="page">用户端</a>
        <a href="#g-worker">服务人员端</a>
        <a href="#g-admin">管理端</a>
        <RouterLink to="/spec">设计规范</RouterLink>
      </nav>
      <div class="topbar__end">
        <SeniorToggle />
        <AppButton to="/login">进入登录</AppButton>
      </div>
    </div>
  </header>

  <main class="idx">
    <p class="eyebrow">Vue 3 + TS + Vite · 16 个路由 · 23 个组件</p>
    <h1 style="margin-top: var(--sp-2)">三端界面，一套令牌。</h1>
    <p style="margin-top: var(--sp-3); font-size: var(--fs-16); color: var(--c-ink-soft); max-width: 70ch">
      静态稿已重构为单文件组件：外壳是布局组件，页面只组合组件与 store，数据全部由 Pinia 响应式驱动。
      样式仍来自同一份 <code>tokens.css</code>，视觉与稿子一致。
    </p>

    <div class="row" style="margin-top: var(--sp-5); gap: var(--sp-2)">
      <span v-for="chip in chips" :key="chip" class="chip">{{ chip }}</span>
    </div>

    <section
      v-for="group in groups"
      :id="`g-${group.title === '用户端' ? 'user' : group.title === '服务人员端' ? 'worker' : 'admin'}`"
      :key="group.title"
    >
      <div class="group-head">
        <h2>{{ group.title }}</h2>
        <span class="muted" style="font-size: var(--fs-13)">{{ group.note }}</span>
      </div>
      <div class="grid grid--3">
        <RouterLink v-for="page in group.pages" :key="page.to" class="pg" :to="page.to">
          <span class="pg__no">{{ page.no }}</span>
          <span class="pg__name">{{ page.name }}</span>
          <span class="pg__desc">{{ page.desc }}</span>
          <span class="pg__file">{{ page.file }}</span>
          <span class="pg__go">打开 →</span>
        </RouterLink>
      </div>
    </section>

    <div class="group-head">
      <h2>设计规范</h2>
      <span class="muted" style="font-size: var(--fs-13)">令牌、对比度与组件总览</span>
    </div>
    <div class="grid grid--2">
      <RouterLink class="pg" to="/spec">
        <span class="pg__no">规范 01</span>
        <span class="pg__name">令牌与组件总览</span>
        <span class="pg__desc">色板 + 对比度实测清单 + 字号阶梯 + 组件 + 三态 + 适老对照</span>
        <span class="pg__file">views/SpecView.vue</span>
        <span class="pg__go">打开 →</span>
      </RouterLink>
      <div class="pg" style="cursor: default">
        <span class="pg__no">运行</span>
        <span class="pg__name">npm run dev</span>
        <span class="pg__desc">localhost:5173；管理员端直接访问 /admin，服务人员端 /worker。</span>
        <span class="pg__file">README.md</span>
        <span class="pg__go muted" style="font-weight: 400">命令与结构见交付说明</span>
      </div>
    </div>
  </main>
</template>

<style scoped>
  .idx {
    max-width: var(--w-user);
    margin-inline: auto;
    padding: var(--sp-8) var(--sp-6) var(--sp-9);
  }
  .idx h1 {
    font-size: var(--fs-38);
    line-height: var(--lh-display);
    letter-spacing: -0.03em;
    font-weight: var(--fw-bold);
    color: var(--c-ink-strong);
  }
  .pg {
    display: grid;
    gap: var(--sp-2);
    padding: var(--sp-5);
    background: var(--c-surface);
    border: 1px solid var(--c-line);
    border-radius: var(--r-card);
    box-shadow: var(--shadow-sm);
    transition:
      box-shadow var(--dur) var(--ease),
      transform var(--dur) var(--ease);
  }
  .pg:hover {
    box-shadow: var(--shadow-md);
    transform: translateY(-2px);
  }
  .pg__no {
    font-size: var(--fs-12);
    color: var(--c-ink-muted);
    font-variant-numeric: tabular-nums;
  }
  .pg__name {
    font-size: var(--fs-18);
    font-weight: var(--fw-semi);
    color: var(--c-ink-strong);
  }
  .pg__desc {
    font-size: var(--fs-13);
    color: var(--c-ink-soft);
  }
  .pg__file {
    font-size: var(--fs-12);
    color: var(--c-ink-muted);
    font-family: var(--font-num);
  }
  .pg__go {
    margin-top: var(--sp-2);
    color: var(--c-primary-600);
    font-size: var(--fs-13);
    font-weight: var(--fw-semi);
  }
  .group-head {
    display: flex;
    align-items: baseline;
    gap: var(--sp-3);
    margin: var(--sp-8) 0 var(--sp-4);
  }
  .group-head h2 {
    font-size: var(--fs-24);
    font-weight: var(--fw-bold);
    color: var(--c-ink-strong);
  }
</style>
