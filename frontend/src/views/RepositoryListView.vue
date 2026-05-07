<template>
  <div>
    <!-- ── Repositories ─────────────────────────────────────────────────────── -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h2 class="text-2xl font-bold text-gray-900">Repositories</h2>
        <p class="mt-1 text-sm text-gray-500">Manage the Git repositories indexed by the source viewer.</p>
      </div>
      <button
        @click="openAddRepoModal"
        class="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition-colors shadow-sm"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4"/>
        </svg>
        Add Repository
      </button>
    </div>

    <!-- Loading state -->
    <div v-if="reposLoading" class="flex items-center justify-center py-24 text-gray-400">
      <svg class="animate-spin w-6 h-6 mr-3" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
      </svg>
      Loading repositories…
    </div>

    <!-- Error state -->
    <div v-else-if="reposLoadError" class="rounded-xl border border-red-200 bg-red-50 px-6 py-5 text-sm text-red-700">
      <p class="font-semibold">Failed to load repositories</p>
      <p class="mt-1">{{ reposLoadError }}</p>
      <button @click="fetchRepositories" class="mt-3 underline hover:no-underline">Try again</button>
    </div>

    <!-- Empty state -->
    <div v-else-if="repositories.length === 0" class="flex flex-col items-center justify-center py-24 text-center">
      <div class="w-16 h-16 rounded-full bg-indigo-50 flex items-center justify-center mb-4">
        <svg class="w-8 h-8 text-indigo-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M3 7a2 2 0 012-2h14a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2V7z"/>
          <path stroke-linecap="round" stroke-linejoin="round" d="M8 12h8M8 8h4"/>
        </svg>
      </div>
      <h3 class="text-base font-semibold text-gray-900">No repositories yet</h3>
      <p class="mt-1 text-sm text-gray-500">Add a repository to start indexing its source code.</p>
      <button
        @click="openAddRepoModal"
        class="mt-4 inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition-colors"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4"/>
        </svg>
        Add Repository
      </button>
    </div>

    <!-- Repository table -->
    <div v-else class="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Name</th>
            <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Remote URL</th>
            <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Branch</th>
            <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Last Scanned</th>
            <th class="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Actions</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
          <tr v-for="repo in repositories" :key="repo.id" class="hover:bg-gray-50 transition-colors">
            <td class="px-6 py-4">
              <span class="font-medium text-gray-900 text-sm">{{ repo.name }}</span>
              <span v-if="repo.lastCommitSha" class="ml-2 font-mono text-xs text-gray-400">
                {{ repo.lastCommitSha.slice(0, 7) }}
              </span>
            </td>
            <td class="px-6 py-4 text-sm text-gray-500 max-w-xs truncate">
              <a
                v-if="repo.remoteUrl"
                :href="repo.remoteUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="text-indigo-600 hover:underline font-mono text-xs"
              >{{ repo.remoteUrl }}</a>
              <span v-else class="text-gray-300 italic">—</span>
            </td>
            <td class="px-6 py-4">
              <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-indigo-50 text-indigo-700 border border-indigo-100">
                {{ repo.defaultBranch }}
              </span>
            </td>
            <td class="px-6 py-4 text-sm text-gray-500">
              <span v-if="repo.lastScannedAt">{{ formatDate(repo.lastScannedAt) }}</span>
              <span v-else class="text-gray-300 italic">Never</span>
            </td>
            <td class="px-6 py-4 text-right">
              <div class="inline-flex items-center gap-2">
                <button
                  @click="openEditRepoModal(repo)"
                  class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/>
                  </svg>
                  Edit
                </button>
                <button
                  @click="openDeleteRepoModal(repo)"
                  class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-red-700 bg-white border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                >
                  <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                  </svg>
                  Delete
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="px-6 py-3 bg-gray-50 border-t border-gray-100 text-xs text-gray-400">
        {{ repositories.length }} {{ repositories.length === 1 ? 'repository' : 'repositories' }}
      </div>
    </div>

    <!-- ── Git Provider Groups ───────────────────────────────────────────────── -->
    <div class="flex items-center justify-between mt-12 mb-6">
      <div>
        <h2 class="text-2xl font-bold text-gray-900">Git Provider Groups</h2>
        <p class="mt-1 text-sm text-gray-500">
          Configure GitLab groups or GitHub organizations whose repositories shall be discovered and indexed automatically.
        </p>
      </div>
      <button
        @click="openAddGroupModal"
        class="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition-colors shadow-sm"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4"/>
        </svg>
        Add Group
      </button>
    </div>

    <!-- Groups loading state -->
    <div v-if="groupsLoading" class="flex items-center justify-center py-16 text-gray-400">
      <svg class="animate-spin w-6 h-6 mr-3" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
      </svg>
      Loading groups…
    </div>

    <!-- Groups error state -->
    <div v-else-if="groupsLoadError" class="rounded-xl border border-red-200 bg-red-50 px-6 py-5 text-sm text-red-700">
      <p class="font-semibold">Failed to load Git provider groups</p>
      <p class="mt-1">{{ groupsLoadError }}</p>
      <button @click="fetchGroups" class="mt-3 underline hover:no-underline">Try again</button>
    </div>

    <!-- Groups empty state -->
    <div v-else-if="groups.length === 0" class="flex flex-col items-center justify-center py-16 text-center">
      <div class="w-16 h-16 rounded-full bg-indigo-50 flex items-center justify-center mb-4">
        <svg class="w-8 h-8 text-indigo-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M17 20h5v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2h5M9 11a4 4 0 118 0 4 4 0 01-8 0z"/>
        </svg>
      </div>
      <h3 class="text-base font-semibold text-gray-900">No groups configured</h3>
      <p class="mt-1 text-sm text-gray-500">Add a GitLab group or GitHub organization to discover repositories automatically.</p>
      <button
        @click="openAddGroupModal"
        class="mt-4 inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition-colors"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4"/>
        </svg>
        Add Group
      </button>
    </div>

    <!-- Groups table -->
    <div v-else class="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Name</th>
            <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Provider</th>
            <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Group Path</th>
            <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Options</th>
            <th class="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Actions</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
          <tr v-for="group in groups" :key="group.id" class="hover:bg-gray-50 transition-colors">
            <td class="px-6 py-4">
              <span class="font-medium text-gray-900 text-sm">{{ group.name }}</span>
            </td>
            <td class="px-6 py-4">
              <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium"
                :class="group.providerType === 'GITLAB'
                  ? 'bg-orange-50 text-orange-700 border border-orange-100'
                  : 'bg-gray-50 text-gray-700 border border-gray-200'">
                {{ group.providerType === 'GITLAB' ? 'GitLab' : 'GitHub' }}
              </span>
            </td>
            <td class="px-6 py-4 text-sm text-gray-500 font-mono">
              {{ group.groupPath }}
              <span v-if="group.baseUrl" class="block text-xs text-gray-400 font-sans">{{ group.baseUrl }}</span>
            </td>
            <td class="px-6 py-4 text-xs text-gray-500 space-y-0.5">
              <div v-if="group.archivedOmitted" class="flex items-center gap-1">
                <svg class="w-3 h-3 text-gray-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"/>
                </svg>
                Archived omitted
              </div>
              <div v-if="group.forkedOmitted" class="flex items-center gap-1">
                <svg class="w-3 h-3 text-gray-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"/>
                </svg>
                Forked omitted
              </div>
              <span v-if="!group.archivedOmitted && !group.forkedOmitted" class="text-gray-300 italic">None</span>
            </td>
            <td class="px-6 py-4 text-right">
              <div class="inline-flex items-center gap-2">
                <button
                  @click="openEditGroupModal(group)"
                  class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/>
                  </svg>
                  Edit
                </button>
                <button
                  @click="deleteGroup(group)"
                  class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-red-700 bg-white border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                >
                  <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                  </svg>
                  Delete
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="px-6 py-3 bg-gray-50 border-t border-gray-100 text-xs text-gray-400">
        {{ groups.length }} {{ groups.length === 1 ? 'group' : 'groups' }}
      </div>
    </div>

    <!-- Modals -->
    <RepositoryFormModal
      v-if="showRepoFormModal"
      :repository="editingRepository"
      @close="closeRepoFormModal"
      @saved="handleRepoSaved"
    />

    <ConfirmDeleteModal
      v-if="showDeleteRepoModal && deletingRepository"
      :repository="deletingRepository"
      @close="closeDeleteRepoModal"
      @deleted="handleRepoDeleted"
    />

    <GitProviderGroupFormModal
      v-if="showGroupFormModal"
      :group="editingGroup"
      @close="closeGroupFormModal"
      @saved="handleGroupSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { Repository } from '../types/repository'
