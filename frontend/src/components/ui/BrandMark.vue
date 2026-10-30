<script setup lang="ts">
  withDefaults(defineProps<{ light?: boolean; size?: number }>(), { light: false, size: 36 })
</script>

<template>
  <span
    class="brandmark"
    :class="{ 'brandmark--light': light }"
    :style="{ width: `${size}px`, height: `${size}px` }"
    aria-hidden="true"
  >
    <svg viewBox="0 0 48 48" fill="none">
      <path
        d="M7 23.5 24 9l17 14.5"
        stroke="currentColor"
        stroke-width="4"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <path d="M11 24v12a5 5 0 0 0 5 5h5" stroke="currentColor" stroke-width="4" stroke-linecap="round" />
      <path d="M37 24v12a5 5 0 0 1-5 5h-5" stroke="currentColor" stroke-width="4" stroke-linecap="round" />
      <circle cx="24" cy="25" r="4" fill="var(--c-warm)" />
      <path
        d="M18 37c1.8-4 3.8-6 6-6s4.2 2 6 6"
        stroke="var(--c-warm)"
        stroke-width="3.5"
        stroke-linecap="round"
      />
    </svg>
  </span>
</template>
