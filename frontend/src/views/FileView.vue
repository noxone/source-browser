<template>
  <div class="flex gap-4 h-full min-h-0">
    <!-- ── Left panel: file content ─────────────────────────────────── -->
    <div class="flex-1 min-w-0 flex flex-col">

      <!-- Loading -->
      <div v-if="loading" class="flex items-center justify-center py-24 text-gray-400">
        <svg class="animate-spin w-6 h-6 mr-3" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
        </svg>
        Loading file…
      </div>

      <!-- Error -->
      <div v-else-if="error" class="rounded-xl border border-red-200 bg-red-50 px-6 py-5 text-sm text-red-700">
        <p class="font-semibold">Failed to load file</p>
        <p class="mt-1">{{ error }}</p>
      </div>

      <!-- File content -->
      <div
        v-else
        class="flex-1 overflow-auto rounded-xl border border-gray-200 bg-white shadow-sm font-mono text-sm leading-5"
      >
        <table class="w-full border-collapse">
          <tbody>
            <tr
              v-for="line in renderedLines"
              :key="line.number"
              class="hover:bg-gray-50 group"
            >
              <!-- Line number gutter -->
              <td
                class="select-none text-right text-gray-400 px-3 py-0 border-r border-gray-100 bg-gray-50 w-12 align-top sticky left-0"
                style="min-width: 3rem; font-variant-numeric: tabular-nums;"
              >{{ line.number }}</td>

              <!-- Token stream line -->
              <td v-if="fileInfo?.hasTokenStream" class="px-4 py-0 whitespace-pre align-top">
                <span
                  v-for="(token, ti) in line.tokens"
                  :key="ti"
                  :class="[tokenColorClass(token.k), isTokenClickable(token) ? 'cursor-pointer hover:underline hover:text-indigo-600' : '', isSelectedToken(token) ? 'bg-yellow-200 rounded outline outline-1 outline-yellow-400' : '']"
                  @click="isTokenClickable(token) ? selectToken(token) : undefined"
                >{{ token.t }}</span>
              </td>

              <!-- Plain text line -->
              <td v-else class="px-4 py-0 whitespace-pre align-top text-gray-800">{{ line.text }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ── Right panel: info pane ──────────────────────────────────── -->
    <div class="w-72 flex-shrink-0 flex flex-col gap-3 overflow-y-auto">

      <!-- File info box (always shown) -->
      <div class="rounded-xl border border-gray-200 bg-white shadow-sm p-4">
        <!-- File path + badges at the top -->
        <div class="mb-3">
          <p class="font-mono text-xs text-gray-800 break-all leading-relaxed">{{ fileInfo?.filePath ?? '…' }}</p>
          <div class="flex flex-wrap gap-1.5 mt-2">
            <a
              v-if="fileInfo?.repositoryName && fileInfo?.repositoryUrl"
              :href="fileInfo.repositoryUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-indigo-50 text-indigo-700 border border-indigo-100 hover:bg-indigo-100"
            >{{ fileInfo.repositoryName }}</a>
            <span
              v-else-if="fileInfo?.repositoryName"
              class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-indigo-50 text-indigo-700 border border-indigo-100"
            >{{ fileInfo.repositoryName }}</span>
            <span
              v-if="fileInfo?.hasTokenStream"
              class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-green-50 text-green-700 border border-green-100"
              title="Syntax-highlighted via token stream"
            >indexed</span>
          </div>
        </div>
        <h3 class="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">File</h3>
        <dl class="space-y-2 text-sm">
          <InfoRow v-if="fileInfo" label="Branch" :value="fileInfo.branch" />
          <InfoRow v-if="fileInfo" label="Language" :value="fileInfo.language" />
          <InfoRow v-if="fileInfo?.fileSize != null" label="Size" :value="formatFileSize(fileInfo.fileSize)" />
          <InfoRow v-if="fileInfo" label="Indexed at" :value="formatDate(fileInfo.indexedAt)" />
          <InfoRow v-if="fileInfo?.lastCommitSha" label="Commit" :value="fileInfo.lastCommitSha.slice(0, 8)" :href="commitUrl ?? undefined" mono />
          <InfoRow v-if="fileInfo?.lastCommitDate" label="Commit date" :value="formatDate(fileInfo.lastCommitDate)" />
        </dl>
        <p v-if="!fileInfo && !loading" class="text-xs text-gray-400 italic">Loading…</p>
      </div>

      <!-- Commit info box (shown when commit data available) -->
      <div v-if="fileInfo?.lastCommitSha" class="rounded-xl border border-gray-200 bg-white shadow-sm p-4">
        <h3 class="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Last Commit</h3>
        <dl class="space-y-2 text-sm">
          <InfoRow label="Commit" :value="fileInfo.lastCommitSha.slice(0, 8)" :href="commitUrl ?? undefined" mono />
          <InfoRow v-if="fileInfo.lastAuthorName" label="Author" :value="fileInfo.lastAuthorName" />
          <InfoRow v-if="fileInfo.lastAuthorEmail" label="Email" :value="fileInfo.lastAuthorEmail" />
          <InfoRow v-if="fileInfo.lastCommitDate" label="Date" :value="formatDate(fileInfo.lastCommitDate)" />
          <div v-if="fileInfo.lastCommitMessage" class="pt-1">
            <dt class="text-xs text-gray-400 mb-0.5">Message</dt>
            <dd class="text-gray-700 text-xs leading-relaxed break-words">{{ fileInfo.lastCommitMessage }}</dd>
          </div>
        </dl>
      </div>

      <!-- Symbol info box (shown when a token is selected) -->
      <div v-if="selectedToken" class="rounded-xl border border-indigo-100 bg-indigo-50 shadow-sm p-4">
        <div class="flex items-center justify-between mb-3">
          <h3 class="text-xs font-semibold text-indigo-600 uppercase tracking-wider">Symbol</h3>
          <button
            @click="clearSelection"
            class="text-indigo-400 hover:text-indigo-600"
            title="Clear selection"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12"/>
            </svg>
          </button>
        </div>

        <!-- Symbol loading -->
        <div v-if="symbolLoading" class="flex items-center gap-2 text-xs text-indigo-400 py-1">
          <svg class="animate-spin w-3 h-3" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
          </svg>
          Resolving symbol…
        </div>

        <!-- Rich symbol info -->
        <template v-else-if="symbolInfo">
          <div class="mb-3">
            <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-indigo-200 text-indigo-800">
              {{ formatKind(symbolInfo.kind) }}
            </span>
          </div>
          <dl class="space-y-2 text-sm">
            <InfoRow label="Name" :value="symbolInfo.simpleName" mono />
            <InfoRow label="Qualified name" :value="symbolInfo.qualifiedName" mono />
            <InfoRow v-if="symbolInfo.signature" label="Signature" :value="symbolInfo.signature" mono />
            <div v-if="symbolInfo.filePath">
              <dt class="text-xs text-gray-400 mb-0.5">Defined in</dt>
              <dd>
                <RouterLink
                  :to="{ name: 'file', params: { fileId: symbolInfo.fileId } }"
                  class="text-indigo-600 hover:text-indigo-800 font-mono text-xs break-all hover:underline"
                >{{ symbolInfo.filePath }}</RouterLink>
              </dd>
            </div>
          </dl>
          <div class="mt-3 pt-3 border-t border-indigo-100 text-xs text-indigo-400">
            Line {{ selectedToken.l }}, col {{ selectedToken.cs }}–{{ selectedToken.ce }}
          </div>
        </template>

        <!-- Fallback: no symbol metadata, show raw token info -->
        <template v-else>
          <dl class="space-y-2 text-sm">
            <InfoRow label="Text" :value="selectedToken.t" mono />
            <InfoRow v-if="selectedToken.q" label="Qualified name" :value="selectedToken.q" mono />
          </dl>
          <div class="mt-2 text-xs text-indigo-400">
            Line {{ selectedToken.l }}, col {{ selectedToken.cs }}–{{ selectedToken.ce }}
          </div>
        </template>
      </div>

      <!-- References box (shown when selected token has a symbol ID) -->
      <div v-if="selectedToken?.s != null" class="rounded-xl border border-gray-200 bg-white shadow-sm p-4">
        <h3 class="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">References</h3>
        <div v-if="referencesLoading" class="flex items-center gap-2 text-xs text-gray-400 py-2">
          <svg class="animate-spin w-4 h-4" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
          </svg>
          Loading references…
        </div>
        <div v-else-if="referencesError" class="text-xs text-red-600">{{ referencesError }}</div>
        <div v-else-if="references.length === 0" class="text-xs text-gray-400 italic">No references found.</div>
        <ul v-else class="space-y-2">
          <li
            v-for="ref in references"
            :key="ref.referenceId"
            class="text-xs"
          >
            <RouterLink
              :to="{ name: 'file', params: { fileId: ref.fileId } }"
              class="text-indigo-600 hover:text-indigo-800 font-mono break-all hover:underline"
            >{{ ref.filePath ?? `File #${ref.fileId}` }}</RouterLink>
            <div class="text-gray-400 mt-0.5">
              <span class="text-gray-600">{{ ref.kind.replace(/_/g, ' ') }}</span>
              <span v-if="ref.line != null"> · line {{ ref.line }}</span>
            </div>
          </li>
        </ul>
      </div>

    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import type { FileInfo, Token, TokenKind, SymbolInfo, SymbolReference } from '../types/file'
import { getFileInfo, getFileContent, getTokenStream, getSymbol, getSymbolReferences } from '../api/files'

/** Simple label+value display row used inside the info panel. */
const InfoRow = {
  props: { label: String, value: String, mono: Boolean, href: String },
  template: `
    <div>
      <dt class="text-xs text-gray-400 mb-0.5">{{ label }}</dt>
      <dd :class="['text-gray-700 break-all', mono ? 'font-mono text-xs' : '']">
        <a v-if="href" :href="href" target="_blank" rel="noopener noreferrer"
           class="text-indigo-600 hover:underline">{{ value }}</a>
        <template v-else>{{ value }}</template>
      </dd>
    </div>
  `
}

const route = useRoute()
const fileId = computed(() => Number(route.params.fileId))

const fileInfo = ref<FileInfo | null>(null)
const tokens = ref<Token[] | null>(null)
const rawContent = ref<string | null>(null)
const loading = ref(true)
const error = ref('')
const selectedToken = ref<Token | null>(null)
const symbolInfo = ref<SymbolInfo | null>(null)
const symbolLoading = ref(false)
const references = ref<SymbolReference[]>([])
const referencesLoading = ref(false)
const referencesError = ref('')

// ── Data loading ─────────────────────────────────────────────────────

async function load(id: number) {
  loading.value = true
  error.value = ''
  clearSelection()
  fileInfo.value = null
  tokens.value = null
  rawContent.value = null

  try {
    const info = await getFileInfo(id)
    fileInfo.value = info

    if (info.hasTokenStream) {
      tokens.value = await getTokenStream(id)
    } else {
      rawContent.value = await getFileContent(id)
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error'
  } finally {
    loading.value = false
  }
}

onMounted(() => load(fileId.value))
watch(fileId, (newId) => load(newId))

// ── Token selection ───────────────────────────────────────────────────

function clearSelection() {
  selectedToken.value = null
  symbolInfo.value = null
  references.value = []
  referencesError.value = ''
}

async function selectToken(token: Token) {
  selectedToken.value = token
  symbolInfo.value = null
  references.value = []
  referencesError.value = ''

  if (token.s != null) {
    // Fetch symbol details and references in parallel
    symbolLoading.value = true
    referencesLoading.value = true

    const [symbolResult, refsResult] = await Promise.allSettled([
      getSymbol(token.s),
      getSymbolReferences(token.s),
    ])

    symbolLoading.value = false
    referencesLoading.value = false

    if (symbolResult.status === 'fulfilled') {
      symbolInfo.value = symbolResult.value
    }
    if (refsResult.status === 'fulfilled') {
      references.value = refsResult.value
    } else {
      referencesError.value = refsResult.reason instanceof Error
        ? refsResult.reason.message : 'Failed to load references'
    }
  }
}

// ── Rendering helpers ─────────────────────────────────────────────────

interface RenderedLine {
  number: number
  tokens: Token[]
  text: string
}

const renderedLines = computed<RenderedLine[]>(() => {
  if (fileInfo.value?.hasTokenStream && tokens.value) {
    const byLine = new Map<number, Token[]>()
    for (const token of tokens.value) {
      if (!byLine.has(token.l)) byLine.set(token.l, [])
      byLine.get(token.l)!.push(token)
    }
    const lineNumbers = [...byLine.keys()].sort((a, b) => a - b)
    return lineNumbers.map(n => ({ number: n, tokens: byLine.get(n)!, text: '' }))
  }

  if (rawContent.value != null) {
    return rawContent.value.split('\n').map((text, i) => ({
      number: i + 1,
      tokens: [],
      text
    }))
  }

  return []
})

function tokenColorClass(kind: TokenKind): string {
  switch (kind) {
    case 'KEYWORD':           return 'text-blue-600 font-semibold'
    case 'STRING_LITERAL':
    case 'CHAR_LITERAL':      return 'text-orange-600'
    case 'INTEGER_LITERAL':
    case 'LONG_LITERAL':
    case 'FLOAT_LITERAL':
    case 'DOUBLE_LITERAL':    return 'text-purple-600'
    case 'LINE_COMMENT':
    case 'BLOCK_COMMENT':
    case 'JAVADOC_COMMENT':   return 'text-green-600 italic'
    case 'OPERATOR':          return 'text-gray-500'
    case 'SEPARATOR':         return 'text-gray-400'
    case 'WHITESPACE':        return ''
    default:                  return 'text-gray-800'
  }
}

function isTokenClickable(token: Token): boolean {
  return token.k === 'IDENTIFIER'
}

function isSelectedToken(token: Token): boolean {
  return selectedToken.value !== null
    && token.l === selectedToken.value.l
    && token.cs === selectedToken.value.cs
}

const KIND_LABELS: Record<string, string> = {
  CLASS: 'Class',
  INTERFACE: 'Interface',
  ENUM: 'Enum',
  RECORD: 'Record',
  ANNOTATION_TYPE: 'Annotation',
  METHOD: 'Method',
  CONSTRUCTOR: 'Constructor',
  FIELD: 'Field',
  PARAMETER: 'Parameter',
  LOCAL_VARIABLE: 'Local variable',
}

function formatKind(kind: string): string {
  return KIND_LABELS[kind] ?? kind
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return iso
  }
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const commitUrl = computed<string | null>(() => {
  const sha = fileInfo.value?.lastCommitSha
  const url = fileInfo.value?.repositoryUrl
  if (!sha || !url) return null
  return `${url.replace(/\.git$/, '')}/commit/${sha}`
})
</script>
