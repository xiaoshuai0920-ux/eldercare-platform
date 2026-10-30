import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { userApi } from '@/api/endpoints'
import { ApiError } from '@/api/client'
import type { ElderRow, FamilyDisputeRow, FamilyInvitation, IdentityClaimRow, Permissions } from '@/api/types'

function ageOf(birthDate: string | null): number | null {
  if (!birthDate) return null
  const born = new Date(birthDate)
  if (Number.isNaN(born.getTime())) return null
  const now = new Date()
  let age = now.getFullYear() - born.getFullYear()
  const beforeBirthday =
    now.getMonth() < born.getMonth() || (now.getMonth() === born.getMonth() && now.getDate() < born.getDate())
  if (beforeBirthday) age -= 1
  return age
}

/** 家庭成员授权行（后端 grants 接口原样字段） */
export interface GrantRow {
  account_id: number
  username: string
  display_name: string
  email: string | null
  can_view_service: number
  can_book: number
  can_manage_reminder: number
  can_cancel: number
  can_confirm: number
  can_review: number
  can_confirm_fee: number
  can_manage_family: number
  status: string
}

export const useFamilyStore = defineStore('family', () => {
  const elders = ref<ElderRow[]>([])
  const invitations = ref<FamilyInvitation[]>([])
  const invitationsError = ref('')
  const selectedId = ref<number | null>(null)
  const permissions = ref<Permissions | null>(null)
  const claims = ref<IdentityClaimRow[]>([])
  const disputes = ref<FamilyDisputeRow[]>([])
  const grants = ref<GrantRow[]>([])
  const grantsError = ref('')
  const preferences = ref<string[]>([])
  const preferencesError = ref('')
  const loading = ref(false)
  const detailLoading = ref(false)
  const error = ref('')
  let detailRequest = 0

  const selected = computed(
    () => elders.value.find((elder) => elder.id === selectedId.value) ?? elders.value[0] ?? null,
  )
  const selectedAge = computed(() => (selected.value ? ageOf(selected.value.birthDate) : null))

  async function load() {
    loading.value = true
    error.value = ''
    try {
      elders.value = await userApi.elders()
      if (!selectedId.value || !elders.value.some((elder) => elder.id === selectedId.value)) {
        selectedId.value = elders.value[0]?.id ?? null
      }
      if (selectedId.value) await loadDetail(selectedId.value)
      else {
        detailRequest += 1
        permissions.value = null
        claims.value = []
        disputes.value = []
        grants.value = []
        grantsError.value = ''
        preferences.value = []
        preferencesError.value = ''
        detailLoading.value = false
      }
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '家庭档案加载失败'
    } finally {
      loading.value = false
    }
  }

  async function loadDetail(elderId: number) {
    const requestId = ++detailRequest
    detailLoading.value = true
    permissions.value = null
    let grantFailure = ''
    let preferenceFailure = ''
    try {
      grantsError.value = ''
      preferencesError.value = ''
      const [perm, claimList, disputeList, grantList, preferenceList] = await Promise.all([
        userApi.permissions(elderId),
        userApi.claims(elderId).catch(() => []),
        userApi.familyDisputes(elderId).catch(() => []),
        userApi.grants(elderId).catch((reason) => {
          grantFailure =
            reason instanceof ApiError ? reason.message : '授权信息加载失败，可能没有管理家庭授权的权限'
          return []
        }),
        userApi.preferences(elderId).catch((reason) => {
          preferenceFailure = reason instanceof ApiError ? reason.message : '服务偏好加载失败'
          return []
        }),
      ])
      if (requestId !== detailRequest) return
      grantsError.value = grantFailure
      preferencesError.value = preferenceFailure
      permissions.value = perm
      claims.value = claimList
      disputes.value = disputeList
      grants.value = grantList as unknown as GrantRow[]
      preferences.value = preferenceList
    } catch (reason) {
      if (requestId !== detailRequest) return
      error.value = reason instanceof ApiError ? reason.message : '档案详情加载失败'
    } finally {
      if (requestId === detailRequest) detailLoading.value = false
    }
  }

  async function select(elderId: number) {
    selectedId.value = elderId
    await loadDetail(elderId)
  }

  async function loadInvitations() {
    try {
      invitations.value = await userApi.invitations()
      invitationsError.value = ''
    } catch (reason) {
      invitationsError.value = reason instanceof ApiError ? reason.message : '家庭邀请加载失败'
    }
  }

  async function createElder(fullName: string, birthDate: string | null, declaresAuthorization: boolean) {
    try {
      const elder = await userApi.createElder({ fullName: fullName.trim(), birthDate, declaresAuthorization })
      selectedId.value = elder.id
      await load()
      return true
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '创建档案失败'
      return false
    }
  }

  async function respondInvitation(id: number, accept: boolean) {
    try {
      await userApi.respondInvitation(id, accept)
      await loadInvitations()
      if (accept) await load()
      return true
    } catch (reason) {
      invitationsError.value = reason instanceof ApiError ? reason.message : '处理邀请失败'
      return false
    }
  }

  async function updateElder(id: number, fullName: string, birthDate: string | null) {
    try {
      const updated = await userApi.updateElder(id, { fullName: fullName.trim(), birthDate })
      const index = elders.value.findIndex((elder) => elder.id === id)
      if (index >= 0) elders.value[index] = updated
      error.value = ''
      return true
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '修改档案失败'
      return false
    }
  }

  /** 邀请家人：返回 true 表示后端已受理，错误文案写入 error 供页面展示 */
  async function invite(
    elderId: number,
    username: string,
    permissionsInput: Record<string, boolean>,
  ): Promise<boolean> {
    try {
      await userApi.invite(elderId, { username: username.trim(), permissions: permissionsInput })
      error.value = ''
      return true
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '邀请失败'
      return false
    }
  }

  async function revoke(elderId: number, accountId: number): Promise<boolean> {
    try {
      await userApi.revokeGrant(elderId, accountId)
      await loadDetail(elderId)
      error.value = ''
      return true
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '撤销授权失败'
      return false
    }
  }

  async function requestClaim(elderId: number): Promise<boolean> {
    try {
      await userApi.createClaim(elderId)
      await loadDetail(elderId)
      error.value = ''
      return true
    } catch (reason) {
      error.value = reason instanceof ApiError ? reason.message : '核验申请提交失败'
      return false
    }
  }

  async function openDispute(elderId: number, reason: string): Promise<boolean> {
    try {
      await userApi.createDispute(elderId, reason.trim())
      await loadDetail(elderId)
      error.value = ''
      return true
    } catch (reason2) {
      error.value = reason2 instanceof ApiError ? reason2.message : '争议提交失败'
      return false
    }
  }

  async function addPreference(elderId: number, tag: string): Promise<boolean> {
    try {
      await userApi.addPreference(elderId, tag.trim())
      preferences.value = await userApi.preferences(elderId)
      preferencesError.value = ''
      return true
    } catch (reason) {
      preferencesError.value = reason instanceof ApiError ? reason.message : '添加服务偏好失败'
      return false
    }
  }

  async function removePreference(elderId: number, tag: string): Promise<boolean> {
    try {
      await userApi.removePreference(elderId, tag)
      preferences.value = await userApi.preferences(elderId)
      preferencesError.value = ''
      return true
    } catch (reason) {
      preferencesError.value = reason instanceof ApiError ? reason.message : '移除服务偏好失败'
      return false
    }
  }

  return {
    elders,
    invitations,
    invitationsError,
    selectedId,
    selected,
    selectedAge,
    permissions,
    claims,
    disputes,
    grants,
    grantsError,
    preferences,
    preferencesError,
    loading,
    detailLoading,
    error,
    load,
    loadInvitations,
    createElder,
    updateElder,
    respondInvitation,
    select,
    invite,
    revoke,
    requestClaim,
    openDispute,
    addPreference,
    removePreference,
    ageOf,
  }
})
