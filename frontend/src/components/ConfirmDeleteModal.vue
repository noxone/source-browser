<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
    <div class="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 overflow-hidden">
      <div class="px-6 py-4 border-b border-gray-200 flex items-center gap-3">
        <div class="flex-shrink-0 w-10 h-10 rounded-full bg-red-100 flex items-center justify-center">
          <svg class="w-5 h-5 text-red-600" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v4m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
          </svg>
        </div>
        <h2 class="text-lg font-semibold text-gray-900">Delete Repository</h2>
      </div>

      <div class="px-6 py-5">
        <p class="text-sm text-gray-600">
          Are you sure you want to delete
          <span class="font-semibold text-gray-900">{{ repository.name }}</span>?
          This will remove it from the index. The local files will not be affected.
        </p>

        <p v-if="errorMessage" class="mt-3 text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
          {{ errorMessage }}
        </p>
      </div>

      <div class="px-6 py-4 bg-gray-50 border-t border-gray-200 flex justify-end gap-3">
        <button
          @click="$emit('close')"
          class="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
        >
          Cancel
        </button>
        <button
          @click="handleDelete"
          :disabled="loading"
          class="px-4 py-2 text-sm font-medium text-white bg-red-600 rounded-lg hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          <span v-if="loading">Deleting…</span>
          <span v-else>Delete</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { Repository } from '../types/repository'
import { deleteRepository } from '../api/repositories'

const props = defineProps<{
  repository: Repository
}>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'deleted', id: number): void
}>()

const loading = ref(false)
const errorMessage = ref('')

async function handleDelete() {
  loading.value = true
  errorMessage.value = ''

  try {
    await deleteRepository(props.repository.id)
    emit('deleted', props.repository.id)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'An unexpected error occurred.'
    loading.value = false
  }
}
</script>
