import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { initAuth } from './auth/useAuth'
import './style.css'

// Auth must complete before mounting so the OIDC callback is consumed
// before Vue Router resolves the first navigation.
initAuth().then(() => {
  createApp(App).use(router).mount('#app')
})
