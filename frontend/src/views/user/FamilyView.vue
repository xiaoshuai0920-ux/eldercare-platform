<script setup lang="ts">
  import { computed, onMounted, reactive, ref } from 'vue'
  import PageHeader from '@/components/ui/PageHeader.vue'
  import AppButton from '@/components/ui/AppButton.vue'
  import AppIcon from '@/components/ui/AppIcon.vue'
  import AppTag from '@/components/ui/AppTag.vue'
  import StatusTag from '@/components/ui/StatusTag.vue'
  import SkeletonBlock from '@/components/ui/SkeletonBlock.vue'
  import ErrorBanner from '@/components/ui/ErrorBanner.vue'
  import EmptyState from '@/components/ui/EmptyState.vue'
  import AppField from '@/components/ui/AppField.vue'
  import AppInput from '@/components/ui/AppInput.vue'
  import AppTextarea from '@/components/ui/AppTextarea.vue'
  import AppDialog from '@/components/ui/AppDialog.vue'
  import { useFamilyStore, type GrantRow } from '@/stores/family'
  import { useUiStore } from '@/stores/ui'
  import { formatFullDate, initial } from '@/utils/format'

  const family = useFamilyStore()
  const ui = useUiStore()

  const PERMISSION_LABELS: { key: keyof NonNullable<typeof family.permissions>; label: string }[] = [
    { key: 'canViewService', label: '查看服务记录' },
    { key: 'canBook', label: '代为预约' },
    { key: 'canCancel', label: '取消订单' },
    { key: 'canConfirm', label: '确认服务完成' },
    { key: 'canReview', label: '提交评价' },
    { key: 'canConfirmFee', label: '确认费用调整' },
    { key: 'canManageReminder', label: '管理生活提醒' },
    { key: 'canManageFamily', label: '管理家庭授权' },
  ]

  const permissionRows = computed(() =>
    PERMISSION_LABELS.map((item) => ({
      label: item.label,
      allowed: Boolean(family.permissions?.[item.key]),
    })),
  )

  // ---- 邀请家人 ----
  const inviteOpen = ref(false)
  const inviteName = ref('')
  const inviteError = ref('')
  const invitePerm = reactive({
    canViewService: true,
    canBook: true,
    canCancel: true,
    canConfirm: true,
    canReview: true,
    canConfirmFee: false,
    canManageReminder: false,
    canManageFamily: false,
  })

  const INVITE_LABELS: { key: keyof typeof invitePerm; label: string }[] = [
    { key: 'canViewService', label: '查看服务记录' },
    { key: 'canBook', label: '代为预约' },
    { key: 'canCancel', label: '取消订单' },
    { key: 'canConfirm', label: '确认完成' },
    { key: 'canReview', label: '提交评价' },
    { key: 'canConfirmFee', label: '确认费用调整' },
    { key: 'canManageReminder', label: '管理生活提醒' },
    { key: 'canManageFamily', label: '管理家庭授权' },
  ]

  const busy = ref(false)
  const elderOpen = ref(false)
  const elderName = ref('')
  const elderBirthDate = ref('')
  const elderAuthorization = ref(false)
  const elderError = ref('')
  const editElderOpen = ref(false)
  const editElderId = ref<number | null>(null)
  const editElderName = ref('')
  const editElderBirthDate = ref('')
  const editElderError = ref('')
  const invitationBusy = ref<number | null>(null)
  const preferenceTag = ref('')
  const preferenceBusy = ref(false)

  async function addPreference() {
    if (!family.selected || !family.permissions?.canManageFamily || !preferenceTag.value.trim()) return
    preferenceBusy.value = true
    const ok = await family.addPreference(family.selected.id, preferenceTag.value)
    preferenceBusy.value = false
    if (ok) {
      ui.notify('服务偏好已添加')
      preferenceTag.value = ''
    } else ui.notify(family.preferencesError, 'danger')
  }

  async function removePreference(tag: string) {
    if (!family.selected || !family.permissions?.canManageFamily) return
    preferenceBusy.value = true
    const ok = await family.removePreference(family.selected.id, tag)
    preferenceBusy.value = false
    ui.notify(ok ? '服务偏好已移除' : family.preferencesError, ok ? 'default' : 'danger')
  }

  function openElder() {
    elderName.value = ''
    elderBirthDate.value = ''
    elderAuthorization.value = false
    elderError.value = ''
    elderOpen.value = true
  }

  async function submitElder() {
    if (!elderName.value.trim()) {
      elderError.value = '请输入姓名'
      return
    }
    if (!elderAuthorization.value) {
      elderError.value = '请先确认您有权创建该档案'
      return
    }
    busy.value = true
    const ok = await family.createElder(elderName.value, elderBirthDate.value || null, true)
    busy.value = false
    if (ok) {
      elderOpen.value = false
      ui.notify('档案已创建')
    } else elderError.value = family.error
  }

  async function respondInvitation(id: number, accept: boolean) {
    invitationBusy.value = id
    const ok = await family.respondInvitation(id, accept)
    invitationBusy.value = null
    if (ok) ui.notify(accept ? '已接受邀请；可查看的档案将显示在下方' : '已拒绝邀请')
  }

  function openEditElder() {
    if (!family.selected || !family.permissions?.canManageFamily || family.detailLoading) return
    editElderId.value = family.selected.id
    editElderName.value = family.selected.fullName
    editElderBirthDate.value = family.selected.birthDate ?? ''
    editElderError.value = ''
    editElderOpen.value = true
  }

  async function submitEditElder() {
    if (!editElderName.value.trim()) {
      editElderError.value = '请输入姓名'
      return
    }
    if (editElderId.value === null) return
    busy.value = true
    const ok = await family.updateElder(
      editElderId.value,
      editElderName.value,
      editElderBirthDate.value || null,
    )
    busy.value = false
    if (ok) {
      editElderOpen.value = false
      ui.notify('档案资料已修改')
    } else editElderError.value = family.error
  }

  function openInvite() {
    inviteName.value = ''
    inviteError.value = ''
    inviteOpen.value = true
  }

  async function submitInvite() {
    if (!family.selected) return
    if (!inviteName.value.trim()) {
      inviteError.value = '请输入对方的登录账号'
      return
    }
    busy.value = true
    const ok = await family.invite(family.selected.id, inviteName.value, { ...invitePerm })
    busy.value = false
    if (ok) {
      inviteOpen.value = false
      ui.notify('邀请已发出，对方在我的家庭中确认后生效')
    } else {
      inviteError.value = family.error
    }
  }

  // ---- 撤销授权 ----
  const revokeOpen = ref(false)
  const revokeTarget = ref<GrantRow | null>(null)

  function askRevoke(grant: GrantRow) {
    revokeTarget.value = grant
    revokeOpen.value = true
  }

  async function doRevoke() {
    if (!family.selected || !revokeTarget.value) return
    busy.value = true
    const ok = await family.revoke(family.selected.id, revokeTarget.value.account_id)
    busy.value = false
    revokeOpen.value = false
    ui.notify(ok ? '已撤销该成员的授权' : family.error, ok ? 'default' : 'danger')
  }

  // ---- 核验与争议 ----
  function grantLabel(grant: GrantRow): string {
    const items = [
      grant.can_book ? '预约' : '',
      grant.can_cancel ? '取消' : '',
      grant.can_confirm ? '确认' : '',
      grant.can_review ? '评价' : '',
      grant.can_manage_reminder ? '提醒' : '',
      grant.can_manage_family ? '授权' : '',
    ].filter(Boolean)
    return items.length ? items.join(' · ') : '仅查看'
  }

  const disputeOpen = ref(false)
  const disputeReason = ref('')
  const disputeError = ref('')

  async function doClaim() {
    if (!family.selected) return
    const ok = await family.requestClaim(family.selected.id)
    ui.notify(ok ? '核验申请已提交，等待管理员线下核验' : family.error, ok ? 'success' : 'danger')
  }

  function openDispute() {
    disputeError.value = ''
    disputeReason.value = ''
    disputeOpen.value = true
  }

  async function doDispute() {
    if (!family.selected) return
    if (!disputeReason.value.trim()) {
      disputeError.value = '请填写争议说明'
      return
    }
    busy.value = true
    const ok = await family.openDispute(family.selected.id, disputeReason.value)
    busy.value = false
    if (ok) {
      disputeOpen.value = false
      disputeReason.value = ''
      ui.notify('争议已提交，运营人员会跟进')
    } else {
      disputeError.value = family.error
    }
  }

  onMounted(() => {
    void family.load()
    void family.loadInvitations()
  })
