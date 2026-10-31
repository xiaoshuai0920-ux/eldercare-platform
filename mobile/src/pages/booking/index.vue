<script setup>
import { computed, ref, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppPageHeader from '../../components/AppPageHeader.vue'
import AppBottomNav from '../../components/AppBottomNav.vue'
import { errorMessage, platformApi, requireRole, showFailure, localDateTime, idempotencyKey } from '../../api/platform.js'

const loading = ref(true)
const submitting = ref(false)
const failure = ref('')
const step = ref(1)
const elders = ref([])
const streets = ref([])
const items = ref([])
const recommendations = ref([])
const elderIndex = ref(0)
const streetIndex = ref(0)
const itemIndex = ref(0)
const date = ref('')
const time = ref('09:00')
const address = ref('')
const contactPhone = ref('')
const duration = ref(60)
const selectedElder = computed(() => elders.value[elderIndex.value])
const selectedStreet = computed(() => streets.value[streetIndex.value])
const selectedItem = computed(() => items.value[itemIndex.value])
const minDuration = computed(() => Number(selectedItem.value?.min_minutes || 60))
const maxDuration = computed(() => Number(selectedItem.value?.max_minutes || 240))
const isHourly = computed(() => selectedItem.value?.pricing_model === 'HOURLY')
// 服务时长必须落在项目约定区间内，否则后端按参数错误拒绝，用户只看到一句笼统的 400。
function normalizedDuration() {
  const raw = Number(duration.value)
  if (!Number.isFinite(raw) || raw <= 0) return minDuration.value
  return Math.min(maxDuration.value, Math.max(minDuration.value, Math.round(raw)))
}
const price = computed(() => {
  const item = selectedItem.value
  if (!item) return 0
  return item.pricing_model === 'HOURLY' ? Number(item.unit_price) * duration.value / 60 : Number(item.unit_price)
})
const tomorrow = (() => {
  const value = new Date()
  value.setDate(value.getDate() + 1)
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
})()
// 同一份预约内容复用同一个幂等键：超时重试不会重复下单，改过内容才重新生成。
const submitKey = ref('')
watch([elderIndex, streetIndex, itemIndex, date, time, address, contactPhone, duration], () => { submitKey.value = '' })

async function load() {
  loading.value = true
  failure.value = ''
  try {
    if (!await requireRole(['USER'])) { loading.value = false; return }
    const [elderList, areaList] = await Promise.all([platformApi.elders(), platformApi.areas()])
    elders.value = elderList
    streets.value = areaList.filter((area) => area.level === 'STREET')
    if (!date.value) date.value = tomorrow
    await loadItems()
  } catch (error) { failure.value = errorMessage(error, '预约信息加载失败') }
  finally { loading.value = false }
}
async function loadItems() {
  if (!selectedStreet.value) { items.value = []; recommendations.value = []; return }
  const [serviceList, recommended] = await Promise.all([
    platformApi.catalog(selectedStreet.value.id),
    selectedElder.value ? platformApi.recommendations(selectedElder.value.id, selectedStreet.value.id) : Promise.resolve([]),
  ])
  items.value = serviceList
  recommendations.value = recommended
  itemIndex.value = Math.max(0, items.value.findIndex((item) => item.id === selectedItem.value?.id))
  if (selectedItem.value) duration.value = minDuration.value
}
async function chooseElder(event) {
  elderIndex.value = Number(event.detail.value)
  loading.value = true
  try { await loadItems() } catch (error) { showFailure(error) }
  finally { loading.value = false }
}
async function chooseStreet(event) {
  streetIndex.value = Number(event.detail.value)
  loading.value = true
  try { await loadItems() } catch (error) { showFailure(error) }
  finally { loading.value = false }
}
function chooseItem(index) {
  itemIndex.value = index
  duration.value = minDuration.value
}
function next() {
  if (step.value === 1 && (!selectedElder.value || !selectedStreet.value)) return showFailure(new Error('请先选择服务对象和服务街道'))
  if (step.value === 2) {
    if (!selectedItem.value || !date.value || !time.value || !address.value.trim()) return showFailure(new Error('请填写服务项目、时间和服务地址'))
    const normalized = normalizedDuration()
    if (Number(duration.value) !== normalized) {
      duration.value = normalized
      return showFailure(new Error(`服务时长需在 ${minDuration.value}–${maxDuration.value} 分钟之间，已按 ${normalized} 分钟填写`))
    }
  }
  if (step.value < 3) step.value += 1
  else submit()
}
function back() { if (step.value > 1) step.value -= 1 }
async function submit() {
  if (!selectedItem.value || !selectedElder.value || !selectedStreet.value) return
  submitting.value = true
  try {
    if (!submitKey.value) submitKey.value = idempotencyKey()
    const result = await platformApi.createOrder({
      elderId: selectedElder.value.id,
      itemId: selectedItem.value.id,
      streetId: selectedStreet.value.id,
      address: address.value.trim(),
      contactPhone: contactPhone.value.trim() || undefined,
      startAt: localDateTime(date.value, time.value),
      durationMinutes: normalizedDuration(),
    }, submitKey.value)
    submitKey.value = ''
    uni.showToast({ title: `预约已提交（${result.id}）`, icon: 'success' })
    uni.redirectTo({ url: '/pages/orders/index' })
  } catch (error) { showFailure(error) }
  finally { submitting.value = false }
}
onShow(load)
</script>

<template>
  <view class="page">
    <AppPageHeader title="预约服务" />
    <view class="content">
      <view class="stepper"><text v-for="number in 3" :key="number" :class="{ current: step === number, done: step > number }">{{ number }} {{ ['服务对象', '服务安排', '确认提交'][number - 1] }}</text></view>
      <view v-if="loading" class="card muted">正在读取服务目录与可预约对象…</view>
      <view v-else-if="failure" class="card error"><text>{{ failure }}</text><button class="secondary" @click="load">重新加载</button></view>
      <template v-else>
        <view v-if="step === 1" class="card">
          <text class="heading">为谁安排服务？</text>
          <picker v-if="elders.length" :range="elders" range-key="fullName" :value="elderIndex" @change="chooseElder"><view class="picker">{{ selectedElder?.fullName || '请选择老人档案' }}　⌄</view></picker>
          <view v-else class="empty">还没有老人档案，请先到“更多 → 家庭授权”创建档案。</view>
          <text class="field-label">服务街道</text>
          <picker v-if="streets.length" :range="streets" range-key="full_name" :value="streetIndex" @change="chooseStreet"><view class="picker">{{ selectedStreet?.full_name || '请选择服务街道' }}　⌄</view></picker>
          <view v-else class="empty">平台尚未配置可预约街道。</view>
          <view class="hint">平台只会展示当前街道覆盖的服务项目。</view>
        </view>
        <view v-else-if="step === 2" class="card">
          <text class="heading">选择服务和时间</text>
          <view v-if="items.length" class="service-list">
            <view v-for="(item, index) in items" :key="item.id" class="service" :class="{ selected: itemIndex === index }" @click="chooseItem(index)">
              <view class="row"><text class="strong">{{ item.name }}</text><text class="price">¥{{ Number(item.unit_price).toFixed(2) }}{{ item.pricing_model === 'HOURLY' ? '/小时' : '/次' }}</text></view>
              <text class="hint">{{ item.description || item.rule_text }}</text>
              <text v-if="recommendations.find((entry) => entry.id === item.id)" class="reason">推荐：{{ recommendations.find((entry) => entry.id === item.id).reason }}</text>
            </view>
          </view>
          <view v-else class="empty">该街道暂无可预约服务。</view>
          <text class="field-label">预约日期</text><picker mode="date" :start="tomorrow" :value="date" @change="date = $event.detail.value"><view class="picker">{{ date || '请选择日期' }}</view></picker>
          <text class="field-label">预约时间</text><picker mode="time" :value="time" @change="time = $event.detail.value"><view class="picker">{{ time || '请选择时间' }}</view></picker>
          <text class="field-label">{{ isHourly ? '服务时长（分钟）' : '预计服务时长（分钟）' }}</text><input v-model.number="duration" type="number" class="input" :placeholder="String(minDuration)" @blur="duration = normalizedDuration()" />
          <text class="hint">可选 {{ minDuration }}–{{ maxDuration }} 分钟{{ isHourly ? '，按小时计费' : '，固定价不受时长影响' }}。</text>
          <text class="field-label">详细地址</text><input v-model="address" class="input" maxlength="300" placeholder="请输入上门服务地址" />
          <text class="field-label">联系电话（选填）</text><input v-model="contactPhone" type="number" maxlength="32" class="input" placeholder="服务人员接单后可见" />
        </view>
        <view v-else class="card">
          <text class="heading">核对预约信息</text>
          <view class="detail"><text>服务对象</text><text>{{ selectedElder?.fullName }}</text></view>
          <view class="detail"><text>服务街道</text><text>{{ selectedStreet?.full_name }}</text></view>
          <view class="detail"><text>服务项目</text><text>{{ selectedItem?.name }}</text></view>
          <view class="detail"><text>预约时间</text><text>{{ date }} {{ time }}</text></view>
          <view class="detail"><text>服务时长</text><text>{{ duration }} 分钟</text></view>
          <view class="detail"><text>服务地址</text><text class="address">{{ address }}</text></view>
          <view v-if="contactPhone" class="detail"><text>联系电话</text><text>{{ contactPhone }}</text></view>
          <view class="detail total"><text>预估金额</text><text>¥{{ price.toFixed(2) }}</text></view>
          <text class="hint">提交后由管理员安排服务人员，金额与时间以服务端校验为准。</text>
        </view>
        <view class="actions"><button v-if="step > 1" class="secondary" @click="back">上一步</button><button class="primary" :disabled="submitting" @click="next">{{ submitting ? '正在提交…' : step === 3 ? '确认预约' : '下一步' }}</button></view>
      </template>
    </view>
    <AppBottomNav active="booking" />
  </view>
</template>

<style scoped>
.page { min-height:100vh; padding-bottom:calc(72px + env(safe-area-inset-bottom)); box-sizing:border-box; background:#f5f8fb; color:#19364d; font-size:17px; line-height:1.55; }
.content { padding:16px 18px 28px; }
.stepper { display:flex; justify-content:space-between; gap:5px; margin:0 0 14px; color:#778896; font-size:12px; }
.stepper text { flex:1; padding:9px 3px; border-radius:8px; background:#edf1f4; text-align:center; }
.stepper .current { background:#dcecf9; color:#125fa8; font-weight:700; }
.stepper .done { color:#23684d; }
.card { padding:18px; border-radius:14px; background:#fff; }
.heading { display:block; margin-bottom:16px; font-size:20px; font-weight:700; }
.field-label { display:block; margin:19px 0 8px; font-size:15px; font-weight:600; }
.picker,.input { min-height:48px; padding:11px 12px; box-sizing:border-box; border:1px solid #cbd9e4; border-radius:9px; background:#fff; font-size:16px; }
.hint,.muted { display:block; margin-top:8px; color:#687b89; font-size:14px; }
.service-list { display:flex; flex-direction:column; gap:9px; }
.service { padding:13px; border:1px solid #dce5ec; border-radius:10px; }
.service.selected { border-color:#125fa8; background:#f1f7fc; }
.row,.detail { display:flex; justify-content:space-between; gap:12px; }
.strong { font-weight:700; }
.price,.reason { color:#125fa8; }
.reason { display:block; margin-top:6px; font-size:13px; }
.empty { padding:15px; border-radius:9px; background:#f5f8fb; color:#687b89; }
.detail { padding:12px 0; border-bottom:1px solid #e4ebf0; font-size:15px; }
.detail > text:first-child { flex-shrink:0; color:#687b89; }
.address { max-width:68%; text-align:right; overflow-wrap:anywhere; }
.total { font-weight:700; color:#125fa8; }
.actions { display:flex; gap:10px; margin-top:14px; }
button { flex:1; min-height:48px; border-radius:9px; font-size:16px; }
button::after { border:0; }
.primary { background:#125fa8; color:#fff; }
.secondary { background:#eaf3fc; color:#104977; }
.error { color:#a34135; }
</style>
