<template>
  <div class="relative h-full overflow-hidden bg-white">

    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center h-full text-gray-400">
      <svg class="animate-spin w-6 h-6 mr-3" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
      </svg>
      Loading file…
    </div>

    <!-- Error -->
    <div v-else-if="error" class="m-6 rounded-xl border border-red-200 bg-red-50 px-6 py-5 text-sm text-red-700">
      <p class="font-semibold">Failed to load file</p>
      <p class="mt-1">{{ error }}</p>
    </div>

    <!-- File content -->
    <div v-else class="h-full overflow-auto font-mono text-sm leading-5">
      <div class="py-4">
        <div
          v-for="line in renderedLines"
          :key="line.number"
          class="line"
          :data-line="line.number"
        ><template v-if="fileInfo?.hasTokenStream"><span
              v-for="(token, ti) in line.tokens"
              :key="ti"
              :class="[
                tokenColorClass(token.k),
                isTokenClickable(token) ? 'cursor-pointer' : '',
                isGroupHovered(token) ? 'underline text-indigo-600' : (isTokenClickable(token) ? 'hover:underline hover:text-indigo-600' : ''),
                isSelectedToken(token) ? 'bg-yellow-200 rounded outline outline-1 outline-yellow-400' : (isHighlightGroupMember(token) ? 'bg-yellow-100 rounded' : ''),
              ]"
              @mouseenter="token.g != null ? hoveredGroup = token.g : undefined"
              @mouseleave="token.g != null ? hoveredGroup = null : undefined"
              @click="isTokenClickable(token) ? selectToken(token) : undefined"
            >{{ token.t }}</span></template><template v-else>{{ line.text }}</template></div>
      </div>
    </div>

    <!-- ── Overlay info panel ──────────────────────────────────────────── -->
    <div
      class="absolute top-3 right-3 bottom-3 flex rounded-xl shadow-xl overflow-hidden border border-gray-200"
      :style="{ width: panelWidth + 'px' }"
    >
      <!-- Drag handle -->
      <div
        class="w-1.5 flex-shrink-0 cursor-col-resize bg-gray-200 hover:bg-indigo-400 transition-colors"
        @mousedown.prevent="startDrag"
      />

      <!-- Panel content -->
      <div class="flex-1 overflow-y-auto bg-white/90 backdrop-blur-sm flex flex-col gap-3 p-3">

        <!-- File info box (always shown) -->
        <div class="rounded-xl border border-gray-200 bg-white shadow-sm p-4">
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
            <InfoRow v-if="fileInfo?.language && fileInfo.language !== 'unknown'" label="Language" :value="fileInfo.language" />
            <InfoRow v-if="fileInfo?.fileSize != null" label="Size" :value="formatFileSize(fileInfo.fileSize)" />
            <InfoRow v-if="fileInfo" label="Indexed at" :value="formatDate(fileInfo.indexedAt)" />
          </dl>
          <p v-if="!fileInfo && !loading" class="text-xs text-gray-400 italic">Loading…</p>
        </div>

        <!-- Commit info box -->
        <div v-if="fileInfo?.branch || fileInfo?.lastCommitSha || fileInfo?.lastAuthorName || fileInfo?.lastAuthorEmail || fileInfo?.lastCommitDate || fileInfo?.lastCommitMessage" class="rounded-xl border border-gray-200 bg-white shadow-sm p-4">
          <button
            class="flex items-center justify-between w-full text-left"
            @click="commitBoxCollapsed = !commitBoxCollapsed"
          >
            <h3 class="text-xs font-semibold text-gray-500 uppercase tracking-wider">Last Commit</h3>
            <svg
              class="w-3.5 h-3.5 text-gray-400 transition-transform"
              :class="commitBoxCollapsed ? '' : 'rotate-180'"
              fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7"/>
            </svg>
          </button>
          <dl v-if="!commitBoxCollapsed" class="space-y-2 text-sm mt-3">
            <InfoRow v-if="fileInfo?.branch" label="Branch" :value="fileInfo.branch" />
            <InfoRow v-if="fileInfo?.lastCommitSha" label="Commit" :value="fileInfo.lastCommitSha.slice(0, 8)" :href="commitUrl ?? undefined" mono />
            <InfoRow
              v-if="fileInfo?.lastAuthorName || fileInfo?.lastAuthorEmail"
              label="Author"
              :value="fileInfo?.lastAuthorName ?? fileInfo?.lastAuthorEmail ?? ''"
              :href="fileInfo?.lastAuthorEmail ? `mailto:${fileInfo.lastAuthorEmail}` : undefined"
            />
            <InfoRow v-if="fileInfo?.lastCommitDate" label="Date" :value="formatDate(fileInfo.lastCommitDate)" />
            <div v-if="fileInfo?.lastCommitMessage" class="pt-1">
              <dt class="text-xs text-gray-400 mb-0.5">Message</dt>
              <dd class="text-gray-700 text-xs leading-relaxed break-words">{{ fileInfo.lastCommitMessage }}</dd>
            </div>
          </dl>
        </div>

        <!-- Token detail box -->
        <div v-if="selectedToken" class="rounded-xl border border-indigo-100 bg-indigo-50 shadow-sm p-4">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-xs font-semibold text-indigo-600 uppercase tracking-wider">Token</h3>
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

          <!-- Loading spinner -->
          <div v-if="detailLoading" class="flex items-center gap-2 text-xs text-indigo-400 py-1">
            <svg class="animate-spin w-3 h-3" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
            </svg>
            Loading…
          </div>

          <!-- TYPE_REF / TYPE_DECL -->
          <template v-else-if="typeDetail">
            <div class="mb-2">
              <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-indigo-200 text-indigo-800">
                {{ typeDetail.kind }}
              </span>
            </div>
            <dl class="space-y-2 text-sm">
              <InfoRow label="Name" :value="typeDetail.qualifiedName" mono />
              <InfoRow v-if="typeDetail.superclassFqn" label="Extends" :value="typeDetail.superclassFqn" mono />
              <div v-if="typeDetail.implementedInterfaces?.length">
                <dt class="text-xs text-gray-400 mb-0.5">Implements</dt>
                <dd class="font-mono text-xs text-gray-700 space-y-0.5">
                  <div v-for="iface in typeDetail.implementedInterfaces" :key="iface">{{ iface }}</div>
                </dd>
              </div>
              <div v-if="typeDetail.knownSubtypes?.length">
                <dt class="text-xs text-gray-400 mb-0.5">Known subtypes</dt>
                <dd class="font-mono text-xs text-gray-700 space-y-0.5">
                  <div v-for="sub in typeDetail.knownSubtypes" :key="sub.qualifiedName">
                    {{ sub.qualifiedName }}
                    <span class="text-gray-400 ml-1">({{ sub.relationshipKind.toLowerCase() }})</span>
                  </div>
                </dd>
              </div>
              <div v-if="javadocUrl">
                <dt class="text-xs text-gray-400 mb-0.5">Javadoc</dt>
                <dd><a :href="javadocUrl" target="_blank" rel="noopener noreferrer" class="text-indigo-600 hover:underline text-xs inline-flex items-center gap-1">Open Javadoc <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"/></svg></a></dd>
              </div>
            </dl>
          </template>

          <!-- VARIABLE -->
          <template v-else-if="variableDetail">
            <div class="mb-2">
              <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-indigo-200 text-indigo-800">
                {{ variableDetail.variableKind.charAt(0) + variableDetail.variableKind.slice(1).toLowerCase() }}
              </span>
            </div>
            <dl class="space-y-2 text-sm">
              <InfoRow label="Name" :value="variableDetail.name" mono />
              <div v-if="variableDetail.typeFqn">
                <dt class="text-xs text-gray-400 mb-0.5">Type</dt>
                <dd class="font-mono text-xs break-all">
                  <RouterLink
                    v-if="variableDetail.typeRepositoryName && variableDetail.typeFilePath"
                    :to="`/file/${encodeURIComponent(variableDetail.typeRepositoryName)}/${variableDetail.typeFilePath}`"
                    class="text-indigo-600 hover:underline"
                  >{{ variableDetail.typeFqn }}</RouterLink>
                  <span v-else class="text-gray-700">{{ variableDetail.typeFqn }}</span>
                </dd>
              </div>
              <div v-if="javadocUrl">
                <dt class="text-xs text-gray-400 mb-0.5">Type Javadoc</dt>
                <dd><a :href="javadocUrl" target="_blank" rel="noopener noreferrer" class="text-indigo-600 hover:underline text-xs inline-flex items-center gap-1">Open Javadoc <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"/></svg></a></dd>
              </div>
            </dl>
          </template>

          <!-- METHOD_CALL / METHOD_DECL -->
          <template v-else-if="methodDetail">
            <div class="mb-2">
              <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-indigo-200 text-indigo-800">
                {{ methodDetail.detailType === 'METHOD_DECL' ? 'Method declaration' : 'Method call' }}
              </span>
            </div>
            <dl class="space-y-2 text-sm">
              <InfoRow label="Name" :value="methodDetail.name" mono />
              <InfoRow v-if="methodDetail.declaringClass" label="Class" :value="methodDetail.declaringClass" mono />
              <InfoRow v-if="methodDetail.returnType" label="Returns" :value="methodDetail.returnType" mono />
              <div v-if="methodDetail.parameters?.length">
                <dt class="text-xs text-gray-400 mb-0.5">Parameters</dt>
                <dd class="font-mono text-xs text-gray-700 space-y-0.5">
                  <div v-for="p in methodDetail.parameters" :key="p.name">
                    <span class="text-purple-600">{{ p.type }}</span>
                    <span class="ml-1">{{ p.name }}</span>
                  </div>
                </dd>
              </div>
              <div v-if="javadocUrl">
                <dt class="text-xs text-gray-400 mb-0.5">Javadoc</dt>
                <dd><a :href="javadocUrl" target="_blank" rel="noopener noreferrer" class="text-indigo-600 hover:underline text-xs inline-flex items-center gap-1">Open Javadoc <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"/></svg></a></dd>
              </div>
            </dl>
            <div v-if="methodDetail.overloads?.length" class="mt-3 pt-3 border-t border-indigo-100">
              <p class="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Overloads</p>
              <ul class="space-y-1">
                <li v-for="o in methodDetail.overloads" :key="o" class="font-mono text-xs text-gray-700">{{ o }}</li>
              </ul>
            </div>
            <div v-if="methodDetail.implementations?.length" class="mt-3 pt-3 border-t border-indigo-100">
              <p class="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Implementations</p>
              <ul class="space-y-2">
                <li v-for="impl in methodDetail.implementations" :key="impl.qualifiedName" class="text-xs">
                  <RouterLink
                    v-if="impl.repositoryName && impl.filePath"
                    :to="`/file/${encodeURIComponent(impl.repositoryName)}/${impl.filePath}`"
                    class="text-indigo-600 hover:underline font-mono break-all"
                  >{{ impl.filePath }}</RouterLink>
                  <span v-else class="font-mono text-gray-600 break-all">{{ impl.qualifiedName }}</span>
                  <div v-if="impl.repositoryName" class="text-gray-400 mt-0.5">{{ impl.repositoryName }}</div>
                </li>
              </ul>
            </div>
          </template>

          <!-- ANNOTATION -->
          <template v-else-if="annotationDetail">
            <div class="mb-2">
              <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-indigo-200 text-indigo-800">Annotation</span>
            </div>
            <dl class="space-y-2 text-sm">
              <InfoRow label="Name" :value="annotationDetail.qualifiedName" mono />
              <div v-if="javadocUrl">
                <dt class="text-xs text-gray-400 mb-0.5">Javadoc</dt>
                <dd><a :href="javadocUrl" target="_blank" rel="noopener noreferrer" class="text-indigo-600 hover:underline text-xs inline-flex items-center gap-1">Open Javadoc <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"/></svg></a></dd>
              </div>
            </dl>
          </template>

          <!-- KEYWORD -->
          <template v-else-if="keywordDetail">
            <div class="mb-2">
              <span class="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-blue-200 text-blue-800">Keyword</span>
            </div>
            <dl class="space-y-2 text-sm">
              <InfoRow label="Keyword" :value="keywordDetail.keyword" mono />
              <div v-if="keywordDetail.description">
                <dt class="text-xs text-gray-400 mb-0.5">Description</dt>
                <dd class="text-xs text-gray-700">{{ keywordDetail.description }}</dd>
              </div>
              <div v-else class="text-xs text-gray-400 italic">No description yet.</div>
            </dl>
          </template>

          <!-- Fallback: no detail loaded -->
          <template v-else-if="!detailLoading">
            <dl class="space-y-2 text-sm">
              <InfoRow label="Text" :value="selectedToken.t" mono />
            </dl>
          </template>

          <div class="mt-3 pt-3 border-t border-indigo-100 text-xs text-indigo-400">
            Line {{ selectedToken.l }}, col {{ selectedToken.cs }}–{{ selectedToken.ce }}
          </div>
        </div>

      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import type { FileInfo, Token, TokenKind, TokenDetail } from '../types/file'
