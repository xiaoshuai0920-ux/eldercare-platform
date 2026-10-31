<script setup lang="ts">
  import { ref } from 'vue'
  import BrandMark from '@/components/ui/BrandMark.vue'
  import SeniorToggle from '@/components/ui/SeniorToggle.vue'
  import AppButton from '@/components/ui/AppButton.vue'
  import AppTag from '@/components/ui/AppTag.vue'
  import AppField from '@/components/ui/AppField.vue'
  import AppInput from '@/components/ui/AppInput.vue'
  import AppSelect from '@/components/ui/AppSelect.vue'
  import AppSwitch from '@/components/ui/AppSwitch.vue'
  import AppSegmented from '@/components/ui/AppSegmented.vue'
  import EmptyState from '@/components/ui/EmptyState.vue'
  import ErrorBanner from '@/components/ui/ErrorBanner.vue'
  import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
  import DataTable, { type TableColumn } from '@/components/ui/DataTable.vue'
  import { useUiStore } from '@/stores/ui'

  const ui = useUiStore()

  const colors = [
    { name: 'primary-50', value: '#eef7f2', var: '--c-primary-50', note: '悬浮底' },
    { name: 'primary-100', value: '#dcefe6', var: '--c-primary-100', note: '描边 / 浅底' },
    { name: 'primary-500', value: '#0f7b5c', var: '--c-primary-500', note: '行动色' },
    { name: 'primary-600', value: '#0c6a4f', var: '--c-primary-600', note: 'hover' },
    { name: 'primary-700', value: '#0a5942', var: '--c-primary-700', note: 'active' },
    { name: 'sidebar', value: '#0e3b31', var: '--c-sidebar', note: '深绿侧栏' },
    { name: 'ink-strong', value: '#16302a', var: '--c-ink-strong', note: '标题 13.4:1' },
    { name: 'ink-soft', value: '#3e564c', var: '--c-ink-soft', note: '次要 7.9:1' },
    { name: 'ink-muted', value: '#5c7168', var: '--c-ink-muted', note: '辅助 5.2:1' },
    { name: 'bg', value: '#faf9f7', var: '--c-bg', note: '暖白底' },
    { name: 'line', value: '#e3e2db', var: '--c-line', note: '1px 描边' },
    { name: 'surface-sunken', value: '#f4f3ef', var: '--c-surface-sunken', note: '表头 / 内凹' },
  ]

  const semantics = [
    { label: '待你处理', var: '--c-warm-soft', color: '--c-warm-ink', ratio: '5.58:1' },
    { label: '已完成', var: '--c-success-soft', color: '--c-success-ink', ratio: '6.69:1' },
    { label: '服务中断', var: '--c-danger-soft', color: '--c-danger-ink', ratio: '6.94:1' },
    { label: '说明信息', var: '--c-info-soft', color: '--c-info-ink', ratio: '6.48:1' },
    { label: '服务中', var: '--c-progress-soft', color: '--c-progress-ink', ratio: '6.95:1' },
    { label: '待派单', var: '--c-neutral-soft', color: '--c-neutral-ink', ratio: '6.40:1' },
  ]

  const contrast = [
    { text: 'ink-strong #16302a', bg: 'bg #faf9f7', ratio: '13.39:1', pass: true },
    { text: 'ink #1b3129', bg: 'bg #faf9f7', ratio: '13.15:1', pass: true },
    { text: 'ink-soft #3e564c', bg: 'surface #ffffff', ratio: '7.95:1', pass: true },
    { text: 'ink-muted #5c7168', bg: 'surface #ffffff', ratio: '5.23:1', pass: true },
    { text: 'ink-muted #5c7168', bg: 'bg #faf9f7', ratio: '4.97:1', pass: true },
    { text: '白字 on primary-500', bg: '#0f7b5c', ratio: '5.24:1', pass: true },
    { text: 'primary-500', bg: 'primary-50 #eef7f2', ratio: '4.80:1', pass: true },
    { text: 'warm-ink #8f5210', bg: 'warm-soft #fdf1e3', ratio: '5.58:1', pass: true },
    { text: 'success-ink #1f5f45', bg: '#e8f4ee', ratio: '6.69:1', pass: true },
    { text: 'danger-ink #8f2f1f', bg: '#fbeae6', ratio: '6.94:1', pass: true },
    { text: 'info-ink #215a80', bg: '#e9f1f7', ratio: '6.48:1', pass: true },
    { text: 'sidebar-ink #cfe6dc', bg: 'sidebar #0e3b31', ratio: '9.48:1', pass: true },
    { text: '白字', bg: 'sidebar-active #14795d', ratio: '5.35:1', pass: true },
    { text: '适老 ink-muted #4e635a', bg: 'bg #faf9f7', ratio: '6.13:1', pass: true },
    { text: 'warm #c2703a（仅图形）', bg: 'surface #ffffff', ratio: '3.70:1', pass: true },
  ]

  const typeScale = [
    {
      size: '38 / 30',
      sample: '欢迎回来，李梅',
      style: 'font-size: var(--fs-38); font-weight: 700; letter-spacing: -0.03em',
    },
    { size: '30', sample: '让每项服务都有回应', style: 'font-size: var(--fs-30); font-weight: 700' },
    { size: '24', sample: '上门助餐 · 王秀兰', style: 'font-size: var(--fs-24); font-weight: 600' },
    { size: '20', sample: '高优先级待办', style: 'font-size: var(--fs-20); font-weight: 600' },
    { size: '16', sample: '正文基准：用户端说明文字与表单输入', style: 'font-size: var(--fs-16)' },
    { size: '14', sample: '管理端正文：表格、卡片、按钮文案', style: 'font-size: var(--fs-14)' },
    { size: '13', sample: '次要信息：时间、地址、辅助说明', style: 'font-size: var(--fs-13)' },
    { size: '12', sample: '标签、表头、脚注（最低 12px）', style: 'font-size: var(--fs-12)' },
  ]

  const steps = ['加载态', '空态', '错误态'] as const
  const state = ref<(typeof steps)[number]>('加载态')

  const tableColumns: TableColumn[] = [
    { key: 'id', label: '订单号' },
    { key: 'elder', label: '老人' },
    { key: 'item', label: '项目' },
    { key: 'startAt', label: '预约时间' },
    { key: 'status', label: '状态', className: 'col-status', headerClass: 'col-status' },
    { key: 'amount', label: '金额', align: 'right' },
  ]
  const tableRows = [
    {
      id: '20261021003',
      elder: '王秀兰',
      item: '上门助餐',
      startAt: '10-22 09:30',
      status: 'WAITING_SERVICE',
      amount: 45,
    },
    {
      id: '20261021002',
      elder: '李国强',
      item: '代取报告',
      startAt: '10-21 09:00',
      status: 'WAITING_CONFIRMATION',
      amount: 50,
    },
    {
      id: '20261021001',
      elder: '陈德芳',
      item: '日常保洁',
      startAt: '10-20 14:00',
      status: 'COMPLETED',
      amount: 120,
    },
  ]

  const demoBoolean = ref(true)
  const demoField = ref('南京西路 1288 弄')
  const stateOptions = steps.map((label) => ({ value: label, label }))
