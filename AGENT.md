# AGENT.md — Project Charter: Java Source Viewer

> This document is the authoritative reference for architecture, rules and
> conventions of the project. Every code change must comply with these rules.
> Violations are detected by ArchUnit tests during the build.

---

## 1. Purpose

**Java Source Viewer** is a server application that indexes Java source code from
multiple Git repositories and provides it via a web interface for interactive
exploration.

### Target Audience
Developers who need to navigate large, organically grown Java codebases
(on the order of several million lines), look up symbols, find references
and understand source code structures.

### Core Features
- **Source code display** with semantic syntax highlighting,
  line-accurate rendering of the original file
- **Hover tooltips** with detailed information about identifiers
- **Clickable symbols**: jump to definition, list of usages
- **Indexing of multiple Git repositories** with incremental scanning
  (only re-process changed files)
- **Webhook-triggered scans** (Git push) plus cron fallback
- **Full-text search** across source code, configuration files and documentation
- **Structural navigation**: types, methods, fields per file

### Non-Goals (explicitly excluded)
- Code editing or refactoring in the user interface
- Languages other than Java in the first release
- Build/CI integration

---

## 2. Technology Stack

| Component | Technology | Version |
|---|---|---|
| Language | Java | 21 (LTS) |
| Build | Maven | 3.9+ |
| Framework | Quarkus | 3.33+ (LTS) |
| REST | RESTEasy Reactive (`quarkus-rest`) | — |
| JSON | Jackson (`quarkus-rest-jackson`) | — |
| Persistence | Hibernate ORM + Panache (`quarkus-hibernate-orm-panache`) | — |
| Database driver | `quarkus-jdbc-postgresql` | — |
| Database | PostgreSQL | 16+ |
| Migrations | Flyway (`quarkus-flyway`) | — |
| Templates | Qute (`quarkus-qute`) | — |
| Scheduling | `quarkus-scheduler` | — |
| Health/Metrics | `quarkus-smallrye-health`, `quarkus-micrometer` | — |
| **Authentication** | **`quarkus-oidc` (bearer/service mode)** | **—** |
| **Identity provider (dev)** | **Keycloak 26.2** | **—** |
| Java parser | JavaParser (`javaparser-symbol-solver-core`) | 3.26+ |
| Git access | JGit (`org.eclipse.jgit`) | 6.10+ |
| Architecture tests | ArchUnit | 1.4+ |
| Test framework | JUnit 5, AssertJ, Testcontainers | — |
| Mapping | MapStruct (when needed) | 1.6+ |
| APT (code generation) | `ValueObjectConverterProcessor` (in-tree) | — |
| **Frontend OIDC client** | **`oidc-client-ts`** | **3.1+** |

### Rationale for Key Decisions
- **Quarkus instead of Spring Boot**: low memory footprint, fast startup time,
  native image option for container deployments.
- **Hibernate + Panache instead of jOOQ**: simplifies entity mapping with
  `@Entity` / `PanacheEntity`, reduces boilerplate for standard CRUD. JPA
  `@Converter(autoApply = true)` converters are generated automatically for all
  `ValueObject<T>` wrapper types by the in-tree APT processor.
- **PostgreSQL instead of SQLite**: concurrent read/write access
  (scanner + multiple developers), built-in full-text search
  (`tsvector`/`tsquery`), scalability across repositories.
- **`quarkus-oidc` in bearer/service mode**: the backend validates JWTs on every
  API request without driving the login flow itself. The frontend SPA (via
  `oidc-client-ts`) performs the PKCE authorization code flow. This separation
  allows any OIDC-compliant provider (Keycloak, Microsoft Entra ID, Okta, …) to
  be used with only environment variable changes.
- **`oidc-client-ts` instead of `keycloak-js`**: provider-neutral OIDC library
  that works identically with Keycloak, Entra ID, and any standards-compliant IdP.
  `keycloak-js` is Keycloak-specific and would require code changes when switching
  providers.
- **Personal Access Tokens with `svt_` prefix**: opaque PATs stored as SHA-256
  hashes in the database allow headless/CI access alongside interactive OIDC
  sessions. The `svt_` prefix lets the custom `PersonalAccessTokenAuthMechanism`
  distinguish PATs from JWTs in the `Authorization: Bearer` header without
  ambiguity, so both mechanisms coexist transparently.

---

## 3. Architecture

The project follows a **simplified Hexagonal Architecture**
(Ports & Adapters). There are three core layers — Domain, Application,
Adapter — plus an Infrastructure layer for bootstrapping and an isolated
layer for jOOQ-generated code.

