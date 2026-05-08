import type { Repository, CreateRepositoryRequest, UpdateRepositoryRequest } from '../types/repository'
import { authenticatedFetch } from './http'

const BASE = '/api/repositories'

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

export async function listRepositories(): Promise<Repository[]> {
  return handleResponse(await authenticatedFetch(BASE))
}

export async function getRepository(id: number): Promise<Repository> {
  return handleResponse(await authenticatedFetch(`${BASE}/${id}`))
}

export async function createRepository(request: CreateRepositoryRequest): Promise<Repository> {
  return handleResponse(
    await authenticatedFetch(BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    })
  )
}

export async function updateRepository(id: number, request: UpdateRepositoryRequest): Promise<Repository> {
  return handleResponse(
    await authenticatedFetch(`${BASE}/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    })
  )
}

export async function deleteRepository(id: number): Promise<void> {
  return handleResponse(
    await authenticatedFetch(`${BASE}/${id}`, { method: 'DELETE' })
  )
}
