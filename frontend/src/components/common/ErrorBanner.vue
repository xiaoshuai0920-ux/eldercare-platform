<template>
  <p v-if="message" class="error-banner" role="alert">{{ message }}</p>
</template>
<script setup lang="ts">
defineProps<{ message?: string }>()
</script>
<style scoped>
.error-banner { margin: 0 0 var(--space-4); padding: var(--space-3) var(--space-4); border-radius: var(--radius-md); background: #fbeae7; color: var(--color-danger); font-size: var(--text-sm); }
</style>
