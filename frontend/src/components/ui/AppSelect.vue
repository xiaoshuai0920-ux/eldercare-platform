<script setup lang="ts">
  const model = defineModel<string | number>()

  withDefaults(defineProps<{ invalid?: boolean; disabled?: boolean }>(), {
    invalid: false,
    disabled: false,
  })
</script>

<template>
  <select
    v-model="model"
    class="select"
    :disabled="disabled || undefined"
    :aria-invalid="invalid || undefined"
  >
    <slot />
  </select>
</template>
