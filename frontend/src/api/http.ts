import { useAuth } from '../auth/useAuth'

/**
 * Wraps {@link fetch} with an Authorization Bearer token from the current OIDC session.
 * Triggers a login redirect if no token is available or if the server returns 401.
 */
export async function authenticatedFetch(
  input: RequestInfo | URL,
  init?: RequestInit
): Promise<Response> {
  const { getAccessToken, login } = useAuth()
  const token = getAccessToken()

  if (!token) {
    await login()
    throw new Error('Redirecting to login')
  }

  const headers = new Headers(init?.headers)
  headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(input, { ...init, headers })

  if (response.status === 401) {
    await login()
    throw new Error('Session expired, redirecting to login')
  }

  return response
}
