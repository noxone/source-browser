import { createRouter, createWebHashHistory } from 'vue-router'
import RepositoryListView from '../views/RepositoryListView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'repositories',
      component: RepositoryListView
    }
  ]
})

export default router
