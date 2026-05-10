<template>
  <div>
    <div class="mb-8">
      <h1 class="text-2xl font-bold text-gray-900">Admin</h1>
      <p class="mt-1 text-sm text-gray-500">Manage repositories, Git provider configuration and users.</p>
    </div>

    <!-- ── Tab bar ──────────────────────────────────────────────────────────── -->
    <div class="flex gap-1 mb-8 border-b border-gray-200">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        @click="activeTab = tab.id"
        class="px-5 py-2.5 text-sm font-medium rounded-t-lg transition-colors -mb-px border-b-2"
        :class="activeTab === tab.id
          ? 'text-indigo-700 border-indigo-600 bg-white'
          : 'text-gray-500 border-transparent hover:text-gray-700 hover:border-gray-300'"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- ════════════════════════════════════════════════════════════════════════
         Tab: Repositories
         ════════════════════════════════════════════════════════════════════════ -->
    <div v-show="activeTab === 'repositories'">

    <!-- ── Repositories ─────────────────────────────────────────────────────── -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h2 class="text-xl font-semibold text-gray-900">Repositories</h2>
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
                <button
                  @click="openCredentialModal('repository', repo.id, repo.name)"
                  class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium transition-colors rounded-lg border"
                  :class="repoCredentials[repo.id]
                    ? 'text-amber-700 bg-white border-amber-200 hover:bg-amber-50'
                    : 'text-gray-600 bg-white border-gray-300 hover:bg-gray-50'"
                  :title="repoCredentials[repo.id] ? 'Credential configured — click to update' : 'No credential — click to configure'"
                >
                  <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round"
                      d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z"/>
                  </svg>
                  {{ repoCredentials[repo.id] ? 'Credential ✓' : 'Credential' }}
                </button>
                <button
                  @click="triggerScan('repository', repo.id)"
                  :disabled="isScanLoading('repository', repo.id)"
                  class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium transition-colors rounded-lg border disabled:opacity-50"
                  :class="isScanQueued('repository', repo.id)
                    ? 'text-green-700 bg-white border-green-200 hover:bg-green-50'
                    : 'text-indigo-700 bg-white border-indigo-200 hover:bg-indigo-50'"
                  title="Enqueue a manual scan for this repository"
                >
                  <svg v-if="isScanLoading('repository', repo.id)" class="animate-spin w-3.5 h-3.5" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
                  </svg>
                  <svg v-else class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
                  </svg>
                  {{ isScanQueued('repository', repo.id) ? 'Queued ✓' : 'Scan' }}
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
        <h2 class="text-xl font-semibold text-gray-900">Git Provider Groups</h2>
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
              <div class="flex items-center gap-2">
                <span class="font-medium text-gray-900 text-sm">{{ group.name }}</span>
                <span class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-600 border border-gray-200">
                  {{ group.repositoryCount }} {{ group.repositoryCount === 1 ? 'repo' : 'repos' }}
                </span>
              </div>
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
                <button
                  @click="openGroupReposModal(group)"
                  class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                  title="View repositories discovered from this group"
                >
                  <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M3 7a2 2 0 012-2h14a2 2 0 012 2v10a2 2 0 01-2 2H5a2 2 0 01-2-2V7z"/>
                    <path stroke-linecap="round" stroke-linejoin="round" d="M8 12h8M8 8h4"/>
                  </svg>
                  Repos
                </button>
                <button
                  @click="openCredentialModal('group', group.id, group.name)"
                  class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium transition-colors rounded-lg border"
                  :class="groupCredentials[group.id]
                    ? 'text-amber-700 bg-white border-amber-200 hover:bg-amber-50'
                    : 'text-gray-600 bg-white border-gray-300 hover:bg-gray-50'"
                  :title="groupCredentials[group.id] ? 'API credential configured — click to update' : 'No API credential — click to configure'"
                >
                  <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round"
                      d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z"/>
                  </svg>
                  {{ groupCredentials[group.id] ? 'API Secret ✓' : 'API Secret' }}
                </button>
                <button
                  @click="openCredentialModal('group-clone', group.id, group.name)"
                  class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium transition-colors rounded-lg border"
                  :class="groupCloneCredentials[group.id]
                    ? 'text-amber-700 bg-white border-amber-200 hover:bg-amber-50'
                    : 'text-gray-600 bg-white border-gray-300 hover:bg-gray-50'"
                  :title="groupCloneCredentials[group.id] ? 'Clone credential configured — click to update' : 'No clone credential — falls back to API secret'"
                >
                  <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"/>
                  </svg>
                  {{ groupCloneCredentials[group.id] ? 'Clone Secret ✓' : 'Clone Secret' }}
                </button>
                <button
                  @click="triggerScan('group', group.id)"
                  :disabled="isScanLoading('group', group.id)"
                  class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium transition-colors rounded-lg border disabled:opacity-50"
                  :class="isScanQueued('group', group.id)
                    ? 'text-green-700 bg-white border-green-200 hover:bg-green-50'
                    : 'text-indigo-700 bg-white border-indigo-200 hover:bg-indigo-50'"
                  title="Enqueue a discovery scan for this group"
                >
                  <svg v-if="isScanLoading('group', group.id)" class="animate-spin w-3.5 h-3.5" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
                  </svg>
                  <svg v-else class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
                  </svg>
                  {{ isScanQueued('group', group.id) ? 'Queued ✓' : 'Scan' }}
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

    </div> <!-- end repositories tab -->

    <!-- ════════════════════════════════════════════════════════════════════════
         Tab: Users
         ════════════════════════════════════════════════════════════════════════ -->
    <div v-show="activeTab === 'users'">

      <div class="flex items-center justify-between mb-6">
        <div>
          <h2 class="text-xl font-semibold text-gray-900">Users</h2>
          <p class="mt-1 text-sm text-gray-500">
            All provisioned users. The first user to log in is automatically made an administrator.
          </p>
        </div>
      </div>

      <!-- Search bar -->
      <div class="mb-4 flex items-center gap-3">
        <div class="relative flex-1 max-w-sm">
          <svg class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 pointer-events-none" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <circle cx="11" cy="11" r="8"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-4.35-4.35"/>
          </svg>
          <input
            v-model="userSearchQuery"
            @input="onUserSearchInput"
            type="search"
            placeholder="Filter by username…"
            class="w-full pl-9 pr-4 py-2 text-sm border border-gray-300 rounded-lg bg-white shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>
        <span v-if="userSearchQuery" class="text-sm text-gray-500">
          {{ userTotalItems }} {{ userTotalItems === 1 ? 'match' : 'matches' }}
        </span>
      </div>

      <!-- Users loading -->
      <div v-if="usersLoading" class="flex items-center justify-center py-16 text-gray-400">
        <svg class="animate-spin w-6 h-6 mr-3" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
        </svg>
        Loading users…
      </div>

      <!-- Users error -->
      <div v-else-if="usersLoadError" class="rounded-xl border border-red-200 bg-red-50 px-6 py-5 text-sm text-red-700">
        <p class="font-semibold">Failed to load users</p>
        <p class="mt-1">{{ usersLoadError }}</p>
        <button @click="fetchUsers" class="mt-3 underline hover:no-underline">Try again</button>
      </div>

      <!-- Users table -->
      <div v-else class="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Username</th>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">First Login</th>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Role</th>
              <th class="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr v-if="paginatedUsers.length === 0">
              <td colspan="4" class="px-6 py-12 text-center text-sm text-gray-400 italic">
                No users match your filter.
              </td>
            </tr>
            <tr v-for="userAccount in paginatedUsers" :key="userAccount.id" class="hover:bg-gray-50 transition-colors">
              <td class="px-6 py-4">
                <span class="font-medium text-gray-900 text-sm">{{ userAccount.principalName }}</span>
              </td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ formatDate(userAccount.createdAt) }}</td>
              <td class="px-6 py-4">
                <span
                  class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium"
                  :class="userAccount.admin
                    ? 'bg-indigo-50 text-indigo-700 border border-indigo-100'
                    : 'bg-gray-50 text-gray-500 border border-gray-200'"
                >
                  {{ userAccount.admin ? 'Admin' : 'User' }}
                </span>
              </td>
              <td class="px-6 py-4 text-right">
                <button
                  v-if="userAccount.admin"
                  @click="setAdminStatus(userAccount, false)"
                  class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  Revoke Admin
                </button>
                <button
                  v-else
                  @click="setAdminStatus(userAccount, true)"
                  class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-indigo-700 bg-white border border-indigo-200 rounded-lg hover:bg-indigo-50 transition-colors"
                >
                  Make Admin
                </button>
              </td>
            </tr>
          </tbody>
        </table>

        <!-- Footer: count + pagination -->
        <div class="px-6 py-3 bg-gray-50 border-t border-gray-100 flex items-center justify-between gap-4">
          <span class="text-xs text-gray-400">
            {{ userTotalItems }} {{ userTotalItems === 1 ? 'user' : 'users' }}
            <template v-if="userSearchQuery"> matching "{{ userSearchQuery }}"</template>
            <template v-if="userTotalPages > 1"> · page {{ userCurrentPage }} of {{ userTotalPages }}</template>
          </span>

          <!-- Pagination controls -->
          <div v-if="userTotalPages > 1" class="flex items-center gap-1">
            <button
              @click="onUserPageChange(1)"
              :disabled="userCurrentPage === 1"
              class="px-2 py-1 text-xs rounded border border-gray-300 text-gray-600 hover:bg-gray-100 disabled:opacity-40 disabled:cursor-not-allowed"
              title="First page"
            >«</button>
            <button
              @click="onUserPageChange(userCurrentPage - 1)"
              :disabled="userCurrentPage === 1"
              class="px-2 py-1 text-xs rounded border border-gray-300 text-gray-600 hover:bg-gray-100 disabled:opacity-40 disabled:cursor-not-allowed"
              title="Previous page"
            >‹</button>

            <template v-for="page in userPageWindow" :key="page">
              <span v-if="page === '...'" class="px-2 py-1 text-xs text-gray-400">…</span>
              <button
                v-else
                @click="onUserPageChange(page as number)"
                class="px-2.5 py-1 text-xs rounded border transition-colors"
                :class="userCurrentPage === page
                  ? 'bg-indigo-600 text-white border-indigo-600'
                  : 'border-gray-300 text-gray-600 hover:bg-gray-100'"
              >{{ page }}</button>
            </template>

            <button
              @click="onUserPageChange(userCurrentPage + 1)"
              :disabled="userCurrentPage === userTotalPages"
              class="px-2 py-1 text-xs rounded border border-gray-300 text-gray-600 hover:bg-gray-100 disabled:opacity-40 disabled:cursor-not-allowed"
              title="Next page"
            >›</button>
            <button
              @click="onUserPageChange(userTotalPages)"
              :disabled="userCurrentPage === userTotalPages"
              class="px-2 py-1 text-xs rounded border border-gray-300 text-gray-600 hover:bg-gray-100 disabled:opacity-40 disabled:cursor-not-allowed"
              title="Last page"
            >»</button>
          </div>
        </div>
      </div>

    </div> <!-- end users tab -->

    <!-- ════════════════════════════════════════════════════════════════════════
         Tab: Service Accounts
         ════════════════════════════════════════════════════════════════════════ -->
    <div v-show="activeTab === 'serviceaccounts'">

      <div class="flex items-center justify-between mb-6">
        <div>
          <h2 class="text-xl font-semibold text-gray-900">Service Accounts</h2>
          <p class="mt-1 text-sm text-gray-500">
            Machine identities for bots and CI systems. Service accounts cannot log in via the UI
            but may use Personal Access Tokens for API access.
          </p>
        </div>
        <button
          @click="openCreateServiceAccountModal"
          class="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition-colors shadow-sm"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4"/>
          </svg>
          Add Service Account
        </button>
      </div>

      <!-- Loading -->
      <div v-if="serviceAccountsLoading" class="flex items-center justify-center py-16 text-gray-400">
        <svg class="animate-spin w-6 h-6 mr-3" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
        </svg>
        Loading service accounts…
      </div>

      <!-- Error -->
      <div v-else-if="serviceAccountsLoadError" class="rounded-xl border border-red-200 bg-red-50 px-6 py-5 text-sm text-red-700">
        <p class="font-semibold">Failed to load service accounts</p>
        <p class="mt-1">{{ serviceAccountsLoadError }}</p>
        <button @click="fetchServiceAccounts" class="mt-3 underline hover:no-underline">Try again</button>
      </div>

      <!-- Empty -->
      <div v-else-if="serviceAccounts.length === 0" class="flex flex-col items-center justify-center py-24 text-center">
        <div class="w-16 h-16 rounded-full bg-indigo-50 flex items-center justify-center mb-4">
          <svg class="w-8 h-8 text-indigo-400" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9.75 3.104v5.714a2.25 2.25 0 01-.659 1.591L5 14.5M9.75 3.104c-.251.023-.501.05-.75.082m.75-.082a24.301 24.301 0 014.5 0m0 0v5.714c0 .597.237 1.17.659 1.591L19.8 15M14.25 3.104c.251.023.501.05.75.082M19.8 15l-1.57.393A9.065 9.065 0 0112 15a9.065 9.065 0 00-6.23-.693L5 14.5m14.8.5-1.562-.394M5 14.5l-1.562.394"/>
          </svg>
        </div>
        <h3 class="text-base font-semibold text-gray-900">No service accounts yet</h3>
        <p class="mt-1 text-sm text-gray-500">Create a service account to allow bots and CI systems to access the API.</p>
        <button
          @click="openCreateServiceAccountModal"
          class="mt-4 inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition-colors"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4"/>
          </svg>
          Add Service Account
        </button>
      </div>

      <!-- Table -->
      <div v-else class="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Name</th>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Created</th>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Role</th>
              <th class="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Actions</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr v-for="account in serviceAccounts" :key="account.id" class="hover:bg-gray-50 transition-colors">
              <td class="px-6 py-4">
                <span class="font-mono text-sm font-medium text-gray-900">{{ account.name }}</span>
              </td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ formatDate(account.createdAt) }}</td>
              <td class="px-6 py-4">
                <span
                  class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium"
                  :class="account.admin
                    ? 'bg-indigo-50 text-indigo-700 border border-indigo-100'
                    : 'bg-gray-50 text-gray-500 border border-gray-200'"
                >
                  {{ account.admin ? 'Admin' : 'User' }}
                </span>
              </td>
              <td class="px-6 py-4 text-right">
                <div class="inline-flex items-center gap-2">
                  <button
                    @click="openEditServiceAccountModal(account)"
                    class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
                  >
                    <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/>
                    </svg>
                    Edit
                  </button>
                  <button
                    @click="openTokensModal(account)"
                    class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-amber-700 bg-white border border-amber-200 rounded-lg hover:bg-amber-50 transition-colors"
                  >
                    <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round"
                        d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z"/>
                    </svg>
                    Tokens
                  </button>
                  <button
                    @click="removeServiceAccount(account)"
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
          {{ serviceAccounts.length }} {{ serviceAccounts.length === 1 ? 'service account' : 'service accounts' }}
        </div>
      </div>

    </div> <!-- end service accounts tab -->

    <!-- ════════════════════════════════════════════════════════════════════════
         Tab: Scan Jobs
         ════════════════════════════════════════════════════════════════════════ -->
    <div v-show="activeTab === 'scanjobs'">

      <div class="flex items-center justify-between mb-6">
        <div>
          <h2 class="text-xl font-semibold text-gray-900">Scan Jobs</h2>
          <p class="mt-1 text-sm text-gray-500">
            View and manage repository scan jobs. Only queued jobs can be deleted.
          </p>
        </div>
        <div class="flex items-center gap-3">
          <button
            @click="fetchScanJobs"
            class="inline-flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
            title="Refresh"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
            </svg>
            Refresh
          </button>
          <button
            @click="removeAllQueuedScanJobs"
            :disabled="queuedScanJobCount === 0"
            class="inline-flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-red-700 bg-white border border-red-200 rounded-lg hover:bg-red-50 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
            </svg>
            Delete All Queued
          </button>
        </div>
      </div>

      <!-- Status filter pills -->
      <div class="flex items-center gap-2 mb-5">
        <span class="text-xs font-medium text-gray-500 mr-1">Filter:</span>
        <button
          v-for="filter in [
            { value: '',        label: 'All'       },
            { value: 'QUEUED',  label: 'Queued'    },
            { value: 'RUNNING', label: 'Running'   },
            { value: 'DONE',    label: 'Done'      },
            { value: 'FAILED',  label: 'Failed'    }
          ]"
          :key="filter.value"
          @click="onScanJobStatusFilterChange(filter.value as ScanJobStatusFilter)"
          class="px-3 py-1 text-xs font-medium rounded-full border transition-colors"
          :class="scanJobStatusFilter === filter.value
            ? 'bg-indigo-600 text-white border-indigo-600'
            : 'bg-white text-gray-600 border-gray-300 hover:border-gray-400'"
        >
          {{ filter.label }}
        </button>
      </div>

      <!-- Loading -->
      <div v-if="scanJobsLoading" class="flex items-center justify-center py-16 text-gray-400">
        <svg class="animate-spin w-6 h-6 mr-3" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
        </svg>
        Loading scan jobs…
      </div>

      <!-- Error -->
      <div v-else-if="scanJobsLoadError" class="rounded-xl border border-red-200 bg-red-50 px-6 py-5 text-sm text-red-700">
        <p class="font-semibold">Failed to load scan jobs</p>
        <p class="mt-1">{{ scanJobsLoadError }}</p>
        <button @click="fetchScanJobs" class="mt-3 underline hover:no-underline">Try again</button>
      </div>

      <!-- Empty -->
      <div v-else-if="scanJobs.length === 0" class="flex flex-col items-center justify-center py-24 text-center">
        <div class="w-16 h-16 rounded-full bg-gray-50 flex items-center justify-center mb-4">
          <svg class="w-8 h-8 text-gray-300" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"/>
          </svg>
        </div>
        <h3 class="text-base font-semibold text-gray-900">No scan jobs</h3>
        <p class="mt-1 text-sm text-gray-500">
          {{ scanJobStatusFilter ? `No jobs with status "${scanJobStatusFilter}".` : 'No scan jobs found.' }}
        </p>
      </div>

      <!-- Table -->
      <div v-else class="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <div class="overflow-x-auto">
          <table class="min-w-full divide-y divide-gray-200">
            <thead class="bg-gray-50">
              <tr>
                <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">ID</th>
                <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Repository</th>
                <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Trigger</th>
                <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Commit</th>
                <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Status</th>
                <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Queued At</th>
                <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Started At</th>
                <th class="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              <tr v-for="job in scanJobs" :key="job.id" class="hover:bg-gray-50 transition-colors">
                <td class="px-6 py-4 text-sm font-mono text-gray-500">#{{ job.id }}</td>
                <td class="px-6 py-4 text-sm font-medium text-gray-900">{{ repositoryName(job.repositoryId) }}</td>
                <td class="px-6 py-4 text-sm text-gray-500">{{ TRIGGER_LABELS[job.triggerType] ?? job.triggerType }}</td>
                <td class="px-6 py-4 text-sm font-mono text-gray-400">
                  {{ job.commitSha ? job.commitSha.substring(0, 8) : '—' }}
                </td>
                <td class="px-6 py-4">
                  <span
                    class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border"
                    :class="STATUS_CLASSES[job.status] ?? 'bg-gray-50 text-gray-500 border-gray-200'"
                  >
                    {{ job.status }}
                  </span>
                </td>
                <td class="px-6 py-4 text-sm text-gray-500">{{ formatDate(job.queuedAt) }}</td>
                <td class="px-6 py-4 text-sm text-gray-500">{{ job.startedAt ? formatDate(job.startedAt) : '—' }}</td>
                <td class="px-6 py-4 text-right">
                  <button
                    @click="removeScanJob(job)"
                    :disabled="job.status !== 'QUEUED'"
                    class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-red-700 bg-white border border-red-200 rounded-lg hover:bg-red-50 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                    :title="job.status !== 'QUEUED' ? 'Only queued jobs can be deleted' : 'Delete scan job'"
                  >
                    <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                    </svg>
                    Delete
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="px-6 py-3 bg-gray-50 border-t border-gray-100 text-xs text-gray-400">
          {{ scanJobs.length }} {{ scanJobs.length === 1 ? 'job' : 'jobs' }}
          <template v-if="queuedScanJobCount > 0"> · {{ queuedScanJobCount }} queued</template>
        </div>
      </div>

    </div> <!-- end scan jobs tab -->

    <!-- ════════════════════════════════════════════════════════════════════════
         Tab: Settings
         ════════════════════════════════════════════════════════════════════════ -->
    <div v-show="activeTab === 'settings'">

      <div class="mb-6">
        <h2 class="text-xl font-semibold text-gray-900">Settings</h2>
        <p class="mt-1 text-sm text-gray-500">
          Runtime-configurable settings. Changes take effect immediately without restarting the application.
        </p>
      </div>

      <!-- Loading -->
      <div v-if="settingsLoading" class="flex items-center justify-center py-16 text-gray-400">
        <svg class="animate-spin w-6 h-6 mr-3" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
        </svg>
        Loading settings…
      </div>

      <!-- Error -->
      <div v-else-if="settingsLoadError" class="rounded-xl border border-red-200 bg-red-50 px-6 py-5 text-sm text-red-700">
        <p class="font-semibold">Failed to load settings</p>
        <p class="mt-1">{{ settingsLoadError }}</p>
        <button @click="fetchSettings" class="mt-3 underline hover:no-underline">Try again</button>
      </div>

      <!-- Settings table -->
      <div v-else class="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
        <table class="min-w-full divide-y divide-gray-200">
          <thead class="bg-gray-50">
            <tr>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider w-64">Setting</th>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Description</th>
              <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider w-48">Value</th>
              <th class="px-6 py-3 text-right text-xs font-semibold text-gray-500 uppercase tracking-wider w-24">Action</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100">
            <tr v-for="setting in settings" :key="setting.key" class="hover:bg-gray-50 transition-colors">
              <td class="px-6 py-4">
                <span class="font-mono text-xs font-medium text-gray-700">{{ setting.key }}</span>
              </td>
              <td class="px-6 py-4 text-sm text-gray-500">{{ setting.description }}</td>
              <td class="px-6 py-4">
                <input
                  :value="settingEditValue(setting.key)"
                  @input="onSettingInput(setting.key, ($event.target as HTMLInputElement).value)"
                  class="w-full px-3 py-1.5 text-sm border rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  :class="isSettingDirty(setting.key) ? 'border-amber-300 bg-amber-50' : 'border-gray-300'"
                />
              </td>
              <td class="px-6 py-4 text-right">
                <button
                  @click="saveSettingValue(setting.key)"
                  :disabled="!isSettingDirty(setting.key)"
                  class="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  Save
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

    </div> <!-- end settings tab -->

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

    <GroupRepositoriesModal
      v-if="showGroupReposModal && viewingReposGroup"
      :group-id="viewingReposGroup.id"
      :group-name="viewingReposGroup.name"
      @close="closeGroupReposModal"
    />

    <CredentialFormModal
      v-if="showCredentialModal"
      :entity-type="credentialEntityType"
      :entity-id="credentialEntityId"
      :entity-name="credentialEntityName"
      @close="closeCredentialModal"
      @saved="handleCredentialSaved"
      @removed="handleCredentialRemoved"
    />

    <ServiceAccountFormModal
      v-if="showServiceAccountFormModal"
      :account="editingServiceAccount"
      @close="closeServiceAccountFormModal"
      @saved="handleServiceAccountSaved"
    />

    <ServiceAccountTokensModal
      v-if="showServiceAccountTokensModal && managingServiceAccount"
      :account="managingServiceAccount"
      @close="closeTokensModal"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import type { Repository } from '../types/repository'
