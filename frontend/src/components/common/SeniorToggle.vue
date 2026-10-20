<template>
  <button type="button" class="senior-toggle" :aria-pressed="senior" @click="toggle">
    {{ senior ? '标准模式' : '适老模式' }}
  </button>
</template>
<script setup lang="ts">
import { useSeniorMode } from '../../composables/useSeniorMode'

const { senior, toggle } = useSeniorMode()
</script>
<style scoped>
.senior-toggle {
  border: 1px solid var(--color-line-strong);
  background: var(--color-surface);
  color: var(--color-primary);
  border-radius: var(--radius-pill);
  padding: 6px 16px;
  font-size: var(--text-sm);
  font-weight: 650;
  cursor: pointer;
}

.senior-toggle:hover {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}
</style>
