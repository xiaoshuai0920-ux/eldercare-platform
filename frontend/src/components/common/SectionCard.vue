<template>
  <section class="section-card surface">
    <header class="head">
      <div>
        <h2>{{ title }}</h2>
        <p v-if="description" class="muted">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="actions"><slot name="actions" /></div>
    </header>
    <div class="body"><slot /></div>
  </section>
</template>
<script setup lang="ts">
defineProps<{ title: string; description?: string }>()
</script>
<style scoped>
.section-card { padding: var(--space-6); }
.head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-4); flex-wrap: wrap; }
.head h2 { margin: 0; font-size: var(--text-lg); }
.head p { margin: var(--space-1) 0 0; font-size: var(--text-sm); }
.actions { display: flex; align-items: center; gap: var(--space-3); }
.body { margin-top: var(--space-4); }
</style>
