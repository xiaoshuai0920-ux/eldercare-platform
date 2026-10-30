<script setup lang="ts">
  withDefaults(defineProps<{ rows?: number }>(), { rows: 3 })
</script>

<template>
  <div class="sk-block" role="status" aria-live="polite">
    <span class="sk sk--title" />
    <div v-for="row in rows" :key="row" class="sk-row">
      <span class="sk" /><span class="sk" /><span class="sk" />
    </div>
    <span class="visually-hidden">正在加载</span>
  </div>
</template>
