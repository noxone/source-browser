# Secret Encryption Key Setup

This document describes how to configure the key used to encrypt Git credentials
(repository tokens, provider-group API tokens) stored in the database.

Secrets are encrypted with **AES-256-GCM** before being written to the `git_credential`
table, and decrypted on demand by the application.  
The encryption key itself is **never** stored in the database.

---

## How It Works

```
UI / API                Application                   Database
  │                        │                              │
  │  PUT /…/credential      │                              │
  │  { secret: "tok_…" }   │                              │
  │ ──────────────────────► │  AES-256-GCM encrypt         │
  │                        │ ────────────────────────────► │
  │                        │                    { iv:ciphertext }
  │                        │                              │
  │  GET /…/credential     │                              │
  │ ──────────────────────► │  returns metadata only       │
  │  ◄─────────────────── { id, description, updatedAt }  │
  │                  (secret is NEVER returned)            │
```

Each secret is encrypted with a fresh random 12-byte IV, so the same plaintext
always produces different ciphertext. Stored format (single TEXT column):

```
base64(iv):base64(ciphertext+authTag)
```

---

## Configuration

### Environment Variable

| Variable | Required in production | Description |
|---|---|---|
| `SECRET_ENCRYPTION_KEY` | **Yes** | Base64-encoded 32-byte (256-bit) AES key |

The Quarkus property that reads this variable is:

```properties
sourceviewer.secret-encryption-key=${SECRET_ENCRYPTION_KEY:<dev-default>}
```

In production, set `SECRET_ENCRYPTION_KEY` in the container environment (or equivalent
secret store). The application will fail to start if the decoded value is not exactly 32 bytes.

### Dev Default (NOT for Production)

The development default is 32 zero bytes encoded as Base64:

```
AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
```

This key is hard-coded as the fallback in `application.properties` so the application
starts without configuration for local development. **Do not use it in any environment
where real secrets are stored.**

---

## Generating a Production Key

Use any tool that can produce 32 cryptographically-random bytes and encode them as Base64.

**OpenSSL (Linux / macOS / Git Bash / WSL):**
```bash
openssl rand -base64 32
```

**PowerShell (Windows):**
```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])
```

**Python:**
```python
import secrets, base64
print(base64.b64encode(secrets.token_bytes(32)).decode())
```

The output (e.g. `K7rQ2Lv…==`) is your `SECRET_ENCRYPTION_KEY` value.

---

## Deployment

### Docker Compose

Pass the key as an environment variable in `docker-compose.yml` or a `.env` file:

```yaml
# docker-compose.yml
services:
  sourceviewer:
    environment:
      SECRET_ENCRYPTION_KEY: ${SECRET_ENCRYPTION_KEY}
```

```dotenv
# .env  (not committed to git)
SECRET_ENCRYPTION_KEY=K7rQ2Lv…==
```

### Kubernetes / Helm

Store the key in a Kubernetes Secret and project it into the pod as an environment variable:

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: sourceviewer-secrets
stringData:
  secret-encryption-key: "K7rQ2Lv…=="
---
# Deployment env section
env:
  - name: SECRET_ENCRYPTION_KEY
    valueFrom:
      secretKeyRef:
        name: sourceviewer-secrets
        key: secret-encryption-key
```

### Other Secret Stores

Any mechanism that injects `SECRET_ENCRYPTION_KEY` into the process environment works
(HashiCorp Vault Agent, AWS Secrets Manager sidecar, Azure Key Vault CSI driver, etc.).

---

## Key Rotation

> **Important:** rotating the key requires re-encrypting all existing credentials.
> There is no automatic rotation — follow the steps below.

1. Generate a new 32-byte key (see [Generating a Production Key](#generating-a-production-key)).
2. Re-encrypt every row in `git_credential`:
   - Decrypt each `encrypted_secret` with the **old** key.
   - Re-encrypt the plaintext with the **new** key.
   - Update the row.
3. Update `SECRET_ENCRYPTION_KEY` in your secret store.
4. Restart the application.

Until step 3 takes effect, the application must still be able to read credentials
using the old key, so keep the old key available until the re-encryption step is complete.

> **Tip:** if you have not yet stored any credentials in the database, you can simply
> replace the key and restart — there is nothing to re-encrypt.

---

## Troubleshooting

### Application fails to start: `Encryption key must be exactly 32 bytes`

The `SECRET_ENCRYPTION_KEY` value does not decode to 32 bytes.

- Check for accidental trailing whitespace or newlines when the value was stored.
- Verify: `echo -n "$SECRET_ENCRYPTION_KEY" | base64 -d | wc -c` should print `32`.

### `SecretEncryptionException` when reading a credential

The application could decrypt the IV/ciphertext structure but the GCM authentication
tag check failed. This means the ciphertext was either:

- encrypted with a **different key** than the one currently configured, or
- corrupted in the database.

If the key was recently rotated without re-encrypting the rows first, restore the old
key and follow the [Key Rotation](#key-rotation) procedure.

### Tests fail with encryption errors

The `%test` Quarkus profile pins the key to the dev default so tests are reproducible
and do not depend on an environment variable:

```properties
# application.properties
%test.sourceviewer.secret-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
```

Do not change this value — it is intentionally the zero-key.
