import type { IndexStats } from '../types/stats'
import { authenticatedFetch } from './http'

export async function getIndexStats(): Promise<IndexStats> {
  const response = await authenticatedFetch('/api/stats')
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.json()
}
