<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { userApi } from '@/api/endpoints'
  import type { SettlementView } from '@/api/types'
  import { money } from '@/utils/format'
  import AppButton from './AppButton.vue'

  const props = defineProps<{ orderId: number }>()
  const data = ref<SettlementView | null>(null)
  const busy = ref(false)
  const error = ref('')

  async function load() {
    busy.value = true
    error.value = ''
    try {
      data.value = await userApi.settlement(props.orderId)
    } catch {
      data.value = null
      error.value = '结算信息加载失败，请重新加载。'
    } finally {
      busy.value = false
    }
  }

  async function start() {
    busy.value = true
    error.value = ''
    try {
      await userApi.createPaymentAttempt(props.orderId)
      data.value = await userApi.settlement(props.orderId)
    } catch {
      // 创建请求可能已提交；重新读取失败时禁止重复发起，直到状态可确认。
      try {
        data.value = await userApi.settlement(props.orderId)
      } catch {
        data.value = null
      }
      error.value = '发起结果尚未确认，请先重新加载结算信息。'
    } finally {
      busy.value = false
    }
  }

  async function sync() {
    busy.value = true
    error.value = ''
    try {
      data.value = await userApi.syncSettlement(props.orderId)
    } catch {
      error.value = '查询未完成，结算结果暂未确认，请稍后重试。'
    } finally {
      busy.value = false
    }
  }

  onMounted(load)
</script>

<template>
  <section class="settlement-panel" aria-labelledby="settlement-title">
    <h2 id="settlement-title">服务结算</h2>
    <p class="muted">模拟支付不涉及真实资金；线下结算由管理员登记。</p>
    <p v-if="error" class="settlement-error" role="alert">{{ error }}</p>
    <template v-if="data">
      <p>
        结算金额：<strong>{{ money(Number(data.settlement.amount)) }}</strong>
      </p>
      <p>
        状态：{{
          {
            WAITING_METHOD: '待选择结算方式',
            PROCESSING: '处理中',
            SETTLED: '已结算',
            FAILED: '模拟交易未成功',
          }[data.settlement.status]
        }}
      </p>
      <template v-if="data.settlement.status === 'PROCESSING' && data.attempts[0]">
        <p class="muted">
          请在<a href="http://127.0.0.1:8081/console.html" target="_blank" rel="noopener noreferrer"
            >本机模拟支付控制台</a
          >登记交易并设置结果，再返回刷新。
        </p>
        <dl class="settlement-reference">
          <div>
            <dt>尝试标识</dt>
            <dd>{{ data.attempts[0].attempt_ref }}</dd>
          </div>
          <div>
            <dt>订单编号</dt>
            <dd>{{ orderId }}</dd>
          </div>
          <div>
            <dt>结算版本</dt>
            <dd>{{ data.attempts[0].settlement_version }}</dd>
          </div>
          <div>
            <dt>金额</dt>
            <dd>{{ money(Number(data.attempts[0].amount)) }}</dd>
          </div>
        </dl>
        <AppButton :loading="busy" @click="sync">刷新支付结果</AppButton>
      </template>
      <AppButton
        v-if="['WAITING_METHOD', 'FAILED'].includes(data.settlement.status)"
        :loading="busy"
        @click="start"
        >发起模拟支付</AppButton
      >
      <p v-if="data.settlement.status === 'SETTLED'">
        结算方式：{{
          { OFFLINE: '管理员线下登记', SIMULATED: '模拟支付', WAIVED: '费用减免' }[
            data.settlement.method ?? ''
          ] ?? '其他'
        }}
      </p>
    </template>
    <AppButton v-else-if="!busy" variant="secondary" @click="load">重新加载结算信息</AppButton>
  </section>
</template>

<style scoped>
  .settlement-panel {
    display: grid;
    gap: var(--sp-3);
    margin-top: var(--sp-5);
    padding: var(--sp-5);
    background: var(--c-primary-50);
    border-radius: var(--r-card);
  }
  .settlement-panel h2,
  .settlement-panel p {
    margin: 0;
  }
  .settlement-panel h2 {
    font-size: var(--fs-18);
  }
  .settlement-error {
    color: var(--c-danger-ink);
  }
  .settlement-reference {
    margin: 0;
    font-size: var(--fs-13);
  }
  .settlement-reference div {
    display: flex;
    gap: var(--sp-3);
    justify-content: space-between;
    padding: var(--sp-1) 0;
  }
  .settlement-reference dd {
    margin: 0;
    text-align: right;
    overflow-wrap: anywhere;
  }
</style>
