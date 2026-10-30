<script setup lang="ts">
  import type { Tone } from '@/types'

  withDefaults(defineProps<{ tone?: Tone; plain?: boolean }>(), { tone: 'neutral', plain: false })
</script>

<template>
  <span class="tag" :class="[`tag--${tone}`, { 'tag--plain': plain }]"><slot /></span>
</template>
