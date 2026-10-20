<template>
  <WorkspaceLayout role="USER">
    <div class="heading"><div><p class="eyebrow">家庭管理</p><h1 class="page-title">一起照顾重要的人。</h1><p class="muted">每位家属只看得到当前被授权的档案与操作。</p></div><button class="primary" @click="createOpen = true">新建老人档案</button></div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <section v-if="invitations.length" class="surface section"><h2>待您确认的邀请</h2><div v-for="invitation in invitations" :key="invitation.id" class="line"><span>{{ invitation.elder_name }}的家庭邀请</span><div><button class="text-button" @click="respond(invitation.id, false)">拒绝</button><button class="primary small" @click="respond(invitation.id, true)">接受</button></div></div></section>
    <section class="section"><h2>已授权的老人档案</h2><div v-if="elders.length" class="elder-grid"><button v-for="elder in elders" :key="elder.id" class="surface elder-card" @click="selectElder(elder)"><strong>{{ elder.fullName }}</strong><span>查看档案与家庭授权 →</span></button></div><div v-else class="empty">暂无可访问的老人档案。您可以创建新档案，或等待家庭成员邀请。</div></section>
    <section v-if="selected" class="surface section detail"><div class="detail-header"><div><p class="eyebrow">当前档案</p><h2>{{ selected.fullName }}</h2><p class="muted">出生日期：{{ selected.birthDate || '未填写' }}</p></div><button v-if="currentPermissions?.canManageFamily" class="text-button" @click="inviteOpen = true">邀请家属</button></div><div><div class="detail-header"><h3>生活服务偏好</h3><button v-if="currentPermissions?.canManageFamily" class="text-button" @click="addPreference">添加偏好</button></div><div class="preferences"><span v-for="tag in preferences" :key="tag">{{ tag }} <button v-if="currentPermissions?.canManageFamily" class="remove-tag" :aria-label="`移除${tag}`" @click="removePreference(tag)">×</button></span><p v-if="!preferences.length" class="muted">暂未设置偏好；服务目录仍可正常浏览。</p></div></div><div v-if="currentPermissions?.canManageFamily"><h3>家庭授权</h3><div v-for="grant in grants" :key="grant.account_id" class="line"><span>{{ grant.display_name }} · {{ grant.email }} <small>{{ grant.status === 'ACTIVE' ? '有效' : '已撤销' }}</small></span><button v-if="grant.status === 'ACTIVE' && grant.account_id !== session.user?.id" class="text-button" @click="revoke(grant.account_id)">撤销授权</button></div></div></section>
    <section v-if="selected" class="surface section detail"><h2>本人核验与家庭协调</h2><p class="muted">本人关联须由管理员线下核验；申请本身不会增加家庭授权。</p><div v-for="claim in identityClaims" :key="claim.id" class="line"><span>本人关联申请 #{{ claim.id }}</span><strong>{{ statusName(claim.status) }}</strong></div><button class="text-button" @click="claimIdentity">申请本人关联</button><h3>家庭关联争议</h3><div v-for="dispute in familyDisputes" :key="dispute.id" class="line"><span>{{ dispute.reason }}</span><strong>{{ statusName(dispute.status) }}</strong></div><button class="text-button" @click="openFamilyDispute(selected.id)">提交争议</button></section>
    <section class="surface section detail"><h2>已撤销授权的争议</h2><p class="muted">如果您的授权已撤销，可凭此前档案编号提交关联争议；此入口不会恢复档案访问权。</p><div class="former-dispute"><el-input-number v-model="formerElderId" :min="1" placeholder="档案编号" /><button class="text-button" :disabled="!formerElderId" @click="openFamilyDispute(formerElderId!)">提交争议</button></div></section>
    <el-dialog v-model="createOpen" title="新建老人档案" width="min(92vw, 520px)"><el-form label-position="top"><el-form-item label="姓名"><el-input v-model="newName" maxlength="80" /></el-form-item><el-form-item label="出生日期（选填）"><el-input v-model="newBirthDate" type="date" /></el-form-item><el-checkbox v-model="declaresAuthorization">我确认有权为此人创建档案并管理其服务</el-checkbox></el-form><template #footer><button class="primary" :disabled="busy" @click="createElder">保存档案</button></template></el-dialog>
    <el-dialog v-model="inviteOpen" title="邀请家庭成员" width="min(92vw, 560px)"><el-form label-position="top"><el-form-item label="家属邮箱"><el-input v-model="inviteEmail" type="email" /></el-form-item><p class="muted">对方注册并登录相同邮箱后，可在家庭管理中确认邀请。</p><div class="permission-grid"><el-checkbox v-model="permissions.canViewService">查看服务信息</el-checkbox><el-checkbox v-model="permissions.canBook">代预约</el-checkbox><el-checkbox v-model="permissions.canManageReminder">管理生活提醒</el-checkbox><el-checkbox v-model="permissions.canCancel">取消订单</el-checkbox><el-checkbox v-model="permissions.canConfirm">确认完成</el-checkbox><el-checkbox v-model="permissions.canReview">评价服务</el-checkbox><el-checkbox v-model="permissions.canConfirmFee">确认费用调整</el-checkbox><el-checkbox v-model="permissions.canManageFamily">管理家庭授权</el-checkbox></div></el-form><template #footer><button class="primary" :disabled="busy" @click="invite">发出邀请</button></template></el-dialog>
  </WorkspaceLayout>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import WorkspaceLayout from '../../layouts/WorkspaceLayout.vue'
