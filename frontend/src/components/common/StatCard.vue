<template>
  <div class="stat-card surface">
    <span class="label">{{ label }}</span>
    <strong class="value">{{ value }}</strong>
    <span v-if="hint" class="hint">{{ hint }}</span>
  </div>
</template>
<script setup lang="ts">
defineProps<{ label: string; value: string | number; hint?: string }>()
</script>
<style scoped>
.stat-card { padding: var(--space-5); display: grid; gap: var(--space-2); min-height: 116px; align-content: center; }
.label { color: var(--color-muted); font-size: var(--text-sm); }
.value { font-size: var(--text-3xl); line-height: 1.1; font-weight: 650; }
.hint { color: var(--color-muted); font-size: var(--text-xs); }
</style>
