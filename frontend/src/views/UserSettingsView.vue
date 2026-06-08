<template>
  <div class="h-full overflow-y-auto p-8">
  <div class="max-w-3xl">
    <div class="mb-8">
      <h1 class="text-2xl font-bold text-gray-900">User Settings</h1>
      <p class="mt-1 text-sm text-gray-500">Your profile and personal access tokens.</p>
    </div>

    <!-- ── Profile ────────────────────────────────────────────────────────────── -->
    <section class="mb-10">
      <h2 class="text-base font-semibold text-gray-900 mb-4">Profile</h2>
      <div class="bg-white rounded-xl border border-gray-200 shadow-sm divide-y divide-gray-100">
        <ProfileRow label="Username" :value="user?.profile.preferred_username ?? '—'" />
        <ProfileRow label="Name" :value="user?.profile.name ?? '—'" />
        <ProfileRow label="Email" :value="user?.profile.email ?? '—'" />
      </div>
      <div class="mt-4">
        <button
          @click="logout"
          class="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a2 2 0 01-2 2H5a2 2 0 01-2-2V7a2 2 0 012-2h6a2 2 0 012 2v1"/>
          </svg>
          Sign out
        </button>
      </div>
    </section>

    <!-- ── Personal Access Tokens ─────────────────────────────────────────────── -->
    <section>
      <div class="flex items-center justify-between mb-4">
        <div>
          <h2 class="text-base font-semibold text-gray-900">Personal Access Tokens</h2>
          <p class="mt-0.5 text-sm text-gray-500">
            Tokens with the <code class="font-mono text-xs bg-gray-100 px-1 py-0.5 rounded">svt_</code> prefix
            allow headless or CI access to the API.
          </p>
        </div>
        <button
          @click="openCreateTokenForm"
          class="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition-colors shadow-sm"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4"/>
          </svg>
          New Token
        </button>
      </div>

      <!-- Create token form -->
      <div v-if="showCreateForm" class="mb-6 bg-white rounded-xl border border-indigo-200 shadow-sm p-6">
        <h3 class="text-sm font-semibold text-gray-900 mb-4">Create new token</h3>
        <div class="space-y-4">
          <div>
            <label class="block text-xs font-medium text-gray-700 mb-1">Token name <span class="text-red-500">*</span></label>
            <input
              v-model="newTokenName"
              type="text"
              placeholder="e.g. CI pipeline"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-700 mb-1">Expiry date <span class="text-gray-400">(optional)</span></label>
            <input
              v-model="newTokenExpiry"
              type="date"
              class="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
            />
          </div>
          <div v-if="createError" class="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-4 py-3">
            {{ createError }}
          </div>
          <div class="flex items-center gap-3">
            <button
              @click="submitCreateToken"
              :disabled="!newTokenName.trim() || creating"
              class="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              <svg v-if="creating" class="animate-spin w-4 h-4" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
              </svg>
              Create Token
            </button>
            <button
              @click="cancelCreateToken"
              class="px-4 py-2 text-sm font-medium text-gray-700 hover:text-gray-900 transition-colors"
            >
              Cancel
            </button>
          </div>
        </div>
      </div>

      <!-- Newly created token banner -->
      <div v-if="createdToken" class="mb-6 bg-green-50 border border-green-200 rounded-xl p-5">
        <div class="flex items-start gap-3">
          <svg class="w-5 h-5 text-green-500 mt-0.5 shrink-0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
          </svg>
          <div class="flex-1 min-w-0">
            <p class="text-sm font-semibold text-green-800">Token created — copy it now</p>
            <p class="text-xs text-green-700 mt-0.5">This token value will not be shown again.</p>
            <div class="mt-3 flex items-center gap-2">
              <code class="flex-1 block font-mono text-xs bg-white border border-green-200 rounded-lg px-3 py-2 text-green-900 break-all">
                {{ createdToken.rawToken }}
              </code>
              <button
                @click="copyToken"
                :title="copied ? 'Copied!' : 'Copy to clipboard'"
                class="shrink-0 p-2 rounded-lg border border-green-200 bg-white hover:bg-green-50 transition-colors text-green-700"
              >
                <svg v-if="!copied" class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                  <path stroke-linecap="round" stroke-linejoin="round" d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/>
                </svg>
                <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"/>
                </svg>
              </button>
            </div>
          </div>
          <button @click="createdToken = null" class="text-green-400 hover:text-green-600 transition-colors">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- Tokens loading -->
      <div v-if="tokensLoading" class="flex items-center justify-center py-12 text-gray-400">
        <svg class="animate-spin w-5 h-5 mr-3" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
        </svg>
        Loading tokens…
      </div>

      <!-- Tokens error -->
      <div v-else-if="tokensLoadError" class="rounded-xl border border-red-200 bg-red-50 px-6 py-5 text-sm text-red-700">
        <p class="font-semibold">Failed to load tokens</p>
        <p class="mt-1">{{ tokensLoadError }}</p>
        <button @click="fetchTokens" class="mt-3 underline hover:no-underline">Try again</button>
      </div>

      <!-- Tokens empty -->
      <div v-else-if="tokens.length === 0 && !showCreateForm" class="flex flex-col items-center justify-center py-12 text-center text-gray-400">
        <svg class="w-10 h-10 mb-3 text-gray-300" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z"/>
        </svg>
        <p class="text-sm">No personal access tokens yet.</p>
      </div>

      <!-- Tokens list -->
      <div v-else-if="tokens.length > 0" class="bg-white rounded-xl border border-gray-200 shadow-sm divide-y divide-gray-100">
        <div
          v-for="token in tokens"
          :key="token.id"
          class="flex items-center justify-between px-6 py-4"
        >
          <div class="min-w-0">
            <p class="text-sm font-medium text-gray-900">{{ token.name }}</p>
            <p class="text-xs text-gray-400 mt-0.5">
              Created {{ formatDate(token.createdAt) }}
              <span v-if="token.expiresAt"> · Expires {{ formatDate(token.expiresAt) }}</span>
              <span v-else> · Never expires</span>
            </p>
          </div>
          <button
            @click="revokeToken(token)"
            class="ml-4 shrink-0 inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-red-700 bg-white border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
            </svg>
            Revoke
          </button>
        </div>
      </div>
    </section>
  </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuth } from '../auth/useAuth'
