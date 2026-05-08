<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
    <div class="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 overflow-hidden">

      <!-- Header -->
      <div class="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="flex-shrink-0 w-8 h-8 rounded-full bg-amber-100 flex items-center justify-center">
            <svg class="w-4 h-4 text-amber-600" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round"
                d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z"/>
            </svg>
          </div>
          <div>
            <h2 class="text-base font-semibold text-gray-900">Git Credential</h2>
            <p class="text-xs text-gray-500">{{ entityName }}</p>
          </div>
        </div>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600 transition-colors">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12"/>
          </svg>
        </button>
      </div>

      <!-- Loading -->
      <div v-if="initialLoading" class="flex items-center justify-center py-12 text-gray-400">
        <svg class="animate-spin w-5 h-5 mr-2" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
        </svg>
        Loading…
      </div>

      <!-- Form -->
      <form v-else @submit.prevent="handleSave" class="px-6 py-5 space-y-4">

        <!-- Existing credential info banner -->
        <div v-if="existingCredential" class="flex items-start gap-3 px-4 py-3 bg-green-50 border border-green-200 rounded-lg">
          <svg class="w-4 h-4 text-green-600 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"/>
          </svg>
          <div class="text-sm">
            <p class="font-medium text-green-800">A credential is configured</p>
            <p v-if="existingCredential.description" class="text-green-700 mt-0.5">
              {{ existingCredential.description }}
            </p>
            <p class="text-green-600 text-xs mt-0.5">
              Last updated: {{ formatDate(existingCredential.updatedAt) }}
            </p>
          </div>
        </div>

        <div v-else class="flex items-center gap-2 px-4 py-3 bg-gray-50 border border-gray-200 rounded-lg text-sm text-gray-500">
          <svg class="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round"
              d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z"/>
          </svg>
          No credential configured yet.
        </div>

        <!-- Description -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Description</label>
          <input
            v-model="form.description"
            type="text"
            placeholder="e.g. Deploy token for CI access"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
          <p class="mt-1 text-xs text-gray-500">Optional label to identify this credential.</p>
        </div>

        <!-- Secret -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">
            Secret <span class="text-red-500">*</span>
          </label>
          <input
            v-model="form.secret"
            type="password"
            required
            autocomplete="new-password"
            placeholder="Personal access token, password, …"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
          <p v-if="existingCredential" class="mt-1 text-xs text-gray-500">
            The stored secret is never shown. Enter a new value to replace it.
          </p>
          <p v-else class="mt-1 text-xs text-gray-500">
            Stored encrypted at rest. Never returned by the API.
          </p>
        </div>

        <p v-if="errorMessage" class="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
          {{ errorMessage }}
        </p>

        <!-- Actions -->
        <div class="flex items-center justify-between pt-2">
          <!-- Remove credential (only when editing) -->
          <button
            v-if="existingCredential"
            type="button"
            :disabled="loading"
            @click="handleRemove"
            class="inline-flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-red-700 bg-white border border-red-200 rounded-lg hover:bg-red-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
            </svg>
            Remove Credential
          </button>
          <span v-else />

          <div class="flex gap-3">
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
              <span v-else>{{ existingCredential ? 'Update Credential' : 'Set Credential' }}</span>
            </button>
          </div>
        </div>
      </form>

    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { GitCredential } from '../types/git-credential'
import {
  getRepositoryCredential, setRepositoryCredential, deleteRepositoryCredential,
  getGroupCredential, setGroupCredential, deleteGroupCredential
} from '../api/git-credentials'

const props = defineProps<{
  entityType: 'repository' | 'group'
  entityId: number
  entityName: string
}>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'saved', credential: GitCredential): void
  (event: 'removed'): void
}>()

const existingCredential = ref<GitCredential | null>(null)
const initialLoading = ref(true)
const loading = ref(false)
const errorMessage = ref('')

const form = ref({
  description: '',
  secret: ''
})

onMounted(async () => {
  try {
    existingCredential.value = props.entityType === 'repository'
      ? await getRepositoryCredential(props.entityId)
      : await getGroupCredential(props.entityId)
    if (existingCredential.value?.description) {
      form.value.description = existingCredential.value.description
    }
  } catch {
    // non-critical: treat as no credential
  } finally {
    initialLoading.value = false
  }
})

async function handleSave() {
  loading.value = true
  errorMessage.value = ''
  try {
    const request = {
      description: form.value.description.trim() || null,
      secret: form.value.secret
    }
    const saved = props.entityType === 'repository'
      ? await setRepositoryCredential(props.entityId, request)
      : await setGroupCredential(props.entityId, request)
    emit('saved', saved)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'An unexpected error occurred.'
  } finally {
    loading.value = false
  }
}

async function handleRemove() {
  if (!confirm('Remove the credential? The secret will be permanently deleted.')) return
  loading.value = true
  errorMessage.value = ''
  try {
    if (props.entityType === 'repository') {
      await deleteRepositoryCredential(props.entityId)
    } else {
      await deleteGroupCredential(props.entityId)
    }
    emit('removed')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'An unexpected error occurred.'
    loading.value = false
  }
}

function formatDate(isoString: string): string {
  return new Date(isoString).toLocaleString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit'
  })
}
</script>
