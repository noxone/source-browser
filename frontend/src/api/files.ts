import type { FileInfo, Token, SymbolInfo, SymbolReference } from '../types/file'
import { authenticatedFetch } from './http'

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text().catch(() => response.statusText)
    throw new Error(`HTTP ${response.status}: ${text}`)
  }
  return response.json()
}

async function handleTextResponse(response: Response): Promise<string> {
  if (!response.ok) {
    const text = await response.text().catch(() => response.statusText)
    throw new Error(`HTTP ${response.status}: ${text}`)
  }
  return response.text()
}

export async function getFileInfo(fileId: number): Promise<FileInfo> {
  return handleResponse(await authenticatedFetch(`/api/files/${fileId}/info`))
}

export async function getFileInfoByPath(repoName: string, filePath: string, branch?: string): Promise<FileInfo> {
  const params = new URLSearchParams({ repo: repoName, path: filePath })
  if (branch) params.set('branch', branch)
  return handleResponse(await authenticatedFetch(`/api/files/info-by-path?${params}`))
}

export async function getFileContent(fileId: number): Promise<string> {
  return handleTextResponse(await authenticatedFetch(`/api/files/${fileId}/content`))
}

export async function getTokenStream(fileId: number): Promise<Token[]> {
  return handleResponse(await authenticatedFetch(`/api/files/${fileId}/token-stream`))
}

export async function getSymbol(symbolId: number): Promise<SymbolInfo> {
  return handleResponse(await authenticatedFetch(`/api/symbols/${symbolId}`))
}

export async function getSymbolReferences(symbolId: number): Promise<SymbolReference[]> {
  return handleResponse(await authenticatedFetch(`/api/symbols/${symbolId}/references`))
}
