<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
    <div class="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 overflow-hidden">
      <div class="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
        <h2 class="text-lg font-semibold text-gray-900">
          {{ isEditing ? 'Edit Service Account' : 'Create Service Account' }}
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
            :disabled="isEditing"
            placeholder="my-ci-bot"
            pattern="[a-zA-Z0-9_\-]{1,64}"
            title="Letters, digits, hyphens and underscores only (max 64 characters)"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 disabled:bg-gray-50 disabled:text-gray-500"
          />
          <p class="mt-1 text-xs text-gray-500">
            Letters, digits, hyphens and underscores only (max 64 chars). Cannot be changed later.
          </p>
        </div>

        <div class="flex items-center gap-3">
          <input
            id="admin-toggle"
            v-model="form.admin"
            type="checkbox"
            class="w-4 h-4 text-indigo-600 border-gray-300 rounded focus:ring-indigo-500"
          />
          <label for="admin-toggle" class="text-sm font-medium text-gray-700">
            Grant administrator privileges
          </label>
        </div>

        <div v-if="error" class="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {{ error }}
        </div>

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
            :disabled="saving"
            class="px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition-colors disabled:opacity-50"
          >
            {{ saving ? 'Saving…' : isEditing ? 'Save Changes' : 'Create' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ServiceAccount } from '../types/service-account'
import { createServiceAccount, updateServiceAccount } from '../api/service-accounts'

const props = defineProps<{
  account?: ServiceAccount
}>()

const emit = defineEmits<{
  close: []
  saved: [account: ServiceAccount]
}>()

const isEditing = computed(() => !!props.account)

const form = ref({
  name: props.account?.name ?? '',
  admin: props.account?.admin ?? false
})

const saving = ref(false)
const error = ref('')

async function handleSubmit() {
  saving.value = true
  error.value = ''
  try {
    let result: ServiceAccount
    if (isEditing.value && props.account) {
      result = await updateServiceAccount(props.account.id, { admin: form.value.admin })
    } else {
      result = await createServiceAccount({ name: form.value.name, admin: form.value.admin })
    }
    emit('saved', result)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'An unexpected error occurred.'
  } finally {
    saving.value = false
  }
}
</script>
