<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppPageHeader from '../../components/AppPageHeader.vue'
import { errorMessage, platformApi, requireRole, showFailure } from '../../api/platform.js'

const loading = ref(true)
const failure = ref('')
const elders = ref([])
const invitations = ref([])
const grants = ref([])
const selectedIndex = ref(0)
const fullName = ref('')
const birthDate = ref('')
const editName = ref('')
const editBirthDate = ref('')
const editingElder = ref(false)
const declaresAuthorization = ref(false)
const inviteUsername = ref('')
const inviteCanBook = ref(false)
const busy = ref(false)
const selected = computed(() => elders.value[selectedIndex.value])
async function load() {
  loading.value = true
  failure.value = ''
  try {
    if (!await requireRole(['USER'])) { loading.value = false; return }
    const [elderList, pending] = await Promise.all([platformApi.elders(), platformApi.invitations()])
    elders.value = elderList; invitations.value = pending
    await loadGrants()
  } catch (error) { failure.value = errorMessage(error, '家庭授权信息加载失败') }
  finally { loading.value = false }
}
async function loadGrants() { grants.value = selected.value ? await platformApi.grants(selected.value.id) : [] }
async function createElder() {
  if (!fullName.value.trim() || !declaresAuthorization.value) return showFailure(new Error('请填写姓名并确认已取得建档授权'))
  busy.value = true
  try {
    const created = await platformApi.createElder({ fullName:fullName.value.trim(), birthDate:birthDate.value || null, declaresAuthorization:true })
    elders.value.push(created); selectedIndex.value = elders.value.length - 1
    fullName.value = ''; birthDate.value = ''; declaresAuthorization.value = false
    await loadGrants(); uni.showToast({ title:'老人档案已建立', icon:'success' })
  } catch (error) { showFailure(error) }
  finally { busy.value = false }
}
function beginElderEdit() {
  if (!selected.value) return
  editName.value = selected.value.fullName
  editBirthDate.value = selected.value.birthDate || ''
  editingElder.value = true
}
async function saveElderEdit() {
  if (!selected.value || !editName.value.trim()) return showFailure(new Error('请填写老人姓名'))
  busy.value = true
  try {
    const updated = await platformApi.updateElder(selected.value.id, { fullName:editName.value.trim(), birthDate:editBirthDate.value || null })
    elders.value[selectedIndex.value] = updated
    editingElder.value = false
    uni.showToast({ title:'档案已更新', icon:'success' })
  } catch (error) { showFailure(error) }
  finally { busy.value = false }
}
async function invite() {
  if (!selected.value || !inviteUsername.value.trim()) return showFailure(new Error('请选择档案并填写家人的平台账号'))
  busy.value = true
  try {
    await platformApi.invite(selected.value.id, {
      username:inviteUsername.value.trim(),
      permissions:{ canViewService:true, canBook:inviteCanBook.value, canManageReminder:false, canCancel:false, canConfirm:false, canReview:false, canConfirmFee:false, canManageFamily:false },
    })
    inviteUsername.value = ''; inviteCanBook.value = false
    await loadGrants(); uni.showToast({ title:'邀请已发送', icon:'success' })
  } catch (error) { showFailure(error) }
  finally { busy.value = false }
}
async function respond(invitation, accept) {
  busy.value = true
  try { await platformApi.respondInvitation(invitation.id, accept); await load(); uni.showToast({ title:accept?'已接受邀请':'已拒绝邀请', icon:'success' }) }
  catch (error) { showFailure(error) }
  finally { busy.value = false }
}
function revoke(grant) {
  uni.showModal({ title:'撤销家庭授权', content:`确定撤销 ${grant.display_name} 的授权吗？`, success:async (result)=>{
    if (!result.confirm || !selected.value) return
    busy.value = true
    try { await platformApi.revokeGrant(selected.value.id, grant.account_id); await loadGrants(); uni.showToast({ title:'授权已撤销', icon:'success' }) }
    catch (error) { showFailure(error) }
    finally { busy.value = false }
  } })
}
async function changeElder(event) { selectedIndex.value = Number(event.detail.value); try { await loadGrants() } catch (error) { showFailure(error) } }
onShow(load)
</script>

