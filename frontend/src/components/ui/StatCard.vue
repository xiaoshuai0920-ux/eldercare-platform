<script setup lang="ts">
  import { computed } from 'vue'
  import type { KpiItem } from '@/types'

  const props = defineProps<KpiItem>()

  const classes = computed(() => [
    'card',
    'stat',
    props.tone === 'action' ? 'stat--action' : '',
    props.tone === 'danger' ? 'stat--danger' : '',
  ])
</script>

<template>
  <div :class="classes">
    <span class="stat__label">{{ label }}</span>
    <span class="stat__value num">{{ value }}</span>
    <span v-if="hint" class="stat__hint">{{ hint }}</span>
  </div>
</template>
