<script setup lang="ts">
  const model = defineModel<string | number>()

  withDefaults(defineProps<{ invalid?: boolean }>(), { invalid: false })
</script>

<template>
  <input v-model="model" class="input" :aria-invalid="invalid || undefined" />
</template>
