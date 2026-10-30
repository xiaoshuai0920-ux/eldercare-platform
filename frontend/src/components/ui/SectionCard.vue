<script setup lang="ts">
  defineProps<{ title?: string; description?: string }>()
</script>

<template>
  <section class="card card--pad">
    <div v-if="title || $slots.actions" class="section__head">
      <div>
        <h2 class="section-title">{{ title }}</h2>
        <p v-if="description" class="section__desc">{{ description }}</p>
      </div>
      <slot name="actions" />
    </div>
    <slot />
  </section>
</template>
