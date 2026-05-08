import { UserManager, WebStorageStateStore, type UserManagerSettings } from 'oidc-client-ts'

const settings: UserManagerSettings = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY as string,
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID as string,
  redirect_uri: window.location.origin + '/',
  post_logout_redirect_uri: window.location.origin + '/',
  response_type: 'code',
  scope: 'openid profile email',
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  automaticSilentRenew: true,
  silent_redirect_uri: window.location.origin + '/silent-renew.html',
}

export const userManager = new UserManager(settings)
