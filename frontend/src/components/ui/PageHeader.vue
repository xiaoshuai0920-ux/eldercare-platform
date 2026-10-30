<script setup lang="ts">
  defineProps<{ eyebrow?: string; title: string; description?: string }>()
</script>

<template>
  <div class="page-head">
    <div>
      <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
      <h1 class="page-title" style="margin-top: var(--sp-2)">{{ title }}</h1>
      <p v-if="description" class="page-head__desc">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-head__actions">
      <slot name="actions" />
    </div>
  </div>
</template>
