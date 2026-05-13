import { createRouter, createWebHashHistory } from 'vue-router'
import SearchView from '../views/SearchView.vue'
import AdminView from '../views/AdminView.vue'
import UserSettingsView from '../views/UserSettingsView.vue'
import FileView from '../views/FileView.vue'
import { useAuth } from '../auth/useAuth'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      redirect: { name: 'search' }
    },
    {
      path: '/search',
      name: 'search',
      component: SearchView,
      meta: { requiresAuth: true }
    },
    {
      path: '/file/:fileId',
      name: 'file',
      component: FileView,
      meta: { requiresAuth: true }
    },
    {
      path: '/admin',
      name: 'admin',
      component: AdminView,
      meta: { requiresAuth: true }
    },
    {
      path: '/settings',
      name: 'settings',
      component: UserSettingsView,
      meta: { requiresAuth: true }
    }
  ]
})

// Safety net for in-app session expiry: block protected navigation but
// do NOT call login() here — that would cause router.go(-1) to re-fire
// this guard in a loop before signinRedirect() has finished navigating away.
router.beforeEach((to) => {
  if (to.meta.requiresAuth && !useAuth().isAuthenticated()) {
    return false
  }
})

export default router