<template>
  <view class="page">
    <AppPageHeader title="家庭授权" />
    <scroll-view scroll-y class="scroll-area">
      <view class="content">
        <view v-if="loading" class="card muted">正在读取家庭授权…</view>
        <view v-else-if="failure" class="card error"><text>{{ failure }}</text><button class="secondary block" :disabled="busy" @click="load">重新加载</button></view>
        <template v-else>
          <view v-if="invitations.length" class="card invite-list">
            <text class="heading">待处理邀请</text>
            <view v-for="invitation in invitations" :key="invitation.id" class="grant-row">
              <view><text class="strong">{{ invitation.elder_name }}</text><text class="muted">服务对象邀请 · {{ invitation.expires_at }}</text></view>
              <view class="row"><button class="primary small" :disabled="busy" @click="respond(invitation,true)">接受</button><button class="secondary small" :disabled="busy" @click="respond(invitation,false)">拒绝</button></view>
            </view>
          </view>
          <view class="card">
            <text class="heading">老人档案</text>
            <picker v-if="elders.length" :range="elders" range-key="fullName" :value="selectedIndex" @change="changeElder"><view class="picker">{{ selected?.fullName }}　⌄</view></picker>
            <view v-else class="empty">尚未建立老人档案。</view>
            <view v-if="selected" class="muted">出生日期：{{ selected.birthDate || '未填写' }} · 档案编号 {{ selected.id }}</view>
            <button v-if="selected && !editingElder" class="secondary small edit-elder" :disabled="busy" @click="beginElderEdit">修改档案</button>
            <view v-if="editingElder" class="edit-form">
              <text class="field-label">姓名</text><input v-model="editName" class="input" maxlength="80" placeholder="请输入老人姓名" />
              <text class="field-label">出生日期（可选）</text><picker mode="date" :value="editBirthDate" @change="editBirthDate = $event.detail.value"><view class="picker">{{ editBirthDate || '请选择日期' }}</view></picker>
              <view class="row"><button class="secondary" :disabled="busy" @click="editingElder=false">取消</button><button class="primary" :disabled="busy" @click="saveElderEdit">保存修改</button></view>
            </view>
          </view>
          <view class="card">
            <text class="heading">建立老人档案</text>
            <text class="field-label">姓名</text><input v-model="fullName" class="input" maxlength="80" placeholder="请输入老人姓名" />
            <text class="field-label">出生日期（可选）</text><picker mode="date" :value="birthDate" @change="birthDate = $event.detail.value"><view class="picker">{{ birthDate || '请选择日期' }}</view></picker>
            <view class="check"><text>我确认已取得为其建立档案的授权</text><switch :checked="declaresAuthorization" color="#125fa8" @change="declaresAuthorization = $event.detail.value" /></view>
            <button class="primary block" :disabled="busy" @click="createElder">保存档案</button>
          </view>
          <view v-if="selected" class="card">
            <text class="heading">邀请家人</text>
            <text class="field-label">家人平台账号</text><input v-model="inviteUsername" class="input" maxlength="32" placeholder="请输入对方注册账号" />
            <view class="check"><text>允许代为预约服务</text><switch :checked="inviteCanBook" color="#125fa8" @change="inviteCanBook = $event.detail.value" /></view>
            <text class="hint">默认仅可查看服务；请只授予当前需要的权限。</text>
            <button class="primary block" :disabled="busy" @click="invite">发送邀请</button>
            <text class="subheading">已授权账号</text>
            <view v-if="!grants.length" class="empty">暂无其他授权账号。</view>
            <view v-for="grant in grants" :key="grant.account_id" class="grant-row">
              <view><text class="strong">{{ grant.display_name }}</text><text class="muted">{{ grant.username }} · {{ grant.status === 'ACTIVE' ? '授权有效' : '已停用' }}</text></view>
              <button v-if="grant.status === 'ACTIVE'" class="text-button" :disabled="busy" @click="revoke(grant)">撤销</button>
            </view>
          </view>
        </template>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped>
.page { min-height:100vh; background:#f5f8fb; color:#19364d; font-size:16px; line-height:1.5; }
.scroll-area { height:calc(100vh - 60px); }
.content { display:flex; flex-direction:column; gap:12px; padding:14px 18px 30px; }
.card { padding:17px; border-radius:13px; background:#fff; }
.heading { display:block; margin-bottom:14px; font-size:19px; font-weight:700; }
.field-label { display:block; margin:14px 0 7px; font-size:14px; font-weight:600; }
.edit-elder { margin-top:10px; }
.edit-form { display:flex; flex-direction:column; gap:8px; margin-top:12px; padding-top:12px; border-top:1px solid #e4ebf0; }
.input,.picker { min-height:46px; padding:10px 11px; box-sizing:border-box; border:1px solid #cbd9e4; border-radius:8px; background:#fff; }
.check { display:flex; align-items:center; gap:7px; margin:13px 0; font-size:14px; }
.row { display:flex; gap:7px; }
.grant-row { display:flex; align-items:center; justify-content:space-between; gap:8px; padding:12px 0; border-top:1px solid #e4ebf0; }
.strong { display:block; font-weight:700; }
.muted,.hint { display:block; margin-top:5px; color:#687b89; font-size:13px; }
.error { color:#a34135; }
.empty { padding:12px; color:#687b89; background:#f5f8fb; border-radius:8px; }
.subheading { display:block; margin:18px 0 6px; font-weight:700; }
button { min-height:42px; padding:0 12px; border-radius:8px; font-size:14px; }
button::after { border:0; }
.primary { background:#125fa8; color:#fff; }
.secondary { background:#eaf3fc; color:#104977; }
.small { min-width:54px; }
.block { width:100%; margin-top:12px; }
.text-button { color:#a34135; background:transparent; }
</style>
