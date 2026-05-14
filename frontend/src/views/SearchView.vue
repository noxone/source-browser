<template>
  <div>
    <!-- Search bar -->
    <div class="mb-6">
      <h2 class="text-2xl font-bold text-gray-900 mb-4">Search</h2>
      <form @submit.prevent="runSearch" class="flex gap-2">
        <div class="relative flex-1">
          <div class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
            <svg class="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <circle cx="11" cy="11" r="8"/>
              <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-4.35-4.35"/>
            </svg>
          </div>
          <input
            v-model="queryInput"
            type="text"
            placeholder="Search for classes, methods, fields…"
            class="block w-full rounded-lg border border-gray-300 bg-white py-2.5 pl-10 pr-4 text-sm text-gray-900 placeholder-gray-400 shadow-sm focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
            autofocus
          />
        </div>
        <button
          type="submit"
          :disabled="loading"
          class="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors shadow-sm"
        >
          <svg v-if="loading" class="animate-spin w-4 h-4" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
          </svg>
          Search
        </button>
      </form>

      <!-- Repository filter -->
      <div v-if="repositories.length > 0" class="mt-3 flex items-center gap-2">
        <span class="text-xs text-gray-500 font-medium shrink-0">Filter by repo:</span>
        <RepoMultiSelect :repositories="repositories" v-model="selectedRepoIds" />
      </div>

      <!-- Search syntax hints -->
      <div class="mt-4 rounded-lg border border-gray-100 bg-gray-50 px-4 py-3 text-xs text-gray-500">
        <span class="font-semibold text-gray-600 mr-2">Search syntax:</span>
        <code class="font-mono bg-white border border-gray-200 rounded px-1 mr-2">java spring</code> both terms ·
        <code class="font-mono bg-white border border-gray-200 rounded px-1 mr-2 ml-1">java OR spring</code> either term ·
        <code class="font-mono bg-white border border-gray-200 rounded px-1 mr-2 ml-1">"exact phrase"</code> phrase match ·
        <code class="font-mono bg-white border border-gray-200 rounded px-1 ml-1">-deprecated</code> exclude term
      </div>
    </div>

    <!-- Initial state -->
    <div v-if="!searched" class="flex flex-col items-center justify-center py-24 text-center text-gray-400">
      <svg class="w-14 h-14 mb-4 text-gray-300" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
        <circle cx="11" cy="11" r="8"/>
        <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-4.35-4.35"/>
      </svg>
      <p class="text-sm">Enter a symbol name to search across all indexed repositories.</p>
    </div>

    <!-- Error state -->
    <div v-else-if="error" class="rounded-xl border border-red-200 bg-red-50 px-6 py-5 text-sm text-red-700">
      <p class="font-semibold">Search failed</p>
      <p class="mt-1">{{ error }}</p>
      <button @click="runSearch" class="mt-3 underline hover:no-underline">Try again</button>
    </div>

    <!-- Empty results -->
    <div v-else-if="results.length === 0" class="flex flex-col items-center justify-center py-24 text-center text-gray-400">
      <svg class="w-14 h-14 mb-4 text-gray-300" fill="none" stroke="currentColor" stroke-width="1.5" viewBox="0 0 24 24">
        <circle cx="11" cy="11" r="8"/>
        <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-4.35-4.35"/>
      </svg>
      <p class="text-sm">No results for <span class="font-medium text-gray-600">{{ lastQuery }}</span>.</p>
    </div>

    <!-- Results table -->
    <div v-else class="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Repository</th>
            <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">File</th>
            <th class="px-6 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">Found areas</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100">
          <tr v-for="result in results" :key="result.fileId + result.snippet" class="hover:bg-gray-50 transition-colors">
            <!-- Repository tag -->
            <td class="px-6 py-4 whitespace-nowrap">
              <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-indigo-50 text-indigo-700 border border-indigo-100">
                {{ result.repositoryName }}
              </span>
            </td>

            <!-- File path with middle-ellipsis and hover popover -->
            <td class="px-6 py-4">
              <div class="relative group inline-block max-w-full">
                <RouterLink
                  :to="{ name: 'file', params: { fileId: result.fileId } }"
                  class="font-mono text-xs text-indigo-600 hover:underline"
                >{{ truncatePath(result.filePath) }}</RouterLink>

                <!-- Popover shown when path is truncated -->
                <div
                  v-if="result.filePath.length > PATH_TRUNCATE_LIMIT"
                  class="absolute z-10 left-0 top-full mt-1 hidden group-hover:block bg-gray-900 text-white text-xs font-mono rounded-lg px-3 py-2 shadow-lg whitespace-nowrap max-w-lg break-all pointer-events-none"
                >{{ result.filePath }}</div>
              </div>
            </td>

            <!-- Found areas / snippet with highlighted matches -->
            <td class="px-6 py-4">
              <span class="font-mono text-sm text-gray-800">
                <template v-for="(seg, si) in parseSnippet(result.snippet)" :key="si">
                  <mark
                    v-if="seg.highlighted"
                    class="bg-yellow-200 text-yellow-900 rounded px-0.5 not-italic"
                  >{{ seg.text }}</mark>
                  <span v-else>{{ seg.text }}</span>
                </template>
              </span>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="px-6 py-3 bg-gray-50 border-t border-gray-100 text-xs text-gray-400">
        {{ results.length }} {{ results.length === 1 ? 'result' : 'results' }} for
        <span class="font-medium text-gray-600">{{ lastQuery }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import type { SearchResult } from '../types/search'
import type { Repository } from '../types/repository'
import { search } from '../api/search'
import { listRepositories } from '../api/repositories'
import RepoMultiSelect from '../components/RepoMultiSelect.vue'

const PATH_TRUNCATE_LIMIT = 100

// Sentinel characters matching PanacheDocumentRepository.HL_START / HL_STOP
const HIGHLIGHT_START = ''
const HIGHLIGHT_STOP  = ''

const route = useRoute()
const router = useRouter()

const queryInput = ref('')
const lastQuery = ref('')
const results = ref<SearchResult[]>([])
const loading = ref(false)
const error = ref('')
const searched = ref(false)

const repositories = ref<Repository[]>([])
const selectedRepoIds = ref<number[]>([])

onMounted(async () => {
  // Load repository list for the filter — non-fatal if it fails
  try {
    repositories.value = await listRepositories()
  } catch {
    // Repo filter stays hidden
  }

  const q = route.query.q as string | undefined
  const repoIdsParam = route.query.repoIds

  if (q) queryInput.value = q

  if (repoIdsParam) {
    const ids = (Array.isArray(repoIdsParam) ? repoIdsParam : [repoIdsParam])
      .map(Number)
      .filter(n => Number.isFinite(n) && n > 0)
    selectedRepoIds.value = ids
  }

  if (q) runSearch()
})

function truncatePath(path: string, maxLen = PATH_TRUNCATE_LIMIT): string {
  if (path.length <= maxLen) return path
  const half = Math.floor((maxLen - 1) / 2)
  return path.slice(0, half) + '…' + path.slice(path.length - half)
}

interface SnippetSegment {
  text: string
  highlighted: boolean
}

function parseSnippet(raw: string): SnippetSegment[] {
  const segments: SnippetSegment[] = []
  let s = raw
  while (s.length > 0) {
    const si = s.indexOf(HIGHLIGHT_START)
    if (si === -1) {
      segments.push({ text: s, highlighted: false })
      break
    }
    if (si > 0) segments.push({ text: s.slice(0, si), highlighted: false })
    s = s.slice(si + 1)
    const ei = s.indexOf(HIGHLIGHT_STOP)
    if (ei === -1) {
      segments.push({ text: s, highlighted: false })
      break
    }
    segments.push({ text: s.slice(0, ei), highlighted: true })
    s = s.slice(ei + 1)
  }
  return segments
}

async function runSearch() {
  const q = queryInput.value.trim()
  if (!q) return

  // Persist the query and active repo filter in the URL
  const repoIdsForUrl = selectedRepoIds.value.length > 0
    ? selectedRepoIds.value.map(String)
    : undefined

  router.replace({
    name: 'search',
    query: {
      q,
      ...(repoIdsForUrl ? { repoIds: repoIdsForUrl } : {}),
    },
  })

  loading.value = true
  error.value = ''
  searched.value = true
  lastQuery.value = q

  try {
    results.value = await search(q, 50, 0, selectedRepoIds.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error'
    results.value = []
  } finally {
    loading.value = false
  }
}
</script>