import type { GitProviderGroup } from '../types/git-provider-group'
import { listRepositories } from '../api/repositories'
import { listGitProviderGroups, deleteGitProviderGroup } from '../api/git-provider-groups'
import RepositoryFormModal from '../components/RepositoryFormModal.vue'
import ConfirmDeleteModal from '../components/ConfirmDeleteModal.vue'
import GitProviderGroupFormModal from '../components/GitProviderGroupFormModal.vue'

// ── Repositories ──────────────────────────────────────────────────────────────

const repositories = ref<Repository[]>([])
const reposLoading = ref(false)
const reposLoadError = ref('')

const showRepoFormModal = ref(false)
const editingRepository = ref<Repository | undefined>(undefined)

const showDeleteRepoModal = ref(false)
const deletingRepository = ref<Repository | undefined>(undefined)

// ── Git Provider Groups ───────────────────────────────────────────────────────

const groups = ref<GitProviderGroup[]>([])
const groupsLoading = ref(false)
const groupsLoadError = ref('')

const showGroupFormModal = ref(false)
const editingGroup = ref<GitProviderGroup | undefined>(undefined)

onMounted(() => {
  fetchRepositories()
  fetchGroups()
})

async function fetchRepositories() {
  reposLoading.value = true
  reposLoadError.value = ''
  try {
    repositories.value = await listRepositories()
  } catch (error) {
    reposLoadError.value = error instanceof Error ? error.message : 'Unknown error'
  } finally {
    reposLoading.value = false
  }
}

