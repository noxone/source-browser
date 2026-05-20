import type { IndexStats } from '../types/stats'

export async function getIndexStats(): Promise<IndexStats> {
  const response = await fetch('/api/stats')
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.json()
}
