import type { GitProviderGroup, CreateGitProviderGroupRequest, UpdateGitProviderGroupRequest } from '../types/git-provider-group'
import { authenticatedFetch } from './http'

const BASE = '/api/git-provider-groups'

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text().catch(() => response.statusText)
    throw new Error(`HTTP ${response.status}: ${text}`)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return response.json()
}

export async function listGitProviderGroups(): Promise<GitProviderGroup[]> {
  return handleResponse(await authenticatedFetch(BASE))
}

export async function getGitProviderGroup(id: number): Promise<GitProviderGroup> {
  return handleResponse(await authenticatedFetch(`${BASE}/${id}`))
}

export async function createGitProviderGroup(request: CreateGitProviderGroupRequest): Promise<GitProviderGroup> {
  return handleResponse(
    await authenticatedFetch(BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    })
  )
}

export async function updateGitProviderGroup(id: number, request: UpdateGitProviderGroupRequest): Promise<GitProviderGroup> {
  return handleResponse(
    await authenticatedFetch(`${BASE}/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    })
  )
}

export async function deleteGitProviderGroup(id: number): Promise<void> {
  return handleResponse(
    await authenticatedFetch(`${BASE}/${id}`, { method: 'DELETE' })
  )
}
