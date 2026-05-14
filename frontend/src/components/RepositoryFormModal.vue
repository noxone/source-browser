<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
    <div class="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 overflow-hidden">
      <div class="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
        <h2 class="text-lg font-semibold text-gray-900">
          {{ isEditing ? 'Edit Repository' : 'Add Repository' }}
        </h2>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600 transition-colors">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12"/>
          </svg>
        </button>
      </div>

      <form @submit.prevent="handleSubmit" class="px-6 py-5 space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">
            Name <span class="text-red-500">*</span>
          </label>
          <input
            v-model="form.name"
            type="text"
            required
            placeholder="my-project"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Remote URL</label>
          <input
            v-model="form.remoteUrl"
            type="text"
            placeholder="https://github.com/org/repo.git"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
          <p class="mt-1 text-xs text-gray-500">Optional — leave blank for local-only repositories.</p>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Default Branch</label>
          <input
            v-model="form.defaultBranch"
            type="text"
            placeholder="main"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
          <p class="mt-1 text-xs text-gray-500">Optional — leave blank to auto-detect from remote, defaults to 'main' for local-only repositories.</p>
        </div>

        <p v-if="errorMessage" class="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
          {{ errorMessage }}
        </p>

        <div class="flex justify-end gap-3 pt-2">
          <button
            type="button"
            @click="$emit('close')"
            class="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
          >
            Cancel
          </button>
          <button
            type="submit"
            :disabled="loading"
            class="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <span v-if="loading">Saving…</span>
            <span v-else>{{ isEditing ? 'Save Changes' : 'Add Repository' }}</span>
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import type { Repository, CreateRepositoryRequest, UpdateRepositoryRequest } from '../types/repository'
import { createRepository, updateRepository } from '../api/repositories'

const props = defineProps<{
  repository?: Repository
}>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'saved', repository: Repository): void
}>()

const isEditing = computed(() => props.repository !== undefined)

const form = ref({
  name: '',
  remoteUrl: '',
  defaultBranch: ''
})

const loading = ref(false)
const errorMessage = ref('')

onMounted(() => {
  if (props.repository) {
    form.value.name = props.repository.name
    form.value.remoteUrl = props.repository.remoteUrl ?? ''
    form.value.defaultBranch = props.repository.defaultBranch
  }
})

async function handleSubmit() {
  loading.value = true
  errorMessage.value = ''

  try {
    const remoteUrl = form.value.remoteUrl.trim() || null
    const defaultBranch = form.value.defaultBranch.trim() || null

    if (isEditing.value && props.repository) {
      const request: UpdateRepositoryRequest = {
        name: form.value.name.trim(),
        remoteUrl,
        defaultBranch
      }
      const updated = await updateRepository(props.repository.id, request)
      emit('saved', updated)
    } else {
      const request: CreateRepositoryRequest = {
        name: form.value.name.trim(),
        remoteUrl,
        defaultBranch
      }
      const created = await createRepository(request)
      emit('saved', created)
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'An unexpected error occurred.'
  } finally {
    loading.value = false
  }
}
</script>
