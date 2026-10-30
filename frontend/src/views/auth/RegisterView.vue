<script setup lang="ts">
  import { reactive, ref } from 'vue'
  import { useRouter } from 'vue-router'
  import AuthLayout from '@/layouts/AuthLayout.vue'
  import AppField from '@/components/ui/AppField.vue'
  import AppInput from '@/components/ui/AppInput.vue'
  import AppButton from '@/components/ui/AppButton.vue'
  import AppIcon from '@/components/ui/AppIcon.vue'
  import { useSessionStore } from '@/stores/session'
  import { authApi } from '@/api/endpoints'
  import { ApiError } from '@/api/client'
  import { useUiStore } from '@/stores/ui'

  const router = useRouter()
  const session = useSessionStore()
  const ui = useUiStore()

  const form = reactive({ username: '', name: '', email: '', password: '', confirm: '', agree: false })
  const errors = reactive({ username: '', name: '', email: '', password: '', confirm: '', agree: '' })
  const loading = ref(false)
  const submitError = ref('')

  const points = [
    { icon: 'shield', title: '服务过程有记录', desc: '接单、开始、完成、异常都写入订单时间轴，家属可查。' },
    { icon: 'users', title: '家庭授权可撤回', desc: '谁可以代为预约、结算，都由档案里的授权决定。' },
  ] as const

  function validate(): boolean {
    errors.username = /^[a-z0-9_]{4,32}$/.test(form.username) ? '' : '账号需为 4–32 位小写字母、数字或下划线'
    errors.name = form.name.trim() ? '' : '请输入您的称呼'
    errors.email = !form.email || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email) ? '' : '邮箱格式不正确'
    errors.password = form.password.length >= 6 ? '' : '密码至少 6 位'
    errors.confirm = form.confirm === form.password ? '' : '两次输入的密码不一致'
    errors.agree = form.agree ? '' : '请先阅读并同意服务说明'
    return Object.values(errors).every((message) => !message)
  }

  async function submit() {
    if (!validate()) return
    loading.value = true
    try {
      await authApi.register({
        username: form.username.trim(),
        name: form.name.trim(),
        password: form.password,
        email: form.email.trim() || undefined,
      })
      await session.login(form.username.trim(), form.password)
      ui.notify('注册成功，已为你登录')
      await router.replace('/user')
    } catch (reason) {
      submitError.value = reason instanceof ApiError ? reason.message : '注册失败，请稍后重试'
    } finally {
      loading.value = false
    }
  }
</script>

<template>
  <AuthLayout
    :title="['先把家人安顿好，', '再谈其他。']"
    lede="注册后可以建立长辈档案、预约服务并跟踪每一次上门安排。所有授权都需要本人或监护人确认。"
  >
    <template #extras>
      <ul style="display: grid; gap: var(--sp-4); margin-top: var(--sp-7); max-width: 520px">
        <li
          v-for="point in points"
          :key="point.title"
          class="row"
          style="align-items: flex-start; flex-wrap: nowrap"
        >
          <span class="empty__icon" style="width: 36px; height: 36px; flex: 0 0 auto" aria-hidden="true">
            <AppIcon :name="point.icon" size="18px" />
          </span>
          <span style="display: grid; gap: 2px">
            <span class="strong">{{ point.title }}</span>
            <span class="muted" style="font-size: var(--fs-13)">{{ point.desc }}</span>
          </span>
        </li>
      </ul>
    </template>

    <form class="card" novalidate @submit.prevent="submit">
      <h2 class="auth__form-title">注册家属账号</h2>
      <p class="auth__form-sub">注册完成后需由街道服务站核验身份，才能为长辈提交预约。</p>

      <div class="auth__fields">
        <div v-if="submitError" class="banner banner--error" role="alert">
          <AppIcon name="alert" />
          <div class="banner__body">
            <p class="banner__title">注册失败</p>
            <p class="banner__desc">{{ submitError }}</p>
          </div>
        </div>

        <AppField
          v-slot="{ describedby }"
          label="账号"
          html-for="reg-username"
          required
          help="登录时使用，注册后不可修改。"
          :error="errors.username"
        >
          <AppInput
            id="reg-username"
            v-model="form.username"
            name="username"
            autocomplete="username"
            placeholder="4–32 位小写字母、数字或下划线"
            :invalid="!!errors.username"
            :aria-describedby="describedby"
          />
        </AppField>

        <AppField label="称呼" html-for="reg-name" required :error="errors.name">
          <AppInput
            id="reg-name"
            v-model="form.name"
            name="name"
            autocomplete="name"
            placeholder="请输入您的称呼"
            :invalid="!!errors.name"
          />
        </AppField>

        <AppField
          v-slot="{ describedby }"
          label="邮箱（选填）"
          html-for="reg-email"
          help="用于接收订单通知，可稍后在账号设置中补充。"
          :error="errors.email"
        >
          <AppInput
            id="reg-email"
            v-model="form.email"
            name="email"
            type="email"
            autocomplete="email"
            placeholder="仅用于联系，不参与登录"
            :invalid="!!errors.email"
            :aria-describedby="describedby"
          />
        </AppField>

        <div class="grid grid--2" style="gap: var(--sp-4)">
          <AppField
            v-slot="{ describedby }"
            label="密码"
            html-for="reg-password"
            required
            help="建议包含字母与数字。"
            :error="errors.password"
          >
            <AppInput
              id="reg-password"
              v-model="form.password"
              name="new-password"
              type="password"
              autocomplete="new-password"
              placeholder="至少 6 位"
              :invalid="!!errors.password"
              :aria-describedby="describedby"
            />
          </AppField>
          <AppField label="确认密码" html-for="reg-password2" required :error="errors.confirm">
            <AppInput
              id="reg-password2"
              v-model="form.confirm"
              name="confirm-password"
              type="password"
              autocomplete="new-password"
              placeholder="再次输入密码"
              :invalid="!!errors.confirm"
            />
          </AppField>
        </div>

        <div>
          <label class="check">
            <input v-model="form.agree" type="checkbox" name="agree" :aria-invalid="!!errors.agree" />
            <span>我已阅读并同意《服务说明》与《隐私保护规则》，并确认以上信息真实有效。</span>
          </label>
          <p v-if="errors.agree" class="field__error" style="margin-top: var(--sp-2)">
            <AppIcon name="alert" size="14px" />{{ errors.agree }}
          </p>
        </div>

        <AppButton type="submit" size="lg" block :loading="loading">提交注册</AppButton>
      </div>

      <p class="auth__legal">提交后 1 个工作日内完成核验，结果会通过站内消息通知。</p>

      <div class="auth__switch-role">
        <span>已有账号？</span>
        <RouterLink class="link-arrow" to="/login">返回登录 →</RouterLink>
      </div>
    </form>
  </AuthLayout>
</template>
