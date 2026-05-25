# TreeSitter + LSP Setup

Source Viewer uses [TreeSitter](https://tree-sitter.github.io/) for syntax tokenisation
and [LSP](https://microsoft.github.io/language-server-protocol/) for semantic analysis
of Java and TypeScript files.

## Architecture Overview

```
Scan phase  ┌──────────────────────────────────────────────────────────────┐
            │  source file ──► TreeSitter ──► lexical tokens               │
            │                                                              │
            │  source file ──► LSP (documentSymbol) ──► symbol list       │
            │                                                              │
            │  per IDENTIFIER token ──► LSP hover + definition            │
            │                       ──► token_hover table (DB)            │
            │                                                              │
            │  LSP server shut down when scan completes                   │
            └──────────────────────────────────────────────────────────────┘

Click phase ┌──────────────────────────────────────────────────────────────┐
            │  token click ──► GET /api/lsp/hover?fileId=&line=&column=    │
            │               ──► DB lookup (token_hover) ──► right panel   │
            └──────────────────────────────────────────────────────────────┘
```

Hover and definition data are pre-computed at scan time and stored in the database.
**No LSP server is needed at runtime** — the application only starts LSP processes
during active scans.

---

## TreeSitter Grammars

TreeSitter grammars for Java and TypeScript are **bundled directly in the
`java-tree-sitter` Maven dependency** (`ch.usi.si.seart:java-tree-sitter`).
No external grammar files need to be compiled or installed.

The bundled native library (`libjava-tree-sitter.so`) is unpacked automatically
from the JAR on first use.

---

## Running in Docker (production)

Docker images only need the LSP servers installed. See the Dockerfile section below.

---

## Local Development Setup

### 1. Install the Java Language Server (JDT LS)

Download the latest release from:
<https://download.eclipse.org/jdtls/snapshots/>

```bash
mkdir -p /opt/jdtls
wget -q "https://download.eclipse.org/jdtls/snapshots/jdt-language-server-latest.tar.gz" \
     -O /tmp/jdtls.tar.gz
tar -xzf /tmp/jdtls.tar.gz -C /opt/jdtls
rm /tmp/jdtls.tar.gz
```

Verify: `ls /opt/jdtls/plugins/org.eclipse.equinox.launcher_*.jar` must return one JAR.

---

### 2. Install the TypeScript Language Server

Requires Node.js ≥ 18 and npm.

```bash
npm install -g typescript typescript-language-server
```

Verify:

```bash
typescript-language-server --version
```

---

### 3. Configure `application.properties`

The default values match the paths used by the Docker image.
Override with environment variables or in `application.properties`:

| Property | Environment variable | Default |
|---|---|---|
| `sourceviewer.lsp.jdtls.launcher-jar` | `JDTLS_LAUNCHER_JAR` | `/opt/jdtls/plugins/org.eclipse.equinox.launcher.jar` |
| `sourceviewer.lsp.jdtls.config-dir` | `JDTLS_CONFIG_DIR` | `/opt/jdtls/config_linux` |
| `sourceviewer.lsp.jdtls.data-dir` | `JDTLS_DATA_DIR` | `/tmp/sourceviewer-jdtls-workspace` |
| `sourceviewer.lsp.typescript.command` | `TYPESCRIPT_LSP_COMMAND` | `typescript-language-server` |
| `sourceviewer.lsp.inactive-timeout-minutes` | — | `60` (safety fallback) |

On macOS use `config_mac` instead of `config_linux` for `jdtls.config-dir`.

---

## Dockerfile Snippet

TreeSitter grammars are bundled in the Maven dependency, so the Dockerfile only
needs the LSP servers:

```dockerfile
FROM eclipse-temurin:21-jdk

# ── Eclipse JDT Language Server ─────────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends wget \
 && mkdir -p /opt/jdtls \
 && wget -q "https://download.eclipse.org/jdtls/snapshots/jdt-language-server-latest.tar.gz" \
         -O /tmp/jdtls.tar.gz \
 && tar -xzf /tmp/jdtls.tar.gz -C /opt/jdtls \
 && rm /tmp/jdtls.tar.gz

# ── TypeScript Language Server ───────────────────────────────────────────────
RUN apt-get install -y --no-install-recommends nodejs npm \
 && npm install -g typescript typescript-language-server

# ── Application ──────────────────────────────────────────────────────────────
COPY target/quarkus-app /app
EXPOSE 8080
CMD ["java", "-jar", "/app/quarkus-run.jar"]
```

---

## LSP Server Lifecycle

- LSP servers are started **lazily** when a scan begins (one server per language per repository).
- Hover and definition data for all IDENTIFIER tokens are collected during the scan and stored
  in the `token_hover` database table.
- The LSP server is **shut down immediately after each scan completes** — no persistent
  processes between scans.
- `sourceviewer.lsp.inactive-timeout-minutes` (default 60) acts as a safety fallback in case
  a scan-end shutdown is missed.
- On application shutdown all remaining servers are stopped gracefully.

---

## Adding Support for Additional Languages

See [`adding-a-language.md`](adding-a-language.md) — the TreeSitter + LSP section.
