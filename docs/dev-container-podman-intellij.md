# Dev Container with Podman and IntelliJ IDEA

This setup allows developing the Quarkus application entirely inside a Linux
container while using Windows as the host operating system.  IntelliJ IDEA
runs its backend inside the container, so all actions (build, run, debug,
tests) execute within the Unix environment.

---

## Architecture

```
Windows (Host)
│
├── IntelliJ IDEA (UI runs on Windows)
│       │
│       │  SSH / Dev Container Protocol
│       ▼
├── Podman VM (Linux, via WSL2 or podman machine)
│   │
│   ├── devcontainer   ← IntelliJ backend + Quarkus process
│   │     JDK 25, Maven, Node.js, JDTLS
│   │     /workspace → bind-mount → C:\hlag\Data\git\sourceviewer
│   │
│   ├── postgres       ← PostgreSQL 16 (reachable as "postgres:5432")
│   └── keycloak       ← Keycloak 26.2  (reachable as "keycloak:8080")
│
└── Browser            ← localhost:8080 (Quarkus), localhost:9090 (Keycloak)
```

---

## Prerequisites

| Software | Minimum Version | Download |
|---|---|---|
| **Podman Desktop** | 1.9+ | https://podman-desktop.io |
| **IntelliJ IDEA Ultimate** | 2023.3+ | https://www.jetbrains.com/idea/ |
| Dev Containers plugin | — | already bundled with IDEA |

