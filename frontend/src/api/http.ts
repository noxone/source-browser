import { useAuth } from '../auth/useAuth'

/**
 * Wraps {@link fetch} with an Authorization Bearer token from the current OIDC session.
 * By default, triggers a login redirect if no token is available or if the server returns 401.
 * Pass `{ noRedirect: true }` to throw instead of redirecting (use for inline AJAX calls
 * where a page navigation would be disruptive).
 */
export async function authenticatedFetch(
  input: RequestInfo | URL,
  init?: RequestInit,
  options?: { noRedirect?: boolean }
): Promise<Response> {
  const { getAccessToken, login } = useAuth()
  const token = getAccessToken()

  if (!token) {
    if (!options?.noRedirect) await login()
    throw new Error('Redirecting to login')
  }

  const headers = new Headers(init?.headers)
  headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(input, { ...init, headers })

  if (response.status === 401) {
    if (!options?.noRedirect) await login()
    throw new Error('Session expired — please refresh the page to re-authenticate')
  }

  return response
}
