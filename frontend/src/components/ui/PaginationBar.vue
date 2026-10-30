<script setup lang="ts">
  import { computed } from 'vue'

  const props = defineProps<{ page: number; total: number; size: number; disabled?: boolean }>()
  const emit = defineEmits<{ change: [page: number] }>()

  const pages = computed(() => Math.max(1, Math.ceil(props.total / Math.max(1, props.size))))

  /** 页码序列：页数少时全列，多时保留首尾与当前页邻域，中间用省略号 */
  const items = computed<(number | 'gap')[]>(() => {
    const last = pages.value
    const current = Math.min(Math.max(1, props.page), last)
    if (last <= 7) return Array.from({ length: last }, (_, index) => index + 1)
    const kept = new Set<number>([1, last, current, current - 1, current + 1])
    const sorted = [...kept].filter((value) => value >= 1 && value <= last).sort((a, b) => a - b)
    const result: (number | 'gap')[] = []
    sorted.forEach((value, index) => {
      if (index > 0 && value - sorted[index - 1] > 1) result.push('gap')
      result.push(value)
    })
    return result
  })

  function go(target: number): void {
    if (props.disabled || target < 1 || target > pages.value || target === props.page) return
    emit('change', target)
  }
</script>

<template>
  <nav v-if="total > 0" class="pager" aria-label="分页">
    <p class="pager__info">
      共 <span class="num">{{ total }}</span> 条 · 第 <span class="num">{{ page }}</span> /
      <span class="num">{{ pages }}</span> 页
    </p>
    <div class="pager__btns">
      <button type="button" class="pager__btn" :disabled="disabled || page <= 1" @click="go(page - 1)">
        上一页
      </button>
      <template v-for="(item, index) in items" :key="`${item}-${index}`">
        <span v-if="item === 'gap'" class="pager__gap" aria-hidden="true">…</span>
        <button
          v-else
          type="button"
          class="pager__btn"
          :class="{ 'pager__btn--on': item === page }"
          :aria-current="item === page ? 'page' : undefined"
          :disabled="disabled"
          @click="go(item)"
        >
          {{ item }}
        </button>
      </template>
      <button type="button" class="pager__btn" :disabled="disabled || page >= pages" @click="go(page + 1)">
        下一页
      </button>
    </div>
  </nav>
</template>
