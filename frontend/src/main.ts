import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElCheckbox, ElDialog, ElForm, ElFormItem, ElInput, ElInputNumber,
  ElOption, ElRate, ElSelect, ElSwitch, ElTabPane, ElTabs } from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './router'
import './styles/main.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
for (const component of [ElCheckbox, ElDialog, ElForm, ElFormItem, ElInput, ElInputNumber,
  ElOption, ElRate, ElSelect, ElSwitch, ElTabPane, ElTabs]) app.use(component)
app.mount('#app')
