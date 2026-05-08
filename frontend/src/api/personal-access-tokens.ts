import type { PersonalAccessToken, PersonalAccessTokenCreated, CreatePersonalAccessTokenRequest } from '../types/personal-access-token'
import { authenticatedFetch } from './http'

const BASE = '/api/tokens'

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

export async function listPersonalAccessTokens(): Promise<PersonalAccessToken[]> {
  return handleResponse(await authenticatedFetch(BASE))
}

export async function createPersonalAccessToken(
  request: CreatePersonalAccessTokenRequest
): Promise<PersonalAccessTokenCreated> {
  return handleResponse(
    await authenticatedFetch(BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    })
  )
}

export async function revokePersonalAccessToken(id: number): Promise<void> {
  return handleResponse(await authenticatedFetch(`${BASE}/${id}`, { method: 'DELETE' }))
}
