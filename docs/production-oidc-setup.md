# Production OIDC Setup Guide

This document describes how to configure the Source Viewer's authentication for production deployments.
The application supports any OIDC-compliant identity provider (Keycloak, Microsoft Entra ID, Okta, etc.)
via environment variables — no code changes are required.

---

## Architecture Overview

```
Browser
  │  ① Redirect to IdP login page
  ▼
Identity Provider (Keycloak / Entra ID / …)
  │  ② Authorization code + redirect back to app
  ▼
Vue SPA (oidc-client-ts)
  │  ③ Exchange code for JWT (PKCE, runs in browser)
  ▼
Quarkus Backend (/api/*)
     Validates Bearer JWT via OIDC discovery endpoint
```

The backend **never** redirects to the IdP itself.
It only validates the JWT presented by the frontend on every API request.

---

## Environment Variables

### Backend (Quarkus)

| Variable | Default (dev) | Description |
|---|---|---|
| `OIDC_AUTH_SERVER_URL` | `http://localhost:9090/realms/sourceviewer` | OIDC issuer / realm base URL |
| `OIDC_CLIENT_ID` | `sourceviewer-ui` | OIDC client identifier |

Quarkus auto-discovers the JWKS endpoint from
`{OIDC_AUTH_SERVER_URL}/.well-known/openid-configuration`.
No JWKS URL needs to be configured manually.

### Frontend (Vue / Vite)

Baked into the bundle at **build time**. Override before building the production image:

| Variable | Default (dev) | Description |
|---|---|---|
| `VITE_OIDC_AUTHORITY` | `http://localhost:9090/realms/sourceviewer` | Same as `OIDC_AUTH_SERVER_URL` |
| `VITE_OIDC_CLIENT_ID` | `sourceviewer-ui` | Same as `OIDC_CLIENT_ID` |

**Alternative: runtime config endpoint** (`GET /api/config`) — the frontend fetches this on startup
and uses the returned values instead of the baked-in ones. This allows swapping the IdP without
rebuilding the frontend bundle; configure `frontend.oidc.authority` and `frontend.oidc.client-id`
in Quarkus properties / environment instead.

---

## Provider-Specific Setup

### Keycloak (Production Realm)

1. Log in to the Keycloak admin console.
2. Create a realm (e.g. `sourceviewer-prod`).
3. Create a client:
   - **Client ID**: `sourceviewer-ui`
   - **Client authentication**: OFF (public client)
   - **Standard flow enabled**: ON
   - **Direct access grants**: OFF
   - **Valid redirect URIs**: `https://your-app.company.com/*`
   - **Web origins**: `https://your-app.company.com`
   - Under **Advanced → Proof Key for Code Exchange**: `S256`
4. Set environment variables:
   ```
   OIDC_AUTH_SERVER_URL=https://keycloak.company.com/realms/sourceviewer-prod
   OIDC_CLIENT_ID=sourceviewer-ui
   VITE_OIDC_AUTHORITY=https://keycloak.company.com/realms/sourceviewer-prod
   VITE_OIDC_CLIENT_ID=sourceviewer-ui
   ```

### Microsoft Entra ID (Azure AD)

1. In the Azure portal, go to **Azure Active Directory → App registrations → New registration**.
2. Name: `Source Viewer`
3. Supported account types: choose based on your tenant policy.
4. Redirect URI: `Single-page application (SPA)` → `https://your-app.company.com/`
5. After creation, go to **Authentication** and add `http://localhost:5173/` and `http://localhost:8080/`
   as additional redirect URIs for local development.
6. Under **Authentication**, ensure **"Allow public client flows"** is OFF for a SPA.
7. Note the **Application (client) ID** and **Directory (tenant) ID**.
8. Set environment variables:
   ```
   OIDC_AUTH_SERVER_URL=https://login.microsoftonline.com/{tenant-id}/v2.0
   OIDC_CLIENT_ID={application-client-id}
   VITE_OIDC_AUTHORITY=https://login.microsoftonline.com/{tenant-id}/v2.0
   VITE_OIDC_CLIENT_ID={application-client-id}
   ```
9. **Audience validation**: Entra ID tokens use the client ID as the audience claim.
   Add to `application.properties`:
   ```properties
   quarkus.oidc.token.audience={application-client-id}
   ```

---

## Personal Access Tokens (PATs)

PATs allow headless / CI access to the API without an interactive browser login.
They are created via the authenticated `POST /api/tokens` endpoint.

### Token Lifecycle

```
POST /api/tokens
  { "name": "CI pipeline", "expiresAt": "2027-01-01T00:00:00Z" }
→ 201 Created
  { "token": { "id": 1, "name": "CI pipeline", ... }, "rawToken": "svt_..." }
```

The `rawToken` is shown **exactly once**. Store it securely (e.g. in a CI secret).

### Using a PAT

```
Authorization: Bearer svt_<raw-token-value>
```

The backend recognises the `svt_` prefix, hashes the token, and looks it up in the database.
No OIDC round-trip is needed.

### Revoking a PAT

```
DELETE /api/tokens/{id}
```

### Token Rotation Guidance

- Set an expiry date on all tokens: `"expiresAt": "2026-01-01T00:00:00Z"`.
- Rotate tokens before expiry by creating a new token, updating the secret in CI,
  then revoking the old token.
- Never store raw token values in version control.

---

## Docker Compose (Dev Environment)

Start the full dev stack including Keycloak:

```bash
docker compose up -d
```

Services:
| Service | URL | Credentials |
|---|---|---|
| Keycloak admin | http://localhost:9090 | admin / admin |
| pgAdmin | http://localhost:9080 | test@hlag.com / admin |
| PostgreSQL | localhost:5432 | sourceviewer / sourceviewer |

Test users (realm `sourceviewer`):
| Username | Password |
|---|---|
| alice | alice |
| bob | bob |

Run the backend:

```bash
mvn quarkus:dev
```

Run the frontend (dev server with hot reload):

```bash
cd frontend && npm install && npm run dev
```

Open http://localhost:5173 — you will be redirected to Keycloak and prompted to log in.

---

## Troubleshooting

### `OIDC server is not available` at Quarkus startup

The backend tries to reach Keycloak at startup to fetch the OIDC discovery document.
Ensure Keycloak is running and `OIDC_AUTH_SERVER_URL` is reachable from the Quarkus process.

In tests, OIDC is disabled (`%test.quarkus.oidc.enabled=false`) so this never occurs.

### `401 Unauthorized` on API calls

- Check that the `Authorization: Bearer <token>` header is present in the request.
- Verify the token has not expired (check the `exp` claim with a JWT debugger).
- Ensure `OIDC_AUTH_SERVER_URL` on the backend matches `VITE_OIDC_AUTHORITY` on the frontend
  (the `iss` claim in the JWT must match the backend's configured issuer).

### Redirect loop after login

- Verify the redirect URI registered in the IdP exactly matches `window.location.origin + '/'`
  (e.g. `http://localhost:5173/` — note the trailing slash).
- In Keycloak, the redirect URI `http://localhost:5173/*` with a wildcard covers all paths.

### Entra ID: `AADSTS700054` invalid redirect URI

The redirect URI in the Entra app registration must be of type **Single-page application (SPA)**,
not "Web". SPA registrations enable PKCE and do not require a client secret.