import { api } from '../../api/client'
import { useSessionStore } from '../../stores/session'

interface Elder { id: number; fullName: string; birthDate: string | null }
interface Invitation { id: number; elder_name: string }
interface Grant { account_id: number; display_name: string; email: string; status: string }
interface CurrentPermissions { canManageFamily: boolean }
interface IdentityClaim { id: number; status: string }
interface FamilyDispute { id: number; reason: string; status: string }
const session = useSessionStore()
const elders = ref<Elder[]>([])
const invitations = ref<Invitation[]>([])
const grants = ref<Grant[]>([])
const preferences=ref<string[]>([])
const identityClaims=ref<IdentityClaim[]>([])
const familyDisputes=ref<FamilyDispute[]>([])
const formerElderId=ref<number>()
const currentPermissions = ref<CurrentPermissions | null>(null)
const selected = ref<Elder | null>(null)
const error = ref('')
const busy = ref(false)
const createOpen = ref(false)
const inviteOpen = ref(false)
const newName = ref('')
const newBirthDate = ref('')
const declaresAuthorization = ref(false)
const inviteEmail = ref('')
const permissions = reactive({ canViewService: true, canBook: false, canManageReminder: false, canCancel: false, canConfirm: false, canReview: false, canConfirmFee: false, canManageFamily: false })
const statusLabels:Record<string,string>={PENDING:'待核验',APPROVED:'已核验',REJECTED:'已拒绝',REVOKED:'已撤销',OPEN:'待协调',RESOLVED:'已协调'}
function statusName(value:string){return statusLabels[value]||value}
async function load() {
  error.value = ''
  try { [elders.value, invitations.value] = await Promise.all([api<Elder[]>('/elders'), api<Invitation[]>('/family/invitations')]) }
  catch (reason) { error.value = reason instanceof Error ? reason.message : '加载失败' }
}
async function selectElder(elder: Elder) {
  selected.value = elder
  try {
    currentPermissions.value = await api<CurrentPermissions>(`/elders/${elder.id}/permissions`)
    grants.value = currentPermissions.value.canManageFamily ? await api<Grant[]>(`/elders/${elder.id}/grants`) : []
    preferences.value=await api<string[]>(`/elders/${elder.id}/preferences`)
    ;[identityClaims.value,familyDisputes.value]=await Promise.all([api<IdentityClaim[]>(`/elders/${elder.id}/identity-claims`),api<FamilyDispute[]>(`/elders/${elder.id}/family-disputes`)])
  } catch (reason) { grants.value = [];preferences.value=[]; currentPermissions.value = null; error.value = reason instanceof Error ? reason.message : '权限加载失败' }
}
async function createElder() {
  if (!newName.value.trim() || !declaresAuthorization.value) { ElMessage.error('请填写姓名并确认授权声明'); return }
  busy.value = true
  try { await api('/elders', { method: 'POST', body: JSON.stringify({ fullName: newName.value.trim(), birthDate: newBirthDate.value || null, declaresAuthorization: true }) }); createOpen.value = false; await load(); ElMessage.success('档案已创建') }
  catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '创建失败') }
  finally { busy.value = false }
}
async function invite() {
  if (!selected.value || !inviteEmail.value.includes('@')) { ElMessage.error('请填写家属邮箱'); return }
  busy.value = true
  try { await api(`/elders/${selected.value.id}/invitations`, { method: 'POST', body: JSON.stringify({ email: inviteEmail.value.trim(), permissions }) }); inviteOpen.value = false; ElMessage.success('邀请已发出') }
  catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '邀请失败') }
  finally { busy.value = false }
}
async function respond(id: number, accept: boolean) {
  try { await api(`/family/invitations/${id}/${accept ? 'accept' : 'reject'}`, { method: 'POST' }); await load(); ElMessage.success(accept ? '已接受邀请' : '已拒绝邀请') }
  catch (reason) { ElMessage.error(reason instanceof Error ? reason.message : '操作失败') }
}
async function revoke(accountId: number) {
  if (!selected.value) return
  try { await ElMessageBox.confirm('撤销后，对方将立即失去该档案的后续访问权限。', '确认撤销授权'); await api(`/elders/${selected.value.id}/grants/${accountId}/revoke`, { method: 'POST' }); await selectElder(selected.value); ElMessage.success('授权已撤销') }
  catch (reason) { if (reason instanceof Error) ElMessage.error(reason.message) }
}
async function addPreference(){if(!selected.value)return;try{const result=await ElMessageBox.prompt('例如：清淡饮食、深度保洁。请使用简短标签。','添加服务偏好',{inputValidator:value=>Boolean(value?.trim())||'请填写偏好'});await api(`/elders/${selected.value.id}/preferences`,{method:'POST',body:JSON.stringify({tag:result.value.trim()})});preferences.value=await api<string[]>(`/elders/${selected.value.id}/preferences`);ElMessage.success('偏好已添加')}catch(reason){if(reason instanceof Error)ElMessage.error(reason.message)}}
async function removePreference(tag:string){if(!selected.value)return;try{await api(`/elders/${selected.value.id}/preferences/${encodeURIComponent(tag)}`,{method:'DELETE'});preferences.value=await api<string[]>(`/elders/${selected.value.id}/preferences`)}catch(reason){ElMessage.error(reason instanceof Error?reason.message:'移除失败')}}
async function claimIdentity(){if(!selected.value)return;try{await ElMessageBox.confirm('提交后需管理员线下核验身份。','申请老人本人关联');await api(`/elders/${selected.value.id}/identity-claims`,{method:'POST'});identityClaims.value=await api<IdentityClaim[]>(`/elders/${selected.value.id}/identity-claims`);ElMessage.success('申请已提交')}catch(reason){if(reason instanceof Error)ElMessage.error(reason.message)}}
async function openFamilyDispute(elderId:number){try{const result=await ElMessageBox.prompt('请说明关联争议及需要协调的事项。','提交家庭关联争议',{inputValidator:value=>Boolean(value?.trim())||'请填写原因'});await api(`/elders/${elderId}/family-disputes`,{method:'POST',body:JSON.stringify({reason:result.value.trim()})});if(selected.value?.id===elderId)familyDisputes.value=await api<FamilyDispute[]>(`/elders/${elderId}/family-disputes`);ElMessage.success('争议已登记，等待管理员协调')}catch(reason){if(reason instanceof Error)ElMessage.error(reason.message)}}
onMounted(load)
</script>
<style scoped>
.heading { display: flex; justify-content: space-between; align-items: center; gap: 24px; margin-bottom: 44px; }.heading .muted { margin-top: 12px; }.section { margin-top: 28px; }.section.surface { padding: 26px; }.section h2 { font-size: 24px; margin: 0 0 18px; }.elder-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 16px; }.elder-card { text-align: left; padding: 26px; cursor: pointer; color: inherit; display: grid; gap: 12px; }.elder-card strong { font-size: 24px; }.elder-card span { color: #118265; }.line { display: flex; justify-content: space-between; align-items: center; gap: 14px; padding: 16px 0; border-top: 1px solid #edf1ee; }.line div { display: flex; gap: 12px; align-items: center; }.line small { color: #73847a; margin-left: 8px; }.small { padding: 8px 16px; }.detail-header { display: flex; justify-content: space-between;align-items:center }.permission-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 4px; }.preferences{display:flex;gap:9px;flex-wrap:wrap;margin-bottom:22px}.preferences span{background:#e8f4ec;padding:7px 12px;border-radius:20px}.remove-tag{border:0;background:none;cursor:pointer;color:#137b60}.error { color: #b34939; }
.former-dispute{display:flex;gap:12px;align-items:center;flex-wrap:wrap}
@media (max-width: 700px) { .heading { align-items: flex-start; flex-direction: column; }.permission-grid { grid-template-columns: 1fr; } }
</style>