</script>

<template>
  <PageHeader
    eyebrow="家庭管理"
    title="谁可以代为预约，写在这里。"
    description="档案与授权分开管理：档案来自老人档案接口，权限来自当前账号在该档案上的有效授权。"
  >
    <template #actions>
      <AppButton v-if="family.selected && family.permissions?.canManageFamily" @click="openInvite"
        ><AppIcon name="users" />邀请家人</AppButton
      >
      <AppButton variant="secondary" @click="openElder"> <AppIcon name="plus" />新增服务对象 </AppButton>
    </template>
  </PageHeader>

  <ErrorBanner
    v-if="family.error"
    :title="family.error"
    description="家庭档案加载失败，请稍后重试。"
    style="margin-top: var(--sp-5)"
  >
    <template #action>
      <AppButton variant="secondary" compact style="margin-left: auto" @click="family.load"
        >重新加载</AppButton
      >
    </template>
  </ErrorBanner>

  <div v-if="family.loading" class="card card--pad" style="margin-top: var(--sp-6)">
    <SkeletonBlock :rows="3" />
  </div>

  <section
    v-if="family.invitations.length || family.invitationsError"
    class="card card--pad"
    style="margin-top: var(--sp-6)"
    aria-labelledby="sec-invitations"
  >
    <h2 id="sec-invitations" class="section-title">待处理的家庭邀请</h2>
    <p v-if="family.invitationsError" role="alert">
      {{ family.invitationsError }}
      <AppButton variant="secondary" compact @click="family.loadInvitations">重试</AppButton>
    </p>
    <div v-for="invitation in family.invitations" :key="invitation.id" class="list__item">
      <div class="list__body">
        <p class="list__title">{{ invitation.elder_name }} 的家庭邀请</p>
        <p class="list__desc">
          有效期至 {{ formatFullDate(invitation.expires_at) }}；授予：{{
            [
              invitation.can_view_service && '查看服务',
              invitation.can_book && '预约',
              invitation.can_cancel && '取消',
              invitation.can_confirm && '确认完成',
              invitation.can_review && '评价',
              invitation.can_confirm_fee && '确认费用',
              invitation.can_manage_reminder && '管理提醒',
              invitation.can_manage_family && '管理授权',
            ]
              .filter(Boolean)
              .join('、') || '无操作权限'
          }}
        </p>
        <p v-if="!invitation.can_view_service" class="list__desc">
          未授予查看权限，接受后该档案不会出现在服务对象列表中。
        </p>
      </div>
      <div class="row">
        <AppButton
          variant="secondary"
          compact
          :loading="invitationBusy === invitation.id"
          @click="respondInvitation(invitation.id, false)"
          >拒绝</AppButton
        ><AppButton
          compact
          :loading="invitationBusy === invitation.id"
          @click="respondInvitation(invitation.id, true)"
          >接受邀请</AppButton
        >
      </div>
    </div>
  </section>

  <section v-if="!family.loading" style="margin-top: var(--sp-6)" aria-label="服务对象档案">
    <div class="elders">
      <button
        v-for="elder in family.elders"
        :key="elder.id"
        class="elder"
        type="button"
        :aria-pressed="family.selectedId === elder.id"
        @click="family.select(elder.id)"
      >
        <span class="elder__top">
          <span class="avatar" aria-hidden="true">{{ initial(elder.fullName) }}</span>
          <span style="display: grid">
            <span class="elder__name">{{ elder.fullName }}</span>
            <span class="elder__meta"
              >{{ family.ageOf(elder.birthDate) ?? '—' }} 岁 · 档案 #{{ elder.id }}</span
            >
          </span>
        </span>
        <span class="elder__meta">出生日期 {{ elder.birthDate ?? '未填写' }}</span>
      </button>
      <EmptyState
        v-if="!family.elders.length"
        title="还没有服务对象档案"
        description="您可以在获得授权后创建档案，或接受家人的邀请。"
      />
    </div>
  </section>

  <section
    v-if="family.selected"
    class="card card--pad"
    style="margin-top: var(--sp-6)"
    aria-labelledby="sec-basic"
  >
    <div class="section__head">
      <div>
        <h2 id="sec-basic" class="section-title">基本信息</h2>
        <p class="section__desc">档案字段由后端返回，未采集的字段不做补造。</p>
      </div>
      <AppButton
        v-if="!family.detailLoading && family.permissions?.canManageFamily"
        variant="secondary"
        compact
        @click="openEditElder"
        >修改资料</AppButton
      >
    </div>
    <div class="kv">
      <div class="kv__item">
        <span class="kv__label">姓名</span><span class="kv__value">{{ family.selected.fullName }}</span>
      </div>
      <div class="kv__item">
        <span class="kv__label">出生日期</span
        ><span class="kv__value num">{{ family.selected.birthDate ?? '—' }}</span>
      </div>
      <div class="kv__item">
        <span class="kv__label">年龄</span
        ><span class="kv__value num">{{ family.selectedAge ?? '—' }} 岁</span>
      </div>
      <div class="kv__item">
        <span class="kv__label">档案编号</span><span class="kv__value num">#{{ family.selected.id }}</span>
      </div>
    </div>
  </section>

  <section
    v-if="family.selected"
    class="card card--pad"
    style="margin-top: var(--sp-5)"
    aria-labelledby="sec-preferences"
  >
    <div class="section__head">
      <div>
        <h2 id="sec-preferences" class="section-title">服务偏好</h2>
        <p class="section__desc">偏好标签用于服务推荐排序与推荐理由；请使用简短、具体的服务需求。</p>
      </div>
    </div>
    <p v-if="family.preferencesError" class="banner banner--error" role="alert">
      {{ family.preferencesError }}
    </p>
    <div v-else-if="family.detailLoading"><SkeletonBlock :rows="1" /></div>
    <template v-else>
      <div v-if="family.preferences.length" class="row" style="flex-wrap: wrap; gap: var(--sp-2)">
        <AppTag v-for="tag in family.preferences" :key="tag" tone="neutral">
          {{ tag }}
          <AppButton
            v-if="family.permissions?.canManageFamily"
            :aria-label="`移除${tag}`"
            variant="ghost"
            compact
            :disabled="preferenceBusy"
            @click="removePreference(tag)"
            >移除{{ tag }}</AppButton
          >
        </AppTag>
      </div>
      <p v-else class="muted">尚未设置服务偏好，推荐将依据服务覆盖区域展示。</p>
      <div v-if="family.permissions?.canManageFamily" class="row" style="margin-top: var(--sp-4)">
        <AppField
          label="偏好标签"
          html-for="preference-tag"
          help="最多 60 个字符，例如：助浴、陪诊、行动不便。"
        >
          <AppInput id="preference-tag" v-model="preferenceTag" maxlength="60" @keyup.enter="addPreference" />
        </AppField>
        <AppButton :loading="preferenceBusy" :disabled="!preferenceTag.trim()" @click="addPreference">
          添加偏好
        </AppButton>
      </div>
    </template>
  </section>

  <section
    v-if="family.selected"
    class="card card--pad"
    style="margin-top: var(--sp-5)"
    aria-labelledby="sec-auth"
  >
    <div class="section__head">
      <div>
        <h2 id="sec-auth" class="section-title">我的权限</h2>
        <p class="section__desc">
          来自 GET /elders/{{ family.selected.id }}/permissions，决定本账号在该档案上能做什么。
        </p>
      </div>
      <AppTag :tone="family.permissions?.canBook ? 'success' : 'neutral'">
        {{ family.permissions?.canBook ? '可代为预约' : '仅可查看' }}
      </AppTag>
    </div>

    <div v-if="family.detailLoading"><SkeletonBlock :rows="2" /></div>
    <div v-else class="grid grid--4" style="gap: var(--sp-3)">
      <div
        v-for="row in permissionRows"
        :key="row.label"
        class="row"
        style="
          justify-content: space-between;
          padding: var(--sp-3) 0;
          border-top: 1px solid var(--c-line-soft);
        "
      >
        <span style="font-size: var(--fs-13)">{{ row.label }}</span>
        <AppTag :tone="row.allowed ? 'success' : 'neutral'">{{ row.allowed ? '允许' : '不允许' }}</AppTag>
      </div>
    </div>
  </section>

  <section
    v-if="family.selected"
    class="card card--pad"
    style="margin-top: var(--sp-5)"
    aria-labelledby="sec-grants"
  >
    <div class="section__head">
      <div>
        <h2 id="sec-grants" class="section-title">家庭授权</h2>
        <p class="section__desc">谁能代为预约、确认与查看，由这里的授权决定；撤销立即生效。</p>
      </div>
      <AppTag :tone="family.grants.length ? 'progress' : 'neutral'">
        共 {{ family.grants.length }} 位成员
      </AppTag>
    </div>

    <div v-if="family.detailLoading"><SkeletonBlock :rows="2" /></div>
    <div v-else-if="family.grantsError" class="banner banner--info" role="note">
      <AppIcon name="info" />
      <div class="banner__body">
        <p class="banner__title">无法查看成员授权</p>
        <p class="banner__desc">{{ family.grantsError }}</p>
      </div>
    </div>
    <div v-else-if="family.grants.length" class="list" style="margin-top: var(--sp-2)">
      <div
        v-for="grant in family.grants"
        :key="grant.account_id"
        class="list__item"
        style="padding-inline: 0"
      >
        <span class="avatar" aria-hidden="true">{{ initial(grant.display_name) }}</span>
        <div class="list__body">
          <p class="list__title">
            {{ grant.display_name }}
            <AppTag :tone="grant.status === 'ACTIVE' ? 'success' : 'neutral'">{{
              grant.status === 'ACTIVE' ? '生效中' : '已停用'
            }}</AppTag>
          </p>
          <p class="list__desc">账号 {{ grant.username }} · 权限：{{ grantLabel(grant) }}</p>
        </div>
        <div class="list__end">
          <AppButton v-if="grant.status === 'ACTIVE'" variant="ghost" compact @click="askRevoke(grant)"
            >撤销授权</AppButton
          >
        </div>
      </div>
    </div>
    <EmptyState
      v-else
      title="还没有其他家庭成员"
      description="邀请家人后，对方在我的家庭中确认即可代为预约。"
    />
  </section>

  <section
    v-if="family.selected"
    class="card card--pad"
    style="margin-top: var(--sp-5)"
    aria-labelledby="sec-verify"
  >
    <div class="section__head">
      <div>
        <h2 id="sec-verify" class="section-title">核验与争议</h2>
        <p class="section__desc">身份核验与争议提交后进入运营协调，处理进度可在此跟踪。</p>
      </div>
      <div class="row">
        <AppButton variant="secondary" compact @click="doClaim">申请本人核验</AppButton>
        <AppButton variant="ghost" compact @click="openDispute">提交争议</AppButton>
      </div>
    </div>

    <div class="grid grid--2" style="gap: var(--sp-5)">
      <div>
        <h3 class="section-title" style="font-size: var(--fs-16); margin-bottom: var(--sp-3)">身份核验</h3>
        <div v-if="family.claims.length">
          <div v-for="claim in family.claims" :key="claim.id" class="auth-row">
            <span class="avatar" aria-hidden="true">{{ initial(claim.account_name) }}</span>
            <div class="auth-row__main">
              <p class="strong">{{ claim.account_name }} · 申请核验</p>
              <p class="muted" style="font-size: var(--fs-13)">
                申请于 {{ formatFullDate(claim.requested_at)
                }}{{ claim.review_note ? ` · ${claim.review_note}` : '' }}
              </p>
            </div>
            <div class="auth-row__end"><StatusTag :value="claim.status" dict="verify" /></div>
          </div>
        </div>
        <EmptyState v-else title="暂无核验申请" description="需要新增档案或变更归属时，由家属发起核验。" />
      </div>

      <div>
        <h3 class="section-title" style="font-size: var(--fs-16); margin-bottom: var(--sp-3)">家庭争议</h3>
        <div v-if="family.disputes.length">
          <div v-for="dispute in family.disputes" :key="dispute.id" class="auth-row">
            <span class="avatar" aria-hidden="true">{{ initial(dispute.reporter_name) }}</span>
            <div class="auth-row__main">
              <p class="strong">{{ dispute.reason }}</p>
              <p class="muted" style="font-size: var(--fs-13)">
                {{ dispute.reporter_name }} · {{ formatFullDate(dispute.reported_at) }}
              </p>
            </div>
            <div class="auth-row__end"><StatusTag :value="dispute.status" dict="assignment" /></div>
          </div>
        </div>
        <EmptyState
          v-else
          title="暂无家庭争议"
          description="对授权或档案有异议时，可在此提交争议并由运营协调。"
        />
      </div>
    </div>
  </section>

  <AppDialog v-model="editElderOpen" title="修改服务对象资料" subtitle="请核对姓名和出生日期后保存。">
    <p v-if="editElderError" role="alert" class="banner banner--error">{{ editElderError }}</p>
    <AppField label="姓名" html-for="edit-elder-name" required
      ><AppInput id="edit-elder-name" v-model="editElderName" maxlength="80"
    /></AppField>
    <AppField label="出生日期（选填）" html-for="edit-elder-birth-date"
      ><AppInput id="edit-elder-birth-date" v-model="editElderBirthDate" type="date"
    /></AppField>
    <template #footer
      ><AppButton variant="ghost" @click="editElderOpen = false">取消</AppButton
      ><AppButton :loading="busy" @click="submitEditElder">保存资料</AppButton></template
    >
  </AppDialog>

  <AppDialog
    v-model="elderOpen"
    title="新增服务对象"
    subtitle="请填写真实信息，并确认您有权为该服务对象创建档案。"
  >
    <p v-if="elderError" role="alert" class="banner banner--error">{{ elderError }}</p>
    <AppField label="姓名" html-for="elder-name" required
      ><AppInput id="elder-name" v-model="elderName" maxlength="80" autocomplete="name"
    /></AppField>
    <AppField label="出生日期（选填）" html-for="elder-birth-date"
      ><AppInput id="elder-birth-date" v-model="elderBirthDate" type="date"
    /></AppField>
    <label class="check"
      ><input id="elder-authorization" v-model="elderAuthorization" type="checkbox" /><span
        >我确认有权创建并管理该服务对象的档案。</span
      ></label
    >
    <template #footer
      ><AppButton variant="ghost" @click="elderOpen = false">取消</AppButton
      ><AppButton :loading="busy" @click="submitElder">创建档案</AppButton></template
    >
  </AppDialog>

  <!-- 邀请家人 -->
  <AppDialog
    v-model="inviteOpen"
    wide
    title="邀请家人"
    subtitle="按账号邀请，对方确认后即可按下列权限代为操作。"
  >
    <div v-if="inviteError" class="banner banner--error" role="alert">
      <AppIcon name="alert" />
      <div class="banner__body">
        <p class="banner__title">无法邀请</p>
        <p class="banner__desc">{{ inviteError }}</p>
      </div>
    </div>
    <AppField
      label="对方登录账号"
      html-for="inv-username"
      required
      help="账号由平台统一开通，例如 family02。"
    >
      <AppInput id="inv-username" v-model="inviteName" autocomplete="off" placeholder="请输入账号" />
    </AppField>
    <fieldset style="border: 0; padding: 0; margin: var(--sp-4) 0 0">
      <legend class="field__label" style="margin-bottom: var(--sp-3)">授予权限</legend>
      <div class="grid grid--2" style="gap: var(--sp-2)">
        <label v-for="item in INVITE_LABELS" :key="item.key" class="check">
          <input v-model="invitePerm[item.key]" type="checkbox" />
          <span>{{ item.label }}</span>
        </label>
      </div>
    </fieldset>
    <template #footer>
      <AppButton variant="ghost" @click="inviteOpen = false">取消</AppButton>
      <AppButton :loading="busy" @click="submitInvite">发出邀请</AppButton>
    </template>
  </AppDialog>

  <!-- 撤销授权 -->
  <AppDialog
    v-model="revokeOpen"
    title="撤销该成员的授权？"
    :subtitle="revokeTarget ? `撤销后 ${revokeTarget.display_name} 将无法再代为操作该档案。` : ''"
  >
    <div class="banner banner--warm" role="note">
      <AppIcon name="alert" />
      <div class="banner__body">
        <p class="banner__title">操作后果</p>
        <p class="banner__desc">撤销立即生效，对方已提交的订单不受影响；需要时可重新邀请。</p>
      </div>
    </div>
    <template #footer>
      <AppButton variant="ghost" @click="revokeOpen = false">保留授权</AppButton>
      <AppButton variant="danger" :loading="busy" @click="doRevoke">确认撤销</AppButton>
    </template>
  </AppDialog>

  <!-- 提交争议 -->
  <AppDialog v-model="disputeOpen" title="提交家庭争议" subtitle="说明会同步给运营人员，由其协调处理。">
    <div v-if="disputeError" class="banner banner--error" role="alert">
      <AppIcon name="alert" />
      <div class="banner__body">
        <p class="banner__title">无法提交</p>
        <p class="banner__desc">{{ disputeError }}</p>
      </div>
    </div>
    <AppField label="争议说明" html-for="dis-reason" required help="请说明时间、涉及成员与诉求。">
      <AppTextarea
        id="dis-reason"
        v-model="disputeReason"
        placeholder="例如：2026-10-20 起无法查看该档案的服务记录。"
      />
    </AppField>
    <template #footer>
      <AppButton variant="ghost" @click="disputeOpen = false">取消</AppButton>
      <AppButton :loading="busy" @click="doDispute">提交争议</AppButton>
    </template>
  </AppDialog>
</template>
