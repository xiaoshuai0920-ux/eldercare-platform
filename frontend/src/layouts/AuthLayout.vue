<template>
  <main class="auth-page">
    <section class="auth-story">
      <div class="brand-line"><BrandMark /><strong>居家养老智能生活服务平台</strong></div>
      <div class="story-body">
        <h1>让每一次照护，<br />都更安心。</h1>
        <p>把可信的上门生活服务、家人的关怀与清晰的安排，放在同一个地方。</p>
        <div class="service-list" aria-label="平台服务">
          <div><span class="service-icon">餐</span><strong>助餐服务</strong><small>上门协助备餐</small></div>
          <div><span class="service-icon">洁</span><strong>居家保洁</strong><small>舒适整洁的家</small></div>
          <div><span class="service-icon">陪</span><strong>陪诊服务</strong><small>就医流程陪伴</small></div>
        </div>
      </div>
      <p class="story-footer">专业服务，温暖相伴。</p>
    </section>
    <section class="auth-form-area"><div class="auth-form-wrap"><slot /></div></section>
  </main>
</template>
<script setup lang="ts">
import BrandMark from '../components/BrandMark.vue'
</script>
<style scoped>
.auth-page { min-height: 100vh; display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(440px, .85fr); background: #fff; }
.auth-story { min-height: 700px; padding: 42px clamp(36px, 6vw, 108px); display: flex; flex-direction: column; background: radial-gradient(circle at 85% 24%, #dcefe5 0, transparent 35%), linear-gradient(145deg, #f5f8f3, #e8f2eb); }
.brand-line { display: flex; align-items: center; gap: 13px; font-size: 21px; letter-spacing: -.03em; }
.story-body { margin: clamp(42px, 7vh, 84px) 0 auto; max-width: 650px; }
h1 { margin: 0 0 20px; font-size: clamp(52px, 5vw, 72px); line-height: 1.14; letter-spacing: -.06em; font-weight: 680; }
.story-body p { max-width: 510px; color: #547066; font-size: 19px; line-height: 1.75; }
.service-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin-top: 42px; }
.service-list div { min-height: 156px; display: flex; flex-direction: column; align-items: flex-start; padding: 20px; border: 1px solid #d5e7db; border-radius: 20px; background: rgba(255,255,255,.72); }
.service-icon { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 14px; background: #dff2e7; color: #12775b; font-weight: 700; margin-bottom: 12px; }
.service-list strong { font-size: 16px; }
.service-list small { color: #607d6d; margin-top: 5px; }
.story-footer { margin: 0; color: #719184; font-size: 14px; }
.auth-form-area { display: grid; place-items: center; padding: 28px; background: radial-gradient(circle at 85% 8%, #cfeade, transparent 37%), #e8f3ed; }
.auth-form-wrap { width: min(100%, 520px); padding: 40px; border: 1px solid #e7eee9; border-radius: 26px; background: #fff; box-shadow: 0 18px 60px rgba(37, 83, 61, .09); }
@media (max-width: 850px) { .auth-page { grid-template-columns: 1fr; } .auth-story { min-height: auto; padding: 28px 32px; } .story-body { margin: 40px 0; } h1 { font-size: 46px; } .story-footer { display: none; } .auth-form-area { padding: 42px 32px 70px; } }
@media (max-height: 720px) and (min-width: 851px) { .auth-story { min-height: 100vh; padding-top: 24px; padding-bottom: 20px; } .story-body { margin-top: 26px; } h1 { font-size: clamp(44px, 4.2vw, 58px); } .story-body p { font-size: 17px; line-height: 1.5; } .service-list { margin-top: 24px; } .service-list div { min-height: 126px; padding: 12px; } .auth-form-area { padding: 14px 24px; } .auth-form-wrap { padding: 16px 32px; } }
@media (max-width: 560px) { .service-list { grid-template-columns: 1fr 1fr 1fr; gap: 6px; } .service-list div { min-height: 124px; padding: 10px; border-radius: 12px; } .service-list strong { font-size: 13px; } .service-list small { font-size: 11px; } .auth-form-area { padding: 24px 16px 48px; } .auth-form-wrap { padding: 28px 22px; } }
</style>
