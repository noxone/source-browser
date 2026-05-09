import type { AppSetting } from '../types/app-setting'
import { authenticatedFetch } from './http'

const BASE = '/api/admin/settings'

export async function listSettings(): Promise<AppSetting[]> {
  const response = await authenticatedFetch(BASE)
  if (!response.ok) throw new Error(`Failed to list settings: ${response.status}`)
  return response.json()
}

export async function updateSetting(key: string, value: string): Promise<void> {
  const response = await authenticatedFetch(`${BASE}/${encodeURIComponent(key)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ key, value, description: '' })
  })
  if (!response.ok) throw new Error(`Failed to update setting "${key}": ${response.status}`)
}
