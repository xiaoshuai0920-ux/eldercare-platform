<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue'
  import { useRouter } from 'vue-router'
  import PageHeader from '@/components/ui/PageHeader.vue'
  import AppButton from '@/components/ui/AppButton.vue'
  import AppField from '@/components/ui/AppField.vue'
  import AppInput from '@/components/ui/AppInput.vue'
  import AppSelect from '@/components/ui/AppSelect.vue'
  import AppSegmented from '@/components/ui/AppSegmented.vue'
  import AppDialog from '@/components/ui/AppDialog.vue'
  import AppIcon from '@/components/ui/AppIcon.vue'
  import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
  import ErrorBanner from '@/components/ui/ErrorBanner.vue'
  import EmptyState from '@/components/ui/EmptyState.vue'
  import RecommendationPanel from '@/components/ui/RecommendationPanel.vue'
  import { useCatalogStore } from '@/stores/catalog'
  import { useFamilyStore } from '@/stores/family'
  import { useUiStore } from '@/stores/ui'
  import { userApi } from '@/api/endpoints'
  import { ApiError } from '@/api/client'
  import { addDays, initial, isPast, money, todayIso, toIsoLocal } from '@/utils/format'
  import type { CatalogItem } from '@/api/types'

  const router = useRouter()
  const catalog = useCatalogStore()
  const family = useFamilyStore()
  const ui = useUiStore()

  const category = ref('ALL')
  const keyword = ref('')
  const filtered = computed(() => catalog.filter(category.value, keyword.value))

  const bookingOpen = ref(false)
  const selected = ref<CatalogItem | null>(null)
  const step = ref(1)
  const submitting = ref(false)
  const submitError = ref('')
  const areas = ref<{ id: number; full_name: string }[]>([])

  const form = reactive({
    elderId: 0,
    streetId: 0,
    address: '',
    // 默认明天上午，避免默认值早于当前时间被后端拒绝
    date: addDays(todayIso(), 1),
    time: '09:00',
    duration: 60,
  })

  /** 日期选择器的最小可选值：不允许选过去 */
  const minDate = todayIso()

  const amount = computed(() => {
    const item = selected.value
    if (!item) return 0
    return item.pricing_model === 'HOURLY' ? (item.unit_price * form.duration) / 60 : item.unit_price
  })

  const durationOptions = computed(() => {
    const item = selected.value
    if (!item) return [60]
    const min = item.min_minutes ?? 60
    const max = item.max_minutes ?? min + 60
    const list: number[] = []
    for (let value = min; value <= max; value += 30) list.push(value)
    return list
  })

  onMounted(() => {
    catalog.load()
    family.load()
  })

  async function openBooking(item: CatalogItem) {
    selected.value = item
    step.value = 1
    submitError.value = ''
    form.duration = item.min_minutes ?? 60
    form.elderId = family.elders[0]?.id ?? 0
    try {
      areas.value = await catalog.itemAreas(item.id)
      form.streetId = areas.value[0]?.id ?? 0
    } catch (reason) {
      submitError.value = reason instanceof ApiError ? reason.message : '服务范围加载失败'
      areas.value = []
    }
    bookingOpen.value = true
  }

  async function openRecommendedBooking(item: CatalogItem, elderId: number, streetId: number) {
    await openBooking(item)
    form.elderId = elderId
    if (areas.value.some((area) => area.id === streetId)) form.streetId = streetId
  }

  function next() {
    if (step.value === 1 && (!form.elderId || !form.streetId || !form.address.trim())) {
      submitError.value = '请选择服务对象、街道并填写详细地址'
      return
    }
    if (step.value === 2 && isPast(form.date, form.time)) {
      submitError.value = '预约时间不能早于当前时间，请重新选择日期与时段'
      return
    }
    submitError.value = ''
    if (step.value < 3) step.value += 1
  }

  async function submit() {
    if (!selected.value) return
    submitting.value = true
    submitError.value = ''
    try {
      await userApi.createOrder({
        elderId: form.elderId,
        itemId: selected.value.id,
        streetId: form.streetId,
        address: form.address.trim(),
        startAt: toIsoLocal(form.date, form.time),
        durationMinutes: form.duration,
      })
      bookingOpen.value = false
      ui.notify('预约已提交，等待管理员派单')
      await router.push('/user/orders')
    } catch (reason) {
      submitError.value = reason instanceof ApiError ? reason.message : '提交失败，请稍后重试'
    } finally {
      submitting.value = false
    }
  }
