<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
    <div class="bg-white rounded-xl shadow-xl w-full max-w-3xl mx-4 overflow-hidden flex flex-col max-h-[80vh]">

      <!-- Header -->
      <div class="px-6 py-4 border-b border-gray-200 flex items-center justify-between flex-shrink-0">
        <div>
          <h2 class="text-base font-semibold text-gray-900">Repositories in group</h2>
          <p class="text-xs text-gray-500 mt-0.5">{{ groupName }}</p>
        </div>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600 transition-colors">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12"/>
          </svg>
        </button>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="flex items-center justify-center py-16 text-gray-400">
        <svg class="animate-spin w-5 h-5 mr-2" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
        </svg>
        Loading repositories…
      </div>

      <!-- Error -->
      <div v-else-if="error" class="px-6 py-5 text-sm text-red-700 bg-red-50 border-b border-red-200">
        <p class="font-semibold">Failed to load repositories</p>
        <p class="mt-1">{{ error }}</p>
      </div>

      <!-- Empty -->
      <div v-else-if="repositories.length === 0" class="flex flex-col items-center justify-center py-16 text-center px-6">
        <div class="w-12 h-12 rounded-full bg-gray-50 flex items-center justify-center mb-3">
          <svg class="w-6 h-6 text-gray-300" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M3 7a2 2 0 012-2h14a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2V7z"/>
          </svg>
        </div>
        <p class="text-sm font-medium text-gray-900">No repositories discovered yet</p>
        <p class="text-xs text-gray-500 mt-1">Trigger a scan on this group to discover repositories.</p>
      </div>

      <!-- Table -->
      <div v-else class="overflow-y-auto flex-1">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50 sticky top-0">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Name</th>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Remote URL</th>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Branch</th>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Last Scanned</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 bg-white">
            <tr v-for="repo in repositories" :key="repo.id" class="hover:bg-gray-50 transition-colors">
              <td class="px-6 py-3">
                <span class="font-medium text-gray-900 text-sm">{{ repo.name }}</span>
                <span v-if="repo.lastCommitSha" class="ml-2 font-mono text-xs text-gray-400">
                  {{ repo.lastCommitSha.slice(0, 7) }}
                </span>
              </td>
              <td class="px-6 py-3 text-sm text-gray-500 max-w-xs truncate">
                <a
                  v-if="repo.remoteUrl"
                  :href="repo.remoteUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="text-indigo-600 hover:underline font-mono text-xs"
                >{{ repo.remoteUrl }}</a>
                <span v-else class="text-gray-300 italic">—</span>
              </td>
              <td class="px-6 py-3">
                <span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-indigo-50 text-indigo-700 border border-indigo-100">
                  {{ repo.defaultBranch }}
                </span>
              </td>
              <td class="px-6 py-3 text-sm text-gray-500">
                <span v-if="repo.lastScannedAt">{{ formatDate(repo.lastScannedAt) }}</span>
                <span v-else class="text-gray-300 italic">Never</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Footer -->
      <div v-if="!loading && !error" class="px-6 py-3 bg-gray-50 border-t border-gray-100 text-xs text-gray-400 flex-shrink-0">
        {{ repositories.length }} {{ repositories.length === 1 ? 'repository' : 'repositories' }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { Repository } from '../types/repository'
import { listGroupRepositories } from '../api/git-provider-groups'

const props = defineProps<{
  groupId: number
  groupName: string
}>()

defineEmits<{ (event: 'close'): void }>()

const repositories = ref<Repository[]>([])
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    repositories.value = await listGroupRepositories(props.groupId)
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Unknown error'
  } finally {
    loading.value = false
  }
})

function formatDate(isoString: string): string {
  return new Date(isoString).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  })
}
</script>
