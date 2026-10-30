<script setup lang="ts">
  import AppIcon from './AppIcon.vue'
  import type { IconName } from './icons'

  withDefaults(defineProps<{ title: string; description?: string; icon?: IconName }>(), {
    icon: 'inbox',
  })
</script>

<template>
  <div class="empty">
    <span class="empty__icon" aria-hidden="true"><AppIcon :name="icon" /></span>
    <p class="empty__title">{{ title }}</p>
    <p v-if="description" class="empty__desc">{{ description }}</p>
    <slot />
  </div>
</template>
