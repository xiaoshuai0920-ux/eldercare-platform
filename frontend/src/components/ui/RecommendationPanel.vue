<script setup lang="ts">
  import { computed, ref, watch } from 'vue'
  import { catalogApi } from '@/api/endpoints'
  import type { Area, CatalogItem, ElderRow } from '@/api/types'
  import AppButton from './AppButton.vue'
  import AppSelect from './AppSelect.vue'

  const props = defineProps<{ elders: ElderRow[]; areas: Area[]; items: CatalogItem[] }>()
  const emit = defineEmits<{ select: [item: CatalogItem, elderId: number, streetId: number] }>()
  const elderId = ref(0)
  const streetId = ref(0)
  const streets = computed(() => props.areas.filter((area) => area.level === 'STREET'))
  const rows = ref<{ id: number; name: string; reason: string }[]>([])
  const loading = ref(false)
  const searched = ref(false)
  const error = ref('')
  let queryId = 0

  watch([elderId, streetId], () => {
    queryId++
    rows.value = []
    searched.value = false
    error.value = ''
    loading.value = false
  })
  async function search() {
    if (!elderId.value || !streetId.value) return
    const current = ++queryId
    loading.value = true
    error.value = ''
    searched.value = false
    try {
      const result = await catalogApi.recommendations(elderId.value, streetId.value)
      if (current === queryId) {
        rows.value = result
        searched.value = true
      }
    } catch {
      if (current === queryId) error.value = '推荐加载失败，请检查服务对象权限或稍后重试。'
    } finally {
      if (current === queryId) loading.value = false
    }
  }
</script>

<template>
  <section class="card card--pad recommendation-panel" aria-labelledby="recommend-title">
    <h2 id="recommend-title">适合您的服务</h2>
    <p class="muted">选择服务对象和所在街道，查看服务覆盖及偏好匹配理由。</p>
    <div class="recommendation-filters">
      <label
        >服务对象<AppSelect v-model.number="elderId"
          ><option :value="0">请选择服务对象</option>
          <option v-for="elder in elders" :key="elder.id" :value="elder.id">
            {{ elder.fullName }}
          </option></AppSelect
        ></label
      >
      <label
        >服务街道<AppSelect v-model.number="streetId"
          ><option :value="0">请选择街道</option>
          <option v-for="area in streets" :key="area.id" :value="area.id">
            {{ area.full_name }}
          </option></AppSelect
        ></label
      >
      <AppButton :disabled="!elderId || !streetId" :loading="loading" @click="search">查看推荐</AppButton>
    </div>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-else-if="searched && !rows.length" class="muted">所选街道暂无可推荐的服务。</p>
    <ul v-if="rows.length" class="recommendation-list">
      <li v-for="row in rows" :key="row.id">
        <span
          ><strong>{{ row.name }}</strong
          ><small>{{ row.reason }}</small></span
        >
        <AppButton
          v-if="items.find((item) => item.id === row.id)"
          variant="secondary"
          compact
          @click="
            emit(
              'select',
              items.find((item) => item.id === row.id)!,
              elderId,
              streetId,
            )
          "
          >预约服务</AppButton
        >
      </li>
    </ul>
  </section>
</template>

<style scoped>
  .recommendation-panel {
    margin-top: var(--sp-6);
  }
  .recommendation-panel h2 {
    margin: 0 0 var(--sp-2);
    font-size: var(--fs-20);
  }
  .recommendation-filters {
    display: flex;
    flex-wrap: wrap;
    gap: var(--sp-3);
    align-items: end;
    margin-top: var(--sp-4);
  }
  .recommendation-filters label {
    display: grid;
    gap: var(--sp-1);
    flex: 1 1 180px;
  }
  .recommendation-list {
    list-style: none;
    margin: var(--sp-4) 0 0;
    padding: 0;
  }
  .recommendation-list li {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: var(--sp-3);
    padding: var(--sp-3) 0;
    border-top: 1px solid var(--c-line);
  }
  .recommendation-list small {
    display: block;
    color: var(--c-ink-muted);
  }
</style>