import type { GitProviderGroup } from '../types/git-provider-group'
import type { GitCredential } from '../types/git-credential'
import type { UserAccount, UserAccountPage } from '../types/user-account'
import type { ServiceAccount } from '../types/service-account'
import type { ScanJob } from '../types/scan-job'
import type { AppSetting } from '../types/app-setting'
import { listRepositories } from '../api/repositories'
import { listGitProviderGroups, deleteGitProviderGroup } from '../api/git-provider-groups'
import { getRepositoryCredential, getGroupCredential, getGroupCloneCredential } from '../api/git-credentials'
import { triggerRepositoryScan } from '../api/repositories'
import { triggerGroupScan } from '../api/git-provider-groups'
import { listUserAccounts, updateUserAccount } from '../api/user-accounts'
import { listServiceAccounts, deleteServiceAccount } from '../api/service-accounts'
import { listScanJobs, deleteScanJob, deleteAllQueuedScanJobs } from '../api/scan-jobs'
import { listSettings, updateSetting } from '../api/settings'
import RepositoryFormModal from '../components/RepositoryFormModal.vue'
import ConfirmDeleteModal from '../components/ConfirmDeleteModal.vue'
import GitProviderGroupFormModal from '../components/GitProviderGroupFormModal.vue'
import GroupRepositoriesModal from '../components/GroupRepositoriesModal.vue'
import CredentialFormModal from '../components/CredentialFormModal.vue'
import ServiceAccountFormModal from '../components/ServiceAccountFormModal.vue'
import ServiceAccountTokensModal from '../components/ServiceAccountTokensModal.vue'

