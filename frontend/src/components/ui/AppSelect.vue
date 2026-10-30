<script setup lang="ts">
  const model = defineModel<string | number>()

  withDefaults(defineProps<{ invalid?: boolean }>(), { invalid: false })
</script>

<template>
  <select v-model="model" class="select" :aria-invalid="invalid || undefined">
    <slot />
  </select>
</template>
