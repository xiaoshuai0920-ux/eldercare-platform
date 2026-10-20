<template>
  <header class="page-header">
    <div>
      <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
      <h1 class="page-title">{{ title }}</h1>
      <p v-if="description" class="muted description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="actions"><slot name="actions" /></div>
  </header>
</template>
<script setup lang="ts">
defineProps<{ title: string; eyebrow?: string; description?: string }>()
</script>
<style scoped>
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-4); flex-wrap: wrap; }
.description { margin: var(--space-2) 0 0; }
.actions { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
</style>