</script>

<template>
  <header class="topbar">
    <div class="topbar__inner">
      <RouterLink class="brand" to="/">
        <BrandMark :size="30" />
        <span class="brand__text" style="font-size: var(--fs-16)">居家养老 · 设计规范</span>
      </RouterLink>
      <div class="topbar__end">
        <span class="chip">v1.1 · Vue 版</span>
        <SeniorToggle />
        <AppButton variant="secondary" compact to="/">返回索引</AppButton>
      </div>
    </div>
  </header>

  <main class="doc">
    <p class="eyebrow">设计规范</p>
    <h1>一套令牌，三端密度。</h1>
    <p class="doc-lede">
      可信、克制、有体温。页面只消费
      <code>tokens.css</code> 中的变量：色值、字号、间距、圆角、阴影、触达面积不允许写死。 Vue
      版本把组件类名映射为 <code>components/ui</code> 下的单文件组件，样式规则未变。
    </p>

    <section class="spec-section">
      <h2>1 · 色彩系统</h2>
      <p>品牌绿只承担「行动」，暖橙只承担「待你处理」，异常红只承担「失败与中断」。三者不互换。</p>
      <div class="swatches" style="margin-top: var(--sp-4)">
        <div v-for="color in colors" :key="color.name" class="swatch">
          <div class="swatch__color" :style="{ background: `var(${color.var})` }" />
          <div class="swatch__meta">
            <span class="swatch__name">{{ color.name }}</span>
            <span class="swatch__value">{{ color.value }} · {{ color.note }}</span>
          </div>
        </div>
      </div>

      <div class="swatches" style="margin-top: var(--sp-4)">
        <div v-for="item in semantics" :key="item.label" class="swatch">
          <div
            class="swatch__color"
            :style="{
              background: `var(${item.var})`,
              color: `var(${item.color})`,
              display: 'grid',
              placeItems: 'center',
              fontSize: 'var(--fs-13)',
              fontWeight: 600,
            }"
          >
            {{ item.label }}
          </div>
          <div class="swatch__meta">
            <span class="swatch__name">{{ item.var }}</span>
            <span class="swatch__value">文字 {{ item.ratio }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="spec-section">
      <h2>2 · 对比度实测</h2>
      <p>按 WCAG 2.1 相对亮度公式计算，正文 ≥4.5:1，大字与图形 ≥3:1。</p>
      <div class="table-wrap" style="margin-top: var(--sp-4); max-height: none">
        <table class="table">
          <caption class="hidden">
            前背景色对比度实测
          </caption>
          <thead>
            <tr>
              <th scope="col">文字 / 图形</th>
              <th scope="col">背景</th>
              <th scope="col" class="cell-num">实测比值</th>
              <th scope="col" class="col-status">判定</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in contrast" :key="row.text + row.bg">
              <td :data-label="'文字'">{{ row.text }}</td>
              <td :data-label="'背景'">{{ row.bg }}</td>
              <td class="cell-num num" :data-label="'比值'">{{ row.ratio }}</td>
              <td class="col-status" :data-label="'判定'"><AppTag tone="success">通过</AppTag></td>
            </tr>
          </tbody>
        </table>
      </div>
      <p style="margin-top: var(--sp-3); font-size: var(--fs-13)">
        注：暖橙 #c2703a 对白底为 3.70:1，只用于 3px 色条、圆点与图标（≥3:1）；橙色系文字一律使用 warm-ink
        #8f5210。
      </p>
    </section>

    <section class="spec-section">
      <h2>3 · 字号与行高</h2>
      <p>九档字号全部走变量；适老模式整体 ×1.28，标题与数字同步放大。</p>
      <div class="type-scale" style="margin-top: var(--sp-4)">
        <div v-for="row in typeScale" :key="row.size" class="type-row">
          <span class="type-row__tag">{{ row.size }}</span>
          <span :style="row.style + '; color: var(--c-ink-strong)'">{{ row.sample }}</span>
        </div>
      </div>
    </section>

    <section class="spec-section">
      <h2>4 · 间距、圆角、阴影与触达</h2>
      <div class="spec-row">
        <div class="spec-row__label">间距 · 4 基数</div>
        <div>
          <div class="row" style="gap: var(--sp-3)">
            <span v-for="n in ['1', '2', '3', '4', '5', '6', '7', '8', '9']" :key="n" class="chip">{{
              n
            }}</span>
          </div>
          <p style="margin-top: var(--sp-3); font-size: var(--fs-13)">
            页面区块 32、卡片内边距 24、控件间距 12–16。禁止 7/13/18/22 一类裸值。
          </p>
        </div>
      </div>

      <div class="spec-row">
        <div class="spec-row__label">圆角 · 三级</div>
        <div class="row" style="gap: var(--sp-5)">
          <div
            v-for="radius in [
              { label: '卡片 14', v: '--r-card' },
              { label: '控件 10', v: '--r-control' },
              { label: '胶囊 999', v: '--r-pill' },
            ]"
            :key="radius.v"
            style="display: grid; gap: var(--sp-2); justify-items: center"
          >
            <div
              :style="{
                width: '104px',
                height: '64px',
                background: 'var(--c-primary-50)',
                border: '1px solid var(--c-primary-100)',
                borderRadius: `var(${radius.v})`,
              }"
            />
            <span class="muted" style="font-size: var(--fs-12)">{{ radius.label }}</span>
          </div>
        </div>
      </div>

      <div class="spec-row">
        <div class="spec-row__label">阴影 · 三级</div>
        <div class="row" style="gap: var(--sp-5)">
          <div
            v-for="shadow in ['--shadow-sm', '--shadow-md', '--shadow-lg']"
            :key="shadow"
            class="card"
            :style="{
              width: '150px',
              height: '76px',
              boxShadow: `var(${shadow})`,
              display: 'grid',
              placeItems: 'center',
            }"
          >
            <span class="muted" style="font-size: var(--fs-12)">{{ shadow }}</span>
          </div>
        </div>
      </div>

      <div class="spec-row">
        <div class="spec-row__label">触达与行高</div>
        <div class="row" style="gap: var(--sp-3)">
          <span class="chip">--tap-min 44（适老 48）</span>
          <span class="chip">--row-compact 44</span>
          <span class="chip">--row-comfortable 56</span>
          <span class="chip">--row-table 48（适老 56）</span>
        </div>
      </div>
    </section>

    <section class="spec-section">
      <h2>5 · 组件</h2>
      <p>
        组件数量克制：按钮、字段、状态标签、卡片、表格、三态。<code>components/ui</code> 下每个文件一个组件。
      </p>

      <div class="card card--pad" style="margin-top: var(--sp-4)">
        <h3 class="section-title" style="font-size: var(--fs-16)">AppButton · variant × size × state</h3>
        <div class="row" style="gap: var(--sp-3); margin-top: var(--sp-4)">
          <AppButton>主操作</AppButton>
          <AppButton variant="secondary">次要操作</AppButton>
          <AppButton variant="ghost">弱操作</AppButton>
          <AppButton variant="danger">危险操作</AppButton>
          <AppButton size="lg">大号按钮</AppButton>
        </div>
        <div class="row" style="gap: var(--sp-3); margin-top: var(--sp-3)">
          <AppButton disabled>禁用</AppButton>
          <AppButton variant="secondary" disabled>禁用次要</AppButton>
          <AppButton :loading="true">加载中</AppButton>
        </div>
      </div>

      <div class="grid grid--2" style="margin-top: var(--sp-4); gap: var(--sp-4)">
        <div class="card card--pad">
          <h3 class="section-title" style="font-size: var(--fs-16)">AppField · 四段式</h3>
          <div class="stack" style="margin-top: var(--sp-4); gap: var(--sp-4)">
            <AppField
              v-slot="{ describedby }"
              label="服务对象"
              html-for="demo-a"
              required
              help="仅列出已完成核验的对象。"
            >
              <AppSelect id="demo-a" :aria-describedby="describedby">
                <option>王秀兰 · 母亲</option>
                <option>李国强 · 父亲</option>
              </AppSelect>
            </AppField>
            <AppField
              label="详细地址"
              html-for="demo-b"
              required
              error="请补全楼栋与门牌号，服务人员需要准确地址。"
            >
              <AppInput id="demo-b" v-model="demoField" :invalid="true" />
            </AppField>
          </div>
        </div>

        <div class="card card--pad">
          <h3 class="section-title" style="font-size: var(--fs-16)">StatusTag · 五档 tone</h3>
          <div class="row" style="gap: var(--sp-2); margin-top: var(--sp-4)">
            <AppTag tone="neutral">待派单</AppTag>
            <AppTag tone="progress">服务中</AppTag>
            <AppTag tone="action">待确认</AppTag>
            <AppTag tone="success">已完成</AppTag>
            <AppTag tone="danger">服务中断</AppTag>
          </div>
          <h3 class="section-title" style="font-size: var(--fs-16); margin-top: var(--sp-5)">触达示例</h3>
          <div class="row" style="gap: var(--sp-3); margin-top: var(--sp-3)">
            <AppSwitch v-model="demoBoolean" label-on="接单中" label-off="暂停接单" />
            <AppSegmented
              v-model="ui.seniorMode"
              :options="[
                { value: false, label: '标准' },
                { value: true, label: '适老' },
              ]"
              aria-label="密度演示"
            />
          </div>
        </div>
      </div>

      <div class="card card--pad" style="margin-top: var(--sp-4)">
        <div class="row" style="justify-content: space-between">
          <h3 class="section-title" style="font-size: var(--fs-16)">三态：加载 / 空 / 错误</h3>
          <AppSegmented v-model="state" :options="stateOptions" aria-label="状态预览" />
        </div>

        <div style="margin-top: var(--sp-4)">
          <div v-if="state === '加载态'" class="card" style="box-shadow: none">
            <SkeletonBlock :rows="3" />
          </div>
          <EmptyState
            v-else-if="state === '空态'"
            title="当前没有符合条件的订单"
            description="调整筛选条件后再试，或先到「服务预约」提交一次服务需求。"
          >
            <AppButton variant="secondary" to="/user/services">去预约服务</AppButton>
          </EmptyState>
          <ErrorBanner
            v-else
            title="数据加载失败"
            description="请求超时（10 秒）。请检查网络后重试，或稍后再打开此页。"
            @retry="ui.notify('已重新加载', 'default')"
          >
            <template #action>
              <AppButton
                variant="secondary"
                compact
                style="margin-left: auto"
                @click="ui.notify('已重新加载', 'default')"
                >重新加载</AppButton
              >
            </template>
          </ErrorBanner>
        </div>
      </div>

      <div class="card" style="margin-top: var(--sp-4); overflow: hidden">
        <div class="toolbar">
          <h3 class="section-title" style="font-size: var(--fs-16)">DataTable</h3>
          <span class="chip">表头 sticky · 行高 48 · 数字右对齐</span>
          <span class="toolbar__count">泛型组件，`rows` 保持业务类型</span>
        </div>
        <DataTable :columns="tableColumns" :rows="tableRows" max-height="none" caption="表格组件示例">
          <template #cell-id="{ value }"
            ><span class="num">{{ value }}</span></template
          >
          <template #cell-startAt="{ value }"
            ><span class="num">{{ value }}</span></template
          >
          <template #cell-status="{ value }"
            ><AppTag tone="progress">{{
              value === 'COMPLETED' ? '已完成' : value === 'WAITING_CONFIRMATION' ? '待确认' : '待服务'
            }}</AppTag></template
          >
          <template #cell-amount="{ value }"
            ><span class="num">¥{{ Number(value).toFixed(2) }}</span></template
          >
        </DataTable>
      </div>
    </section>

    <section class="spec-section">
      <h2>6 · 适老模式</h2>
      <p>右上角开关切换 <code>html[data-senior="on"]</code>，由 Pinia 的 ui store 统一写入，刷新后保持。</p>
      <div class="card card--pad" style="margin-top: var(--sp-4)">
        <div class="row" style="justify-content: space-between">
          <div>
            <p class="strong">切换后可以观察到的变化</p>
            <p class="muted" style="font-size: var(--fs-13); margin-top: 2px">
              字号 ×1.28、正文行高 1.7、触达 48px、三列卡降为单列、表格转为卡片流、次要文字加深到 6.1:1。
            </p>
          </div>
          <SeniorToggle />
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
  .doc {
    max-width: 1080px;
    margin-inline: auto;
    padding: var(--sp-8) var(--sp-6) var(--sp-9);
  }
  .doc h1 {
    margin-top: var(--sp-2);
    font-size: var(--fs-38);
    line-height: var(--lh-display);
    letter-spacing: -0.03em;
    font-weight: var(--fw-bold);
    color: var(--c-ink-strong);
  }
  .doc h2 {
    font-size: var(--fs-24);
    font-weight: var(--fw-bold);
    color: var(--c-ink-strong);
  }
  .doc p {
    font-size: var(--fs-14);
    color: var(--c-ink-soft);
    margin-top: var(--sp-2);
  }
  .doc-lede {
    margin-top: var(--sp-3) !important;
    font-size: var(--fs-16) !important;
    max-width: 68ch;
  }
</style>