</script>

<template>
  <PageHeader
    eyebrow="服务预约"
    title="为日常生活，安排一份安心。"
    description="选择服务与服务对象，提交后由管理员人工派单。价格与时段以提交时后端校验为准。"
  >
    <template #actions>
      <AppButton variant="secondary" to="/user/orders">查看我的订单</AppButton>
    </template>
  </PageHeader>

  <RecommendationPanel
    :elders="family.elders"
    :areas="catalog.areas"
    :items="catalog.items"
    @select="openRecommendedBooking"
  />

  <section class="card" style="margin-top: var(--sp-6)" aria-label="筛选与推荐">
    <div class="toolbar" style="border-bottom: 0">
      <AppSegmented v-model="category" :options="catalog.categories" aria-label="服务分类" />
      <div class="input-affix" style="flex: 1 1 260px">
        <AppIcon name="search" size="18px" />
        <label class="visually-hidden" for="svc-search">搜索服务名称</label>
        <AppInput id="svc-search" v-model="keyword" type="search" placeholder="搜索服务名称，如「陪诊」" />
      </div>
      <span class="chip">服务对象 {{ family.elders.length }} 位</span>
    </div>
  </section>

  <p class="muted" style="margin-top: var(--sp-5); font-size: var(--fs-13)">
    共 <span class="num">{{ filtered.length }}</span> 个服务项目 · 金额以提交时后端校验为准
  </p>

  <ErrorBanner
    v-if="catalog.error"
    :title="catalog.error"
    description="服务目录加载失败，请稍后重试。"
    style="margin-top: var(--sp-4)"
  >
    <template #action>
      <AppButton variant="secondary" compact style="margin-left: auto" @click="catalog.load(true)"
        >重新加载</AppButton
      >
    </template>
  </ErrorBanner>

  <div v-if="catalog.loading" class="card card--pad" style="margin-top: var(--sp-4)">
    <SkeletonBlock :rows="4" />
  </div>
  <section v-else class="grid grid--3" style="margin-top: var(--sp-4)" aria-label="服务项目列表">
    <article v-for="item in filtered" :key="item.id" class="card card--hover svc-card">
      <p class="svc-card__tag">{{ catalog.categoryLabel(item.category) }}</p>
      <h2 class="svc-card__title">{{ item.name }}</h2>
      <p class="svc-card__desc">{{ item.description }}</p>
      <p class="muted" style="font-size: var(--fs-13)">{{ item.rule_text }}</p>
      <div class="svc-card__foot">
        <p class="price">
          <span class="num">{{ money(item.unit_price) }}</span>
          <span class="price__unit">{{ catalog.priceUnit(item) }}</span>
        </p>
        <AppButton @click="openBooking(item)">预约服务</AppButton>
      </div>
    </article>
    <EmptyState
      v-if="!filtered.length"
      title="当前没有符合条件的服务项目"
      description="换个分类或清空搜索词再试。"
    />
  </section>

  <AppDialog
    v-model="bookingOpen"
    wide
    :title="selected ? `预约${selected.name}` : '预约服务'"
    subtitle="按三步确认，提交后进入人工派单，不会立即扣费。"
  >
    <template #head-end>
      <div class="stepper" aria-label="预约进度">
        <div class="step" :class="{ 'step--active': step === 1, 'step--done': step > 1 }">
          <span class="step__dot">1</span>选对象
        </div>
        <span class="step__line" aria-hidden="true" />
        <div class="step" :class="{ 'step--active': step === 2, 'step--done': step > 2 }">
          <span class="step__dot">2</span>选时段
        </div>
        <span class="step__line" aria-hidden="true" />
        <div class="step" :class="{ 'step--active': step === 3 }">
          <span class="step__dot">3</span>确认金额
        </div>
      </div>
    </template>

    <div v-if="submitError" class="banner banner--error" role="alert">
      <AppIcon name="alert" />
      <div class="banner__body">
        <p class="banner__title">无法继续</p>
        <p class="banner__desc">{{ submitError }}</p>
      </div>
    </div>

    <div v-show="step === 1">
      <fieldset style="border: 0; padding: 0; margin: 0">
        <legend class="field__label" style="margin-bottom: var(--sp-3)">
          服务对象<span class="req" aria-hidden="true">*</span>
        </legend>
        <div class="grid grid--3" style="gap: var(--sp-3)">
          <button
            v-for="elder in family.elders"
            :key="elder.id"
            class="elder"
            type="button"
            :aria-pressed="form.elderId === elder.id"
            style="padding: var(--sp-4)"
            @click="form.elderId = elder.id"
          >
            <span class="elder__top">
              <span class="avatar" aria-hidden="true">{{ initial(elder.fullName) }}</span>
              <span>
                <span class="elder__name" style="font-size: var(--fs-16)">{{ elder.fullName }}</span>
                <span class="elder__meta" style="display: block"
                  >{{ family.ageOf(elder.birthDate) ?? '—' }} 岁</span
                >
              </span>
            </span>
          </button>
        </div>
        <p v-if="!family.elders.length" class="muted" style="font-size: var(--fs-13)">
          还没有可预约的服务对象档案。
        </p>
      </fieldset>

      <div class="grid grid--2" style="gap: var(--sp-4); margin-top: var(--sp-5)">
        <AppField label="服务街道" html-for="bk-street" required help="仅列出该服务项目覆盖的街道。">
          <AppSelect id="bk-street" v-model.number="form.streetId">
            <option v-for="area in areas" :key="area.id" :value="area.id">{{ area.full_name }}</option>
          </AppSelect>
        </AppField>
        <AppField
          label="详细地址"
          html-for="bk-address"
          required
          help="服务人员按此地址上门，请保留楼栋与门牌号。"
        >
          <AppInput
            id="bk-address"
            v-model="form.address"
            autocomplete="street-address"
            placeholder="例如：安宁区东湖街道梧桐里 3 号楼 502"
          />
        </AppField>
      </div>
    </div>

    <div v-show="step === 2">
      <div class="grid grid--3" style="gap: var(--sp-4)">
        <AppField label="服务日期" html-for="bk-date" required>
          <AppInput id="bk-date" v-model="form.date" type="date" :min="minDate" />
        </AppField>
        <AppField label="开始时间" html-for="bk-time" required>
          <AppInput id="bk-time" v-model="form.time" type="time" />
        </AppField>
        <AppField label="服务时长" html-for="bk-duration" required>
          <AppSelect id="bk-duration" v-model.number="form.duration">
            <option v-for="minutes in durationOptions" :key="minutes" :value="minutes">
              {{ minutes }} 分钟
            </option>
          </AppSelect>
        </AppField>
      </div>
      <p class="muted" style="font-size: var(--fs-13); margin-top: var(--sp-4)">
        该项目需提前 {{ Math.round((selected?.advance_minutes ?? 0) / 60) }} 小时预约，可服务时段
        {{ selected?.available_from?.slice(0, 5) }}–{{ selected?.available_to?.slice(0, 5) }}。
      </p>
    </div>

    <div v-show="step === 3">
      <div
        class="card"
        style="padding: var(--sp-5); background: var(--c-surface-sunken); border-color: var(--c-line-soft)"
      >
        <div class="detail-row">
          <dt>服务项目</dt>
          <dd>{{ selected?.name }}</dd>
        </div>
        <div class="detail-row">
          <dt>服务对象</dt>
          <dd>{{ family.elders.find((elder) => elder.id === form.elderId)?.fullName ?? '—' }}</dd>
        </div>
        <div class="detail-row">
          <dt>上门地址</dt>
          <dd>{{ areas.find((area) => area.id === form.streetId)?.full_name }} · {{ form.address }}</dd>
        </div>
        <div class="detail-row">
          <dt>服务时段</dt>
          <dd class="num">{{ form.date }} {{ form.time }}（{{ form.duration }} 分钟）</dd>
        </div>
        <div class="detail-row">
          <dt>项目规则</dt>
          <dd>{{ selected?.rule_text }}</dd>
        </div>
      </div>
      <div class="row" style="justify-content: space-between; align-items: flex-end; margin-top: var(--sp-5)">
        <div>
          <p class="muted" style="font-size: var(--fs-13)">预计金额（提交时以后端校验为准）</p>
          <p class="price" style="font-size: var(--fs-30)">{{ money(amount) }}</p>
        </div>
        <span class="tag tag--action">提交后等待人工派单</span>
      </div>
    </div>

    <template #footer>
      <p class="hint">提交后可在「我的订单」查看派单进度。</p>
      <AppButton variant="ghost" :disabled="step === 1" @click="step -= 1">上一步</AppButton>
      <AppButton v-if="step < 3" @click="next">下一步</AppButton>
      <AppButton v-else :loading="submitting" @click="submit">提交预约</AppButton>
    </template>
  </AppDialog>
</template>
