import type { SearchResult } from '../types/search'
import { authenticatedFetch } from './http'

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text().catch(() => response.statusText)
    throw new Error(`HTTP ${response.status}: ${text}`)
  }
  return response.json()
}

export async function search(query: string, maxResults = 50, offset = 0): Promise<SearchResult[]> {
  const params = new URLSearchParams({
    q: query,
    maxResults: String(maxResults),
    offset: String(offset),
  })
  return handleResponse(await authenticatedFetch(`/api/search?${params}`))
}
