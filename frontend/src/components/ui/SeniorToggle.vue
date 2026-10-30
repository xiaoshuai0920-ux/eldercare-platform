<script setup lang="ts">
  import AppSwitch from './AppSwitch.vue'
  import { useUiStore } from '@/stores/ui'

  withDefaults(defineProps<{ variant?: 'default' | 'onDark' }>(), { variant: 'default' })

  const ui = useUiStore()
</script>

<template>
  <AppSwitch
    :model-value="ui.seniorMode"
    :variant="variant"
    label-off="适老模式"
    label-on="适老模式 已开"
    @update:model-value="ui.set"
  />
</template>