import type { PersonalAccessToken, PersonalAccessTokenCreated } from '../types/personal-access-token'
import {
  listPersonalAccessTokens,
  createPersonalAccessToken,
  revokePersonalAccessToken
} from '../api/personal-access-tokens'
import ProfileRow from '../components/ProfileRow.vue'

const { user, logout } = useAuth()

// ── Tokens ────────────────────────────────────────────────────────────────────

const tokens = ref<PersonalAccessToken[]>([])
const tokensLoading = ref(false)
const tokensLoadError = ref('')

const showCreateForm = ref(false)
const newTokenName = ref('')
const newTokenExpiry = ref('')
const creating = ref(false)
const createError = ref('')

const createdToken = ref<PersonalAccessTokenCreated | null>(null)
const copied = ref(false)

onMounted(() => {
  fetchTokens()
})

async function fetchTokens() {
  tokensLoading.value = true
  tokensLoadError.value = ''
  try {
    tokens.value = await listPersonalAccessTokens()
  } catch (error) {
    tokensLoadError.value = error instanceof Error ? error.message : 'Unknown error'
  } finally {
    tokensLoading.value = false
  }
}

function openCreateTokenForm() {
  showCreateForm.value = true
  newTokenName.value = ''
  newTokenExpiry.value = ''
  createError.value = ''
  createdToken.value = null
}

function cancelCreateToken() {
  showCreateForm.value = false
}

async function submitCreateToken() {
  createError.value = ''
  creating.value = true
  try {
    const result = await createPersonalAccessToken({
      name: newTokenName.value.trim(),
      expiresAt: newTokenExpiry.value ? new Date(newTokenExpiry.value).toISOString() : null
    })
    tokens.value.push(result.token)
    createdToken.value = result
    showCreateForm.value = false
    copied.value = false
  } catch (error) {
    createError.value = error instanceof Error ? error.message : 'Unknown error'
  } finally {
    creating.value = false
  }
}

async function revokeToken(token: PersonalAccessToken) {
  if (!confirm(`Revoke token "${token.name}"? This cannot be undone.`)) return
  try {
    await revokePersonalAccessToken(token.id)
    tokens.value = tokens.value.filter(t => t.id !== token.id)
  } catch (error) {
    alert(error instanceof Error ? error.message : 'Failed to revoke token')
  }
}

async function copyToken() {
  if (!createdToken.value) return
  await navigator.clipboard.writeText(createdToken.value.rawToken)
  copied.value = true
  setTimeout(() => { copied.value = false }, 2000)
}

function formatDate(isoString: string): string {
  return new Date(isoString).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  })
}
</script>
