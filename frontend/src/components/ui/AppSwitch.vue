<script setup lang="ts">
  import { computed } from 'vue'

  const props = withDefaults(
    defineProps<{
      modelValue: boolean
      label?: string
      labelOn?: string
      labelOff?: string
      size?: 'md' | 'lg'
      variant?: 'default' | 'onDark'
    }>(),
    { size: 'md', variant: 'default' },
  )

  const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

  const text = computed(() => {
    if (props.modelValue) return props.labelOn ?? props.label ?? ''
    return props.labelOff ?? props.label ?? ''
  })
</script>

<template>
  <button
    type="button"
    class="switch"
    :class="[{ 'switch--lg': size === 'lg' }, { 'switch--on-dark': variant === 'onDark' }]"
    :aria-pressed="modelValue"
    @click="emit('update:modelValue', !modelValue)"
  >
    <span class="switch__track" aria-hidden="true" />
    <span class="switch__label"
      ><slot :on="modelValue">{{ text }}</slot></span
    >
  </button>
</template>
