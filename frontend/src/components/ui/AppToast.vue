<script setup lang="ts">
  import { useUiStore } from '@/stores/ui'

  const ui = useUiStore()
</script>

<template>
  <div class="toast-stack" aria-live="polite">
    <TransitionGroup name="toast">
      <div
        v-for="toast in ui.toasts"
        :key="toast.id"
        class="toast"
        :class="`toast--${toast.tone}`"
        role="status"
      >
        {{ toast.message }}
      </div>
    </TransitionGroup>
  </div>
</template>
