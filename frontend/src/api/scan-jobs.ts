import type { ScanJob } from '../types/scan-job'
import { authenticatedFetch } from './http'

const BASE = '/api/admin/scan-jobs'

export async function listScanJobs(status?: string): Promise<ScanJob[]> {
  const url = status ? `${BASE}?status=${encodeURIComponent(status)}` : BASE
  const response = await authenticatedFetch(url)
  if (!response.ok) throw new Error(`Failed to list scan jobs: ${response.status}`)
  return response.json()
}

export async function deleteScanJob(id: number): Promise<void> {
  const response = await authenticatedFetch(`${BASE}/${id}`, { method: 'DELETE' })
  if (!response.ok) throw new Error(`Failed to delete scan job ${id}: ${response.status}`)
}

export async function deleteAllQueuedScanJobs(): Promise<void> {
  const response = await authenticatedFetch(BASE, { method: 'DELETE' })
  if (!response.ok) throw new Error(`Failed to delete queued scan jobs: ${response.status}`)
}

export async function deleteAllFinishedScanJobs(): Promise<void> {
  const response = await authenticatedFetch(`${BASE}/finished`, { method: 'DELETE' })
  if (!response.ok) throw new Error(`Failed to delete finished scan jobs: ${response.status}`)
}

