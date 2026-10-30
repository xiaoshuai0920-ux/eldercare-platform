<script setup lang="ts">
  import AppIcon from './AppIcon.vue'

  withDefaults(
    defineProps<{ title: string; description?: string; tone?: 'error' | 'warm' | 'info' | 'success' }>(),
    {
      tone: 'error',
    },
  )

  defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="banner" :class="`banner--${tone}`" :role="tone === 'error' ? 'alert' : 'note'">
    <AppIcon :name="tone === 'error' || tone === 'warm' ? 'alert' : 'info'" />
    <div class="banner__body">
      <p class="banner__title">{{ title }}</p>
      <p v-if="description" class="banner__desc">{{ description }}</p>
    </div>
    <slot name="action" />
  </div>
</template>
