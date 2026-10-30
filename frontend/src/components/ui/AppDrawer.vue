<script setup lang="ts">
  import { onBeforeUnmount, ref, useId, watch } from 'vue'
  import AppIcon from './AppIcon.vue'

  const props = defineProps<{
    modelValue: boolean
    title?: string
    subtitle?: string
    ariaLabel?: string
  }>()

  const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

  const panel = ref<HTMLElement | null>(null)
  const titleId = useId()
  let restoreTo: HTMLElement | null = null

  function close() {
    emit('update:modelValue', false)
  }

  function onKeydown(event: KeyboardEvent) {
    if (event.key === 'Escape') close()
  }

  watch(
    () => props.modelValue,
    (open) => {
      if (open) {
        restoreTo = document.activeElement as HTMLElement | null
        document.addEventListener('keydown', onKeydown)
        requestAnimationFrame(() => panel.value?.focus())
      } else {
        document.removeEventListener('keydown', onKeydown)
        restoreTo?.focus?.()
      }
    },
    { immediate: true },
  )

  onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div class="scrim" :class="{ 'is-open': modelValue }" aria-hidden="true" @click="close" />
    <div
      ref="panel"
      class="drawer"
      :class="{ 'is-open': modelValue }"
      role="dialog"
      tabindex="-1"
      aria-modal="true"
      :aria-hidden="!modelValue"
      :aria-labelledby="title ? titleId : undefined"
      :aria-label="ariaLabel"
    >
      <div class="drawer__head">
        <div>
          <slot name="eyebrow" />
          <h2 v-if="title" :id="titleId" class="drawer__title">{{ title }}</h2>
          <p v-if="subtitle" class="muted" style="font-size: var(--fs-13); margin-top: 2px">{{ subtitle }}</p>
          <slot name="head" />
        </div>
        <button class="iconbtn" type="button" aria-label="关闭" @click="close">
          <AppIcon name="close" />
        </button>
      </div>
      <div class="drawer__body"><slot /></div>
      <div v-if="$slots.footer" class="drawer__foot"><slot name="footer" /></div>
    </div>
  </Teleport>
</template>
