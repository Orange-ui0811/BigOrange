import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './styles/theme.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import DOMPurify from 'dompurify'

const app = createApp(App)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.use(ElementPlus)
app.use(router)
app.directive('safe-html', {
  mounted: (el, binding) => { el.innerHTML = DOMPurify.sanitize(String(binding.value ?? '')) },
  updated: (el, binding) => { el.innerHTML = DOMPurify.sanitize(String(binding.value ?? '')) }
})
app.mount('#app')
