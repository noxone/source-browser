# Adding a New Language Server (LSP)

This guide explains how to add a new language server integration to the indexing backend.

## Architecture

LSP classes are grouped in dedicated `lsp` sub-packages:

- API contracts: `com.hlag.sourceviewer.application.scan.lsp`
- Runtime orchestration: `com.hlag.sourceviewer.infrastructure.lsp`
- Language-specific providers: `com.hlag.sourceviewer.infrastructure.lsp.<language>`

The `LspManager` starts the provider, waits until the server is ready, and returns a
`LanguageServerSession` for LSP4J communication.

## Runtime model

- A language server process is started for one indexing run.
- The caller closes the returned session when indexing finishes.
- Workspace folders are persisted per repository to speed up future startups.
- Workspace base path is configurable through app settings (`lsp.workspace.base-path`).

## Step 1 - Implement a provider

Create a CDI bean implementing `LanguageServerProvider` in a language-specific package.

Required methods:

- `supportedLanguage()` returns the language token (for example `java`).
- `startSession(LspProjectContext)` starts the process and returns a `LanguageServerSession`.

Optional method:

- `readinessStrategy()` to override the default readiness strategy.

## Step 2 - Start the server with LSP4J

Use stdio transport for the process and LSP4J launcher:

1. Start process (`ProcessBuilder`) with the language-server command.
2. Create `Launcher<LanguageServer>` via `LSPLauncher.createClientLauncher(...)`.
3. Call `startListening()`.
4. Send `initialize` and then `initialized`.

## Step 3 - Configure settings

Add any language-specific settings to `ManageAppSettingsUseCase` and expose them in
`AppSettingsResource` so they can be administered at runtime.

For JDTLS, platform paths are explicit admin settings by OS and architecture.

## Step 4 - Readiness

By default, `DefaultLspReadinessStrategy` polls a generic LSP request (`workspace/symbol`)
until it succeeds or the global timeout (`lsp.default-ready-timeout-ms`) is reached.

If the server sends an explicit readiness event, use an event-driven strategy instead:

1. Extend `LanguageClient` with a `@JsonNotification`-annotated method for the custom
   notification on a sub-interface (e.g. `JdtlsLanguageClient`).
2. Implement the client; drive a `CompletableFuture<Void>` on the readiness event.
3. Pass the future to the session via `ProcessBackedLanguageServerSession`'s
   `readySignal` constructor argument.
4. Implement `LspReadinessStrategy` to call `session.readySignal().get(timeout)`.
   No polling — the first server event unblocks the caller.
5. Return your strategy from `LanguageServerProvider#readinessStrategy()`.

### JDTLS readiness — event-driven via `language/status`

JDTLS sends `language/status` custom notifications throughout its lifecycle:

| `type`      | `message`      | Meaning                        |
|-------------|----------------|--------------------------------|
| `Starting`  | free text      | Still initialising             |
| `Started`   | `ServiceReady` | Fully indexed, ready to use    |
| `Error`     | free text      | Startup failed                 |

`JdtlsNotifyingLanguageClient` (implements `JdtlsLanguageClient`) completes
`serviceReadyFuture` when `message = "ServiceReady"` arrives, or exceptionally on `Error`.
`JdtlsReadinessStrategy` awaits that future with the global hard-timeout as upper bound.
There is no polling fallback; the block releases the moment the server event arrives.

## Step 5 - Workspace cleanup

Repository deletion should also delete the matching language-server workspace directory.
This is handled through the `LanguageServerWorkspaceStore` port.

