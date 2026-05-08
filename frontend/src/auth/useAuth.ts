import { ref, shallowRef, readonly } from 'vue'
import type { UserManager, User } from 'oidc-client-ts'
import { createUserManager } from './oidcConfig'

const currentUser = shallowRef<User | null>(null)
const isLoading = ref(true)
let userManager: UserManager | null = null

function getManager(): UserManager {
  if (!userManager) throw new Error('Auth not initialised — call initAuth() first')
  return userManager
}

/**
 * Fetches OIDC configuration from the backend's /api/config endpoint at runtime,
 * then initialises the UserManager. Must be called once before mounting the Vue app.
 * Using the runtime endpoint allows the identity provider to be changed via environment
 * variables on the server without rebuilding the frontend bundle.
 */
export async function initAuth(): Promise<void> {
  const config = await fetchOidcConfig()
  userManager = createUserManager(config.oidcAuthority, config.oidcClientId)

  userManager.events.addUserLoaded((user) => { currentUser.value = user })
  userManager.events.addUserUnloaded(() => { currentUser.value = null })
  userManager.events.addSilentRenewError(() => { currentUser.value = null })

  try {
    // Detect OIDC callback: the authorization server adds ?code=...&state=... before the hash
    if (window.location.search.includes('code=') && window.location.search.includes('state=')) {
      const user = await userManager.signinRedirectCallback()
      currentUser.value = user
      // Remove ?code=&state= from the browser URL without triggering a navigation
      window.history.replaceState({}, document.title, window.location.pathname + window.location.hash)
    } else {
      currentUser.value = await userManager.getUser()
    }
  } finally {
    isLoading.value = false
  }
}

/**
 * Returns reactive authentication state and auth actions for use in Vue components.
 */
export function useAuth() {
  return {
    user: readonly(currentUser),
    isLoading: readonly(isLoading),
    isAuthenticated: () => currentUser.value !== null && !currentUser.value.expired,
    getAccessToken: () => currentUser.value?.access_token ?? null,
    login: () => getManager().signinRedirect(),
    logout: () => getManager().signoutRedirect(),
  }
}

interface OidcConfig {
  oidcAuthority: string
  oidcClientId: string
}

async function fetchOidcConfig(): Promise<OidcConfig> {
  try {
    const response = await fetch('/api/config')
    if (response.ok) return response.json()
  } catch {
    // fall through to env var fallback below
  }
  // Fallback: use build-time env vars (useful in unit tests / standalone Vite dev server)
  return {
    oidcAuthority: import.meta.env.VITE_OIDC_AUTHORITY as string,
    oidcClientId: import.meta.env.VITE_OIDC_CLIENT_ID as string,
  }
}
