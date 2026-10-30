<script setup lang="ts">
  import { computed, useId } from 'vue'
  import AppIcon from './AppIcon.vue'

  const props = defineProps<{
    /** 控件 id，用于 label 的 for 关联 */
    htmlFor?: string
    label?: string
    required?: boolean
    help?: string
    error?: string
  }>()

  const uid = useId()
  const helpId = computed(() => `${props.htmlFor ?? uid}-help`)
  const errorId = computed(() => `${props.htmlFor ?? uid}-error`)
  /** 传给控件，保证错误/帮助文案被读屏关联 */
  const describedby = computed(() => {
    const ids = []
    if (props.help) ids.push(helpId.value)
    if (props.error) ids.push(errorId.value)
    return ids.length ? ids.join(' ') : undefined
  })
</script>

<template>
  <div class="field" :class="{ 'field--invalid': !!error }">
    <label v-if="label" class="field__label" :for="htmlFor">
      {{ label }}<span v-if="required" class="req" aria-hidden="true">*</span>
    </label>
    <slot :describedby="describedby" :invalid="!!error" :id="htmlFor" />
    <p v-if="help" class="field__help" :id="helpId">{{ help }}</p>
    <p v-if="error" class="field__error" :id="errorId">
      <AppIcon name="alert" size="14px" />
      {{ error }}
    </p>
  </div>
</template>
