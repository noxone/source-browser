<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
    <div class="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 overflow-hidden">
      <div class="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
        <h2 class="text-lg font-semibold text-gray-900">
          {{ isEditing ? 'Edit Group' : 'Add Group' }}
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
            placeholder="my-gitlab-group"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">
            Provider <span class="text-red-500">*</span>
          </label>
          <select
            v-model="form.providerType"
            required
            class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          >
            <option value="GITLAB">GitLab</option>
            <option value="GITHUB">GitHub</option>
          </select>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">
            Group Path <span class="text-red-500">*</span>
          </label>
          <input
            v-model="form.groupPath"
            type="text"
            required
            :placeholder="form.providerType === 'GITLAB' ? 'my-org/my-subgroup' : 'my-org'"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
          <p class="mt-1 text-xs text-gray-500">
            <template v-if="form.providerType === 'GITLAB'">Full GitLab group path, e.g. <code>my-org/my-subgroup</code>.</template>
            <template v-else>GitHub organization login name, e.g. <code>my-org</code>.</template>
          </p>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Base URL</label>
          <input
            v-model="form.baseUrl"
            type="text"
            placeholder="https://gitlab.example.com"
            class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
          <p class="mt-1 text-xs text-gray-500">Optional — leave blank to use the provider's default URL.</p>
        </div>

        <div class="space-y-2">
          <label class="block text-sm font-medium text-gray-700">Options</label>
          <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
            <input
              v-model="form.archivedOmitted"
              type="checkbox"
              class="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
            />
            Omit archived repositories
          </label>
          <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
            <input
              v-model="form.forkedOmitted"
              type="checkbox"
              class="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
            />
            Omit forked repositories
          </label>
          <template v-if="form.providerType === 'GITLAB'">
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input
                v-model="form.sharedOmitted"
                type="checkbox"
                class="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
              />
              Omit shared repositories
            </label>
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input
                v-model="form.importedOmitted"
                type="checkbox"
                class="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
              />
              Omit imported repositories
            </label>
          </template>
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
            <span v-else>{{ isEditing ? 'Save Changes' : 'Add Group' }}</span>
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import type { GitProviderGroup, GitProviderType, CreateGitProviderGroupRequest, UpdateGitProviderGroupRequest } from '../types/git-provider-group'
import { createGitProviderGroup, updateGitProviderGroup } from '../api/git-provider-groups'

const props = defineProps<{
  group?: GitProviderGroup
}>()

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'saved', group: GitProviderGroup): void
}>()

const isEditing = computed(() => props.group !== undefined)

const form = ref({
  name: '',
  providerType: 'GITLAB' as GitProviderType,
  groupPath: '',
  baseUrl: '',
  archivedOmitted: false,
  forkedOmitted: false,
  sharedOmitted: false,
  importedOmitted: false
})

const loading = ref(false)
const errorMessage = ref('')

onMounted(() => {
  if (props.group) {
    form.value.name = props.group.name
    form.value.providerType = props.group.providerType
    form.value.groupPath = props.group.groupPath
    form.value.baseUrl = props.group.baseUrl ?? ''
    form.value.archivedOmitted = props.group.archivedOmitted
    form.value.forkedOmitted = props.group.forkedOmitted
    form.value.sharedOmitted = props.group.sharedOmitted
    form.value.importedOmitted = props.group.importedOmitted
  }
})

async function handleSubmit() {
  loading.value = true
  errorMessage.value = ''

  try {
    const baseUrl = form.value.baseUrl.trim() || null

    if (isEditing.value && props.group) {
      const request: UpdateGitProviderGroupRequest = {
        name: form.value.name.trim(),
        providerType: form.value.providerType,
        groupPath: form.value.groupPath.trim(),
        baseUrl,
        archivedOmitted: form.value.archivedOmitted,
        forkedOmitted: form.value.forkedOmitted,
        sharedOmitted: form.value.sharedOmitted,
        importedOmitted: form.value.importedOmitted
      }
      const updated = await updateGitProviderGroup(props.group.id, request)
      emit('saved', updated)
    } else {
      const request: CreateGitProviderGroupRequest = {
        name: form.value.name.trim(),
        providerType: form.value.providerType,
        groupPath: form.value.groupPath.trim(),
        baseUrl,
        archivedOmitted: form.value.archivedOmitted,
        forkedOmitted: form.value.forkedOmitted,
        sharedOmitted: form.value.sharedOmitted,
        importedOmitted: form.value.importedOmitted
      }
      const created = await createGitProviderGroup(request)
      emit('saved', created)
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'An unexpected error occurred.'
  } finally {
    loading.value = false
  }
}
</script>