// ── Tab navigation ────────────────────────────────────────────────────────────

const tabs = [
  { id: 'repositories',    label: 'Repositories'    },
  { id: 'users',           label: 'Users'           },
  { id: 'serviceaccounts', label: 'Service Accounts' },
  { id: 'scanjobs',        label: 'Scan Jobs'       },
  { id: 'settings',        label: 'Settings'        }
] as const

type TabId = typeof tabs[number]['id']
const activeTab = ref<TabId>('repositories')

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

// ── Credentials ───────────────────────────────────────────────────────────────

const repoCredentials = ref<Record<number, GitCredential | null>>({})
const groupCredentials = ref<Record<number, GitCredential | null>>({})
const groupCloneCredentials = ref<Record<number, GitCredential | null>>({})

const showCredentialModal = ref(false)
const credentialEntityType = ref<'repository' | 'group' | 'group-clone'>('repository')
const credentialEntityId = ref(0)
const credentialEntityName = ref('')

// ── Group Repos Modal ─────────────────────────────────────────────────────────

const showGroupReposModal = ref(false)
const viewingReposGroup = ref<GitProviderGroup | undefined>(undefined)

// ── Scan trigger ──────────────────────────────────────────────────────────────

const scanLoading = ref<Record<string, boolean>>({})
const scanQueued = ref<Record<string, boolean>>({})

