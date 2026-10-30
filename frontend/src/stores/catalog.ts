import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { catalogApi } from '@/api/endpoints'
import { ApiError } from '@/api/client'
import type { Area, CatalogItem } from '@/api/types'

const CATEGORY_LABEL: Record<string, string> = { MEAL: '助餐', CLEANING: '保洁', ESCORT: '陪诊' }

export const useCatalogStore = defineStore('catalog', () => {
  const items = ref<CatalogItem[]>([])
  const areas = ref<Area[]>([])
  const loading = ref(false)
  const error = ref('')
  const loaded = ref(false)
  const areaCache = new Map<number, { id: number; full_name: string }[]>()

  const categories = computed(() => [
    { value: 'ALL', label: '全部' },
    ...Array.from(new Set(items.value.map((item) => item.category))).map((category) => ({
      value: category,
      label: CATEGORY_LABEL[category] ?? category,
    })),
  ])

  function categoryLabel(category: string): string {
    return CATEGORY_LABEL[category] ?? category
  }

  async function load(force = false) {
    if (loaded.value && !force) return
    loading.value = true
    error.value = ''
    try {
      const [list, areaList] = await Promise.all([catalogApi.items(), catalogApi.areas()])
      items.value = list
      areas.value = areaList
      loaded.value = true
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '服务项目加载失败'
    } finally {
      loading.value = false
    }
  }

  function filter(category: string, keyword: string): CatalogItem[] {
    const key = keyword.trim()
    return items.value.filter((item) => {
      const matchCategory = category === 'ALL' || item.category === category
      const matchKeyword = !key || item.name.includes(key) || item.description.includes(key)
      return matchCategory && matchKeyword
    })
  }

  /** 单个项目的可服务街道，按项目缓存 */
  async function itemAreas(itemId: number): Promise<{ id: number; full_name: string }[]> {
    const cached = areaCache.get(itemId)
    if (cached) return cached
    const list = await catalogApi.itemAreas(itemId)
    areaCache.set(itemId, list)
    return list
  }

  function priceUnit(item: CatalogItem): string {
    return item.pricing_model === 'HOURLY' ? '/ 小时' : '/ 次'
  }

  return {
    items,
    areas,
    loading,
    error,
    loaded,
    categories,
    categoryLabel,
    filter,
    itemAreas,
    priceUnit,
    load,
  }
})
