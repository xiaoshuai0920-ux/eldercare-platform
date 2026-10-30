<script setup lang="ts">
  import { onBeforeUnmount, ref, useId, watch } from 'vue'
  import AppIcon from './AppIcon.vue'

  const props = withDefaults(
    defineProps<{
      modelValue: boolean
      title?: string
      subtitle?: string
      wide?: boolean
      ariaLabel?: string
    }>(),
    { wide: false },
  )

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
    <div
      class="dialog-wrap"
      :class="{ 'is-open': modelValue }"
      role="dialog"
      aria-modal="true"
      :aria-hidden="!modelValue"
      :aria-labelledby="title ? titleId : undefined"
      :aria-label="ariaLabel"
      @click.self="close"
    >
      <div ref="panel" class="dialog" :class="{ 'dialog--wide': wide }" tabindex="-1" style="outline: none">
        <div class="dialog__head">
          <div>
            <slot name="eyebrow" />
            <h2 v-if="title" :id="titleId" class="drawer__title">{{ title }}</h2>
            <p v-if="subtitle" class="muted" style="font-size: var(--fs-13); margin-top: 2px">
              {{ subtitle }}
            </p>
            <slot name="head" />
          </div>
          <div style="display: flex; align-items: center; gap: var(--sp-5)">
            <slot name="head-end" />
            <button class="iconbtn" type="button" aria-label="关闭" @click="close">
              <AppIcon name="close" />
            </button>
          </div>
        </div>
        <div class="dialog__body"><slot /></div>
        <div v-if="$slots.footer" class="dialog__foot"><slot name="footer" /></div>
      </div>
    </div>
  </Teleport>
</template>