function isScanLoading(entityType: 'repository' | 'group', id: number): boolean {
  return !!scanLoading.value[`${entityType}-${id}`]
}

function isScanQueued(entityType: 'repository' | 'group', id: number): boolean {
  return !!scanQueued.value[`${entityType}-${id}`]
}

async function triggerScan(entityType: 'repository' | 'group', id: number) {
  const key = `${entityType}-${id}`
  scanLoading.value[key] = true
  scanQueued.value[key] = false
  try {
    if (entityType === 'repository') {
      await triggerRepositoryScan(id)
    } else {
      await triggerGroupScan(id)
    }
    scanQueued.value[key] = true
    setTimeout(() => { scanQueued.value[key] = false }, 3000)
  } catch (e) {
    console.error('Scan trigger failed', e)
  } finally {
    scanLoading.value[key] = false
  }
}

onMounted(() => {
  fetchRepositories()
  fetchGroups()
  fetchUsers()
  fetchServiceAccounts()
})

async function fetchRepositories() {
  reposLoading.value = true
  reposLoadError.value = ''
  try {
    repositories.value = await listRepositories()
    loadRepoCredentials()
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
    loadGroupCredentials()
  } catch (error) {
    groupsLoadError.value = error instanceof Error ? error.message : 'Unknown error'
  } finally {
    groupsLoading.value = false
  }
}

