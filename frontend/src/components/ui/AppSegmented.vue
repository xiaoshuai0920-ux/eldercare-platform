<script setup lang="ts">
  export interface SegmentOption {
    value: string | number | boolean
    label: string
    disabled?: boolean
  }

  defineProps<{ modelValue: string | number | boolean; options: SegmentOption[]; ariaLabel?: string }>()
  const emit = defineEmits<{ 'update:modelValue': [value: string | number | boolean] }>()
</script>

<template>
  <div class="segmented" role="group" :aria-label="ariaLabel">
    <button
      v-for="option in options"
      :key="String(option.value)"
      type="button"
      :aria-pressed="modelValue === option.value"
      :disabled="option.disabled"
      @click="emit('update:modelValue', option.value)"
    >
      {{ option.label }}
    </button>
  </div>
</template>
