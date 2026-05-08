import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { initAuth, useAuth } from './auth/useAuth'
import './style.css'

// Auth must complete before mounting so the OIDC callback is consumed
// before Vue Router resolves the first navigation.
initAuth().then(() => {
  const { isAuthenticated, login } = useAuth()
  if (!isAuthenticated()) {
    // No active session: redirect to the identity provider.
    // The app is not mounted — no router guard loop can form.
    login()
    return
  }
  createApp(App).use(router).mount('#app')
})
