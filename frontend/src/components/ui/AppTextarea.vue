<script setup lang="ts">
  const model = defineModel<string>()
</script>

<template>
  <textarea v-model="model" class="textarea" />
</template>