function loadRepoCredentials() {
  repositories.value.forEach(repo => {
    getRepositoryCredential(repo.id)
      .then(credential => { repoCredentials.value[repo.id] = credential })
      .catch(() => { repoCredentials.value[repo.id] = null })
  })
}

function loadGroupCredentials() {
  groups.value.forEach(group => {
    getGroupCredential(group.id)
      .then(credential => { groupCredentials.value[group.id] = credential })
      .catch(() => { groupCredentials.value[group.id] = null })
    getGroupCloneCredential(group.id)
      .then(credential => { groupCloneCredentials.value[group.id] = credential })
      .catch(() => { groupCloneCredentials.value[group.id] = null })
  })
}

function openCredentialModal(entityType: 'repository' | 'group' | 'group-clone', id: number, name: string) {
  credentialEntityType.value = entityType
  credentialEntityId.value = id
  credentialEntityName.value = name
  showCredentialModal.value = true
}

function closeCredentialModal() {
  showCredentialModal.value = false
}

function handleCredentialSaved(credential: GitCredential) {
  if (credentialEntityType.value === 'repository') {
    repoCredentials.value[credentialEntityId.value] = credential
  } else if (credentialEntityType.value === 'group-clone') {
    groupCloneCredentials.value[credentialEntityId.value] = credential
  } else {
    groupCredentials.value[credentialEntityId.value] = credential
  }
  closeCredentialModal()
}

