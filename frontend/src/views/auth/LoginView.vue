<script setup lang="ts">
  import { reactive, ref } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import AuthLayout from '@/layouts/AuthLayout.vue'
  import AppField from '@/components/ui/AppField.vue'
  import AppInput from '@/components/ui/AppInput.vue'
  import AppButton from '@/components/ui/AppButton.vue'
  import AppIcon from '@/components/ui/AppIcon.vue'
  import { useSessionStore } from '@/stores/session'
  import { useUiStore } from '@/stores/ui'

  const route = useRoute()
  const router = useRouter()
  const session = useSessionStore()
  const ui = useUiStore()

  const username = ref('')
  const password = ref('')
  const remember = ref(true)
  const errors = reactive({ username: '', password: '' })
  const submitError = ref('')
  const submitting = ref(false)

  const services = [
    { icon: 'meal', name: '助餐服务', desc: '上门协助备餐' },
    { icon: 'broom', name: '居家保洁', desc: '舒适整洁的家' },
    { icon: 'bag', name: '陪诊服务', desc: '就医流程陪伴' },
  ] as const

  const HOME = { USER: '/user', WORKER: '/worker', ADMIN: '/admin' } as const

  function validate(): boolean {
    errors.username = username.value.trim() ? '' : '请输入登录账号'
    errors.password = password.value ? '' : '请输入密码'
    return !errors.username && !errors.password
  }

  async function submit() {
    submitError.value = ''
    if (!validate()) return
    submitting.value = true
    try {
      const user = await session.login(username.value.trim(), password.value)
      ui.notify(`欢迎回来，${user.name}`)
      const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
      await router.replace(redirect || HOME[user.role])
    } catch {
      submitError.value = session.error || '登录失败，请检查账号与密码'
    } finally {
      submitting.value = false
    }
  }
</script>

<template>
  <AuthLayout
    :title="['让每一次照护，', '都更安心。']"
    lede="把可信的上门生活服务、家人的关怀与清晰的安排，放在同一个地方。"
  >
    <template #extras>
      <div class="auth__cards">
        <div v-for="item in services" :key="item.name" class="auth__card">
          <span
            class="svc-card__icon"
            style="width: 36px; height: 36px; border-radius: var(--r-control)"
            aria-hidden="true"
          >
            <AppIcon :name="item.icon" size="20px" />
          </span>
          <strong>{{ item.name }}</strong>
          <small>{{ item.desc }}</small>
        </div>
      </div>
    </template>

    <form class="card" novalidate @submit.prevent="submit">
      <h2 class="auth__form-title">登录</h2>
      <p class="auth__form-sub">账号由平台统一开通。家属、服务人员与管理员使用同一入口登录。</p>

      <div class="auth__fields">
        <div v-if="submitError" class="banner banner--error" role="alert">
          <AppIcon name="alert" />
          <div class="banner__body">
            <p class="banner__title">登录失败</p>
            <p class="banner__desc">{{ submitError }}</p>
          </div>
        </div>

        <AppField
          v-slot="{ describedby }"
          label="账号"
          html-for="login-username"
          required
          help="演示环境可用 family01 / worker01 / admin。"
          :error="errors.username"
        >
          <AppInput
            id="login-username"
            v-model="username"
            name="username"
            autocomplete="username"
            placeholder="请输入登录账号"
            :invalid="!!errors.username"
            :aria-describedby="describedby"
          />
        </AppField>

        <AppField label="密码" html-for="login-password" required :error="errors.password">
          <AppInput
            id="login-password"
            v-model="password"
            name="password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            :invalid="!!errors.password"
          />
        </AppField>

        <div class="auth__aux">
          <label class="check">
            <input v-model="remember" type="checkbox" name="remember" />
            <span>在这台电脑上保持登录</span>
          </label>
          <button
            class="linkbtn linkbtn--muted"
            type="button"
            style="min-height: auto"
            @click="ui.notify('请联系所在街道服务站重置密码', 'default')"
          >
            忘记密码？
          </button>
        </div>

        <AppButton type="submit" size="lg" block :loading="submitting">登录</AppButton>
      </div>

      <p class="auth__legal">
        登录即表示同意平台的《服务说明》与《隐私保护规则》。<br />
        账号与密码由本地 `.env` 中的演示账号配置提供，不在此处展示。
      </p>

      <div class="auth__switch-role">
        <span>还没有账号？</span>
        <RouterLink class="link-arrow" to="/register">注册家属账号 →</RouterLink>
      </div>
    </form>
  </AuthLayout>
</template>
