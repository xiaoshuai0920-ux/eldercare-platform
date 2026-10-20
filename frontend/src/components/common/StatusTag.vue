<template>
  <span :class="['status-tag', `tone-${meta.tone}`]">{{ meta.label }}</span>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { statusMeta, type StatusMeta } from '../../constants/status'

const props = defineProps<{ value: string; dict: Record<string, StatusMeta> }>()
const meta = computed(() => statusMeta(props.dict, props.value))
</script>
<style scoped>
.status-tag { display: inline-flex; align-items: center; padding: 3px 10px; border-radius: var(--radius-pill); font-size: var(--text-xs); font-weight: 650; white-space: nowrap; }
.tone-neutral { background: #eef2f0; color: var(--color-ink-soft); }
.tone-progress { background: #e4f0fa; color: var(--color-info); }
.tone-action { background: #fbf0e2; color: var(--color-warning); }
.tone-success { background: var(--color-primary-soft); color: var(--color-success); }
.tone-danger { background: #fbeae7; color: var(--color-danger); }
</style>