function handleCredentialRemoved() {
  if (credentialEntityType.value === 'repository') {
    repoCredentials.value[credentialEntityId.value] = null
  } else if (credentialEntityType.value === 'group-clone') {
    groupCloneCredentials.value[credentialEntityId.value] = null
  } else {
    groupCredentials.value[credentialEntityId.value] = null
  }
  closeCredentialModal()
}

function openGroupReposModal(group: GitProviderGroup) {
  viewingReposGroup.value = group
  showGroupReposModal.value = true
}

function closeGroupReposModal() {
  showGroupReposModal.value = false
  viewingReposGroup.value = undefined
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

// ── Users ─────────────────────────────────────────────────────────────────────

const userPage = ref<UserAccountPage | null>(null)
const usersLoading = ref(false)
const usersLoadError = ref('')

const userSearchQuery = ref('')
const userCurrentPage = ref(1)
const userPageSize = 25

let userSearchDebounceTimer: ReturnType<typeof setTimeout> | null = null

const paginatedUsers = computed(() => userPage.value?.items ?? [])
const userTotalPages = computed(() => userPage.value?.totalPages ?? 1)
const userTotalItems = computed(() => userPage.value?.totalItems ?? 0)

/** Sliding window of page numbers shown around the current page, with ellipsis. */
const userPageWindow = computed<(number | '...')[]>(() => {
  const total = userTotalPages.value
  const current = userCurrentPage.value
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  const pages: (number | '...')[] = [1]
  if (current > 3) pages.push('...')
  const start = Math.max(2, current - 1)
  const end   = Math.min(total - 1, current + 1)
  for (let p = start; p <= end; p++) pages.push(p)
  if (current < total - 2) pages.push('...')
  pages.push(total)
  return pages
})

async function fetchUsers() {
  usersLoading.value = true
  usersLoadError.value = ''
  try {
    userPage.value = await listUserAccounts(userSearchQuery.value, userCurrentPage.value, userPageSize)
  } catch (error) {
    usersLoadError.value = error instanceof Error ? error.message : 'Unknown error'
  } finally {
    usersLoading.value = false
  }
}

function onUserSearchInput() {
  if (userSearchDebounceTimer !== null) clearTimeout(userSearchDebounceTimer)
  userSearchDebounceTimer = setTimeout(() => {
    userCurrentPage.value = 1
    fetchUsers()
  }, 300)
}

function onUserPageChange(page: number) {
  userCurrentPage.value = page
  fetchUsers()
}

async function setAdminStatus(userAccount: UserAccount, admin: boolean) {
  try {
    await updateUserAccount(userAccount.id, { admin })
    await fetchUsers()
  } catch (error) {
    alert(error instanceof Error ? error.message : 'Failed to update user')
  }
}

function formatDate(isoString: string): string {
  return new Date(isoString).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short'
  })
}

