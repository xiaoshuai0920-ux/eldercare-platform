import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElCheckbox, ElDialog, ElForm, ElFormItem, ElInput, ElInputNumber,
  ElOption, ElRate, ElSelect, ElSwitch, ElTabPane, ElTabs } from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './router'
import { initSeniorMode } from './composables/useSeniorMode'
import './styles/tokens.css'
import './styles/base.css'
import './styles/senior.css'

initSeniorMode()

const app = createApp(App)
app.use(createPinia())
app.use(router)
for (const component of [ElCheckbox, ElDialog, ElForm, ElFormItem, ElInput, ElInputNumber,
  ElOption, ElRate, ElSelect, ElSwitch, ElTabPane, ElTabs]) app.use(component)
app.mount('#app')
