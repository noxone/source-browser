<template>
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
    <div class="bg-white rounded-xl shadow-xl w-full max-w-2xl mx-4 overflow-hidden flex flex-col max-h-[90vh]">
      <!-- Header -->
      <div class="px-6 py-4 border-b border-gray-200 flex items-center justify-between flex-shrink-0">
        <div>
          <h2 class="text-lg font-semibold text-gray-900">Personal Access Tokens</h2>
          <p class="text-sm text-gray-500 mt-0.5">Service account: <code class="font-mono text-xs bg-gray-100 px-1.5 py-0.5 rounded">{{ account.name }}</code></p>
        </div>
        <button @click="$emit('close')" class="text-gray-400 hover:text-gray-600 transition-colors">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12"/>
          </svg>
        </button>
      </div>

      <!-- Token list -->
      <div class="flex-1 overflow-y-auto px-6 py-4">
        <!-- One-time token reveal -->
        <div v-if="newRawToken" class="mb-4 rounded-lg border border-green-200 bg-green-50 px-4 py-3">
          <p class="text-sm font-semibold text-green-800 mb-1">Token created — copy it now, it will not be shown again.</p>
          <div class="flex items-center gap-2 mt-2">
            <code class="flex-1 font-mono text-xs bg-white border border-green-200 rounded px-3 py-2 text-green-900 break-all select-all">{{ newRawToken }}</code>
            <button
              @click="copyToken"
              class="flex-shrink-0 px-3 py-2 text-xs font-medium text-green-700 bg-white border border-green-300 rounded-lg hover:bg-green-50 transition-colors"
            >
              {{ copied ? 'Copied!' : 'Copy' }}
            </button>
          </div>
        </div>

        <!-- Loading -->
        <div v-if="loading" class="flex items-center justify-center py-10 text-gray-400">
          <svg class="animate-spin w-5 h-5 mr-2" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
          </svg>
          Loading tokens…
        </div>

        <!-- Error -->
        <div v-else-if="loadError" class="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {{ loadError }}
        </div>

        <!-- Empty -->
        <div v-else-if="tokens.length === 0 && !newRawToken" class="text-center py-10 text-gray-400 text-sm italic">
          No tokens yet.
        </div>

        <!-- Token table -->
        <div v-else-if="tokens.length > 0" class="overflow-hidden rounded-lg border border-gray-200">
          <table class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Name</th>
                <th class="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Created</th>
                <th class="px-4 py-2.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Expires</th>
                <th class="px-4 py-2.5 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              <tr v-for="token in tokens" :key="token.id" class="hover:bg-gray-50 transition-colors">
                <td class="px-4 py-3 text-sm font-medium text-gray-900">{{ token.name }}</td>
                <td class="px-4 py-3 text-sm text-gray-500">{{ formatDate(token.createdAt) }}</td>
                <td class="px-4 py-3 text-sm text-gray-500">
                  <span v-if="token.expiresAt" :class="isExpired(token.expiresAt) ? 'text-red-600' : ''">
                    {{ formatDate(token.expiresAt) }}
                    <span v-if="isExpired(token.expiresAt)" class="ml-1 text-xs font-medium text-red-500">(expired)</span>
                  </span>
                  <span v-else class="text-gray-300 italic">Never</span>
                </td>
                <td class="px-4 py-3 text-right">
                  <button
                    @click="revokeToken(token)"
                    class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-red-700 bg-white border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                  >
                    Revoke
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Create new token form -->
      <div class="flex-shrink-0 border-t border-gray-200 px-6 py-4 bg-gray-50">
        <p class="text-sm font-medium text-gray-700 mb-3">Create new token</p>
        <form @submit.prevent="createToken" class="flex items-start gap-3">
          <div class="flex-1">
            <input
              v-model="newTokenName"
              type="text"
              required
              placeholder="Token name…"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            />
          </div>
          <div class="flex-1">
            <input
              v-model="newTokenExpiry"
              type="datetime-local"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              title="Leave blank for a token that never expires"
            />
            <p class="mt-0.5 text-xs text-gray-400">Expiry (optional)</p>
          </div>
          <button
            type="submit"
            :disabled="creating"
            class="flex-shrink-0 px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition-colors disabled:opacity-50"
          >
            {{ creating ? 'Creating…' : 'Create' }}
          </button>
        </form>
        <div v-if="createError" class="mt-2 text-sm text-red-600">{{ createError }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { ServiceAccount, ServiceAccountToken } from '../types/service-account'
import {
  listServiceAccountTokens,
  createServiceAccountToken,
  revokeServiceAccountToken
} from '../api/service-accounts'

const props = defineProps<{
  account: ServiceAccount
}>()

defineEmits<{ close: [] }>()

const tokens = ref<ServiceAccountToken[]>([])
const loading = ref(false)
const loadError = ref('')

const newTokenName = ref('')
const newTokenExpiry = ref('')
const creating = ref(false)
const createError = ref('')

const newRawToken = ref('')
const copied = ref(false)

onMounted(fetchTokens)

async function fetchTokens() {
  loading.value = true
  loadError.value = ''
  try {
    tokens.value = await listServiceAccountTokens(props.account.id)
  } catch (err) {
    loadError.value = err instanceof Error ? err.message : 'Failed to load tokens.'
  } finally {
    loading.value = false
  }
}

async function createToken() {
  creating.value = true
  createError.value = ''
  newRawToken.value = ''
  try {
    const expiresAt = newTokenExpiry.value
      ? new Date(newTokenExpiry.value).toISOString()
      : null
    const result = await createServiceAccountToken(props.account.id, {
      name: newTokenName.value,
      expiresAt
    })
    newRawToken.value = result.rawToken
    tokens.value.unshift(result.token)
    newTokenName.value = ''
    newTokenExpiry.value = ''
  } catch (err) {
    createError.value = err instanceof Error ? err.message : 'Failed to create token.'
  } finally {
    creating.value = false
  }
}

async function revokeToken(token: ServiceAccountToken) {
  try {
    await revokeServiceAccountToken(props.account.id, token.id)
    tokens.value = tokens.value.filter(t => t.id !== token.id)
    if (newRawToken.value) {
      newRawToken.value = ''
    }
  } catch (err) {
    loadError.value = err instanceof Error ? err.message : 'Failed to revoke token.'
  }
}

async function copyToken() {
  await navigator.clipboard.writeText(newRawToken.value)
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}

function formatDate(isoString: string): string {
  return new Date(isoString).toLocaleString()
}

function isExpired(expiresAt: string): boolean {
  return new Date(expiresAt) < new Date()
}
</script>
