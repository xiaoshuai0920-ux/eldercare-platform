<script setup lang="ts">
  import { computed } from 'vue'
  import { RouterLink } from 'vue-router'
  import type { RouteLocationRaw } from 'vue-router'

  type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'outline-danger'

  const props = withDefaults(
    defineProps<{
      variant?: Variant
      size?: 'md' | 'lg'
      block?: boolean
      compact?: boolean
      loading?: boolean
      disabled?: boolean
      /** 渲染为 router-link */
      to?: RouteLocationRaw
      /** 渲染为普通链接 */
      href?: string
      type?: 'button' | 'submit' | 'reset'
    }>(),
    { variant: 'primary', size: 'md', type: 'button' },
  )

  const tag = computed(() => (props.to ? RouterLink : props.href ? 'a' : 'button'))
  const classes = computed(() => [
    'btn',
    `btn--${props.variant}`,
    {
      'btn--lg': props.size === 'lg',
      'btn--block': props.block,
      'btn--compact': props.compact,
      'btn--loading': props.loading,
    },
  ])
  const isNativeButton = computed(() => !props.to && !props.href)
</script>

<template>
  <component
    :is="tag"
    :class="classes"
    :to="to"
    :href="href"
    :type="isNativeButton ? type : undefined"
    :disabled="isNativeButton ? disabled || loading : undefined"
    :aria-disabled="disabled || loading ? 'true' : undefined"
  >
    <slot />
  </component>
</template>
