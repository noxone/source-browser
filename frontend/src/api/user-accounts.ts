import type { UserAccount, UserAccountPage, UpdateUserAccountRequest } from '../types/user-account'
import { authenticatedFetch } from './http'

const BASE = '/api/users'

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text().catch(() => response.statusText)
    throw new Error(`HTTP ${response.status}: ${text}`)
  }
  return response.json()
}

export async function getCurrentUserAccount(): Promise<UserAccount> {
  return handleResponse(await authenticatedFetch(`${BASE}/me`))
}

export async function listUserAccounts(
  query: string = '',
  page: number = 1,
  pageSize: number = 25
): Promise<UserAccountPage> {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) })
  if (query.trim()) params.set('query', query.trim())
  return handleResponse(await authenticatedFetch(`${BASE}?${params}`))
}

export async function updateUserAccount(
  id: number,
  request: UpdateUserAccountRequest
): Promise<UserAccount> {
  return handleResponse(
    await authenticatedFetch(`${BASE}/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    })
  )
}
