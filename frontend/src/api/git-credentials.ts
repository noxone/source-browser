import type { GitCredential, SetCredentialRequest } from '../types/git-credential'
import { authenticatedFetch } from './http'

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

/** Returns the credential metadata for the given repository, or null if none is configured. */
export async function getRepositoryCredential(repoId: number): Promise<GitCredential | null> {
  const response = await authenticatedFetch(`/api/repositories/${repoId}/credential`)
  if (response.status === 404) return null
  return handleResponse<GitCredential>(response)
}

/** Creates or replaces the credential for the given repository. */
export async function setRepositoryCredential(
  repoId: number,
  request: SetCredentialRequest
): Promise<GitCredential> {
  return handleResponse(
    await authenticatedFetch(`/api/repositories/${repoId}/credential`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    })
  )
}

/** Removes the credential for the given repository. */
export async function deleteRepositoryCredential(repoId: number): Promise<void> {
  return handleResponse(
    await authenticatedFetch(`/api/repositories/${repoId}/credential`, { method: 'DELETE' })
  )
}

/** Returns the credential metadata for the given Git provider group, or null if none is configured. */
export async function getGroupCredential(groupId: number): Promise<GitCredential | null> {
  const response = await authenticatedFetch(`/api/git-provider-groups/${groupId}/credential`)
  if (response.status === 404) return null
  return handleResponse<GitCredential>(response)
}

/** Creates or replaces the credential for the given Git provider group. */
export async function setGroupCredential(
  groupId: number,
  request: SetCredentialRequest
): Promise<GitCredential> {
  return handleResponse(
    await authenticatedFetch(`/api/git-provider-groups/${groupId}/credential`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    })
  )
}

/** Removes the API credential for the given Git provider group. */
export async function deleteGroupCredential(groupId: number): Promise<void> {
  return handleResponse(
    await authenticatedFetch(`/api/git-provider-groups/${groupId}/credential`, { method: 'DELETE' })
  )
}

/** Returns the clone credential metadata for the given Git provider group, or null if none is configured. */
export async function getGroupCloneCredential(groupId: number): Promise<GitCredential | null> {
  const response = await authenticatedFetch(`/api/git-provider-groups/${groupId}/clone-credential`)
  if (response.status === 404) return null
  return handleResponse<GitCredential>(response)
}

/** Creates or replaces the clone credential for the given Git provider group. */
export async function setGroupCloneCredential(
  groupId: number,
  request: SetCredentialRequest
): Promise<GitCredential> {
  return handleResponse(
    await authenticatedFetch(`/api/git-provider-groups/${groupId}/clone-credential`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    })
  )
}

/** Removes the clone credential for the given Git provider group. */
export async function deleteGroupCloneCredential(groupId: number): Promise<void> {
  return handleResponse(
    await authenticatedFetch(`/api/git-provider-groups/${groupId}/clone-credential`, { method: 'DELETE' })
  )
}
