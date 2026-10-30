<script setup lang="ts">
  import { computed } from 'vue'
  import AppTag from './AppTag.vue'
  import {
    ORDER_STATUS,
    ASSIGNMENT_STATUS,
    ACCOUNT_STATUS,
    LEAVE_STATUS,
    VERIFY_STATUS,
    AUTH_STATUS,
    PRIORITY,
    statusMeta,
  } from '@/constants/status'
  import type { StatusMeta } from '@/types'

  const props = withDefaults(
    defineProps<{
      /** 状态码，如 WAITING_SERVICE */
      value: string
      /** 使用哪本字典，默认订单状态 */
      dict?: 'order' | 'assignment' | 'account' | 'leave' | 'verify' | 'auth' | 'priority'
    }>(),
    { dict: 'order' },
  )

  const DICTS: Record<string, Record<string, StatusMeta>> = {
    order: ORDER_STATUS,
    assignment: ASSIGNMENT_STATUS,
    account: ACCOUNT_STATUS,
    leave: LEAVE_STATUS,
    verify: VERIFY_STATUS,
    auth: AUTH_STATUS,
    priority: PRIORITY,
  }

  const meta = computed(() => statusMeta(DICTS[props.dict] ?? ORDER_STATUS, props.value))
</script>

<template>
  <AppTag :tone="meta.tone">{{ meta.label }}</AppTag>
</template>