import type { JavadocProvider } from '../types/javadoc-provider'
import { getFileInfoByPath, getFileContent, getTokenStream, getTokenDetail } from '../api/files'
import { listJavadocProviders } from '../api/javadoc'
import { buildJavadocUrl } from '../utils/javadocUrl'
import InfoRow from '../components/InfoRow.vue'

const COMMIT_BOX_COLLAPSED_KEY = 'fileView.commitBox.collapsed'
const PANEL_WIDTH_KEY = 'fileView.panel.width'

const route = useRoute()
const repoName = computed(() => route.params.repoName as string)
const filePath = computed(() => route.params.filePath as string)

const commitBoxCollapsed = ref(localStorage.getItem(COMMIT_BOX_COLLAPSED_KEY) === 'true')
watch(commitBoxCollapsed, v => localStorage.setItem(COMMIT_BOX_COLLAPSED_KEY, String(v)))

const panelWidth = ref(Number(localStorage.getItem(PANEL_WIDTH_KEY)) || 288)

function startDrag(e: MouseEvent) {
  const startX = e.clientX
  const startWidth = panelWidth.value
  const onMove = (ev: MouseEvent) => {
    panelWidth.value = Math.max(200, Math.min(700, startWidth - (ev.clientX - startX)))
  }
  const onUp = () => {
    localStorage.setItem(PANEL_WIDTH_KEY, String(panelWidth.value))
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

const fileInfo = ref<FileInfo | null>(null)
const tokens = ref<Token[] | null>(null)
const rawContent = ref<string | null>(null)
const loading = ref(true)
const error = ref('')
const selectedToken = ref<Token | null>(null)
const tokenDetail = ref<TokenDetail | null>(null)
const detailLoading = ref(false)
const javadocProviders = ref<JavadocProvider[]>([])
const hoveredGroup = ref<number | null>(null)

// Typed helpers to narrow the discriminated union in templates
const typeDetail = computed(() =>
  (tokenDetail.value?.detailType === 'TYPE_REF' || tokenDetail.value?.detailType === 'TYPE_DECL')
    ? tokenDetail.value : null)
const variableDetail = computed(() =>
  tokenDetail.value?.detailType === 'VARIABLE' ? tokenDetail.value : null)
const methodDetail = computed(() =>
  (tokenDetail.value?.detailType === 'METHOD_CALL' || tokenDetail.value?.detailType === 'METHOD_DECL')
    ? tokenDetail.value : null)
const annotationDetail = computed(() =>
  tokenDetail.value?.detailType === 'ANNOTATION' ? tokenDetail.value : null)
const keywordDetail = computed(() =>
  tokenDetail.value?.detailType === 'KEYWORD' ? tokenDetail.value : null)

// ── Data loading ─────────────────────────────────────────────────────

async function load(repo: string, path: string) {
  loading.value = true
  error.value = ''
  clearSelection()
  fileInfo.value = null
  tokens.value = null
  rawContent.value = null

  try {
    const info = await getFileInfoByPath(repo, path)
    fileInfo.value = info

    if (info.hasTokenStream) {
      tokens.value = await getTokenStream(info.fileId)
    } else {
      rawContent.value = await getFileContent(info.fileId)
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load(repoName.value, filePath.value)
  listJavadocProviders().then(p => { javadocProviders.value = p }).catch(() => {})
})
watch([repoName, filePath], ([newRepo, newPath]) => load(newRepo, newPath))

// ── Token selection ───────────────────────────────────────────────────

function clearSelection() {
  selectedToken.value = null
  tokenDetail.value = null
}

async function selectToken(token: Token) {
  selectedToken.value = token
  tokenDetail.value = null
  detailLoading.value = true
  try {
    tokenDetail.value = await getTokenDetail(fileInfo.value!.fileId, token.l, token.cs)
  } catch {
    // detail not available — panel shows bare position info
  } finally {
    detailLoading.value = false
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
    // ANTLR whitespace tokens can span multiple lines (e.g. "\n    ").
    // Split them so each part is assigned to its actual line number.
    const expanded: Token[] = []
    for (const token of tokens.value) {
      if (token.t.includes('\n')) {
        const parts = token.t.split('\n')
        let lineNum = token.l
        for (const part of parts) {
          expanded.push({ ...token, l: lineNum, t: part })
          lineNum++
        }
      } else {
        expanded.push(token)
      }
    }

    const byLine = new Map<number, Token[]>()
    for (const token of expanded) {
      if (!byLine.has(token.l)) byLine.set(token.l, [])
      byLine.get(token.l)!.push(token)
    }

    // Use a contiguous range so blank lines (no tokens) are preserved.
    let minLine = Infinity, maxLine = -Infinity
    for (const n of byLine.keys()) {
      if (n < minLine) minLine = n
      if (n > maxLine) maxLine = n
    }
    const result: RenderedLine[] = []
    for (let n = minLine; n <= maxLine; n++) {
      result.push({ number: n, tokens: byLine.get(n) ?? [], text: '' })
    }
    return result
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
  return token.d === true
}

function isGroupHovered(token: Token): boolean {
  return token.g != null && token.g === hoveredGroup.value
}

function isSelectedToken(token: Token): boolean {
  return selectedToken.value !== null
    && token.l === selectedToken.value.l
    && token.cs === selectedToken.value.cs
}

function isHighlightGroupMember(token: Token): boolean {
  return selectedToken.value?.hg != null
    && token.hg === selectedToken.value.hg
    && !isSelectedToken(token)
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

const javadocUrl = computed<string | null>(() => {
  const d = tokenDetail.value
  if (!d) return null
  if (d.detailType === 'TYPE_REF' || d.detailType === 'TYPE_DECL' || d.detailType === 'ANNOTATION') {
    return buildJavadocUrl(d.qualifiedName, 'CLASS', null, javadocProviders.value)
  }
  if (d.detailType === 'METHOD_CALL' || d.detailType === 'METHOD_DECL') {
    const fqn = d.declaringClass + '.' + d.name
    const sig = d.parameters?.map(p => p.type).join(', ') ?? null
    return buildJavadocUrl(fqn, 'METHOD', sig ? `(${sig})` : null, javadocProviders.value)
  }
  if (d.detailType === 'VARIABLE') {
    return buildJavadocUrl(d.typeFqn, 'CLASS', null, javadocProviders.value)
  }
  return null
})

const commitUrl = computed<string | null>(() => {
  const sha = fileInfo.value?.lastCommitSha
  const url = fileInfo.value?.repositoryUrl
  if (!sha || !url) return null
  return `${url.replace(/\.git$/, '')}/commit/${sha}`
})
</script>

<style scoped>
.line {
  display: block;
  min-width: 100%;
  white-space: pre;
}
.line::before {
  content: attr(data-line);
  display: inline-block;
  width: 3.5rem;
  padding-right: 0.75rem;
  text-align: right;
  color: #9ca3af;
  user-select: none;
  -webkit-user-select: none;
  font-variant-numeric: tabular-nums;
  opacity: 0.6;
}
.line:hover::before {
  opacity: 1;
}
.line:hover {
  background: rgba(0, 0, 0, 0.03);
}
</style>
