<template>
  <div class="empty-state">
    <p class="title">{{ title }}</p>
    <p v-if="description" class="muted description">{{ description }}</p>
    <slot />
  </div>
</template>
<script setup lang="ts">
defineProps<{ title: string; description?: string }>()
</script>
<style scoped>
.empty-state { padding: var(--space-8) var(--space-5); border: 1px dashed var(--color-line-strong); border-radius: var(--radius-lg); text-align: center; }
.title { margin: 0; font-weight: 650; }
.description { margin: var(--space-2) 0 0; font-size: var(--text-sm); }
</style>