### 3.1 Layers

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Infrastructure                             │
│      (Configuration, Scheduler, Bootstrapping — knows everything)   │
└──────────────────┬───────────────────────────────────┬──────────────┘
                   │                                   │
        ┌──────────▼─────────┐               ┌─────────▼──────────┐
        │   Adapter (in)     │               │  Adapter (out)     │
        │   REST, Webhooks,  │               │  jOOQ, JGit,       │
        │   View Resources   │               │  external systems  │
        └──────────┬─────────┘               └─────────┬──────────┘
                   │                                   │
                   └─────────────┬─────────────────────┘
                                 │
                       ┌─────────▼─────────┐
                       │    Application    │
                       │  Use-Case Impls   │
                       │   Orchestration   │
                       └─────────┬─────────┘
                                 │
                       ┌─────────▼─────────┐
                       │      Domain       │
                       │  Models, Logic    │
                       │  Ports (Iface)    │
                       └───────────────────┘
```

**Dependency direction:** Arrows always point inward. The Domain knows
nothing about the outer layers.

### 3.2 Security Architecture

```
Browser (oidc-client-ts)
  ① PKCE redirect → Identity Provider (Keycloak / Entra ID)
  ② Authorization code → token exchange (in browser)
  ③ Bearer JWT on every request → Quarkus (/api/*)
       quarkus-oidc validates JWT via JWKS from IdP

CI / headless tools
  ④ Bearer svt_<opaque-token> → Quarkus (/api/*)
       PersonalAccessTokenAuthMechanism hashes token,
       looks up SHA-256 digest in personal_access_token table
```

**Auth mechanisms (both active simultaneously):**

| Mechanism | Trigger | Lives in |
|---|---|---|
| `quarkus-oidc` BearerTokenAuthentication | Bearer token NOT starting with `svt_` | Quarkus OIDC extension |
| `PersonalAccessTokenAuthMechanism` | Bearer token starting with `svt_` | `infrastructure.security` |

The custom mechanism is registered as `@Alternative @Priority(1)` so it runs first.
If the token does not start with `svt_`, it returns an empty result and OIDC takes over.

**Security annotations used in incoming adapters:**
- `@Authenticated` (from `io.quarkus.security`) on resource classes — all endpoints in the
  class require a valid identity regardless of mechanism.
- `@Context SecurityIdentity` injection in method parameters — provides the current
  principal's username for owner-scoped operations (e.g. PAT management).

**ArchUnit rule:** `io.quarkus.security..` and `jakarta.annotation.security..` annotations are
permitted in `adapter.incoming..` (on resource classes) and `infrastructure.security..`
(auth mechanism and identity provider). They must not appear in `domain..` or `application..`.

### 3.3 Package Structure

The root package of the project is **`com.hlag.sourceviewer`**.

```
com.hlag.sourceviewer
│
├── domain                           ← Pure business logic, no frameworks
│   ├── model
│   │   ├── identifier               ← All wrapper types (ValueObject<T> records)
│   │   ├── converter                ← JPA AttributeConverters — AUTO-GENERATED
│   │   │                              by ValueObjectConverterProcessor at build
│   │   │                              time; no hand-written files here
│   │   ├── source                   ← ParsedFile, SourceToken, ElementInfo
│   │   ├── repository               ← Repository, BranchName, CommitSha
│   │   ├── token                    ← PersonalAccessToken entity
│   │   └── search                   ← SearchQuery, SearchResult
│   ├── service                      ← Domain services (pure logic)
│   └── port
│       ├── incoming                 ← Use-case interfaces
│       └── outgoing                 ← Repository/gateway interfaces
│
├── application                      ← Use-case implementations
│   ├── scan                         ← Scan workflow
│   ├── search                       ← Search
│   └── resolve                      ← Symbol resolution
│
├── adapter
│   ├── incoming                     ← REST, Webhooks, Views
│   │   ├── rest
│   │   │   └── dto                  ← API data transfer objects (records)
│   │   ├── view                     ← Qute view resources
│   │   └── webhook
│   └── outgoing                     ← Hibernate repositories, JGit adapters
│       ├── persistence
│       │   └── mapping              ← Record ↔ Domain mappers (MapStruct)
│       ├── git
│       └── jackson                  ← Jackson module for wrapper serialization
│
├── infrastructure                   ← Configuration, Scheduler, Health, Security
│   ├── configuration
│   ├── scheduler
│   ├── health
│   └── security                     ← Custom HTTP auth mechanisms and identity providers
│                                      (PersonalAccessTokenAuthMechanism,
│                                       PersonalAccessTokenIdentityProvider)
│
└── processor                        ← COMPILE-TIME ONLY — APT processor that
                                       generates the JPA converters above;
                                       classes in this package are compiled to
                                       target/processor-classes and are NOT
                                       included in the application JAR
```

> **Convention:** Abbreviations are avoided throughout the project. Packages,
> classes and methods use fully written-out names
> (`incoming` instead of `in`, `outgoing` instead of `out`,
> `configuration` instead of `config`, `identifier` instead of `ids`, etc.).
> Established acronyms from the Java ecosystem (`REST`, `JSON`, `SQL`,
> `HTTP`) are retained.

---

## 4. Architecture Rules (mandatory, enforced by ArchUnit)

### 4.1 Layer Dependencies

| Layer | May access |
|---|---|
| `domain` | nothing (except JDK + allowed exceptions, see 4.2) |
| `application` | `domain` |
| `adapter.incoming` | `application`, `domain` |
| `adapter.outgoing` | `application`, `domain`, `generated` |
| `infrastructure` | all layers (for wiring) |
| `generated` | readable only by `adapter.outgoing.persistence` |

### 4.2 Domain Is Framework-Free

Classes under `com.hlag.sourceviewer.domain..` must **not** depend on:
- `io.quarkus..`
- `jakarta.persistence..`
- `jakarta.ws.rs..`
- `jakarta.inject..`
- `jakarta.enterprise..`
- `org.jooq..`
- `org.eclipse.jgit..`
- `com.fasterxml.jackson..`
- `io.smallrye..`

**Allowed exceptions** (because they are part of the domain):
- `com.github.javaparser..` (parsing IS the domain)
- JDK classes

### 4.3 Adapters Must Not Know Each Other

- `adapter.incoming` must not access `adapter.outgoing`
- `adapter.outgoing` must not access `adapter.incoming`

Communication runs exclusively through the Application layer.

### 4.4 Framework Localization

Certain frameworks may only appear in designated packages:
- **Hibernate / JPA** (`jakarta.persistence..`, `org.hibernate..`):
  only in `adapter.outgoing.persistence..` and `domain.model.converter..`
  (converters need JPA annotations to be discovered by Hibernate alongside entities)
- **JGit** (`org.eclipse.jgit..`):
  only in `adapter.outgoing.git..`
- **JAX-RS** (`jakarta.ws.rs..`):
  only in `adapter.incoming..`
- **Qute** (`io.quarkus.qute..`):
  only in `adapter.incoming.view..`
- **Quarkus Security** (`io.quarkus.security..`) and **Jakarta Security**
  (`jakarta.annotation.security..`):
  annotations such as `@Authenticated` and `@RolesAllowed` may only appear in
  `adapter.incoming..` (on REST resource classes) and `infrastructure.security..`
  (custom `HttpAuthenticationMechanism` and `IdentityProvider` implementations).
  They must not appear in `domain..` or `application..`.
- **JavaParser** (`com.github.javaparser..`):
  only in `domain..` and `application..`
- **Jackson annotations / modules** (`com.fasterxml.jackson..`):
  only in `adapter.incoming..` and `adapter.outgoing.jackson..`

### 4.5 Naming and Structural Conventions

- Classes with the suffix `UseCase` must be interfaces and reside in
  `domain.port.incoming..`.
- Classes with the suffix `Repository` must be interfaces and reside in
  `domain.port.outgoing..`. (Implementations are named
  `JooqXxxRepository`, `InMemoryXxxRepository`, etc.)
- Classes with the suffix `Service` reside in `application..` or
  `domain.service..`, never in adapters.
- Classes with the suffix `Resource` reside in `adapter.incoming..`.
- Classes with the suffix `Dto` reside in `adapter.incoming.rest.dto..` or
  `adapter.incoming.view.dto..`.
- Classes with the suffix `Mapper` reside in
  `adapter.outgoing.persistence.mapping..`.
- Classes with the suffix `Converter` that implement `jakarta.persistence.AttributeConverter`
  (JPA attribute converters for domain wrapper types) reside in
  `domain.model.converter..`. They are **auto-generated** at build time by
  `ValueObjectConverterProcessor` (see Section 4.6); do not write these by hand.
  The generated files appear under `target/generated-sources/annotations/` and
  are never committed to source control.

### 4.6 Type Safety for Attributes (Wrapper Requirement)

**Ground rule:** *All* attributes in domain and application classes are
wrapped in typed wrapper types — without exception. There is no exception for
"simple" text fields. Even `description`, `displayName`, `errorMessage`,
`comment` and similar pure display texts get their own wrapper types
(`Description`, `DisplayName`, `ErrorMessage`, `Comment`).

The overhead of unwrapping the actual value via `.value()` on access is
accepted deliberately, because it guarantees:
- No value can accidentally be assigned to a semantically different field,
  even if both share the same base type.
- Method signatures are self-documenting.
- The compiler catches mix-ups at build time.

**Wrapper types include in particular:**

| Category | Examples |
|---|---|
| Identifiers | `SymbolIdentifier`, `FileIdentifier`, `RepositoryIdentifier`, `ReferenceIdentifier`, `ScanJobIdentifier` |
| Positions | `LineNumber`, `ColumnNumber` |
| Version/Git concepts | `CommitSha`, `BranchName`, `TagName` |
| Code concepts | `QualifiedName`, `SimpleName`, `PackageName`, `FilePath` |
| Display texts | `Description`, `DisplayName`, `ErrorMessage`, `Comment` |
| Time/quantity concepts | `TokenCount`, `LineCount` |
| Classifiers | `SymbolKind` (enum), `ReferenceKind` (enum) |

**Conventions for wrappers:**
- Wrappers reside in `domain.model.identifier..`.
- Implemented as `record` with a single field named `value`.
- Validation in the compact constructor (e.g. `value > 0` for `LineNumber`,
  `value != null && !value.isBlank()` for text wrappers).
- Wrappers are immutable.
- Fields in `domain..` classes whose name ends with `Identifier` must have
  a wrapper type (or `Optional<XxxIdentifier>`).
- A JPA `AttributeConverter` is **automatically generated** for every
  `ValueObject<T>` record by `ValueObjectConverterProcessor` (APT, runs during
  `mvn compile`). The converter uses `@Converter(autoApply = true)`, so no
  `@Convert` annotation is needed on entity fields — just declare the field
  with the wrapper type.
- A central Jackson module (see 4.7) ensures that wrappers appear in
  JSON as their inner value only — wrappers remain invisible to API consumers.

### 4.7 Jackson Module for Wrapper Serialization

To ensure that wrapper types appear in JSON output **not** as nested objects
but as their bare inner value, a central Jackson module is implemented:

- Location:
  `com.hlag.sourceviewer.adapter.outgoing.jackson.WrapperModule`.
- The module is registered globally via `@QuarkusObjectMapperCustomizer`.
- It recognizes wrapper types automatically via the marker interface
  `ValueObject<T>`, which all wrapper records implement.
- On serialization, only the inner value is written.
- On deserialization, the inner value is read and wrapped.

**Example:**
```java
// Domain record
record SymbolDto(SymbolIdentifier identifier,
                 SimpleName name,
                 LineNumber line,
                 Description description) {}

// Serialized as:
// {"identifier": 42, "name": "Foo", "line": 17,
//  "description": "Represents a data transfer object"}
//
// Not as:
// {"identifier": {"value": 42}, "name": {"value": "Foo"}, ...}
```

API consumers never see the wrappers — they see only the bare values.
Inside the Java codebase, full type safety is maintained.

### 4.8 API Data Transfer Objects Are Separate from the Domain Model

- REST resources must **not** use domain classes directly as request or
  response bodies. Instead, data transfer objects are defined in
  `adapter.incoming.rest.dto`.
- jOOQ-generated classes (`Record`, `Pojo`) must never appear as
  method signatures in REST resources.
- Mapping between domain and data transfer object happens in the resource
  or in a dedicated mapper.

### 4.9 No Cyclic Dependencies

Between the top-level packages (`domain`, `application`, `adapter.incoming`,
`adapter.outgoing`, `infrastructure`) and their sub-packages there must be no
cycles. ArchUnit checks this via `slices()`.

### 4.10 Logging

- Use of `System.out.println` and `System.err.println` is forbidden in
  production code. Use SLF4J (`org.slf4j.Logger`) instead.
- Loggers are declared as
  ```java
  private static final Logger logger = LoggerFactory.getLogger(MyClass.class);
  ```
  The name is always `logger` (lowercase, fully written out).
- `printStackTrace()` is forbidden — exceptions are logged or re-thrown,
  never silently swallowed.

### 4.11 JavaDoc Conventions

Every public class and every public or protected method must have a JavaDoc comment.
Comments describe the **contract** (what the element guarantees to callers), not the
implementation (how it is done internally).

**Class-level JavaDoc:**
- One sentence that names the responsibility of the class.
- If necessary, a second paragraph with relevant constraints or usage notes.
- Do **not** describe the internals or implementation strategy.

**Method-level JavaDoc:**
- First sentence: what the method does from the caller's perspective (imperative mood,
  e.g. "Returns the symbol identified by …", "Creates a new scan job for …").
- `@param` tags for every parameter whose meaning is not fully obvious from the name alone.
- `@return` tag unless the return type is `void` or the first sentence already describes the return value unambiguously.
- `@throws` tag **only** for checked exceptions (always) and for unchecked exceptions
  only if the method **directly** throws them (explicit `throw` statement) or if they
  are an unavoidable consequence of the contract (e.g. `NullPointerException` for a
  non-null contract). Do **not** document unchecked exceptions that originate somewhere
  deep in the call stack and are not part of the method's own contract.

**Overriding methods:**
- Annotated with `@Override` must carry exactly `/** @inheritDoc */` — no further text.
  The contract is already defined on the interface or superclass.

**Formatting:**
- Use standard HTML tags (`<p>`, `<ul>`, `<li>`, `<code>`, `<pre>`) for multi-paragraph
  or structured content. Do not use Markdown inside JavaDoc.
- Keep the first sentence on one line so that it appears correctly as the summary in
  generated documentation.
- Avoid padding with obvious or redundant sentences ("This method …", "This class …").

### 4.13 General Code Hygiene

- No use of `java.util.Date` and `java.util.Calendar` —
  use `java.time.*` exclusively.
- No field injection outside of test classes — prefer constructor injection.
- No `public` fields except in records (where it is convention).
- No generic `Exception`/`RuntimeException` as thrown types —
  use specific exception classes.

### 4.14 Threading

- **No direct thread handling.** Code must not create or start `Thread`
  instances (`new Thread(...)`, `thread.start()`).
- **No custom executor pool management.** Code must not create its own
  `ExecutorService` instances
  (`Executors.newFixedThreadPool(...)`, etc.).
- **Instead:** Use the Quarkus-managed worker pool via
  `@Inject ManagedExecutor` (MicroProfile Context Propagation) or by
  annotating methods with `@Blocking`/`@RunOnVirtualThread`,
  depending on the use case.
- **Asynchronous background work** runs either via
  `@Scheduled` methods (Quarkus Scheduler) or via the
  `ManagedExecutor`.
- **Rationale:** Quarkus integrates lifecycle management, metrics,
  context propagation (CDI request context, transactions) and
  shutdown behavior. Self-created threads bypass all of this and lead
  to resource leaks and untraceable behavior.

### 4.15 Tests

- Test classes reside in the same package as the class under test, but
  under `src/test/java`.
- Test class suffixes are mandatory and categorize the test type:

  | Suffix | Test Type | Description |
  |---|---|---|
  | `UnitTest` | Unit test | Isolated test of a single class, no external resources, mocks for dependencies. |
  | `InfraTest` | Infrastructure test | Test against a real technical component (database, filesystem, JGit repository), but without starting the full Quarkus application. Typically uses Testcontainers. |
  | `ApiTest` | API test | Test of a REST resource or webhook endpoint using `@QuarkusTest`, verifies HTTP behavior and serialization. |
  | `E2eTest` | End-to-end test | Full slice through all layers, including database and optionally a Git repository. Slower but realistic. |

- ArchUnit tests reside in `com.hlag.sourceviewer.architecture` and
  are executed on every build.

---

## 5. Data Model Conventions

### 5.1 Schema Source of Truth

The database schema is defined **exclusively** via Flyway migrations under
`src/main/resources/db/migration/`. Hibernate entities reflect this schema —
they are written by hand, not generated.

There are **no** schema-generation annotations (`hbm2ddl`, `@Table(schema=...)`)
that bypass Flyway. Java code reflects the database, not the other way around.

### 5.2 Migration Conventions

- Version scheme: `V{major}_{minor}__{snake_case_description}.sql`,
  starting at `V1_0__init.sql`.
- One migration changes exactly one logical feature (one table, one
  index, one column). Multiple small migrations are better than one large one.
- Migrations are **immutable** after merging into the main branch.
  Corrections are made via new migrations.

### 5.3 Database Conventions

- Table names: `snake_case`, singular (`symbol`, not `symbols`).
- Column names: `snake_case`.
- Primary keys are always named `id`.
- Foreign keys are named `<referenced_table>_id`
  (e.g. `repository_id`, `file_id`).
- Timestamps as `TIMESTAMPTZ`, never `TIMESTAMP` (without time zone).
- Boolean columns start with `is_`/`has_`/`was_`
  (`is_active`, `has_errors`).

---

## 6. Build and Tests

### 6.1 Standard Commands

```bash
mvn verify                      # Full build including ArchUnit
mvn quarkus:dev                 # Dev mode with hot reload
mvn package -Pnative            # Native image (optional)
```

### 6.2 ArchUnit as Build Gate

ArchUnit tests are part of the `test` phase. A violation of architecture
rules results in a failed build. This rule is **non-negotiable** — if a rule
no longer fits the project globally, it is discussed in this AGENT.md and
in the test, and changed jointly.

### 6.3 Testcontainers

Integration tests start PostgreSQL via Testcontainers. Local PostgreSQL
installations are not required. The test configuration uses Quarkus Dev
Services for seamless Testcontainers integration.

---

## 7. Performance Expectations

For a repository with approximately 7 million lines of Java code:
- **Initial scan:** completed in under 30 minutes.
- **Incremental scan** (typical commit, approximately 10 files changed):
  under 10 seconds until the index is updated.
- **Symbol lookup** (click on an identifier):
  under 100 milliseconds.
- **Full-text search** across all documents:
  under 500 milliseconds.
- **Code display of a single file:**
  under 200 milliseconds.

These values are targets, not guarantees — they define when performance
investigations are warranted.

---

## 8. Conventions for AI-Assisted Development

When this project is further developed with the help of an AI assistant:

- **Verify Java version before starting.** This project requires Java 21.
  Before beginning any work, run `java -version` and confirm the output
  shows version 21 (e.g. `openjdk version "21.x.x"`).
  If the version is incorrect, stop and ask the user how to proceed
  (e.g. which JDK to use, how to set `JAVA_HOME`, or whether to use a
  wrapper like `mvn -Djava.home=...`). Do not attempt to build or run
  tests with an incompatible Java version.
- **This file is authoritative.** In case of conflict between an AI
  recommendation and a rule here, this file wins.
- **Architecture rules are not circumvented.** If a rule is in the way,
  that is an occasion to discuss the rule — not to ignore it.
- **New classes must fit the schema.** Before creating a class, check:
  which layer does it belong to, what name suffix does it get, which
  imports are allowed?
- **Violations become visible in the build.** ArchUnit tests give clear
  error messages — take them seriously.
- **Abbreviations are avoided.** Both in source code and in comments
  and documentation, names are written out in full.

---

## 9. Glossary

| Term | Meaning |
|---|---|
| **Symbol** | A declaration in source code (class, method, field, ...) |
| **Reference** | A usage of a symbol at another location |
| **ParsedFile** | The complete parse result of a single file |
| **Token** | A lexical element (identifier, keyword, literal, ...) |
| **Use-Case** | A business-related operation (e.g. "scan repository") |
| **Port** | An interface through which the domain communicates with the outside world |
| **Adapter** | A concrete implementation of a port |
| **Wrapper / ValueObject** | A typed wrapper type around a primitive or simple value |
| **Full-text search** | Search over free text via PostgreSQL `tsvector`/`tsquery` |
| **Data transfer object** | A class that carries data between layers or across API boundaries |

---

## 10. Open Points / Roadmap

Points not yet decided, to be resolved during the project:

- **Authentication/Authorization**: implemented via OIDC (Keycloak in dev,
  swappable via env vars). All `/api/*` endpoints require a valid Bearer JWT or PAT.
  Personal Access Tokens (`svt_` prefix) are supported for headless/CI access.
  Open items: role-based access control (RBAC) beyond simple `@Authenticated`;
  frontend UI for managing personal access tokens.
- **Multi-branch support**: currently the default branch is primarily
  indexed. Multi-branch support to follow.
- **Cross-repository symbol resolution**: if repository A uses a class from
  repository B, it is currently unresolved. Later extension planned.
- **Frontend stack**: currently server-rendered HTML with Qute. Possibly
  a single-page application (React/Vue/Svelte) later.
- **Indexing of additional languages**: currently Java only. Kotlin, Groovy,
  Scala would be natural extensions — but would each require their own
  parser adapters.
