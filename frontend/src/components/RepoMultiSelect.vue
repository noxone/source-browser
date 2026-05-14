<template>
  <div class="relative" ref="containerRef">
    <!-- Trigger button -->
    <button
      type="button"
      @click="toggleOpen"
      class="inline-flex items-center gap-2 min-w-48 px-3 py-1.5 rounded-lg border border-gray-300 bg-white text-sm text-gray-700 shadow-sm hover:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500 transition-colors"
    >
      <!-- Selected chips (1–5) -->
      <template v-if="selectedRepos.length > 0 && selectedRepos.length <= CHIP_THRESHOLD">
        <span
          v-for="repo in selectedRepos"
          :key="repo.id"
          class="inline-flex items-center px-1.5 py-0.5 rounded-full text-xs font-medium bg-indigo-100 text-indigo-700"
        >{{ repo.name }}</span>
      </template>

      <!-- Count label (≥6) -->
      <span v-else-if="selectedRepos.length > CHIP_THRESHOLD" class="text-indigo-700 font-medium">
        {{ selectedRepos.length }} repositories selected
      </span>

      <!-- Placeholder -->
      <span v-else class="text-gray-400">All repositories</span>

      <span class="flex-1" />

      <!-- Clear button -->
      <span
        v-if="modelValue.length > 0"
        @click.stop="$emit('update:modelValue', [])"
        class="text-gray-400 hover:text-gray-600 leading-none"
        title="Clear selection"
      >×</span>

      <!-- Chevron -->
      <svg
        class="w-4 h-4 text-gray-400 transition-transform"
        :class="{ 'rotate-180': open }"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        viewBox="0 0 24 24"
      >
        <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
      </svg>
    </button>

    <!-- Dropdown panel -->
    <div
      v-if="open"
      class="absolute left-0 top-full mt-1 z-50 w-72 rounded-lg border border-gray-200 bg-white shadow-lg"
    >
      <!-- Search input -->
      <div class="p-2 border-b border-gray-100">
        <input
          ref="searchInputRef"
          v-model="filterText"
          type="text"
          placeholder="Filter repositories…"
          class="block w-full rounded-md border border-gray-300 px-2.5 py-1.5 text-sm text-gray-900 placeholder-gray-400 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
          @keydown.escape="close"
        />
      </div>

      <!-- Select all / clear row -->
      <div class="flex items-center justify-between px-3 py-1.5 border-b border-gray-100 text-xs text-gray-500">
        <button type="button" class="hover:text-indigo-600" @click="selectAllVisible">Select all</button>
        <button type="button" class="hover:text-gray-700" @click="$emit('update:modelValue', [])">Clear</button>
      </div>

      <!-- Repo list -->
      <ul class="max-h-64 overflow-y-auto py-1">
        <li v-if="filteredRepos.length === 0" class="px-3 py-2 text-sm text-gray-400 italic">
          No repositories match "{{ filterText }}"
        </li>
        <li
          v-for="repo in filteredRepos"
          :key="repo.id"
          class="flex items-center gap-2 px-3 py-1.5 cursor-pointer hover:bg-gray-50 select-none"
          @click="toggleRepo(repo.id)"
        >
          <input
            type="checkbox"
            :checked="modelValue.includes(repo.id)"
            class="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500 pointer-events-none"
            tabindex="-1"
          />
          <span class="text-sm text-gray-800">{{ repo.name }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onUnmounted } from 'vue'
import type { Repository } from '../types/repository'

const CHIP_THRESHOLD = 5

const props = defineProps<{
  repositories: Repository[]
  modelValue: number[]
}>()

const emit = defineEmits<{
  'update:modelValue': [ids: number[]]
}>()

const open = ref(false)
const filterText = ref('')
const containerRef = ref<HTMLElement | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)

const selectedRepos = computed(() =>
  props.repositories.filter(r => props.modelValue.includes(r.id))
)

const filteredRepos = computed(() => {
  const q = filterText.value.trim().toLowerCase()
  if (!q) return props.repositories
  return props.repositories.filter(r => r.name.toLowerCase().includes(q))
})

function toggleOpen() {
  if (open.value) {
    close()
  } else {
    open.value = true
    filterText.value = ''
    nextTick(() => searchInputRef.value?.focus())
    document.addEventListener('mousedown', handleClickOutside)
  }
}

function close() {
  open.value = false
  document.removeEventListener('mousedown', handleClickOutside)
}

function handleClickOutside(event: MouseEvent) {
  if (containerRef.value && !containerRef.value.contains(event.target as Node)) {
    close()
  }
}

function toggleRepo(id: number) {
  const current = props.modelValue
  const idx = current.indexOf(id)
  if (idx >= 0) {
    emit('update:modelValue', current.filter(v => v !== id))
  } else {
    emit('update:modelValue', [...current, id])
  }
}

function selectAllVisible() {
  const visibleIds = filteredRepos.value.map(r => r.id)
  const merged = [...new Set([...props.modelValue, ...visibleIds])]
  emit('update:modelValue', merged)
}

onUnmounted(() => {
  document.removeEventListener('mousedown', handleClickOutside)
})
</script>