async function fetchGroups() {
  groupsLoading.value = true
  groupsLoadError.value = ''
  try {
    groups.value = await listGitProviderGroups()
  } catch (error) {
    groupsLoadError.value = error instanceof Error ? error.message : 'Unknown error'
  } finally {
    groupsLoading.value = false
  }
}

function openAddRepoModal() {
  editingRepository.value = undefined
  showRepoFormModal.value = true
}

function openEditRepoModal(repo: Repository) {
  editingRepository.value = repo
  showRepoFormModal.value = true
}

function closeRepoFormModal() {
  showRepoFormModal.value = false
  editingRepository.value = undefined
}

function handleRepoSaved(saved: Repository) {
  const index = repositories.value.findIndex(r => r.id === saved.id)
  if (index >= 0) {
    repositories.value[index] = saved
  } else {
    repositories.value.push(saved)
  }
  closeRepoFormModal()
}

function openDeleteRepoModal(repo: Repository) {
  deletingRepository.value = repo
  showDeleteRepoModal.value = true
}

function closeDeleteRepoModal() {
  showDeleteRepoModal.value = false
  deletingRepository.value = undefined
}

function handleRepoDeleted(id: number) {
  repositories.value = repositories.value.filter(r => r.id !== id)
  closeDeleteRepoModal()
}

function openAddGroupModal() {
  editingGroup.value = undefined
  showGroupFormModal.value = true
}

function openEditGroupModal(group: GitProviderGroup) {
  editingGroup.value = group
  showGroupFormModal.value = true
}

function closeGroupFormModal() {
  showGroupFormModal.value = false
  editingGroup.value = undefined
}

function handleGroupSaved(saved: GitProviderGroup) {
  const index = groups.value.findIndex(g => g.id === saved.id)
  if (index >= 0) {
    groups.value[index] = saved
  } else {
    groups.value.push(saved)
  }
  closeGroupFormModal()
}

async function deleteGroup(group: GitProviderGroup) {
  if (!confirm(`Delete group "${group.name}"? This cannot be undone.`)) return
  try {
    await deleteGitProviderGroup(group.id)
    groups.value = groups.value.filter(g => g.id !== group.id)
  } catch (error) {
    alert(error instanceof Error ? error.message : 'Failed to delete group')
  }
}

function formatDate(isoString: string): string {
  return new Date(isoString).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  })
}
</script>
