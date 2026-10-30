<script setup lang="ts">
  import { computed } from 'vue'
  import BrandMark from '@/components/ui/BrandMark.vue'
  import SeniorToggle from '@/components/ui/SeniorToggle.vue'

  const props = withDefaults(defineProps<{ title: string | string[]; lede: string; footer?: string }>(), {
    footer: '专业服务，温暖相伴。',
  })

  const lines = computed(() => (Array.isArray(props.title) ? props.title : [props.title]))
</script>

<template>
  <a class="skip" href="#main">跳到主内容</a>
  <div class="auth">
    <section class="auth__story" aria-label="平台介绍">
      <div class="brand">
        <BrandMark />
        <span class="brand__text">居家养老智能生活服务平台</span>
      </div>

      <div class="auth__body">
        <h1 class="auth__title">
          <template v-for="(line, index) in lines" :key="line">
            {{ line }}<br v-if="index < lines.length - 1" />
          </template>
        </h1>
        <p class="auth__lede">{{ lede }}</p>
        <slot name="extras" />
      </div>

      <p class="auth__foot">{{ footer }}</p>
    </section>

    <section class="auth__panel">
      <SeniorToggle />
      <main id="main" tabindex="-1" class="auth__form">
        <slot />
      </main>
    </section>
  </div>
</template>
