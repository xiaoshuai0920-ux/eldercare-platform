<script setup lang="ts">
  import { ref } from 'vue'

  export interface TabItem {
    id: string
    label: string
    count?: number
  }

  const props = defineProps<{ modelValue: string; tabs: TabItem[]; ariaLabel?: string; padded?: boolean }>()
  const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

  const buttons = ref<HTMLButtonElement[]>([])

  function select(id: string) {
    emit('update:modelValue', id)
  }

  function onKeydown(event: KeyboardEvent, index: number) {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return
    event.preventDefault()
    const next = (index + (event.key === 'ArrowRight' ? 1 : props.tabs.length - 1)) % props.tabs.length
    select(props.tabs[next].id)
    buttons.value[next]?.focus()
  }
</script>

<template>
  <div
    class="tabs"
    role="tablist"
    :aria-label="ariaLabel"
    :style="padded ? { paddingInline: 'var(--sp-4)' } : undefined"
  >
    <button
      v-for="(tab, index) in tabs"
      :id="`tab-${tab.id}`"
      :key="tab.id"
      ref="buttons"
      class="tab"
      type="button"
      role="tab"
      :aria-controls="`panel-${tab.id}`"
      :aria-selected="modelValue === tab.id"
      :tabindex="modelValue === tab.id ? 0 : -1"
      @click="select(tab.id)"
      @keydown="onKeydown($event, index)"
    >
      {{ tab.label }}<span v-if="tab.count !== undefined" class="tab__count num">{{ tab.count }}</span>
    </button>
  </div>
</template>
