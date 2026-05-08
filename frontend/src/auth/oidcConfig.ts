import { UserManager, WebStorageStateStore, type UserManagerSettings } from 'oidc-client-ts'

/**
 * Creates a UserManager with the given OIDC authority and client ID.
 * Called lazily from initAuth() after fetching runtime config from /api/config.
 */
export function createUserManager(authority: string, clientId: string): UserManager {
  const settings: UserManagerSettings = {
    authority,
    client_id: clientId,
    redirect_uri: window.location.origin + '/',
    post_logout_redirect_uri: window.location.origin + '/',
    response_type: 'code',
    scope: 'openid profile email',
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
    automaticSilentRenew: true,
    silent_redirect_uri: window.location.origin + '/silent-renew.html',
  }
  return new UserManager(settings)
}