// ── Service Accounts ──────────────────────────────────────────────────────────

const serviceAccounts = ref<ServiceAccount[]>([])
const serviceAccountsLoading = ref(false)
const serviceAccountsLoadError = ref('')

const showServiceAccountFormModal = ref(false)
const editingServiceAccount = ref<ServiceAccount | undefined>(undefined)

const showServiceAccountTokensModal = ref(false)
const managingServiceAccount = ref<ServiceAccount | undefined>(undefined)

async function fetchServiceAccounts() {
  serviceAccountsLoading.value = true
  serviceAccountsLoadError.value = ''
  try {
    serviceAccounts.value = await listServiceAccounts()
  } catch (error) {
    serviceAccountsLoadError.value = error instanceof Error ? error.message : 'Unknown error'
  } finally {
    serviceAccountsLoading.value = false
  }
}

function openCreateServiceAccountModal() {
  editingServiceAccount.value = undefined
  showServiceAccountFormModal.value = true
}

function openEditServiceAccountModal(account: ServiceAccount) {
  editingServiceAccount.value = account
  showServiceAccountFormModal.value = true
}

function closeServiceAccountFormModal() {
  showServiceAccountFormModal.value = false
  editingServiceAccount.value = undefined
}

function handleServiceAccountSaved(saved: ServiceAccount) {
  const index = serviceAccounts.value.findIndex(a => a.id === saved.id)
  if (index >= 0) {
    serviceAccounts.value[index] = saved
  } else {
    serviceAccounts.value.push(saved)
  }
  closeServiceAccountFormModal()
}

