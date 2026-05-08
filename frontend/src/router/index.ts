import { createRouter, createWebHashHistory } from 'vue-router'
import RepositoryListView from '../views/RepositoryListView.vue'
import { useAuth } from '../auth/useAuth'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'repositories',
      component: RepositoryListView,
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
