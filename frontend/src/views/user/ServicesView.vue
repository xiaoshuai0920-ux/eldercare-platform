<template>
  <WorkspaceLayout role="USER">
    <p class="eyebrow">服务预约</p><h1 class="page-title">为日常生活，安排一份安心。</h1>
    <p class="muted">选择服务与服务对象，提交后由管理员人工派单。</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <div class="filters"><el-select v-model="category" placeholder="全部分类" clearable @change="loadItems"><el-option label="助餐" value="MEAL" /><el-option label="保洁" value="CLEANING" /><el-option label="陪诊" value="ESCORT" /></el-select><el-input v-model="search" placeholder="搜索服务名称" clearable /></div><div class="recommend-controls"><el-select v-model="recommendElderId" placeholder="选择服务对象"><el-option v-for="elder in elders" :key="elder.id" :label="elder.fullName" :value="elder.id" /></el-select><el-select v-model="recommendStreetId" placeholder="选择服务街道"><el-option v-for="area in streets" :key="area.id" :label="area.full_name" :value="area.id" /></el-select><button class="text-button" @click="loadRecommended">按覆盖与偏好查看</button><button v-if="recommended" class="text-button" @click="loadItems">返回全部服务</button></div>
    <div v-if="filtered.length" class="service-grid"><article v-for="item in filtered" :key="item.id" class="surface service-card"><span class="eyebrow">{{ categoryName(item.category) }}</span><h2>{{ item.name }}</h2><p>{{ item.description }}</p><p v-if="item.reason" class="reason">{{ item.reason }}</p><p class="muted">{{ item.rule_text }}</p><div class="card-bottom"><strong>¥{{ item.unit_price }}{{ item.pricing_model === 'HOURLY' ? ' / 小时' : ' / 次' }}</strong><button class="primary" @click="choose(item)">预约服务</button></div></article></div>
    <div v-else-if="!error" class="empty">当前没有符合条件的服务项目。</div>
    <el-dialog v-model="bookingOpen" :title="selected?.name || '预约服务'" width="min(94vw, 600px)"><el-form label-position="top">
      <el-form-item label="服务对象"><el-select v-model="form.elderId" placeholder="请选择已授权对象" style="width:100%"><el-option v-for="elder in elders" :key="elder.id" :label="elder.fullName" :value="elder.id" /></el-select></el-form-item>
      <el-form-item label="服务街道"><el-select v-model="form.streetId" placeholder="请选择覆盖街道" style="width:100%"><el-option v-for="area in bookingStreets" :key="area.id" :label="area.full_name" :value="area.id" /></el-select></el-form-item>
      <el-form-item label="详细地址"><el-input v-model="form.address" maxlength="300" /></el-form-item>
      <el-form-item label="开始时间"><el-input v-model="form.startAt" type="datetime-local" /></el-form-item>
      <el-form-item label="服务时长（分钟）"><el-input-number v-model="form.durationMinutes" :min="selected?.min_minutes || 1" :max="selected?.max_minutes || 1440" :step="30" /></el-form-item>
      <p class="muted">提交时以后端校验的价格和时段为准；预约成功仅表示等待派单。</p>
    </el-form><template #footer><button class="primary" :disabled="busy" @click="book">提交预约</button></template></el-dialog>
  </WorkspaceLayout>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import WorkspaceLayout from '../../layouts/WorkspaceLayout.vue'
import { api } from '../../api/client'
type Item = { id: number; category: string; name: string; description: string; pricing_model: string; unit_price: number; min_minutes: number; max_minutes: number; rule_text: string;reason?:string }
type Elder = { id: number; fullName: string }
type Area = { id: number; level: string; name: string; full_name: string }
const router = useRouter()
const items = ref<Item[]>([]), elders = ref<Elder[]>([]), streets = ref<Area[]>([]), bookingStreets = ref<Area[]>([])
const category = ref(''), search = ref(''), error = ref(''), bookingOpen = ref(false), busy = ref(false)
const recommendElderId=ref<number>(),recommendStreetId=ref<number>(),recommended=ref(false)
const selected = ref<Item | null>(null)
const form = reactive({ elderId: undefined as number | undefined, streetId: undefined as number | undefined, address: '', startAt: '', durationMinutes: 60 })
const filtered = computed(() => items.value.filter(item => item.name.includes(search.value.trim())&&(!category.value||item.category===category.value)))
function categoryName(value: string) { return ({ MEAL: '助餐', CLEANING: '保洁', ESCORT: '陪诊' } as Record<string,string>)[value] || value }
async function loadItems() { try { items.value = await api<Item[]>(`/catalog/items${category.value ? `?category=${category.value}` : ''}`); recommended.value=false;error.value = '' } catch (reason) { error.value = reason instanceof Error ? reason.message : '服务加载失败' } }
async function loadRecommended(){if(!recommendElderId.value||!recommendStreetId.value){ElMessage.error('请选择服务对象和街道');return}try{items.value=await api<Item[]>(`/catalog/recommendations?elderId=${recommendElderId.value}&streetId=${recommendStreetId.value}`);recommended.value=true;error.value=''}catch(reason){ElMessage.error(reason instanceof Error?reason.message:'推荐加载失败')}}
async function choose(item: Item) {
  try {
    bookingStreets.value = await api<Area[]>(`/catalog/items/${item.id}/areas`)
    selected.value = item
    form.durationMinutes = item.min_minutes
    form.streetId = recommended.value && bookingStreets.value.some(area => area.id === recommendStreetId.value) ? recommendStreetId.value : undefined
    if (recommended.value) form.elderId = recommendElderId.value
    bookingOpen.value = true
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '服务范围加载失败') }
}
async function book() {
  if (!selected.value || !form.elderId || !form.streetId || !form.address.trim() || !form.startAt) { ElMessage.error('请完整填写预约信息'); return }
  busy.value = true
  try {
    await api('/orders', { method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ elderId: form.elderId, itemId: selected.value.id, streetId: form.streetId, address: form.address.trim(), startAt: form.startAt, durationMinutes: form.durationMinutes }) })
    ElMessage.success('预约已提交，等待人工派单'); bookingOpen.value = false; await router.push('/user/orders')
  } catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '预约失败') }
  finally { busy.value = false }
}
onMounted(async () => { await loadItems(); try { [elders.value, streets.value] = await Promise.all([api<Elder[]>('/elders'), api<Area[]>('/catalog/areas').then(rows => rows.filter(row => row.level === 'STREET'))]) } catch (reason) { error.value = reason instanceof Error ? reason.message : '基础数据加载失败' } })
</script>
<style scoped>
.filters,.recommend-controls { display:flex; gap:14px; margin:28px 0 18px; align-items:center;flex-wrap:wrap }.filters .el-select,.recommend-controls .el-select { width:190px; }.filters .el-input { max-width:340px; }.recommend-controls{margin-top:0}.service-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:18px; }.service-card { padding:28px; display:flex; flex-direction:column; min-height:290px; }.service-card h2 { margin:12px 0; font-size:25px; }.service-card p { line-height:1.6; }.reason{color:#0e765b;font-weight:700}.card-bottom { display:flex; align-items:center; justify-content:space-between; gap:12px; margin-top:auto; }.card-bottom strong { font-size:21px; }.form-error { color:#b34939; }@media(max-width:900px){.service-grid{grid-template-columns:1fr 1fr}}@media(max-width:630px){.service-grid{grid-template-columns:1fr}.filters{flex-direction:column}}
</style>
