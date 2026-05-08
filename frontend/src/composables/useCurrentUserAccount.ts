import { shallowRef, computed, readonly } from 'vue'
import type { UserAccount } from '../types/user-account'
import { getCurrentUserAccount } from '../api/user-accounts'

const currentUserAccount = shallowRef<UserAccount | null>(null)

/**
 * Provides reactive access to the currently authenticated user's account,
 * including their admin status. Call {@link fetchCurrentUserAccount} once on
 * app startup to populate the state.
 */
export function useCurrentUserAccount() {
  return {
    userAccount: readonly(currentUserAccount),
    isAdmin: computed(() => currentUserAccount.value?.admin ?? false),
  }
}

/**
 * Fetches the current user's account from the backend and stores it in
 * the module-level reactive state used by {@link useCurrentUserAccount}.
 * Should be called once after the app mounts.
 */
export async function fetchCurrentUserAccount(): Promise<void> {
  try {
    currentUserAccount.value = await getCurrentUserAccount()
  } catch {
    currentUserAccount.value = null
  }
}
