<script setup lang="ts">
  import { computed } from 'vue'
  import { ICONS, type IconName } from './icons'

  const props = withDefaults(defineProps<{ name: IconName; size?: string | number }>(), {
    size: undefined,
  })

  const markup = computed(() => ICONS[props.name] ?? '')
  const style = computed(() =>
    props.size
      ? {
          width: typeof props.size === 'number' ? `${props.size}px` : props.size,
          height: typeof props.size === 'number' ? `${props.size}px` : props.size,
        }
      : undefined,
  )
</script>

<template>
  <svg class="ico" viewBox="0 0 24 24" aria-hidden="true" :style="style" v-html="markup" />
</template>
