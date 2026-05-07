<template>
  <div class="min-h-screen bg-gray-50">
    <header class="bg-white border-b border-gray-200 shadow-sm">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4 flex items-center gap-3">
        <svg class="w-7 h-7 text-indigo-600" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M3 7a2 2 0 012-2h14a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2V7z"/>
          <path stroke-linecap="round" stroke-linejoin="round" d="M8 12h8M8 8h4"/>
        </svg>
        <h1 class="text-xl font-semibold text-gray-900">Java Source Viewer</h1>
      </div>
    </header>
    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <RouterView />
    </main>
  </div>
</template>

<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>
