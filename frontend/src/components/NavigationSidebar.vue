<template>
  <nav
    class="flex flex-col bg-gray-900 text-white h-screen sticky top-0 shrink-0 transition-all duration-200 overflow-hidden"
    :class="expanded ? 'w-52' : 'w-14'"
  >
    <!-- Logo + toggle -->
    <div class="flex items-center h-14 border-b border-gray-700 shrink-0 px-2 gap-2">
      <button
        @click="expanded = !expanded"
        class="flex items-center justify-center w-10 h-10 rounded-lg text-indigo-400 hover:text-indigo-300 hover:bg-gray-700 transition-colors shrink-0"
        :title="expanded ? 'Collapse sidebar' : 'Expand sidebar'"
      >
        <svg class="w-6 h-6" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M3 7a2 2 0 012-2h14a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2V7z"/>
          <path stroke-linecap="round" stroke-linejoin="round" d="M8 12h8M8 8h4"/>
        </svg>
      </button>
      <span v-if="expanded" class="text-sm font-semibold text-white whitespace-nowrap overflow-hidden">
        Source Viewer
      </span>
    </div>

    <!-- Top actions -->
    <div class="flex flex-col gap-1 pt-2 px-2">
      <NavButton :to="{ name: 'search' }" label="Search" :expanded="expanded">
        <template #icon>
          <svg class="w-5 h-5 shrink-0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <circle cx="11" cy="11" r="8"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-4.35-4.35"/>
          </svg>
        </template>
      </NavButton>
    </div>

    <!-- Middle: future navigation items -->
    <div class="flex-1" />

    <!-- Bottom actions -->
    <div class="flex flex-col gap-1 pb-2 border-t border-gray-700 pt-2 px-2">
      <NavButton :to="{ name: 'admin' }" label="Admin" :expanded="expanded">
        <template #icon>
          <svg class="w-5 h-5 shrink-0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round"
              d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37a1.724 1.724 0 002.572-1.065z"/>
            <circle cx="12" cy="12" r="3"/>
          </svg>
        </template>
      </NavButton>

      <NavButton :to="{ name: 'settings' }" label="User Settings" :expanded="expanded">
        <template #icon>
          <svg class="w-5 h-5 shrink-0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round"
              d="M5.121 17.804A13.937 13.937 0 0112 16c2.5 0 4.847.655 6.879 1.804M15 10a3 3 0 11-6 0 3 3 0 016 0z"/>
          </svg>
        </template>
      </NavButton>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import NavButton from './NavButton.vue'

const expanded = ref(false)
</script>
