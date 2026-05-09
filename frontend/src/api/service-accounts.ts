import type {
  ServiceAccount,
  CreateServiceAccountRequest,
  UpdateServiceAccountRequest,
  ServiceAccountToken,
  ServiceAccountTokenCreated,
  CreateServiceAccountTokenRequest
} from '../types/service-account'
import { authenticatedFetch } from './http'

const BASE = '/api/admin/service-accounts'

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

export async function listServiceAccounts(): Promise<ServiceAccount[]> {
  return handleResponse(await authenticatedFetch(BASE))
}

export async function createServiceAccount(
  request: CreateServiceAccountRequest
): Promise<ServiceAccount> {
  return handleResponse(
    await authenticatedFetch(BASE, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    })
  )
}

export async function updateServiceAccount(
  id: number,
  request: UpdateServiceAccountRequest
): Promise<ServiceAccount> {
  return handleResponse(
    await authenticatedFetch(`${BASE}/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    })
  )
}

export async function deleteServiceAccount(id: number): Promise<void> {
  return handleResponse(await authenticatedFetch(`${BASE}/${id}`, { method: 'DELETE' }))
}

export async function listServiceAccountTokens(id: number): Promise<ServiceAccountToken[]> {
  return handleResponse(await authenticatedFetch(`${BASE}/${id}/tokens`))
}

export async function createServiceAccountToken(
  id: number,
  request: CreateServiceAccountTokenRequest
): Promise<ServiceAccountTokenCreated> {
  return handleResponse(
    await authenticatedFetch(`${BASE}/${id}/tokens`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    })
  )
}

export async function revokeServiceAccountToken(
  serviceAccountId: number,
  tokenId: number
): Promise<void> {
  return handleResponse(
    await authenticatedFetch(`${BASE}/${serviceAccountId}/tokens/${tokenId}`, { method: 'DELETE' })
  )
}