> **Note:** The Dev Container feature requires IntelliJ IDEA **Ultimate**.
> With the Community Edition, use JetBrains Gateway (free) instead —
> see [Alternative: Gateway](#alternative-jetbrains-gateway) below.

---

## Step-by-Step Setup

### 1  Install and start Podman Desktop

1. Download and install Podman Desktop from https://podman-desktop.io.
2. On first launch, the setup wizard runs automatically:
   - It creates a **Podman Machine** (Linux VM via WSL2).
   - It enables the **Docker-compatible socket** — IntelliJ communicates with
     Podman through this socket just as it would with Docker.
3. Confirm the Podman Machine is running (green status in the dashboard).

Quick test in a PowerShell terminal:

```powershell
podman info          # shows the Podman version and running machine
podman ps            # lists running containers
```

### 2  Register the Docker socket in IntelliJ

IntelliJ needs to know it should use Podman instead of Docker:

1. **File → Settings → Build, Execution, Deployment → Docker**
2. Click **+** to add a new entry.
3. Select **"Unix socket"** or **"TCP socket"** as the connection type:
   - Podman Desktop exposes the socket by default at
     `\\.\pipe\docker_engine` (Windows Named Pipe) or
     `npipe:////./pipe/docker_engine`.
   - The exact path is shown in Podman Desktop under
     *Settings → Resources → Podman Machine → Docker compat. socket*.
4. Click **Test Connection** — a green checkmark confirms the connection.

### 3  Open the project in a Dev Container

1. Open the project in IntelliJ (if not already open).
2. Go to **File → Remote Development → Dev Containers →
   Create Dev Container…**
   — or click the notification IntelliJ shows when it detects
   `.devcontainer/devcontainer.json`.
3. IntelliJ builds the image (first time only, takes ~5 minutes), starts all
   Compose services, and installs its backend inside the container.
4. A new IntelliJ window opens connected to the container.
   All subsequent actions (Maven, Run, Debug) now run **inside the container**.

> **Tip:** After the first build the image is cached locally.  Subsequent
> starts take only a few seconds.

### 4  Start Quarkus in dev mode

In the IntelliJ window connected to the container:

**Option A — Run Configuration (recommended)**

1. **Run → Edit Configurations… → + → Maven**
2. Settings:
   - **Working directory:** `/workspace`
   - **Command line:** `quarkus:dev`
3. Save and start the configuration (▶).

**Option B — Terminal inside the container**

In the embedded terminal of the container window:

```bash
cd /workspace
mvn quarkus:dev
```

In both cases Quarkus connects automatically to the PostgreSQL and Keycloak
containers — the environment variables are already set in the Compose file.

### 5  Remote debugging (optional)

Quarkus dev mode enables debug port 5005 by default.  If an explicit port is
needed, start Quarkus with:

```bash
mvn quarkus:dev -Ddebug=5005
```

IntelliJ on Windows can attach via the forwarded port:

1. **Run → Edit Configurations… → + → Remote JVM Debug**
2. Host: `localhost`, Port: `5005`
3. Start the debugger — breakpoints in source code work just like locally.

---

## Ports and URLs

| Service | URL from Windows | Reachable inside the container as |
|---|---|---|
| Quarkus HTTP | http://localhost:8080 | `http://localhost:8080` |
| Quarkus Debug | `localhost:5005` | — |
| PostgreSQL | `localhost:5432` | `postgres:5432` |
| Keycloak | http://localhost:9090 | `http://keycloak:8080` |

---

## Volumes and data persistence

| Volume | Contents | Lifetime |
|---|---|---|
| `maven-cache` | Maven local repository (`~/.m2`) | persists across container restarts |
| `jdtls-workspace` | JDTLS indexes and project data | persists |
| `postgres-data` | Database data | persists |

Source code lives as a **bind-mount** at `/workspace` inside the container —
changes made in IntelliJ or on the Windows filesystem are immediately visible
in the container and vice versa.

---

## Stopping and cleaning up

Stop containers (data is preserved):

```powershell
podman compose -f .devcontainer/docker-compose.devcontainer.yml stop
```

Remove containers and **all** associated volumes (full reset):

```powershell
podman compose -f .devcontainer/docker-compose.devcontainer.yml down -v
```

---

## Alternative: JetBrains Gateway

Users running IntelliJ IDEA Community Edition can use **JetBrains Gateway**
(free download, https://www.jetbrains.com/remote-development/gateway/):

1. Launch Gateway → select **Dev Containers**.
2. Set the repository path to `C:\hlag\Data\git\sourceviewer`.
3. Gateway detects `.devcontainer/devcontainer.json` automatically and starts
   the stack.

The result is functionally identical to the Ultimate approach.

---

## Troubleshooting

### "Cannot find image with id: \<hash\>"

This is the most common issue when IntelliJ first connects to Podman on Windows.
IntelliJ builds the image via the Docker-compat API, stores the returned image ID,
and then tries to look it up again through the same socket — but the Podman socket
context on Windows does not resolve short image IDs reliably.

**Fix 1 — Enable the Docker compatibility socket in Podman Desktop (recommended)**

1. Open **Podman Desktop → Settings → Resources**.
2. Find the **Podman Machine** entry and click the ⚙ icon.
3. Enable **"Docker Socket Compatibility"**.  
   This creates a named pipe at `\\.\pipe\docker_engine` that behaves exactly
   like Docker Desktop's socket.
4. In IntelliJ: **File → Settings → Build, Execution, Deployment → Docker**  
   Set the connection to **"Docker for Windows"** or
   **"Named pipe: `\\.\pipe\docker_engine`"**.
5. Click **Test Connection** → green checkmark expected.
6. Remove the old Dev Container entry and create a new one.

**Fix 2 — Use the Podman machine socket directly**

If Fix 1 does not help (e.g. Docker Desktop is also installed and conflicts with
the pipe name):

1. In IntelliJ **Docker settings**, choose **"TCP socket"** and enter:
   ```
   npipe:////./pipe/podman-machine-default
   ```
2. Test and save.

**Fix 3 — Restart the Podman Machine**

Occasionally the Podman Machine gets into an inconsistent state after a Windows
sleep/hibernate cycle:

```powershell
podman machine stop
podman machine start
```

Then retry opening the Dev Container in IntelliJ.

**Fix 4 — Docker Desktop socket conflict**

If both Docker Desktop and Podman Desktop are installed, they compete for
`\\.\pipe\docker_engine`.  Make sure only **one** of the two exposes that pipe,
or point IntelliJ explicitly to the Podman-native pipe
(`\\.\pipe\podman-machine-default`).

---

### `DOCKER_HOST` environment variable (alternative to IntelliJ settings)

Instead of configuring the socket in the IntelliJ UI, you can set `DOCKER_HOST`
in your PowerShell profile — IntelliJ picks it up automatically on startup:

```powershell
# Add to $PROFILE (e.g. Documents\WindowsPowerShell\Microsoft.PowerShell_profile.ps1)
$env:DOCKER_HOST = "npipe:////./pipe/docker_engine"
```

Or for the Podman-native pipe (if Docker Desktop is also installed):

```powershell
$env:DOCKER_HOST = "npipe:////./pipe/podman-machine-default"
```

Restart IntelliJ after changing the profile.

---

### "Cannot connect to Docker daemon"

- Make sure Podman Desktop is running and the Podman Machine is started.
- In Podman Desktop under *Settings → Resources* verify that the
  **Docker compatibility socket** is enabled.

### Slow first start

- The Dev Container image downloads JDTLS (~150 MB) and Maven (~10 MB).
  This download only happens during the first `docker build`.
- Afterwards the image is cached locally.

### SELinux / Podman permission errors on the mounted volume

- The `:z` flag in the Compose file (`- ..:/workspace:z`) sets the SELinux
  label correctly.  If errors persist:
  ```bash
  podman unshare chown -R 0:0 /path/to/workspace
  ```

### `mvn quarkus:dev` cannot find Postgres

- Check that all Compose services are running:
  ```bash
  podman ps
  ```
- Make sure the `postgres` service has reached the `healthy` status.