async function removeServiceAccount(account: ServiceAccount) {
  if (!confirm(`Delete service account "${account.name}" and all its tokens? This cannot be undone.`)) return
  try {
    await deleteServiceAccount(account.id)
    serviceAccounts.value = serviceAccounts.value.filter(a => a.id !== account.id)
  } catch (error) {
    alert(error instanceof Error ? error.message : 'Failed to delete service account')
  }
}

function openTokensModal(account: ServiceAccount) {
  managingServiceAccount.value = account
  showServiceAccountTokensModal.value = true
}

function closeTokensModal() {
  showServiceAccountTokensModal.value = false
  managingServiceAccount.value = undefined
}

// ── Scan Jobs ─────────────────────────────────────────────────────────────────

type ScanJobStatusFilter = '' | 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED'

const scanJobs = ref<ScanJob[]>([])
const scanJobsLoading = ref(false)
const scanJobsLoadError = ref('')
const scanJobStatusFilter = ref<ScanJobStatusFilter>('QUEUED')

/** Reload scan jobs whenever the user switches to this tab. */
watch(activeTab, (tab) => {
  if (tab === 'scanjobs') fetchScanJobs()
})

async function fetchScanJobs() {
  scanJobsLoading.value = true
  scanJobsLoadError.value = ''
  try {
    const status = scanJobStatusFilter.value || undefined
    scanJobs.value = await listScanJobs(status)
  } catch (error) {
    scanJobsLoadError.value = error instanceof Error ? error.message : 'Unknown error'
  } finally {
    scanJobsLoading.value = false
  }
}

async function onScanJobStatusFilterChange(status: ScanJobStatusFilter) {
  scanJobStatusFilter.value = status
  await fetchScanJobs()
}

async function removeScanJob(job: ScanJob) {
  if (!confirm(`Delete scan job #${job.id} for repository ${repositoryName(job.repositoryId)}?`)) return
  try {
    await deleteScanJob(job.id)
    scanJobs.value = scanJobs.value.filter(j => j.id !== job.id)
  } catch (error) {
    alert(error instanceof Error ? error.message : 'Failed to delete scan job')
  }
}

async function removeAllQueuedScanJobs() {
  const count = scanJobs.value.filter(j => j.status === 'QUEUED').length
  if (count === 0) return
  if (!confirm(`Delete all ${count} queued scan job${count === 1 ? '' : 's'}? This cannot be undone.`)) return
  try {
    await deleteAllQueuedScanJobs()
    await fetchScanJobs()
  } catch (error) {
    alert(error instanceof Error ? error.message : 'Failed to delete queued scan jobs')
  }
}

/** Returns the human-readable repository name for a given ID, falling back to the raw ID. */
function repositoryName(repositoryId: number): string {
  return repositories.value.find(r => r.id === repositoryId)?.name ?? `#${repositoryId}`
}

const TRIGGER_LABELS: Record<string, string> = {
  WEBHOOK: 'Webhook',
  CRON:    'Scheduled',
  MANUAL:  'Manual'
}

const STATUS_CLASSES: Record<string, string> = {
  QUEUED:  'bg-amber-50 text-amber-700 border-amber-200',
  RUNNING: 'bg-blue-50 text-blue-700 border-blue-200',
  DONE:    'bg-green-50 text-green-700 border-green-200',
  FAILED:  'bg-red-50 text-red-700 border-red-200'
}

const queuedScanJobCount = computed(() => scanJobs.value.filter(j => j.status === 'QUEUED').length)

// ── Settings ──────────────────────────────────────────────────────────────────

const settings = ref<AppSetting[]>([])
const settingsLoading = ref(false)
const settingsLoadError = ref('')
const settingsDirty = ref<Record<string, string>>({})

watch(activeTab, (tab) => {
  if (tab === 'settings') fetchSettings()
})

async function fetchSettings() {
  settingsLoading.value = true
  settingsLoadError.value = ''
  try {
    settings.value = await listSettings()
    settingsDirty.value = {}
  } catch (error) {
    settingsLoadError.value = error instanceof Error ? error.message : 'Unknown error'
  } finally {
    settingsLoading.value = false
  }
}

function settingEditValue(key: string): string {
  return key in settingsDirty.value ? settingsDirty.value[key] : (settings.value.find(s => s.key === key)?.value ?? '')
}

function onSettingInput(key: string, value: string) {
  settingsDirty.value[key] = value
}

async function saveSettingValue(key: string) {
  const value = settingsDirty.value[key]
  if (value === undefined) return
  try {
    await updateSetting(key, value)
    const setting = settings.value.find(s => s.key === key)
    if (setting) setting.value = value
    delete settingsDirty.value[key]
  } catch (error) {
    alert(error instanceof Error ? error.message : 'Failed to save setting')
  }
}

function isSettingDirty(key: string): boolean {
  return key in settingsDirty.value && settingsDirty.value[key] !== settings.value.find(s => s.key === key)?.value
}
</script>
