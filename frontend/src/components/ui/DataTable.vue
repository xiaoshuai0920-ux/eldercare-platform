<script setup lang="ts" generic="T extends object">
  import EmptyState from './EmptyState.vue'
  import SkeletonBlock from './SkeletonBlock.vue'

  export interface TableColumn {
    key: string
    label: string
    align?: 'left' | 'right'
    width?: string
    className?: string
    headerClass?: string
  }

  const props = withDefaults(
    defineProps<{
      columns: TableColumn[]
      rows: T[]
      rowKey?: string
      caption?: string
      emptyTitle?: string
      emptyDesc?: string
      /** 传 none 让页面整体滚动 */
      maxHeight?: string
      loading?: boolean
    }>(),
    { rowKey: 'id', emptyTitle: '暂无数据', maxHeight: '620px' },
  )

  function keyOf(row: T, index: number): string {
    const record = row as Record<string, unknown>
    return String(record[props.rowKey] ?? index)
  }

  function cellValue(row: T, key: string): unknown {
    return (row as Record<string, unknown>)[key]
  }
</script>

<template>
  <div v-if="loading" class="card" style="box-shadow: none">
    <SkeletonBlock :rows="4" />
  </div>

  <div v-else-if="!rows.length" style="border: 0; background: transparent; box-shadow: none">
    <slot name="empty">
      <EmptyState :title="emptyTitle" :description="emptyDesc" />
    </slot>
  </div>

  <div v-else class="table-wrap" :style="{ maxHeight }">
    <table class="table">
      <caption class="hidden">
        {{
          caption
        }}
      </caption>
      <thead>
        <tr>
          <th
            v-for="col in columns"
            :key="col.key"
            scope="col"
            :class="[col.headerClass, col.align === 'right' ? 'cell-num' : '']"
            :style="col.width ? { width: col.width } : undefined"
          >
            {{ col.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in rows" :key="keyOf(row, index)">
          <td
            v-for="col in columns"
            :key="col.key"
            :class="[col.className, col.align === 'right' ? 'cell-num num' : '']"
            :data-label="col.label"
          >
            <slot :name="`cell-${col.key}`" :row="row" :value="cellValue(row, col.key)">{{
              cellValue(row, col.key)
            }}</slot>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
