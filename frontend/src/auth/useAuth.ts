import { ref, readonly } from 'vue'
import type { User } from 'oidc-client-ts'
import { userManager } from './oidcConfig'

const currentUser = ref<User | null>(null)
const isLoading = ref(true)

/**
 * Initialises authentication state. Must be called once before mounting the Vue app.
 * Handles the OIDC authorization code callback when the page is loaded after a redirect.
 */
export async function initAuth(): Promise<void> {
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
    login: () => userManager.signinRedirect(),
    logout: () => userManager.signoutRedirect(),
  }
}
