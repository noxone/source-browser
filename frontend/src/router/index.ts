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

router.beforeEach(async (to) => {
  if (to.meta.requiresAuth) {
    const { isAuthenticated, login } = useAuth()
    if (!isAuthenticated()) {
      await login()
      return false
    }
  }
})

export default router
