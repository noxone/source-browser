<template>
  <div class="flex h-screen overflow-hidden bg-gray-50">
    <NavigationSidebar />
    <main class="flex-1 overflow-hidden">
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import NavigationSidebar from './components/NavigationSidebar.vue'
import { fetchCurrentUserAccount } from './composables/useCurrentUserAccount'

onMounted(() => fetchCurrentUserAccount())
</script>
