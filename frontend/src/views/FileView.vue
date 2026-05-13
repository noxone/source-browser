<template>
  <div class="flex gap-4 h-full min-h-0">
    <!-- ── Left panel: file content ─────────────────────────────────── -->
    <div class="flex-1 min-w-0 flex flex-col">
      <!-- Header -->
      <div class="flex items-center gap-3 mb-4 flex-shrink-0">
        <RouterLink
          :to="{ name: 'search' }"
          class="inline-flex items-center gap-1 text-sm text-indigo-600 hover:text-indigo-800"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7"/>
          </svg>
          Back
        </RouterLink>
        <span class="text-gray-300">|</span>
        <span class="font-mono text-sm text-gray-700 truncate">{{ fileInfo?.filePath }}</span>
        <span
          v-if="fileInfo"
          class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-indigo-50 text-indigo-700 border border-indigo-100 flex-shrink-0"
        >{{ fileInfo.repositoryName }}</span>
        <span
          v-if="fileInfo?.hasTokenStream"
          class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-green-50 text-green-700 border border-green-100 flex-shrink-0"
          title="Syntax-highlighted via token stream"
        >indexed</span>
      </div>

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
                  :class="[tokenColorClass(token.k), tokenHasMetadata(token) ? 'cursor-pointer hover:underline hover:text-indigo-600' : '']"
                  @click="tokenHasMetadata(token) ? selectToken(token) : undefined"
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
      <div class="rounded-xl border border-gray-200 bg-white shadow-sm p-4">

        <!-- Token detail view -->
        <template v-if="selectedToken">
          <div class="flex items-center gap-2 mb-3">
            <button
              @click="selectedToken = null"
              class="text-xs text-indigo-600 hover:text-indigo-800 inline-flex items-center gap-1"
            >
              <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7"/>
              </svg>
              File info
            </button>
          </div>
          <h3 class="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">Token</h3>
          <dl class="space-y-2 text-sm">
            <InfoRow label="Text" :value="selectedToken.t" mono />
            <InfoRow label="Kind" :value="selectedToken.k" />
            <InfoRow label="Line" :value="String(selectedToken.l)" />
            <InfoRow label="Columns" :value="`${selectedToken.cs}–${selectedToken.ce}`" />
            <InfoRow v-if="selectedToken.q" label="Qualified name" :value="selectedToken.q" mono />
            <InfoRow v-if="selectedToken.s != null" label="Symbol ID" :value="String(selectedToken.s)" />
          </dl>
        </template>

        <!-- File info view -->
        <template v-else>
          <h3 class="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-3">File</h3>
          <dl class="space-y-2 text-sm">
            <InfoRow v-if="fileInfo" label="Path" :value="fileInfo.filePath" mono />
            <InfoRow v-if="fileInfo?.repositoryName" label="Repository" :value="fileInfo.repositoryName" />
            <InfoRow v-if="fileInfo" label="Branch" :value="fileInfo.branch" />
            <InfoRow v-if="fileInfo" label="Language" :value="fileInfo.language" />
            <InfoRow v-if="fileInfo" label="Indexed at" :value="formatDate(fileInfo.indexedAt)" />
          </dl>

          <template v-if="fileInfo?.lastCommitSha">
            <h3 class="text-xs font-semibold text-gray-500 uppercase tracking-wider mt-4 mb-3">Last Commit</h3>
            <dl class="space-y-2 text-sm">
              <InfoRow label="Commit" :value="fileInfo.lastCommitSha.slice(0, 8)" mono />
              <InfoRow v-if="fileInfo.lastAuthorName" label="Author" :value="fileInfo.lastAuthorName" />
              <InfoRow v-if="fileInfo.lastAuthorEmail" label="Email" :value="fileInfo.lastAuthorEmail" />
              <InfoRow v-if="fileInfo.lastCommitDate" label="Date" :value="formatDate(fileInfo.lastCommitDate)" />
              <div v-if="fileInfo.lastCommitMessage" class="pt-1">
                <dt class="text-xs text-gray-400 mb-0.5">Message</dt>
                <dd class="text-gray-700 text-xs leading-relaxed break-words">{{ fileInfo.lastCommitMessage }}</dd>
              </div>
            </dl>
          </template>

          <p v-else-if="fileInfo && !fileInfo.lastCommitSha" class="mt-3 text-xs text-gray-400 italic">
            Git commit info not available.
          </p>

          <p v-if="!fileInfo && !loading" class="text-xs text-gray-400 italic">Loading…</p>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import type { FileInfo, Token, TokenKind } from '../types/file'
import { getFileInfo, getFileContent, getTokenStream } from '../api/files'

/** Simple label+value display row used inside the info panel. */
const InfoRow = {
  props: { label: String, value: String, mono: Boolean },
  template: `
    <div>
      <dt class="text-xs text-gray-400 mb-0.5">{{ label }}</dt>
      <dd :class="['text-gray-700 break-all', mono ? 'font-mono text-xs' : '']">{{ value }}</dd>
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

// ── Data loading ─────────────────────────────────────────────────────

async function load(id: number) {
  loading.value = true
  error.value = ''
  selectedToken.value = null
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

// ── Rendering helpers ─────────────────────────────────────────────────

interface RenderedLine {
  number: number
  tokens: Token[]   // populated when using token stream
  text: string      // populated when using plain text
}

const renderedLines = computed<RenderedLine[]>(() => {
  if (fileInfo.value?.hasTokenStream && tokens.value) {
    // Group tokens by line number
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

function tokenHasMetadata(token: Token): boolean {
  return token.q != null || token.s != null
}

function selectToken(token: Token) {
  selectedToken.value = token
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return iso
  }
}
</script>
